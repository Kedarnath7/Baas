package baas.core;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SnapshotManager {
    private final StorageService storage;
    private final WAL wal;
    private final String snapshotDir;

    public SnapshotManager(StorageService storage, WAL wal, String snapshotDir) {
        this.storage = storage;
        this.wal = wal;
        this.snapshotDir = snapshotDir.endsWith(File.separator) ? snapshotDir : snapshotDir + File.separator;
        new File(this.snapshotDir).mkdirs();
    }

    public void takeSnapshot() throws IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        File snapshotFile = new File(snapshotDir, "snapshot_" + timestamp + ".gz"); // using .gz for compressed format
        long snapshotTimestamp = Long.parseLong(timestamp);

        try (ObjectOutputStream out = new ObjectOutputStream(
                new GZIPOutputStream(new FileOutputStream(snapshotFile)))) {
//v1
//            synchronized (storage) {
//                // Use a plain Map for serialization compatibility
//                Map<String, Map<String, Map<String, Object>>> plainMap = new HashMap<>();
//                for (var entry : storage.getAllData().entrySet()) {
//                    plainMap.put(entry.getKey(), new HashMap<>(entry.getValue()));
//                }
//
//                out.writeObject(plainMap);
//                out.writeLong(snapshotTimestamp);
//                wal.markCheckpoint();
//            }
//v2
            synchronized (storage) {
                Map<String, Map<String, Map<String, Object>>> plainMap = new HashMap<>();
                for (var entry : storage.getAllData().entrySet()) {
                    Map<String, Map<String, Object>> filtered = new HashMap<>();
                    for (var docEntry : entry.getValue().entrySet()) {
                        if (!storage.isExpired(docEntry.getValue(), snapshotTimestamp)) {
                            filtered.put(docEntry.getKey(), docEntry.getValue());
                        }
                    }
                    if (!filtered.isEmpty()) {
                        plainMap.put(entry.getKey(), filtered);
                    }
                }
                out.writeUTF("SNAPSHOT_V1");
                out.writeObject(plainMap);
                out.writeLong(snapshotTimestamp);
                wal.markCheckpoint();
                wal.rotate();
            }

            cleanupOldSnapshots(3); // Retain only 3 recent snapshots
        }
    }

    public void restore() throws IOException, ClassNotFoundException {
        File latest = getLatestSnapshot();
        if (latest == null) {
            throw new FileNotFoundException("No snapshots available");
        }

        try (ObjectInputStream in = new ObjectInputStream(
                new GZIPInputStream(new FileInputStream(latest)))) {

            String version = in.readUTF();
            if (!"SNAPSHOT_V1".equals(version)) {
                throw new IOException("Invalid snapshot format or version");
            }

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Map<String, Object>>> raw =
                    (Map<String, Map<String, Map<String, Object>>>) in.readObject();

            long snapshotTimestamp = in.readLong();
            Map<String, NavigableMap<String, Map<String, Object>>> restoredData = new HashMap<>();
            for (Map.Entry<String, Map<String, Map<String, Object>>> entry : raw.entrySet()) {
                restoredData.put(entry.getKey(), new TreeMap<>(entry.getValue()));
            }

            synchronized (storage) {
                storage.restoreData(restoredData);
                wal.recoverAfterCheckpoint(entry -> {

                    Object tsObj = entry.get("timestamp");
                    if (tsObj != null && Long.parseLong(tsObj.toString()) > snapshotTimestamp) {
                        String collection = entry.get("collection").toString();
                        String id = entry.get("id").toString();
                        Map<String, Object> document = (Map<String, Object>) entry.get("document");
                        storage.insertDocument(collection, id, document, false);
                    }
                });
            }
        }
    }

    private File getLatestSnapshot() {
        File[] snapshots = new File(snapshotDir).listFiles(
                file -> file.getName().startsWith("snapshot_") && file.getName().endsWith(".gz"));

        if (snapshots == null || snapshots.length == 0) {
            return null;
        }

        return Arrays.stream(snapshots)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }

    private void cleanupOldSnapshots(int keepLast) {
        File[] snapshots = new File(snapshotDir).listFiles(
                file -> file.getName().startsWith("snapshot_") && file.getName().endsWith(".gz"));

        if (snapshots != null && snapshots.length > keepLast) {
            Arrays.stream(snapshots)
                    .sorted(Comparator.comparingLong(File::lastModified).reversed())
                    .skip(keepLast)
                    .forEach(File::delete);
        }
    }
}
