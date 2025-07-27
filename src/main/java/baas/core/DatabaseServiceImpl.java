package baas.core;

import com.minibaas.proto.DatabaseServiceGrpc;
import com.minibaas.proto.DatabaseServiceProto.*;
import io.grpc.stub.StreamObserver;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class DatabaseServiceImpl extends DatabaseServiceGrpc.DatabaseServiceImplBase {
    private final StorageService storage;

    public DatabaseServiceImpl(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public void insert(InsertRequest request, StreamObserver<InsertResponse> responseObserver) {
        try {
            String documentJson = request.getDocument().trim();
            List<String> generatedIds = new ArrayList<>();

            if (documentJson.startsWith("[")) {
                // Bulk insert (array of documents)
                org.json.JSONArray jsonArray = new org.json.JSONArray(documentJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    org.json.JSONObject obj = jsonArray.getJSONObject(i);
                    Map<String, Object> doc = obj.toMap();

                    String docId = UUID.randomUUID().toString();
                    storage.insertDocument(request.getCollection(), docId, doc, true);
                    generatedIds.add(docId);
                }

                responseObserver.onNext(InsertResponse.newBuilder()
                        .setSuccess(true)
                        .addAllGeneratedIds(generatedIds)
                        .setCode(ErrorCode.OK)
                        .build());

            } else {
                // Single document insert
                Map<String, Object> document = new JSONObject(documentJson).toMap();
                String id = request.getId();

                if (id == null || id.isBlank()) {
                    id = UUID.randomUUID().toString();
                    generatedIds.add(id);
                }

                storage.insertDocument(request.getCollection(), id, document, true);

                InsertResponse.Builder builder = InsertResponse.newBuilder()
                        .setSuccess(true)
                        .setCode(ErrorCode.OK);

                if (!generatedIds.isEmpty()) {
                    builder.addAllGeneratedIds(generatedIds);
                }

                responseObserver.onNext(builder.build());
            }

        } catch (Exception e) {
            e.printStackTrace();
            String errorMsg = (e.getMessage() != null) ? e.getMessage() : e.toString();
            responseObserver.onNext(InsertResponse.newBuilder()
                    .setSuccess(false)
                    .setError(errorMsg)
                    .setCode(ErrorCode.INVALID_DOCUMENT)
                    .build());
        }
        responseObserver.onCompleted();
    }


    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        try {
            Map<String, Object> document = storage.getDocument(request.getCollection(), request.getId());
            if (document == null) {
                responseObserver.onNext(GetResponse.newBuilder()
                        .setSuccess(false)
                        .setError("Document not found")
                        .setCode(ErrorCode.NOT_FOUND)
                        .build());
            } else {
                responseObserver.onNext(GetResponse.newBuilder()
                        .setSuccess(true)
                        .setDocument(new JSONObject(document).toString())
                        .setCode(ErrorCode.OK)
                        .build());
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errorMsg = (e.getMessage() != null) ? e.getMessage() : e.toString();
            responseObserver.onNext(GetResponse.newBuilder()
                    .setSuccess(false)
                    .setError(errorMsg)
                    .setCode(ErrorCode.INTERNAL_ERROR)
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void query(QueryRequest request, StreamObserver<QueryResponse> responseObserver) {
        try {
            List<Map<String, Object>> documents = storage.queryDocuments(request.getCollection(), request.getField(), request.getValue());
            QueryResponse.Builder response = QueryResponse.newBuilder()
                    .setSuccess(true)
                    .setCode(ErrorCode.OK);
            for (Map<String, Object> doc : documents) {
                response.addDocuments(new JSONObject(doc).toString());
            }
            responseObserver.onNext(response.build());
        } catch (Exception e) {
            e.printStackTrace();
            String errorMsg = (e.getMessage() != null) ? e.getMessage() : e.toString();
            responseObserver.onNext(QueryResponse.newBuilder()
                    .setSuccess(false)
                    .setError(errorMsg)
                    .setCode(ErrorCode.INTERNAL_ERROR)
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void enhancedQuery(EnhancedQueryRequest request, StreamObserver<EnhancedQueryResponse> responseObserver) {
        try {
            List<Map<String, Object>> allDocuments;

            // Get documents based on request type
            if (request.getGetAll()) {
                // Get all documents in collection
                allDocuments = storage.getAllDocuments(request.getCollection());
            } else if (!request.getWhereCondition().isEmpty()) {
                // Apply where condition
                allDocuments = processWhereCondition(request.getCollection(), request.getWhereCondition());
            } else {
                // No specific condition, get all
                allDocuments = storage.getAllDocuments(request.getCollection());
            }

            // Apply sorting if specified
            if (!request.getSort().isEmpty()) {
                allDocuments = applySorting(allDocuments, request.getSort());
            }

            int totalCount = allDocuments.size();

            // Apply pagination (skip and limit)
            if (request.getSkip() > 0) {
                allDocuments = allDocuments.stream()
                        .skip(request.getSkip())
                        .collect(Collectors.toList());
            }

            boolean hasMore = false;
            if (request.getLimit() > 0 && allDocuments.size() > request.getLimit()) {
                allDocuments = allDocuments.stream()
                        .limit(request.getLimit())
                        .collect(Collectors.toList());
                hasMore = true;
            }

            // Apply field selection if specified
            if (!request.getFields().isEmpty()) {
                allDocuments = applyFieldSelection(allDocuments, request.getFields());
            }

            // Build response
            EnhancedQueryResponse.Builder responseBuilder = EnhancedQueryResponse.newBuilder()
                    .setSuccess(true)
                    .setCode(ErrorCode.OK)
                    .setResultCount(totalCount)
                    .setReturnedCount(allDocuments.size())
                    .setHasMore(hasMore);

            // Convert documents to JSON strings
            for (Map<String, Object> doc : allDocuments) {
                responseBuilder.addDocuments(new JSONObject(doc).toString());
            }

            responseObserver.onNext(responseBuilder.build());

        } catch (Exception e) {
            e.printStackTrace();
            String errorMsg = (e.getMessage() != null) ? e.getMessage() : e.toString();
            responseObserver.onNext(EnhancedQueryResponse.newBuilder()
                    .setSuccess(false)
                    .setError(errorMsg)
                    .setCode(ErrorCode.INVALID_QUERY)
                    .build());
        }
        responseObserver.onCompleted();
    }

    // Helper method to process where conditions
    private List<Map<String, Object>> processWhereCondition(String collection, String whereCondition) {
        try {
            // Parse simple conditions like "field=value", "field>=value", etc.
            String condition = whereCondition.trim();

            // Handle different operators
            if (condition.contains(">=")) {
                String[] parts = condition.split(">=", 2);
                return filterByComparison(collection, parts[0].trim(), parts[1].trim(), ">=");
            } else if (condition.contains("<=")) {
                String[] parts = condition.split("<=", 2);
                return filterByComparison(collection, parts[0].trim(), parts[1].trim(), "<=");
            } else if (condition.contains("!=")) {
                String[] parts = condition.split("!=", 2);
                return filterByComparison(collection, parts[0].trim(), parts[1].trim(), "!=");
            } else if (condition.contains(">")) {
                String[] parts = condition.split(">", 2);
                return filterByComparison(collection, parts[0].trim(), parts[1].trim(), ">");
            } else if (condition.contains("<")) {
                String[] parts = condition.split("<", 2);
                return filterByComparison(collection, parts[0].trim(), parts[1].trim(), "<");
            } else if (condition.contains("=")) {
                String[] parts = condition.split("=", 2);
                return storage.queryDocuments(collection, parts[0].trim(), parts[1].trim());
            } else {
                // Fallback to getting all documents
                return storage.getAllDocuments(collection);
            }
        } catch (Exception e) {
            // If parsing fails, return all documents
            return storage.getAllDocuments(collection);
        }
    }

    // Helper method for comparison operations
    private List<Map<String, Object>> filterByComparison(String collection, String field, String value, String operator) {
        List<Map<String, Object>> allDocs = storage.getAllDocuments(collection);
        return allDocs.stream()
                .filter(doc -> compareValues(doc.get(field), value, operator))
                .collect(Collectors.toList());
    }

    // Helper method to compare values
    private boolean compareValues(Object docValue, String targetValue, String operator) {
        if (docValue == null) return false;

        try {
            // Try numeric comparison first
            if (docValue instanceof Number && isNumeric(targetValue)) {
                double docNum = ((Number) docValue).doubleValue();
                double targetNum = Double.parseDouble(targetValue);

                switch (operator) {
                    case ">": return docNum > targetNum;
                    case "<": return docNum < targetNum;
                    case ">=": return docNum >= targetNum;
                    case "<=": return docNum <= targetNum;
                    case "!=": return docNum != targetNum;
                    default: return docNum == targetNum;
                }
            } else {
                // String comparison
                String docStr = docValue.toString();
                switch (operator) {
                    case "!=": return !docStr.equals(targetValue);
                    default: return docStr.equals(targetValue);
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    // Helper method to check if string is numeric
    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Helper method to apply sorting
    private List<Map<String, Object>> applySorting(List<Map<String, Object>> documents, String sort) {
        try {
            String[] parts = sort.split(" ", 2);
            String field = parts[0].trim();
            boolean ascending = parts.length == 1 || parts[1].trim().equalsIgnoreCase("ASC");

            return documents.stream()
                    .sorted((doc1, doc2) -> {
                        Object val1 = doc1.get(field);
                        Object val2 = doc2.get(field);

                        if (val1 == null && val2 == null) return 0;
                        if (val1 == null) return ascending ? -1 : 1;
                        if (val2 == null) return ascending ? 1 : -1;

                        int comparison;
                        if (val1 instanceof Number && val2 instanceof Number) {
                            comparison = Double.compare(((Number) val1).doubleValue(), ((Number) val2).doubleValue());
                        } else {
                            comparison = val1.toString().compareTo(val2.toString());
                        }

                        return ascending ? comparison : -comparison;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // If sorting fails, return original list
            return documents;
        }
    }

    // Helper method to apply field selection
    private List<Map<String, Object>> applyFieldSelection(List<Map<String, Object>> documents, String fields) {
        try {
            Set<String> fieldSet = Arrays.stream(fields.split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());

            return documents.stream()
                    .map(doc -> {
                        Map<String, Object> filteredDoc = new HashMap<>();
                        for (String field : fieldSet) {
                            if (doc.containsKey(field)) {
                                filteredDoc.put(field, doc.get(field));
                            }
                        }
                        return filteredDoc;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // If field selection fails, return original documents
            return documents;
        }
    }
    // --- Synchronous helper methods for REST API ---
    public InsertResponse insertSync(String collection, String id, String document) {
        InsertRequest request = InsertRequest.newBuilder()
                .setCollection(collection)
                .setId(id == null ? "" : id)
                .setDocument(document)
                .build();

        final InsertResponse[] responseHolder = new InsertResponse[1];
        insert(request, new StreamObserver<InsertResponse>() {
            @Override
            public void onNext(InsertResponse value) {
                responseHolder[0] = value;
            }

            @Override
            public void onError(Throwable t) {
                responseHolder[0] = InsertResponse.newBuilder()
                        .setSuccess(false)
                        .setError(t.getMessage())
                        .setCode(ErrorCode.INTERNAL_ERROR)
                        .build();
            }

            @Override
            public void onCompleted() { }
        });
        return responseHolder[0];
    }

    public GetResponse getSync(String collection, String id) {
        GetRequest request = GetRequest.newBuilder()
                .setCollection(collection)
                .setId(id)
                .build();

        final GetResponse[] responseHolder = new GetResponse[1];
        get(request, new StreamObserver<GetResponse>() {
            @Override
            public void onNext(GetResponse value) {
                responseHolder[0] = value;
            }

            @Override
            public void onError(Throwable t) {
                responseHolder[0] = GetResponse.newBuilder()
                        .setSuccess(false)
                        .setError(t.getMessage())
                        .setCode(ErrorCode.INTERNAL_ERROR)
                        .build();
            }

            @Override
            public void onCompleted() { }
        });
        return responseHolder[0];
    }

    public QueryResponse querySync(String collection, String field, String value) {
        QueryRequest request = QueryRequest.newBuilder()
                .setCollection(collection)
                .setField(field)
                .setValue(value)
                .build();

        final QueryResponse[] responseHolder = new QueryResponse[1];
        query(request, new StreamObserver<QueryResponse>() {
            @Override
            public void onNext(QueryResponse value) {
                responseHolder[0] = value;
            }

            @Override
            public void onError(Throwable t) {
                responseHolder[0] = QueryResponse.newBuilder()
                        .setSuccess(false)
                        .setError(t.getMessage())
                        .setCode(ErrorCode.INTERNAL_ERROR)
                        .build();
            }

            @Override
            public void onCompleted() { }
        });
        return responseHolder[0];
    }

}