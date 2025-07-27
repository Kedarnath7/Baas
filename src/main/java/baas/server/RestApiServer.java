package baas.server;

import baas.core.DatabaseServiceImpl;
import baas.core.StorageService;
import com.minibaas.proto.DatabaseServiceProto.*;
import static spark.Spark.*;

public class RestApiServer {
    public static void main(String[] args) throws Exception {
        int httpPort = 8080; // REST server port
        int grpcPort = 9090; // gRPC server port (already used)

        // Start gRPC-based storage service (reuse your existing DatabaseServiceImpl)
        String walPath = "data/wal.log";
        String snapshotDir = "data/snapshots";
        StorageService storageService = new StorageService(walPath, snapshotDir);
        DatabaseServiceImpl dbService = new DatabaseServiceImpl(storageService);

        // Start REST API
        port(httpPort);
        System.out.println("REST API running on http://localhost:" + httpPort);

        // Insert endpoint (POST)
        post("/insert", (req, res) -> {
            String collection = req.queryParams("collection");
            String id = req.queryParams("id");
            String document = req.body();

            InsertResponse response = dbService.insertSync(collection, id, document);
            res.type("application/json");
            return String.format("{\"success\":%b, \"generatedIds\":%s, \"error\":\"%s\"}",
                    response.getSuccess(),
                    response.getGeneratedIdsList(),
                    response.getError());
        });

        // Get endpoint (GET)
        get("/get", (req, res) -> {
            String collection = req.queryParams("collection");
            String id = req.queryParams("id");

            GetResponse response = dbService.getSync(collection, id);
            res.type("application/json");
            if (response.getSuccess()) {
                return response.getDocument();
            } else {
                return String.format("{\"success\":false, \"error\":\"%s\"}", response.getError());
            }
        });

        // Query endpoint (GET)
        get("/query", (req, res) -> {
            String collection = req.queryParams("collection");
            String field = req.queryParams("field");
            String value = req.queryParams("value");

            QueryResponse response = dbService.querySync(collection, field, value);
            res.type("application/json");
            return String.format("{\"success\":%b, \"documents\":%s, \"error\":\"%s\"}",
                    response.getSuccess(),
                    response.getDocumentsList(),
                    response.getError());
        });
    }
}
