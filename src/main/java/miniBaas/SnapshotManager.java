package miniBaas;

import java.io.*;
import java.util.*;
import java.util.zip.*;

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

        try (ObjectOutputStream out = new ObjectOutputStream(
                new GZIPOutputStream(new FileOutputStream(snapshotFile)))) {

            synchronized (storage) {
                // Use a plain Map for serialization compatibility
                Map<String, Map<String, Map<String, Object>>> plainMap = new HashMap<>();
                for (var entry : storage.getAllData().entrySet()) {
                    plainMap.put(entry.getKey(), new HashMap<>(entry.getValue()));
                }

                out.writeObject(plainMap);
                wal.markCheckpoint();
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

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Map<String, Object>>> raw =
                    (Map<String, Map<String, Map<String, Object>>>) in.readObject();

            Map<String, NavigableMap<String, Map<String, Object>>> restoredData = new HashMap<>();
            for (Map.Entry<String, Map<String, Map<String, Object>>> entry : raw.entrySet()) {
                restoredData.put(entry.getKey(), new TreeMap<>(entry.getValue()));
            }

            synchronized (storage) {
                storage.restoreData(restoredData);
                wal.recoverAfterCheckpoint(entry -> {
                    String collection = entry.get("collection").toString();
                    String id = entry.get("id").toString();
                    Map<String, Object> document = (Map<String, Object>) entry.get("document");
                    storage.insertDocument(collection, id, document, false);
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
