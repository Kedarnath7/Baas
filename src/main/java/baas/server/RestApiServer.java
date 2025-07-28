package baas.server;

import baas.core.DatabaseServiceImpl;
import baas.core.StorageService;
import com.minibaas.proto.DatabaseServiceProto.*;
import org.json.JSONObject;

import static spark.Spark.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RestApiServer {
    private static final String AUTH_COLLECTION = "signins";
    private static final Map<String, Long> activeTokens = new ConcurrentHashMap<>();
    private static final long TOKEN_EXPIRY_MS = 30 * 60 * 1000; // 30 min

    public static void main(String[] args) throws Exception {
        int httpPort = 8080;

        Path frontendPath = Paths.get("frontend");
        if (!Files.exists(frontendPath) || !Files.isDirectory(frontendPath)) {
            throw new IllegalStateException("Frontend directory not found at: " + frontendPath.toAbsolutePath());
        }

        String walPath = "data/wal.log";
        String snapshotDir = "data/snapshots";
        StorageService storageService = new StorageService(walPath, snapshotDir);
        DatabaseServiceImpl dbService = new DatabaseServiceImpl(storageService);

        port(httpPort);
        staticFiles.externalLocation(frontendPath.toAbsolutePath().toString());

        System.out.println("REST API + Frontend running on http://localhost:" + httpPort);

        // Signup
        post("/signup", (req, res) -> {
            JSONObject body = new JSONObject(req.body());
            String username = body.getString("username");
            String password = Base64.getEncoder().encodeToString(body.getString("password").getBytes()); // simple hash

            String docId = UUID.randomUUID().toString();
            JSONObject userDoc = new JSONObject()
                    .put("username", username)
                    .put("password", password);

            dbService.insertSync(AUTH_COLLECTION, docId, userDoc.toString());
            res.type("application/json");
            return "{\"success\":true,\"message\":\"Signup successful\"}";
        });

        // Login
        post("/login", (req, res) -> {
            JSONObject body = new JSONObject(req.body());
            String username = body.getString("username");
            String password = Base64.getEncoder().encodeToString(body.getString("password").getBytes());

            QueryResponse response = dbService.querySync(AUTH_COLLECTION, "username", username);
            boolean authenticated = false;

            for (String docStr : response.getDocumentsList()) {
                JSONObject doc = new JSONObject(docStr);
                if (password.equals(doc.getString("password"))) {
                    authenticated = true;
                    break;
                }
            }

            res.type("application/json");
            if (authenticated) {
                String token = UUID.randomUUID().toString();
                activeTokens.put(token, System.currentTimeMillis() + TOKEN_EXPIRY_MS);
                return String.format("{\"success\":true,\"token\":\"%s\"}", token);
            } else {
                return "{\"success\":false,\"error\":\"Invalid credentials\"}";
            }
        });

        // Authentication middleware
        before((req, res) -> {
            String path = req.pathInfo();
            if (path.startsWith("/insert") || path.startsWith("/get") || path.startsWith("/query")) {
                String token = req.headers("auth-token");
                if (token == null || !activeTokens.containsKey(token) ||
                        System.currentTimeMillis() > activeTokens.get(token)) {
                    halt(401, "{\"success\":false,\"error\":\"Invalid or expired token\"}");
                }
            }
        });

        // Insert
        post("/insert", (req, res) -> {
            String collection = req.queryParams("collection");
            String id = req.queryParams("id");
            String document = req.body();

            InsertResponse response = dbService.insertSync(collection, id, document);
            res.type("application/json");
            return String.format("{\"success\":%b,\"generatedIds\":%s,\"error\":\"%s\"}",
                    response.getSuccess(),
                    response.getGeneratedIdsList(),
                    response.getError());
        });

        // Get
        get("/get", (req, res) -> {
            String collection = req.queryParams("collection");
            String id = req.queryParams("id");

            GetResponse response = dbService.getSync(collection, id);
            res.type("application/json");
            if (response.getSuccess()) {
                return response.getDocument();
            } else {
                return String.format("{\"success\":false,\"error\":\"%s\"}", response.getError());
            }
        });

        // Query
        get("/query", (req, res) -> {
            String collection = req.queryParams("collection");
            String field = req.queryParams("field");
            String value = req.queryParams("value");

            QueryResponse response = dbService.querySync(collection, field, value);
            res.type("application/json");
            return String.format("{\"success\":%b,\"documents\":%s,\"error\":\"%s\"}",
                    response.getSuccess(),
                    response.getDocumentsList(),
                    response.getError());
        });

        // Fallback for SPA
        get("/*", (req, res) -> {
            res.type("text/html");
            return new String(Files.readAllBytes(frontendPath.resolve("index.html")));
        });
    }
}
