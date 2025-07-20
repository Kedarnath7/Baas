package baas.core;

import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class WAL {
    private final String filePath;
    private final File walFile;
    private RandomAccessFile raf;
    private final Object fileLock = new Object();

    private long checkpointPosition = 0;
    private final int maxWalSizeBytes;
    private final int walRetention;

    public WAL(String filePath, int maxWalSizeMB, int walRetentionFiles) throws IOException {
        this.filePath = filePath;
        this.maxWalSizeBytes = maxWalSizeMB * 1024 * 1024;
        this.walRetention = walRetentionFiles;
        this.walFile = new File(filePath);
        initializeFile();
    }

    private void initializeFile() throws IOException {
        if (!walFile.exists()) {
            walFile.createNewFile();
        }
        this.raf = new RandomAccessFile(walFile, "rw");
        this.raf.seek(raf.length());
    }

    public void log(Map<String, Object> entry) throws IOException {
        synchronized (fileLock) {
            entry.put("timestamp", System.currentTimeMillis());
            String jsonLine = new JSONObject(entry).toString() + "\n";
            raf.write(jsonLine.getBytes());
            raf.getFD().sync();

            // Rotate if needed
            if (raf.length() >= maxWalSizeBytes) {
                rotate();
            }
        }
    }

    public void markCheckpoint() throws IOException {
        synchronized (fileLock) {
            this.checkpointPosition = raf.getFilePointer();
        }
    }

    public void recoverAfterCheckpoint(Consumer<Map<String, Object>> processor) throws IOException {
        synchronized (fileLock) {
            raf.seek(checkpointPosition);
            String line;
            while ((line = raf.readLine()) != null) {
                processor.accept(new JSONObject(line).toMap());
            }
        }
    }

    public void recover(Consumer<Map<String, Object>> processor) throws IOException {
        synchronized (fileLock) {
            // First replay rotated logs
            for (File rotated : getRotatedLogs()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(rotated))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        processor.accept(new JSONObject(line).toMap());
                    }
                }
            }

            // Then replay current WAL
            raf.seek(0);
            String line;
            while ((line = raf.readLine()) != null) {
                processor.accept(new JSONObject(line).toMap());
            }
        }
    }

    public void replayAfterCheckpoint(StorageService storage) throws IOException {
        recoverAfterCheckpoint(entry -> {
            String collection = entry.get("collection").toString();
            String id = entry.get("id").toString();

            @SuppressWarnings("unchecked")
            Map<String, Object> document = (Map<String, Object>) entry.get("document");

            storage.insertDocument(collection, id, document, false); // don't re-log
        });
    }

    public void rotate() throws IOException {
        synchronized (fileLock) {
            raf.close();

            String rotatedName = filePath + "." + System.currentTimeMillis() + ".log";
            Files.move(Paths.get(filePath), Paths.get(rotatedName), StandardCopyOption.REPLACE_EXISTING);

            cleanupOldLogs();

            // Open new WAL
            initializeFile();
        }
    }

    private void cleanupOldLogs() {
        File dir = walFile.getParentFile();
        String baseName = walFile.getName();

        File[] rotated = dir.listFiles(f ->
                f.getName().startsWith(baseName + ".") && f.getName().endsWith(".log"));

        if (rotated != null && rotated.length > walRetention) {
            Arrays.stream(rotated)
                    .sorted(Comparator.comparingLong(File::lastModified).reversed())
                    .skip(walRetention)
                    .forEach(File::delete);
        }
    }

    private List<File> getRotatedLogs() {
        File dir = walFile.getParentFile();
        if (dir == null) {
            System.err.println("WAL parent directory is null. No rotated logs found.");
            return List.of();
        }

        String baseName = walFile.getName();

        File[] rotated = dir.listFiles(f ->
                f.getName().startsWith(baseName + ".") && f.getName().endsWith(".log"));

        if (rotated == null) return List.of();

        return Arrays.stream(rotated)
                .sorted(Comparator.comparing(File::getName))
                .toList();
    }


    public void close() throws IOException {
        if (raf != null) raf.close();
    }
}
