package baas.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class StorageService {
    private final Map<String, NavigableMap<String, Map<String, Object>>> collections = new HashMap<>();
    private final Map<String, Map<String, Map<Object, Set<String>>>> indexes = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final WAL wal;
    private final String snapshotDir;

    // Constants for WAL keys
    private static final String KEY_OPERATION = "operation";
    private static final String KEY_COLLECTION = "collection";
    private static final String KEY_ID = "id";
    private static final String KEY_DOCUMENT = "document";

    public StorageService(String walPath, String snapshotDir) throws IOException {
        this.wal = new WAL(walPath,5, 3);
        this.snapshotDir = snapshotDir.endsWith(File.separator) ? snapshotDir : snapshotDir + File.separator;
        File snapshotFolder = new File(this.snapshotDir);
        if (!snapshotFolder.exists() && !snapshotFolder.mkdirs()) {
            throw new IOException("Failed to create snapshot directory");
        }
        recoverFromWAL();
    }

    @SuppressWarnings("unchecked")
    private void recoverFromWAL() {
        try {
            wal.recover(entry -> {
                if (entry == null ||
                        entry.get(KEY_COLLECTION) == null ||
                        entry.get(KEY_ID) == null ||
                        entry.get(KEY_DOCUMENT) == null) return;

                String collection = entry.get(KEY_COLLECTION).toString();
                String id = entry.get(KEY_ID).toString();

                if (!(entry.get(KEY_DOCUMENT) instanceof Map)) return;

                Map<String, Object> document = (Map<String, Object>) entry.get(KEY_DOCUMENT);
                insertDocument(collection, id, document, false);
            });
        } catch (IOException e) {
            System.err.println("WAL recovery failed: " + e.getMessage());
        }
    }

    //v1
//    public void insertDocument(String collection, String id, Map<String, Object> document, boolean logToWAL) {
//        lock.writeLock().lock();
//        try {
//            collections.computeIfAbsent(collection, k -> new TreeMap<>());
//            collections.get(collection).put(id, new HashMap<>(document));
//
//            // Update indexes
//            if (indexes.containsKey(collection)) {
//                for (String indexedField : indexes.get(collection).keySet()) {
//                    if (document.containsKey(indexedField)) {
//                        Object value = document.get(indexedField);
//                        indexes.get(collection).get(indexedField)
//                                .computeIfAbsent(value, k -> new HashSet<>())
//                                .add(id);
//                    }
//                }
//            }
//
//            if (logToWAL) {
//                wal.log(Map.of(
//                        KEY_OPERATION, "insert",
//                        KEY_COLLECTION, collection,
//                        KEY_ID, id,
//                        KEY_DOCUMENT, document
//                ));
//            }
//        } catch (IOException e) {
//            throw new RuntimeException("WAL write failed", e);
//        } finally {
//            lock.writeLock().unlock();
//        }
//    }

    //v2

    public void insertDocument(String collection, String id, Map<String, Object> document, boolean logToWAL) {
        lock.writeLock().lock();
        try {
            // Calculate expiry time if TTL is specified
            Map<String, Object> docToStore = new HashMap<>(document);
            if (document.containsKey("_ttl_ms")) {
                Object ttlObj = document.get("_ttl_ms");
                if (ttlObj instanceof Number) {
                    long ttl = ((Number) ttlObj).longValue();
                    long expiryTime = System.currentTimeMillis() + ttl;
                    docToStore.put("_expiry", expiryTime);
                }
            }

            collections.computeIfAbsent(collection, k -> new TreeMap<>());
            collections.get(collection).put(id, docToStore);

            // Update indexes
            if (indexes.containsKey(collection)) {
                for (String indexedField : indexes.get(collection).keySet()) {
                    if (docToStore.containsKey(indexedField)) {
                        Object value = docToStore.get(indexedField);
                        indexes.get(collection).get(indexedField)
                                .computeIfAbsent(value, k -> new HashSet<>())
                                .add(id);
                    }
                }
            }

            if (logToWAL) {
                Map<String, Object> walEntry = new HashMap<>();
                walEntry.put(KEY_OPERATION, "insert");
                walEntry.put(KEY_COLLECTION, collection);
                walEntry.put(KEY_ID, id);
                walEntry.put(KEY_DOCUMENT, docToStore);
                wal.log(walEntry);
            }
        } catch (IOException e) {
            throw new RuntimeException("WAL write failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }



    public void takeSnapshot() throws IOException {
        lock.writeLock().lock();
        try (ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(Paths.get(snapshotDir, "snapshot_" + System.currentTimeMillis() + ".dat").toString()))) {
            out.writeObject(collections);
            wal.markCheckpoint();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, NavigableMap<String, Map<String, Object>>> getAllData() {
        lock.readLock().lock();
        try {
            Map<String, NavigableMap<String, Map<String, Object>>> copy = new HashMap<>();
            for (Map.Entry<String, NavigableMap<String, Map<String, Object>>> entry : collections.entrySet()) {
                copy.put(entry.getKey(), new TreeMap<>(entry.getValue()));
            }
            return copy;
        } finally {
            lock.readLock().unlock();
        }
    }


    public void restoreData(Map<String, NavigableMap<String, Map<String, Object>>> data) {
        lock.writeLock().lock();
        try {
            collections.clear();
            collections.putAll(data);
            rebuildAllIndexes();
        } finally {
            lock.writeLock().unlock();
        }
    }


    private void rebuildAllIndexes() {
        indexes.forEach((collection, fieldMap) -> {
            fieldMap.forEach((field, valueMap) -> {
                valueMap.clear();
                NavigableMap<String, Map<String, Object>> docs = collections.getOrDefault(collection, new TreeMap<>());
                for (Map.Entry<String, Map<String, Object>> entry : docs.entrySet()) {
                    String id = entry.getKey();
                    Map<String, Object> doc = entry.getValue();
                    if (doc.containsKey(field)) {
                        Object value = doc.get(field);
                        valueMap.computeIfAbsent(value, k -> new HashSet<>()).add(id);
                    }
                }
            });
        });
    }
    //v1
//    public Map<String, Object> getDocument(String collection, String id) {
//        lock.readLock().lock();
//        try {
//            if (!collections.containsKey(collection)) {
//                return null;
//            }
//            Map<String, Object> doc = collections.get(collection).get(id);
//            return doc != null ? new HashMap<>(doc) : null;
//        } finally {
//            lock.readLock().unlock();
//        }
//    }
//v2
    public Map<String, Object> getDocument(String collection, String id) {
        lock.writeLock().lock();  // Use write lock because we might remove expired doc
        try {
            if (!collections.containsKey(collection)) {
                return null;
            }

            Map<String, Object> doc = collections.get(collection).get(id);
            if (doc == null) return null;

            // TTL check
            if (doc.containsKey("_expiry")) {
                Object expiryObj = doc.get("_expiry");
                if (expiryObj instanceof Number) {
                    long expiryTime = ((Number) expiryObj).longValue();
                    if (System.currentTimeMillis() > expiryTime) {
                        // Lazy deletion of expired doc
                        collections.get(collection).remove(id);
                        return null;
                    }
                }
            }

            return new HashMap<>(doc);
        } finally {
            lock.writeLock().unlock();
        }
    }

    //v1
//    public List<Map<String, Object>> queryDocuments(String collection, String field, Object value) {
//        lock.readLock().lock();
//        try {
//            List<Map<String, Object>> results = new ArrayList<>();
//            if (!collections.containsKey(collection)) {
//                return results;
//            }
//
//            // Use index if available
//            if (indexes.containsKey(collection) && indexes.get(collection).containsKey(field)) {
//                Set<String> ids = indexes.get(collection).get(field).get(value);
//                if (ids != null) {
//                    for (String id : ids) {
//                        Map<String, Object> doc = collections.get(collection).get(id);
//                        if (doc != null) {
//                            results.add(new HashMap<>(doc));
//                        }
//                    }
//                    return results;
//                }
//            }
//
//            // Fallback to full scan
//            for (Map<String, Object> doc : collections.get(collection).values()) {
//                if (doc.containsKey(field) && Objects.equals(doc.get(field), value)) {
//                    results.add(new HashMap<>(doc));
//                }
//            }
//            return results;
//        } finally {
//            lock.readLock().unlock();
//        }
//    }
//v2
    public List<Map<String, Object>> queryDocuments(String collection, String field, Object value) {
        lock.writeLock().lock(); // Upgrade to writeLock for lazy expiry deletion
        try {
            List<Map<String, Object>> results = new ArrayList<>();
            if (!collections.containsKey(collection)) {
                return results;
            }

            NavigableMap<String, Map<String, Object>> docs = collections.get(collection);
            long now = System.currentTimeMillis();

            // Use index if available
            if (indexes.containsKey(collection) && indexes.get(collection).containsKey(field)) {
                Set<String> ids = indexes.get(collection).get(field).get(value);
                if (ids != null) {
                    Iterator<String> iterator = ids.iterator();
                    while (iterator.hasNext()) {
                        String id = iterator.next();
                        Map<String, Object> doc = docs.get(id);
                        if (doc != null) {
                            if (isExpired(doc, now)) {
                                docs.remove(id); // lazy removal
                                iterator.remove(); // clean index
                            } else {
                                results.add(new HashMap<>(doc));
                            }
                        }
                    }
                    return results;
                }
            }

            // Fallback to full scan
            Iterator<Map.Entry<String, Map<String, Object>>> iterator = docs.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                Map<String, Object> doc = entry.getValue();
                if (isExpired(doc, now)) {
                    iterator.remove(); // lazy removal
                    continue;
                }

                if (doc.containsKey(field) && Objects.equals(doc.get(field), value)) {
                    results.add(new HashMap<>(doc));
                }
            }

            return results;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void createIndex(String collection, String field) {
        lock.writeLock().lock();
        try {
            indexes.computeIfAbsent(collection, k -> new HashMap<>());
            indexes.get(collection).computeIfAbsent(field, k -> new HashMap<>());

            if (collections.containsKey(collection)) {
                for (Map.Entry<String, Map<String, Object>> entry : collections.get(collection).entrySet()) {
                    String id = entry.getKey();
                    Map<String, Object> doc = entry.getValue();
                    if (doc.containsKey(field)) {
                        Object value = doc.get(field);
                        indexes.get(collection).get(field)
                                .computeIfAbsent(value, k -> new HashSet<>())
                                .add(id);
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    public boolean isExpired(Map<String, Object> doc) {
        return isExpired(doc, System.currentTimeMillis());
    }

    public boolean isExpired(Map<String, Object> doc, long now) {
        if (doc.containsKey("_expiry")) {
            Object expiryObj = doc.get("_expiry");
            if (expiryObj instanceof Number) {
                return now > ((Number) expiryObj).longValue();
            }
        }
        return false;
    }

}
