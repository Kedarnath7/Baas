package miniBaas;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize storage with WAL
        StorageService storage = new StorageService("wal.log");

        // Create some indexes
        storage.createIndex("users", "name");
        storage.createIndex("products", "category");

        // Start gRPC server
        Server server = ServerBuilder.forPort(50051)
                .addService(new DatabaseServiceImpl(storage))
                .build();

        server.start();
        System.out.println("Server started on port 50051");

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            server.shutdown();
        }));

        server.awaitTermination();
    }
}