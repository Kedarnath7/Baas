package miniBaas;

import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class WALTest {

    private static final String TEST_WAL_PATH = "./testdata/testwal.log";
    private WAL wal;

    @BeforeEach
    public void setup() throws Exception {
        Files.createDirectories(new File("./testdata").toPath());
        File walFile = new File(TEST_WAL_PATH);
        if (walFile.exists()) walFile.delete();
        wal = new WAL(TEST_WAL_PATH);
    }

    @AfterEach
    public void cleanup() throws Exception {
        if (wal != null) wal.close();
    }

    @Test
    public void testLogAndRecover() throws Exception {
        Map<String, Object> entry = Map.of(
                "collection", "users",
                "id", "u1",
                "document", Map.of("name", "kedar", "age", 25)
        );

        wal.log(entry);

        List<Map<String, Object>> recovered = new ArrayList<>();
        wal.recover(recovered::add);

        assertEquals(1, recovered.size());
        assertEquals("users", recovered.get(0).get("collection"));
        assertEquals("u1", recovered.get(0).get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) recovered.get(0).get("document");
        assertEquals("kedar", doc.get("name"));
        assertEquals(25, doc.get("age"));
    }

    @Test
    public void testCheckpointAndRecoverAfterCheckpoint() throws Exception {
        Map<String, Object> beforeCheckpoint = Map.of(
                "collection", "logs",
                "id", "l1",
                "document", Map.of("msg", "before")
        );

        wal.log(beforeCheckpoint);
        wal.markCheckpoint();

        Map<String, Object> afterCheckpoint = Map.of(
                "collection", "logs",
                "id", "l2",
                "document", Map.of("msg", "after")
        );

        wal.log(afterCheckpoint);

        List<Map<String, Object>> recovered = new ArrayList<>();
        wal.recoverAfterCheckpoint(recovered::add);

        assertEquals(1, recovered.size());
        assertEquals("l2", recovered.get(0).get("id"));
    }
}
