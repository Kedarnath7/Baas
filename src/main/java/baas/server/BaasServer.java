package baas.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import baas.core.DatabaseServiceImpl;
import baas.core.StorageService;

public class BaasServer {
    public static void main(String[] args) throws Exception {
        int port = 9090;

        // WAL file and snapshot directory
        String walPath = "data/wal.log";
        String snapshotDir = "data/snapshots";

        StorageService storageService = new StorageService(walPath, snapshotDir);

        DatabaseServiceImpl dbService = new DatabaseServiceImpl(storageService);

        Server server = ServerBuilder.forPort(port)
                .addService((BindableService) dbService)
                .build()
                .start();

        System.out.println("BAAS Server started on port " + port);

        server.awaitTermination();
    }
}
