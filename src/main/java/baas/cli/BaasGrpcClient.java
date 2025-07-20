package baas.cli;

import com.minibaas.proto.DatabaseServiceGrpc;
import com.minibaas.proto.DatabaseServiceProto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class BaasGrpcClient implements AutoCloseable {
    private final ManagedChannel channel;
    private final DatabaseServiceGrpc.DatabaseServiceBlockingStub blockingStub;
    private final DatabaseServiceGrpc.DatabaseServiceStub asyncStub;

    public BaasGrpcClient(String host, int port) {
        this.channel = ManagedChannelBuilder
                .forTarget("dns:///" + host + ":" + port) // force TCP resolver
                .usePlaintext()
                .defaultLoadBalancingPolicy("pick_first")
                .build();
        this.blockingStub = DatabaseServiceGrpc.newBlockingStub(channel);
        this.asyncStub = DatabaseServiceGrpc.newStub(channel);
    }

    // Improved insert with error handling
    public String insert(String collection, String id, String document) {
        try {
            InsertResponse response = blockingStub.insert(
                    InsertRequest.newBuilder()
                            .setCollection(collection)
                            .setId(id)
                            .setDocument(document)
                            .build());
            return response.getSuccess() ?
                    "Insert successful" :
                    "Insert failed: " + response.getError();
        } catch (StatusRuntimeException e) {
            return "RPC failed: " + e.getStatus();
        }
    }

    // Improved get with document validation
    public String get(String collection, String id) {
        try {
            GetResponse response = blockingStub.get(
                    GetRequest.newBuilder()
                            .setCollection(collection)
                            .setId(id)
                            .build());

            if (!response.getSuccess()) {
                return "Get failed: " + response.getError();
            }
            return response.getDocument().isEmpty() ?
                    "No document found" :
                    response.getDocument();
        } catch (StatusRuntimeException e) {
            return "RPC failed: " + e.getStatus();
        }
    }

    // Improved query with empty result handling
    public String query(String collection, String field, String value) {
        try {
            QueryResponse response = blockingStub.query(
                    QueryRequest.newBuilder()
                            .setCollection(collection)
                            .setField(field)
                            .setValue(value)
                            .build());

            if (!response.getSuccess()) {
                return "Query failed: " + response.getError();
            }

            List<String> docs = response.getDocumentsList();
            return docs.isEmpty() ?
                    "No documents match the query" :
                    "Query Results (" + docs.size() + "):\n" +
                            String.join("\n", docs);
        } catch (StatusRuntimeException e) {
            return "RPC failed: " + e.getStatus();
        }
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }
}
