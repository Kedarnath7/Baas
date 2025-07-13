package miniBaas;

import miniBaas.proto.*;
import miniBaas.proto.Query.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class QueryServiceTest {

    private DatabaseServiceGrpc.DatabaseServiceBlockingStub stubMock;
    private QueryService service;

    @BeforeEach
    public void setup() throws Exception {
        // Mock the gRPC stub
        stubMock = mock(DatabaseServiceGrpc.DatabaseServiceBlockingStub.class);

        // Create dummy ConfigLoader (mocked file not needed)
        ConfigLoader dummyConfig = mock(ConfigLoader.class);
        when(dummyConfig.get("auth.token")).thenReturn("test-token");
        when(dummyConfig.get("grpc.cert")).thenReturn("src/main/resources/certs/server.crt");

        // Instantiate service (we won’t use real TLS channel in tests)
        service = new QueryService("localhost", 9090, dummyConfig);

        // Inject mock executor using reflection
        Field executorField = QueryService.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        executorField.set(service, new QueryExecutor(stubMock));
    }

    @Test
    public void testGetByIdQuery() {
        // Simulate gRPC response
        GetResponse mockResponse = GetResponse.newBuilder()
                .setSuccess(true)
                .setDocument("{\"name\": \"Alice\", \"role\": \"admin\"}")
                .build();

        when(stubMock.get(any(GetRequest.class))).thenReturn(mockResponse);

        String queryJson = "{\"collection\":\"users\", \"id\":\"u1\"}";
        JSONObject result = service.executeQuery(queryJson);

        assertEquals("Alice", result.getString("name"));
        assertEquals("admin", result.getString("role"));
    }

    @Test
    public void testFilterQuery() {
        QueryResponse mockResponse = QueryResponse.newBuilder()
                .setSuccess(true)
                .addDocuments("{\"name\": \"Alice\"}")
                .build();

        when(stubMock.query(any(QueryRequest.class))).thenReturn(mockResponse);

        String queryJson = "{\"collection\":\"users\", \"role\":\"admin\"}";
        JSONObject result = service.executeQuery(queryJson);

        assertTrue(result.has("results"));
        JSONArray arr = result.getJSONArray("results");
        assertEquals("Alice", arr.getJSONObject(0).getString("name"));
    }
}
