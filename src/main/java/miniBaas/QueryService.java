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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import miniBaas.QueryParser.ParsedQuery;
import miniBaas.proto.DatabaseServiceGrpc;
import org.json.JSONObject;

public class QueryService {
    private final ManagedChannel channel;
    private final QueryExecutor executor;

    public QueryService(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        DatabaseServiceGrpc.DatabaseServiceBlockingStub stub =
                DatabaseServiceGrpc.newBlockingStub(channel);

        this.executor = new QueryExecutor(stub);
    }

    public JSONObject executeQuery(String queryJson) {
        ParsedQuery query = QueryParser.parse(queryJson);
        return executor.execute(query);
    }

    public void shutdown() {
        channel.shutdown();
    }
}
