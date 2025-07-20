package baas.core;

import com.minibaas.proto.DatabaseServiceGrpc;
import com.minibaas.proto.DatabaseServiceProto.*;
//import com.minibaas.proto.QueryParser.ParsedQuery;
import baas.core.QueryParser.ParsedQuery;
import org.json.JSONArray;
import org.json.JSONObject;

public class QueryExecutor {
    private final DatabaseServiceGrpc.DatabaseServiceBlockingStub stub;

    public QueryExecutor(DatabaseServiceGrpc.DatabaseServiceBlockingStub stub) {
        this.stub = stub;
    }

    public JSONObject execute(ParsedQuery query) {
        switch (query.type) {
            case GET_BY_ID:
                GetResponse getResponse = stub.get(GetRequest.newBuilder()
                        .setCollection(query.collection)
                        .setId(query.id)
                        .build());

                if (!getResponse.getSuccess()) {
                    throw new RuntimeException("Query failed: " + getResponse.getCode() + " - " + getResponse.getError());
                }
                return new JSONObject(getResponse.getDocument());

            case FILTER:
                QueryResponse queryResponse = stub.query(QueryRequest.newBuilder()
                        .setCollection(query.collection)
                        .setField(query.field)
                        .setValue(query.value.toString())
                        .build());

                if (!queryResponse.getSuccess()) {
                    throw new RuntimeException("Query failed: " + queryResponse.getCode() + " - " + queryResponse.getError());
                }

                JSONArray results = new JSONArray();
                for (String doc : queryResponse.getDocumentsList()) {
                    results.put(new JSONObject(doc));
                }
                return new JSONObject().put("results", results);

            case COMPOUND:
                JSONArray compoundResults = new JSONArray();
                for (ParsedQuery sub : query.subqueries) {
                    JSONObject subResult = execute(sub);
                    if (subResult.has("results")) {
                        for (Object obj : subResult.getJSONArray("results")) {
                            compoundResults.put(obj);
                        }
                    } else {
                        compoundResults.put(subResult);
                    }
                }
                return new JSONObject().put("results", compoundResults);

            default:
                throw new IllegalArgumentException("Unsupported query type: " + query.type);
        }
    }
}
