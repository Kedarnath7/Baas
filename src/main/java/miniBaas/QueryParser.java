package miniBaas;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class QueryParser {

    public enum QueryType {
        GET_BY_ID,
        FILTER,
        COMPOUND
    }

    public static class ParsedQuery {
        public QueryType type;
        public String collection;
        public String id;         // Only for GET_BY_ID
        public String field;      // Only for FILTER
        public Object value;      // Only for FILTER

        public String operator;   // $and / $or (COMPOUND only)
        public List<ParsedQuery> subqueries;  // For COMPOUND
    }

    public static ParsedQuery parse(String queryJson) {
        JSONObject obj = new JSONObject(queryJson);
        ParsedQuery parsed = new ParsedQuery();

        if (!obj.has("collection")) {
            throw new IllegalArgumentException("Missing 'collection' field in query");
        }

        parsed.collection = obj.getString("collection");

        // GET_BY_ID
        if (obj.has("id")) {
            parsed.type = QueryType.GET_BY_ID;
            parsed.id = obj.getString("id");
            return parsed;
        }

        // COMPOUND: $and / $or
        if (obj.has("$and") || obj.has("$or")) {
            parsed.type = QueryType.COMPOUND;
            parsed.operator = obj.has("$and") ? "$and" : "$or";
            JSONArray conditions = obj.getJSONArray(parsed.operator);
            parsed.subqueries = new ArrayList<>();

            for (int i = 0; i < conditions.length(); i++) {
                JSONObject cond = conditions.getJSONObject(i);

                if (cond.keySet().size() != 1) {
                    throw new IllegalArgumentException("Each condition inside " + parsed.operator + " must have exactly one key");
                }

                String field = cond.keys().next();
                Object value = cond.get(field);

                ParsedQuery sub = new ParsedQuery();
                sub.type = QueryType.FILTER;
                sub.collection = parsed.collection;
                sub.field = field;
                sub.value = value;

                parsed.subqueries.add(sub);
            }
            return parsed;
        }

        // Single-field FILTER (default case)
        for (String key : obj.keySet()) {
            if (!key.equals("collection")) {
                parsed.type = QueryType.FILTER;
                parsed.field = key;
                parsed.value = obj.get(key);
                return parsed;
            }
        }

        throw new IllegalArgumentException("Unable to parse query: must include 'id', a filter, or $and/$or");
    }
}
