package miniBaas;

import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        // Load config from classpath
        ConfigLoader config = new ConfigLoader("config.properties");

        String token = config.get("auth.token");
        String certResourcePath = config.get("grpc.cert");
        String keyResourcePath = config.get("grpc.key");

        // Copy classpath certs to temp files
        File certFile = extractResourceToTempFile(certResourcePath);
        File keyFile = extractResourceToTempFile(keyResourcePath);

        // Initialize storage
        StorageService storage = new StorageService("data/wal.log", "snapshots");
        storage.createIndex("users", "name");
        storage.createIndex("products", "category");

        // Schedule periodic snapshots
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                storage.takeSnapshot();
                System.out.println("Snapshot taken at " + System.currentTimeMillis());
            } catch (IOException e) {
                System.err.println("Snapshot failed: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.MINUTES);

        // Secure gRPC server with TLS and token auth
        Server server = NettyServerBuilder.forPort(50051)
                .useTransportSecurity(certFile, keyFile)
                .addService(new AuthInterceptor(token).intercept(new DatabaseServiceImpl(storage)))

                .build();

        server.start();
        System.out.println("Server started securely on port 50051");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            try {
                storage.takeSnapshot();
            } catch (IOException e) {
                System.err.println("Final snapshot failed: " + e.getMessage());
            }
            scheduler.shutdown();
            server.shutdown();
        }));

        server.awaitTermination();
    }

    // Utility to extract classpath resource to a temp file (needed by Netty)
    private static File extractResourceToTempFile(String resourcePath) throws IOException {
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) throw new IOException("Resource not found: " + resourcePath);
            File tempFile = File.createTempFile("cert-", ".tmp");
            tempFile.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                input.transferTo(out);
            }
            return tempFile;
        }
    }
}
