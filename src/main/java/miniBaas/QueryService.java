package miniBaas;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import miniBaas.proto.*;
import org.json.JSONArray;
import org.json.JSONObject;
import miniBaas.proto.DatabaseServiceGrpc;
import miniBaas.proto.Query.InsertRequest;
import miniBaas.proto.Query.GetRequest;
import miniBaas.proto.Query.QueryRequest;
import miniBaas.proto.Query.InsertResponse;
import miniBaas.proto.Query.GetResponse;
import miniBaas.proto.Query.QueryResponse;
import miniBaas.QueryParser.ParsedQuery;

public class QueryService {
    private final ManagedChannel channel;
    private final DatabaseServiceGrpc.DatabaseServiceBlockingStub blockingStub;

    public QueryService(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = DatabaseServiceGrpc.newBlockingStub(channel);
    }

    public JSONObject executeQuery(String queryJson) {
        ParsedQuery query = QueryParser.parse(queryJson);

        switch (query.type) {
            case GET_BY_ID:
                GetResponse getResponse = blockingStub.get(GetRequest.newBuilder()
                        .setCollection(query.collection)
                        .setId(query.id)
                        .build());

                if (!getResponse.getSuccess()) {
                    throw new RuntimeException("Query failed: " + getResponse.getError());
                }
                return new JSONObject(getResponse.getDocument());

            case FILTER:
                QueryResponse queryResponse = blockingStub.query(QueryRequest.newBuilder()
                        .setCollection(query.collection)
                        .setField(query.field)
                        .setValue(query.value.toString())
                        .build());

                if (!queryResponse.getSuccess()) {
                    throw new RuntimeException("Query failed: " + queryResponse.getError());
                }

                JSONArray results = new JSONArray();
                for (String doc : queryResponse.getDocumentsList()) {
                    results.put(new JSONObject(doc));
                }
                return new JSONObject().put("results", results);

            default:
                throw new IllegalArgumentException("Unknown query type");
        }
    }

    public void shutdown() {
        channel.shutdown();
    }
}
