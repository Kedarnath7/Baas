package baas.core;

import com.minibaas.proto.DatabaseServiceGrpc;
import com.minibaas.proto.DatabaseServiceProto.*;
import io.grpc.stub.StreamObserver;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public class DatabaseServiceImpl extends DatabaseServiceGrpc.DatabaseServiceImplBase {
    private final StorageService storage;

    public DatabaseServiceImpl(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public void insert(InsertRequest request, StreamObserver<InsertResponse> responseObserver) {
        try {
            Map<String, Object> document = new JSONObject(request.getDocument()).toMap();
            storage.insertDocument(request.getCollection(), request.getId(), document, true);
            responseObserver.onNext(InsertResponse.newBuilder()
                    .setSuccess(true)
                    .setCode(ErrorCode.OK)
                    .build());
        } catch (Exception e) {
            e.printStackTrace(); // full stack trace in server logs
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
}
