package miniBaas;

import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SnapshotManagerTest {

    private static final String TEST_SNAPSHOT_DIR = "./testdata/snapshots/";
    private static final String TEST_WAL_PATH = "./testdata/snapshot_wal.log";

    private StorageService storage;
    private WAL wal;
    private SnapshotManager snapshotManager;

    @BeforeEach
    public void setup() throws Exception {
        // Clean up
        Files.createDirectories(new File(TEST_SNAPSHOT_DIR).toPath());
        for (File f : new File(TEST_SNAPSHOT_DIR).listFiles()) {
            if (f.getName().endsWith(".gz")) f.delete();
        }

        File walFile = new File(TEST_WAL_PATH);
        if (walFile.exists()) walFile.delete();

        storage = new StorageService(TEST_WAL_PATH, TEST_SNAPSHOT_DIR);
        wal = new WAL(TEST_WAL_PATH,5,3);
        snapshotManager = new SnapshotManager(storage, wal, TEST_SNAPSHOT_DIR);
    }

    @AfterEach
    public void teardown() throws Exception {
        if (wal != null) wal.close();
    }

    @Test
    public void testSnapshotCreationAndRestore() throws Exception {
        // Step 1: Insert and snapshot
        storage.insertDocument("testCol", "id1", Map.of("val", 123), false);
        snapshotManager.takeSnapshot();

        // Ensure snapshot exists
        File[] snapshotFiles = new File(TEST_SNAPSHOT_DIR).listFiles(f -> f.getName().endsWith(".gz"));
        assertNotNull(snapshotFiles);
        assertEquals(1, snapshotFiles.length);

        // Step 2: Restore into fresh storage
        StorageService freshStorage = new StorageService(TEST_WAL_PATH, TEST_SNAPSHOT_DIR);
        WAL freshWal = new WAL(TEST_WAL_PATH,5,3);
        SnapshotManager freshManager = new SnapshotManager(freshStorage, freshWal, TEST_SNAPSHOT_DIR);
        freshManager.restore();

        Map<String, Object> restored = freshStorage.getDocument("testCol", "id1");
        assertNotNull(restored);
        assertEquals(123, restored.get("val"));

        freshWal.close();
    }

    @Test
    public void testSnapshotRetentionLimit() throws Exception {
        // Create 5 snapshots
        for (int i = 0; i < 5; i++) {
            storage.insertDocument("col", "id" + i, Map.of("x", i), false);
            snapshotManager.takeSnapshot();
            Thread.sleep(5); // Ensure different timestamps
        }

        File[] snapshotFiles = new File(TEST_SNAPSHOT_DIR).listFiles(f -> f.getName().endsWith(".gz"));
        assertNotNull(snapshotFiles);
        assertEquals(3, snapshotFiles.length); // Only last 3 retained
    }
}
