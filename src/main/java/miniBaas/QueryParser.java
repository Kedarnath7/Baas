package miniBaas;

import org.json.JSONObject;

public class QueryParser {
    public static ParsedQuery parse(String queryJson) {
        JSONObject query = new JSONObject(queryJson);
        ParsedQuery result = new ParsedQuery();

        if (query.has("collection")) {
            result.collection = query.getString("collection");
        }

        if (query.has("id")) {
            result.id = query.getString("id");
            result.type = QueryType.GET_BY_ID;
        } else {
            // Find first field that's not collection
            for (String key : query.keySet()) {
                if (!key.equals("collection")) {
                    result.field = key;
                    result.value = query.get(key);
                    result.type = QueryType.FILTER;
                    break;
                }
            }
        }

        return result;
    }

    public enum QueryType {
        GET_BY_ID,
        FILTER
    }

    public static class ParsedQuery {
        public QueryType type;
        public String collection;
        public String id;
        public String field;
        public Object value;
    }
}