package miniBaas;

import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

public class WAL {
    private final String filePath;
    private final Object fileLock = new Object();
    private long checkpointPosition = 0;
    private RandomAccessFile raf;

    public WAL(String filePath) throws IOException {
        this.filePath = filePath;
        initializeFile();
    }

    private void initializeFile() throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            file.createNewFile();
        }
        this.raf = new RandomAccessFile(file, "rw");
    }

    public void log(Map<String, Object> entry) throws IOException {
        synchronized (fileLock) {
            String json = new JSONObject(entry).toString() + System.lineSeparator();
            raf.seek(raf.length());  // Go to end of file
            raf.write(json.getBytes());
        }
    }

    public void markCheckpoint() throws IOException {
        synchronized (fileLock) {
            this.checkpointPosition = raf.getFilePointer(); // Store current pointer
        }
    }

    public void recoverAfterCheckpoint(Consumer<Map<String, Object>> processor) throws IOException {
        synchronized (fileLock) {
            raf.seek(checkpointPosition);
            String line;
            while ((line = raf.readLine()) != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = new JSONObject(line).toMap();
                processor.accept(entry);
            }
        }
    }

    public void recover(Consumer<Map<String, Object>> processor) throws IOException {
        synchronized (fileLock) {
            raf.seek(0);  // Start from beginning
            String line;
            while ((line = raf.readLine()) != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = new JSONObject(line).toMap();
                processor.accept(entry);
            }
        }
    }

    public void replayAfterCheckpoint(StorageService storage) throws IOException {
        recoverAfterCheckpoint(entry -> {
            String collection = entry.get("collection").toString();
            String id = entry.get("id").toString();

            @SuppressWarnings("unchecked")
            Map<String, Object> document = (Map<String, Object>) entry.get("document");

            storage.insertDocument(collection, id, document, false);  // Don't log again
        });
    }

    public void close() throws IOException {
        if (raf != null) {
            raf.close();
        }
    }
}
