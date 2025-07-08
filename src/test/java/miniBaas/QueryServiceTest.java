package miniBaas;

import miniBaas.proto.*;
import miniBaas.proto.Query.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class QueryServiceTest {

    private DatabaseServiceGrpc.DatabaseServiceBlockingStub stubMock;
    private QueryService service;

    @BeforeEach
    public void setup() {
        stubMock = mock(DatabaseServiceGrpc.DatabaseServiceBlockingStub.class);

        // Injecting stub into a custom subclass to bypass channel creation
        service = new QueryService("localhost", 9090) {
            {
                // Replace blockingStub with mocked one
                this.shutdown(); // avoid starting real connection
                try {
                    var blockingStubField = QueryService.class.getDeclaredField("blockingStub");
                    blockingStubField.setAccessible(true);
                    blockingStubField.set(this, stubMock);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to inject mock stub", e);
                }
            }

            @Override
            public void shutdown() {
                // Skip real channel shutdown
            }
        };
    }

    @Test
    public void testGetByIdQuery() {
        // Mock gRPC response
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
