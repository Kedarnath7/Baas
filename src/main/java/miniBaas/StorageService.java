package miniBaas;

import java.io.*;
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
        this.wal = new WAL(walPath);
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

    public void insertDocument(String collection, String id, Map<String, Object> document, boolean logToWAL) {
        lock.writeLock().lock();
        try {
            collections.computeIfAbsent(collection, k -> new TreeMap<>());
            collections.get(collection).put(id, new HashMap<>(document));

            // Update indexes
            if (indexes.containsKey(collection)) {
                for (String indexedField : indexes.get(collection).keySet()) {
                    if (document.containsKey(indexedField)) {
                        Object value = document.get(indexedField);
                        indexes.get(collection).get(indexedField)
                                .computeIfAbsent(value, k -> new HashSet<>())
                                .add(id);
                    }
                }
            }

            if (logToWAL) {
                wal.log(Map.of(
                        KEY_OPERATION, "insert",
                        KEY_COLLECTION, collection,
                        KEY_ID, id,
                        KEY_DOCUMENT, document
                ));
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

    public Map<String, Object> getDocument(String collection, String id) {
        lock.readLock().lock();
        try {
            if (!collections.containsKey(collection)) {
                return null;
            }
            Map<String, Object> doc = collections.get(collection).get(id);
            return doc != null ? new HashMap<>(doc) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Map<String, Object>> queryDocuments(String collection, String field, Object value) {
        lock.readLock().lock();
        try {
            List<Map<String, Object>> results = new ArrayList<>();
            if (!collections.containsKey(collection)) {
                return results;
            }

            // Use index if available
            if (indexes.containsKey(collection) && indexes.get(collection).containsKey(field)) {
                Set<String> ids = indexes.get(collection).get(field).get(value);
                if (ids != null) {
                    for (String id : ids) {
                        Map<String, Object> doc = collections.get(collection).get(id);
                        if (doc != null) {
                            results.add(new HashMap<>(doc));
                        }
                    }
                    return results;
                }
            }

            // Fallback to full scan
            for (Map<String, Object> doc : collections.get(collection).values()) {
                if (doc.containsKey(field) && Objects.equals(doc.get(field), value)) {
                    results.add(new HashMap<>(doc));
                }
            }
            return results;
        } finally {
            lock.readLock().unlock();
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
}
