package miniBaas;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize storage with WAL and snapshot directory
        StorageService storage = new StorageService("wal.log", "snapshots/");  // Changed constructor

        // Create indexes (unchanged)
        storage.createIndex("users", "name");
        storage.createIndex("products", "category");

        // Start scheduled snapshots (NEW)
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                storage.takeSnapshot();
                System.out.println("Snapshot taken at " + System.currentTimeMillis());
            } catch (IOException e) {
                System.err.println("Snapshot failed: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.MINUTES);  // Every 5 minutes

        // Start gRPC server (unchanged)
        Server server = ServerBuilder.forPort(50051)
                .addService(new DatabaseServiceImpl(storage))
                .build();

        server.start();
        System.out.println("Server started on port 50051");

        // Enhanced shutdown hook (NEW)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            try {
                storage.takeSnapshot();  // Final snapshot before exit
            } catch (IOException e) {
                System.err.println("Final snapshot failed: " + e.getMessage());
            }
            scheduler.shutdown();
            server.shutdown();
        }));

        server.awaitTermination();
    }
}