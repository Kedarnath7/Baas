package miniBaas;

import java.util.*;

import io.grpc.stub.StreamObserver;
import miniBaas.proto.*;
import org.json.JSONObject;
import miniBaas.proto.DatabaseServiceGrpc;
import miniBaas.proto.Query.InsertRequest;
import miniBaas.proto.Query.GetRequest;
import miniBaas.proto.Query.QueryRequest;
import miniBaas.proto.Query.InsertResponse;
import miniBaas.proto.Query.GetResponse;
import miniBaas.proto.Query.QueryResponse;

public class DatabaseServiceImpl extends DatabaseServiceGrpc.DatabaseServiceImplBase {
    private final StorageService storage;

    public DatabaseServiceImpl(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public void insert(InsertRequest request, StreamObserver<InsertResponse> responseObserver) {
        try {
            Map<String, Object> document = new JSONObject(request.getDocument()).toMap();
            storage.insertDocument(
                    request.getCollection(),
                    request.getId(),
                    document,
                    true
            );
            responseObserver.onNext(InsertResponse.newBuilder()
                    .setSuccess(true)
                    .build());
        } catch (Exception e) {
            responseObserver.onNext(InsertResponse.newBuilder()
                    .setSuccess(false)
                    .setError(e.getMessage())
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        try {
            Map<String, Object> document = storage.getDocument(
                    request.getCollection(),
                    request.getId()
            );
            if (document == null) {
                responseObserver.onNext(GetResponse.newBuilder()
                        .setSuccess(false)
                        .setError("Document not found")
                        .build());
            } else {
                responseObserver.onNext(GetResponse.newBuilder()
                        .setSuccess(true)
                        .setDocument(new JSONObject(document).toString())
                        .build());
            }
        } catch (Exception e) {
            responseObserver.onNext(GetResponse.newBuilder()
                    .setSuccess(false)
                    .setError(e.getMessage())
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void query(QueryRequest request, StreamObserver<QueryResponse> responseObserver) {
        try {
            List<Map<String, Object>> documents = storage.queryDocuments(
                    request.getCollection(),
                    request.getField(),
                    request.getValue()
            );
            QueryResponse.Builder response = QueryResponse.newBuilder().setSuccess(true);
            for (Map<String, Object> doc : documents) {
                response.addDocuments(new JSONObject(doc).toString());
            }
            responseObserver.onNext(response.build());
        } catch (Exception e) {
            responseObserver.onNext(QueryResponse.newBuilder()
                    .setSuccess(false)
                    .setError(e.getMessage())
                    .build());
        }
        responseObserver.onCompleted();
    }
}