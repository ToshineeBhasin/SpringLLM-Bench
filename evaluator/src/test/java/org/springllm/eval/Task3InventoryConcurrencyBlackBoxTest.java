package org.springllm.eval;

import org.junit.jupiter.api.*;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springllm.eval.HttpTestSupport.*;

@TestMethodOrder(MethodOrderer.DisplayName.class)
class Task3InventoryConcurrencyBlackBoxTest {

    private long createProduct(int qty) throws Exception {
        String body = JSON.createObjectNode()
                .put("name", "product-" + System.nanoTime())
                .put("availableQuantity", qty).toString();
        HttpResponse<String> r = post("/api/products", body);
        assertTrue(is2xx(r.statusCode()), "Product creation failed: " + r.statusCode() + " " + r.body());
        return findLong(r.body(), "id", "productId");
    }

    private long quantity(long id) throws Exception {
        HttpResponse<String> r = get("/api/products/" + id);
        assertTrue(is2xx(r.statusCode()), "Product read failed: " + r.statusCode());
        return findLong(r.body(), "availableQuantity", "quantity", "stock");
    }

    @Test @Tag("functional") @DisplayName("T3.01 sequential purchase decrements exactly once")
    void sequentialPurchase() throws Exception {
        long id = createProduct(3);
        assertTrue(is2xx(post("/api/products/" + id + "/purchase", "{}").statusCode()));
        assertEquals(2, quantity(id));
    }

    @Test @Tag("functional") @DisplayName("T3.02 zero inventory purchase is rejected")
    void zeroInventoryRejected() throws Exception {
        long id = createProduct(0);
        HttpResponse<String> r = post("/api/products/" + id + "/purchase", "{}");
        assertTrue(is4xx(r.statusCode()), "Out-of-stock purchase must be rejected");
        assertEquals(0, quantity(id));
    }

    @Test @Tag("reliability") @DisplayName("T3.03 twenty simultaneous buyers for one item yield exactly one success")
    void concurrentSingleItem() throws Exception {
        long id = createProduct(1);
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return post("/api/products/" + id + "/purchase", "{}").statusCode();
            }));
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();

        int successes = 0;
        for (Future<Integer> f : futures) {
            if (is2xx(f.get(30, TimeUnit.SECONDS))) successes++;
        }
        pool.shutdownNow();

        assertEquals(1, successes, "Exactly one request should buy the only item");
        assertEquals(0, quantity(id), "Inventory must finish at zero, never negative");
    }
}
