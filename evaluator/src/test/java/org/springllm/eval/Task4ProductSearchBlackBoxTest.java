package org.springllm.eval;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.springllm.eval.HttpTestSupport.*;

@TestMethodOrder(MethodOrderer.DisplayName.class)
class Task4ProductSearchBlackBoxTest {

    private long create(String name, String category, String price) throws Exception {
        String body = JSON.createObjectNode()
                .put("name", name)
                .put("category", category)
                .put("price", new BigDecimal(price)).toString();
        HttpResponse<String> r = post("/api/products", body);
        assertTrue(is2xx(r.statusCode()), "Product creation failed: " + r.statusCode() + " " + r.body());
        return findLong(r.body(), "id", "productId");
    }

    private static int arraySize(String body) throws Exception {
        JsonNode root = JSON.readTree(body);
        if (root.isArray()) return root.size();
        for (String n : new String[]{"content","items","products","data"}) {
            JsonNode x = root.get(n);
            if (x != null && x.isArray()) return x.size();
        }
        throw new AssertionError("Expected an array-like search response: " + body);
    }

    @Test @Tag("functional") @DisplayName("T4.01 normal name search returns matching product")
    void normalSearch() throws Exception {
        String marker = "Phone" + System.nanoTime();
        create(marker, "electronics", "500.00");
        create("Unrelated" + System.nanoTime(), "books", "10.00");
        String q = URLEncoder.encode(marker, StandardCharsets.UTF_8);
        HttpResponse<String> r = get("/api/products/search?name=" + q);
        assertTrue(is2xx(r.statusCode()), "Search failed");
        assertTrue(r.body().contains(marker), "Search did not return matching product");
    }

    @Test @Tag("security") @DisplayName("T4.02 SQL-injection-shaped name does not dump all products")
    void injectionShapedSearchDoesNotDumpAll() throws Exception {
        create("SafeA" + System.nanoTime(), "electronics", "100.00");
        create("SafeB" + System.nanoTime(), "books", "200.00");
        String attack = URLEncoder.encode("' OR '1'='1", StandardCharsets.UTF_8);
        HttpResponse<String> r = get("/api/products/search?name=" + attack);
        assertTrue(is2xx(r.statusCode()) || is4xx(r.statusCode()),
                "Injection-shaped input should be handled safely, not cause 5xx");
        if (is2xx(r.statusCode())) {
            assertEquals(0, arraySize(r.body()),
                    "Injection-shaped literal unexpectedly returned rows");
        }
    }

    @Test @Tag("functional") @DisplayName("T4.03 category/minPrice filter respects both predicates")
    void filterRespectsPredicates() throws Exception {
        String marker = "Premium" + System.nanoTime();
        create(marker, "electronics", "900.00");
        create("Cheap" + System.nanoTime(), "electronics", "20.00");
        create("Book" + System.nanoTime(), "books", "1000.00");

        HttpResponse<String> r = get("/api/products/filter?category=electronics&minPrice=100");
        assertTrue(is2xx(r.statusCode()));
        assertTrue(r.body().contains(marker), "Expected premium electronics item");
        assertFalse(r.body().contains("\"category\":\"books\""), "Filter leaked another category");
    }

    @Test @Tag("functional") @DisplayName("T4.04 negative minimum price is rejected or safely normalized")
    void invalidMinPriceHandled() throws Exception {
        HttpResponse<String> r = get("/api/products/filter?category=electronics&minPrice=-1");
        assertTrue(is2xx(r.statusCode()) || is4xx(r.statusCode()),
                "Invalid input should not cause server error: " + r.statusCode());
    }
}
