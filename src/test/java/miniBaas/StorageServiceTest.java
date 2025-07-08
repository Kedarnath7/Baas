package miniBaas;

import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class StorageServiceTest {

    private static final String WAL_PATH = "./testdata/wal.log";
    private static final String SNAPSHOT_DIR = "./testdata/snapshots/";
    private StorageService storage;

    @BeforeEach
    public void setup() throws Exception {
        // Ensure snapshot directory exists
        Files.createDirectories(Paths.get(SNAPSHOT_DIR));

        // Clear WAL file contents without deleting the file (to avoid locking issues)
        try {
            Files.write(Paths.get(WAL_PATH), new byte[0]);
        } catch (IOException ignored) {}

        // Delete old snapshot files (.gz only)
        File snapshotDir = new File(SNAPSHOT_DIR);
        for (File f : Objects.requireNonNull(snapshotDir.listFiles())) {
            if (f.getName().endsWith(".gz")) {
                f.delete();
            }
        }

        storage = new StorageService(WAL_PATH, SNAPSHOT_DIR);
    }

    @Test
    public void testInsertAndGet() {
        Map<String, Object> doc = Map.of("name", "kedar", "age", 25);
        storage.insertDocument("users", "u1", doc, false);

        Map<String, Object> retrieved = storage.getDocument("users", "u1");

        assertNotNull(retrieved);
        assertEquals("kedar", retrieved.get("name"));
        assertEquals(25, retrieved.get("age"));
    }

    @Test
    public void testQuery() {
        storage.insertDocument("users", "u1", Map.of("role", "admin"), false);
        storage.insertDocument("users", "u2", Map.of("role", "user"), false);

        List<Map<String, Object>> results = storage.queryDocuments("users", "role", "admin");

        assertEquals(1, results.size());
        assertEquals("admin", results.get(0).get("role"));
    }

    @Test
    public void testSnapshotAndRecovery() throws Exception {
        storage.insertDocument("users", "u3", Map.of("job", "engineer"), false);


        SnapshotManager sm1 = new SnapshotManager(storage, new WAL(WAL_PATH), SNAPSHOT_DIR);
        sm1.takeSnapshot();

        StorageService restored = new StorageService(WAL_PATH, SNAPSHOT_DIR);
        WAL restoredWAL = new WAL(WAL_PATH);
        SnapshotManager sm2 = new SnapshotManager(restored, restoredWAL, SNAPSHOT_DIR);
        sm2.restore();

        Map<String, Object> doc = restored.getDocument("users", "u3");
        assertNotNull(doc);
        assertEquals("engineer", doc.get("job"));

        restoredWAL.close();
    }

}
