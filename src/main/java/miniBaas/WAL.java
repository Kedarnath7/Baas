package miniBaas;

import org.json.JSONObject;
//import io.grpc.stub.StreamObserver;
import java.io.*;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;

public class WAL {
    private final String filePath;
    private final Object fileLock = new Object();
    private long checkpointPosition = 0;
    private RandomAccessFile raf;

    public WAL(String filePath)throws IOException{
        this.filePath = filePath;
        initializeFile();
    }

    private void initializeFile() throws IOException{
        File file = new File(filePath);
        if(!file.exists()){
            file.createNewFile();
        }
        this.raf = new RandomAccessFile(file, "rw");
    }

    public void log(Map<String, Object> entry) throws IOException{
        synchronized(fileLock){
            try ( FileWriter fw = new FileWriter(filePath,true);
                  BufferedWriter bw = new BufferedWriter(fw);
                  PrintWriter out = new PrintWriter(bw)){
                out.println(new JSONObject(entry).toString());
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
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = new JSONObject(line).toMap();
                processor.accept(entry);
            }
        }
    }

//    public void recover(Consumer<Map<String,Object>> processor) throws IOException{
//        synchronized(fileLock){
//            File file = new File(filePath);
//            if(!file.exists()) return;
//
//            try(Scanner scanner = new Scanner(file)){
//                while(scanner.hasNextLine()){
//                    String line = scanner.nextLine();
//                    @SuppressWarnings("unchecked")
//                    Map<String,Object> entry = new JSONObject(line).toMap();
//                    processor.accept(entry);
//                }
//            }
//        }
//    }

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

    public void close() throws IOException {
        if (raf != null) {
            raf.close();
        }
    }
}