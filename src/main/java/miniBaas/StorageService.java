package miniBaas;

import java.io.*;
import java.util.*;
import org.json.JSONObject;
import java.util.function.Consumer;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class StorageService {
    private final Map<String, NavigableMap<String, Map<String, Object>>> collections = new HashMap<>();
    private final Map<String, Map<String, Map<Object, Set<String>>>> indexes = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final WAL wal;
    private final String snapshotDir;

    public StorageService(String walPath, String snapshotDir) throws IOException {
        this.wal = new WAL(walPath);
        this.snapshotDir = snapshotDir;
        new File(snapshotDir).mkdirs();
        recoverFromWAL();
    }

    private void recoverFromWAL() {
        try {
            wal.recover(entry -> {
                String collection = entry.get("collection").toString();
                String id = entry.get("id").toString();
                Map<String, Object> document = (Map<String, Object>) entry.get("document");
                insertDocument(collection, id, document, false);
            });
        } catch (IOException e) {
            System.err.println("WAL recovery failed: " + e.getMessage());
        }
    }
//    private void recoverFromWAL() {
//        try {
//            wal.recover((Map<String, Object> entry) -> {
//                String collection = entry.get("collection").toString();
//                String id = entry.get("id").toString();
//
//                @SuppressWarnings("unchecked")
//                Map<String, Object> document = (Map<String, Object>) entry.get("document");
//
//                insertDocument(collection, id, document, false);
//            });
//        } catch (IOException e) {
//            System.err.println("WAL recovery failed: " + e.getMessage());
//        }
//    }

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
                        "operation", "insert",
                        "collection", collection,
                        "id", id,
                        "document", document
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
                new FileOutputStream(snapshotDir + "snapshot_" + System.currentTimeMillis() + ".dat"))) {
            out.writeObject(collections);
            wal.markCheckpoint();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void rebuildAllIndexes() {
        indexes.forEach((collection, fieldMap) -> {
            if (collections.containsKey(collection)) {
                fieldMap.forEach((field, valueMap) -> {
                    valueMap.clear();
                    collections.get(collection).forEach((id, doc) -> {
                        if (doc.containsKey(field)) {
                            valueMap.computeIfAbsent(doc.get(field), k -> new HashSet<>()).add(id);
                        }
                    });
                });
            }
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

            // Build index from existing docs
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
