package miniBaas;

import io.grpc.stub.StreamObserver;
import miniBaas.proto.Query.*;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DatabaseServiceImplTest {

    private StorageService storageMock;
    private DatabaseServiceImpl service;

    @BeforeEach
    public void setup() {
        storageMock = mock(StorageService.class);
        service = new DatabaseServiceImpl(storageMock);
    }

    @Test
    public void testInsertSuccess() {
        InsertRequest req = InsertRequest.newBuilder()
                .setCollection("users")
                .setId("u1")
                .setDocument(new JSONObject(Map.of("name", "Alice")).toString())
                .build();

        StreamObserver<InsertResponse> observer = new StreamObserver<>() {
            InsertResponse response;
            boolean completed = false;

            @Override
            public void onNext(InsertResponse insertResponse) {
                this.response = insertResponse;
            }

            @Override
            public void onError(Throwable throwable) {
                fail("Insert threw error: " + throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                completed = true;
            }
        };

        service.insert(req, observer);
        verify(storageMock).insertDocument(eq("users"), eq("u1"), anyMap(), eq(true));
    }

    @Test
    public void testGetFound() {
        when(storageMock.getDocument("users", "u1")).thenReturn(Map.of("name", "Bob"));

        GetRequest req = GetRequest.newBuilder().setCollection("users").setId("u1").build();

        StreamObserver<GetResponse> observer = new StreamObserver<>() {
            GetResponse response;
            boolean completed = false;

            @Override
            public void onNext(GetResponse getResponse) {
                this.response = getResponse;
                assertTrue(getResponse.getSuccess());
                assertTrue(getResponse.getDocument().contains("Bob"));
            }

            @Override
            public void onError(Throwable throwable) {
                fail(throwable);
            }

            @Override
            public void onCompleted() {
                completed = true;
            }
        };

        service.get(req, observer);
    }

    @Test
    public void testQueryReturnsResults() {
        when(storageMock.queryDocuments("products", "type", "book"))
                .thenReturn(List.of(Map.of("title", "Java 101")));

        QueryRequest req = QueryRequest.newBuilder()
                .setCollection("products")
                .setField("type")
                .setValue("book")
                .build();

        StreamObserver<QueryResponse> observer = new StreamObserver<>() {
            QueryResponse response;

            @Override
            public void onNext(QueryResponse queryResponse) {
                this.response = queryResponse;
                assertTrue(queryResponse.getSuccess());
                assertEquals(1, queryResponse.getDocumentsCount());
            }

            @Override
            public void onError(Throwable throwable) {
                fail(throwable);
            }

            @Override
            public void onCompleted() {
            }
        };

        service.query(req, observer);
    }
}
