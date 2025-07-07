package miniBaas;

import java.io.*;
import java.util.*;

public class SnapshotManager {
    private final StorageService storage;
    private final WAL wal;
    private final String snapshotDir;

    public SnapshotManager(StorageService storage, WAL wal, String snapshotDir){
        this.storage = storage;
        this.wal = wal;
        this.snapshotDir = snapshotDir;
        new File(snapshotDir).mkdirs();
    }

    public void takeSnapsnot(){
        try(ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(snapshotDir + " snapshot_" + System.currentTimeMillis() + ".dat")
        )){
            synchronized(storage){
                out.writeObject(storage.getAllData());
                wal.markCheckPoint();
            }
        }catch (IOException e){
            System.err.println("Snapshot failed: "+e.getMessage());
        }
    }

    public void restore() throws IOException, ClassNotFoundException{
        File latest = getLatestSnapshot();
        try(ObjectInputStream in = new ObjectInputStream(new FileInputStream(latest))){
            Map<String, Map<String, Map<String, Object>>> data = (Map<String, Map<String, Map<String, Object>>>) in.readObject();
        storage.restoreData(date);
        wal.replayAfterheckpoint();
        }
    }
}
