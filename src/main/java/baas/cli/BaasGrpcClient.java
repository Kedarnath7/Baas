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
                            .setId(id == null ? "" : id)
                            .setDocument(document)
                            .build());

            if (response.getSuccess()) {
                StringBuilder message = new StringBuilder("Insert successful");

                // Show generated IDs if present (bulk or auto-generated single)
                if (response.getGeneratedIdsCount() > 0) {
                    message.append(". Generated IDs: ")
                            .append(String.join(", ", response.getGeneratedIdsList()));
                }
                return message.toString();
            } else {
                return "Insert failed: " + response.getError();
            }
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

    // Enhanced query method for new CLI functionality
    public String queryDocuments(String collection, String whereCondition,
                                 Integer limit, String fields, String sort, boolean getAll) {
        try {
            // Build enhanced query request
            EnhancedQueryRequest.Builder requestBuilder = EnhancedQueryRequest.newBuilder()
                    .setCollection(collection)
                    .setGetAll(getAll);

            // Add where condition if provided
            if (whereCondition != null && !whereCondition.trim().isEmpty()) {
                requestBuilder.setWhereCondition(whereCondition);
            }

            // Add limit if provided
            if (limit != null && limit > 0) {
                requestBuilder.setLimit(limit);
            }

            // Add fields selection if provided
            if (fields != null && !fields.trim().isEmpty()) {
                requestBuilder.setFields(fields);
            }

            // Add sort if provided
            if (sort != null && !sort.trim().isEmpty()) {
                requestBuilder.setSort(sort);
            }

            // Make the enhanced query call
            EnhancedQueryResponse response = blockingStub.enhancedQuery(requestBuilder.build());

            if (!response.getSuccess()) {
                return "Enhanced query failed: " + response.getError();
            }

            List<String> docs = response.getDocumentsList();
            if (docs.isEmpty()) {
                return "No documents found";
            }

            // Format the response
            StringBuilder result = new StringBuilder();

            // Check if resultCount is greater than 0 (instead of using hasResultCount())
            if (response.getResultCount() > 0) {
                result.append("Found ").append(response.getResultCount()).append(" document(s)");
                if (response.getReturnedCount() != response.getResultCount()) {
                    result.append(" (showing ").append(response.getReturnedCount()).append(")");
                }
                if (response.getHasMore()) {
                    result.append(" - more results available");
                }
                result.append(":\n\n");
            }

            for (int i = 0; i < docs.size(); i++) {
                result.append("Document ").append(i + 1).append(":\n");
                result.append(docs.get(i));
                if (i < docs.size() - 1) {
                    result.append("\n\n");
                }
            }

            return result.toString();

        } catch (StatusRuntimeException e) {
            // Fallback to existing query method if enhanced query is not available
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNIMPLEMENTED) {
                return fallbackQuery(collection, whereCondition, limit, getAll);
            }
            return "RPC failed: " + e.getStatus();
        }
    }

    // Fallback method for backward compatibility
    private String fallbackQuery(String collection, String whereCondition, Integer limit, boolean getAll) {
        try {
            if (getAll) {
                // Try to use existing query with a wildcard or special field
                return query(collection, "*", "*");
            } else if (whereCondition != null) {
                // Parse simple where conditions like "field=value"
                String[] parts = whereCondition.split("=", 2);
                if (parts.length == 2) {
                    String field = parts[0].trim();
                    String value = parts[1].trim();
                    return query(collection, field, value);
                }
            }
            return "Enhanced query not supported by server. Please upgrade server or use basic query command.";
        } catch (Exception e) {
            return "Fallback query failed: " + e.getMessage();
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