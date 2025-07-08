package miniBaas;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public class QueryParserTest {

    @Test
    public void testParseGetByIdQuery() {
        String input = "{\"collection\":\"users\",\"id\":\"u1\"}";
        QueryParser.ParsedQuery result = QueryParser.parse(input);

        assertEquals(QueryParser.QueryType.GET_BY_ID, result.type);
        assertEquals("users", result.collection);
        assertEquals("u1", result.id);
        assertNull(result.field);
        assertNull(result.value);
    }

    @Test
    public void testParseFilterQuery() {
        String input = "{\"collection\":\"users\",\"role\":\"admin\"}";
        QueryParser.ParsedQuery result = QueryParser.parse(input);

        assertEquals(QueryParser.QueryType.FILTER, result.type);
        assertEquals("users", result.collection);
        assertEquals("role", result.field);
        assertEquals("admin", result.value);
        assertNull(result.id);
    }

    @Test
    public void testParseMissingCollection() {
        String input = "{\"status\":\"active\"}";
        QueryParser.ParsedQuery result = QueryParser.parse(input);

        assertNull(result.collection);
        assertEquals(QueryParser.QueryType.FILTER, result.type);
        assertEquals("status", result.field);
        assertEquals("active", result.value);
    }

    @Test
    public void testParseMultipleFieldsPrefersFirstNonCollection() {
        String queryJson = "{\"collection\": \"products\", \"brand\": \"apple\", \"price\": 1000}";

        QueryParser.ParsedQuery result = QueryParser.parse(queryJson);

        assertEquals(QueryParser.QueryType.FILTER, result.type);
        assertEquals("products", result.collection);

        // Assert that field is either "brand" or "price", since JSONObject does not guarantee order
        Set<String> expectedFields = Set.of("brand", "price");
        assertTrue(expectedFields.contains(result.field));

        if (result.field.equals("brand")) {
            assertEquals("apple", result.value);
        } else if (result.field.equals("price")) {
            assertEquals(1000, result.value);
        } else {
            fail("Unexpected field: " + result.field);
        }
    }
}
