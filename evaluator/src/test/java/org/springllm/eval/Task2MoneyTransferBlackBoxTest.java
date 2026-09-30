package org.springllm.eval;

import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springllm.eval.HttpTestSupport.*;

@TestMethodOrder(MethodOrderer.DisplayName.class)
class Task2MoneyTransferBlackBoxTest {

    record Account(long id, BigDecimal balance) {}

    private Account create(BigDecimal balance) throws Exception {
        String body = JSON.createObjectNode()
                .put("owner", "user-" + System.nanoTime())
                .put("balance", balance).toString();
        HttpResponse<String> r = post("/api/accounts", body);
        assertTrue(is2xx(r.statusCode()), "Account creation failed: " + r.statusCode() + " " + r.body());
        return new Account(findLong(r.body(), "id", "accountId"), findDecimal(r.body(), "balance"));
    }

    private BigDecimal balance(long id) throws Exception {
        HttpResponse<String> r = get("/api/accounts/" + id);
        assertTrue(is2xx(r.statusCode()), "Account read failed: " + r.statusCode() + " " + r.body());
        return findDecimal(r.body(), "balance");
    }

    private HttpResponse<String> transfer(long from, long to, BigDecimal amount) throws Exception {
        String body = JSON.createObjectNode().put("amount", amount).toString();
        return post("/api/accounts/" + from + "/transfer/" + to, body);
    }

    @Test @Tag("functional") @DisplayName("T2.01 successful transfer preserves total balance")
    void successfulTransfer() throws Exception {
        Account a = create(new BigDecimal("1000.00"));
        Account b = create(new BigDecimal("500.00"));
        HttpResponse<String> r = transfer(a.id(), b.id(), new BigDecimal("300.00"));
        assertTrue(is2xx(r.statusCode()), "Transfer should succeed: " + r.statusCode() + " " + r.body());
        assertEquals(0, new BigDecimal("700.00").compareTo(balance(a.id())));
        assertEquals(0, new BigDecimal("800.00").compareTo(balance(b.id())));
    }

    @Test @Tag("functional") @DisplayName("T2.02 negative transfer is rejected and balances unchanged")
    void negativeTransferRejected() throws Exception {
        Account a = create(new BigDecimal("1000.00"));
        Account b = create(new BigDecimal("500.00"));
        HttpResponse<String> r = transfer(a.id(), b.id(), new BigDecimal("-10.00"));
        assertTrue(is4xx(r.statusCode()), "Negative amount must be rejected");
        assertEquals(0, new BigDecimal("1000.00").compareTo(balance(a.id())));
        assertEquals(0, new BigDecimal("500.00").compareTo(balance(b.id())));
    }

    @Test @Tag("functional") @DisplayName("T2.03 missing destination does not debit source")
    void missingDestinationDoesNotDebit() throws Exception {
        Account a = create(new BigDecimal("1000.00"));
        HttpResponse<String> r = transfer(a.id(), Long.MAX_VALUE - 3, new BigDecimal("100.00"));
        assertTrue(is4xx(r.statusCode()), "Missing destination must be a 4xx");
        assertEquals(0, new BigDecimal("1000.00").compareTo(balance(a.id())),
                "Source changed despite failed transfer");
    }

    @Test @Tag("functional") @DisplayName("T2.04 self-transfer is rejected without changing balance")
    void selfTransferRejected() throws Exception {
        Account a = create(new BigDecimal("1000.00"));
        HttpResponse<String> r = transfer(a.id(), a.id(), new BigDecimal("100.00"));
        assertTrue(is4xx(r.statusCode()), "Self-transfer should be rejected");
        assertEquals(0, new BigDecimal("1000.00").compareTo(balance(a.id())));
    }

    @Test @Tag("reliability") @DisplayName("T2.05 concurrent overspend allows only one 700 transfer from 1000")
    void concurrentOverspend() throws Exception {
        Account source = create(new BigDecimal("1000.00"));
        Account d1 = create(BigDecimal.ZERO);
        Account d2 = create(BigDecimal.ZERO);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> c1 = () -> { go.await(); return transfer(source.id(), d1.id(), new BigDecimal("700.00")).statusCode(); };
        Callable<Integer> c2 = () -> { go.await(); return transfer(source.id(), d2.id(), new BigDecimal("700.00")).statusCode(); };
        Future<Integer> f1 = pool.submit(c1);
        Future<Integer> f2 = pool.submit(c2);
        go.countDown();

        int s1 = f1.get(30, TimeUnit.SECONDS);
        int s2 = f2.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        long successes = java.util.stream.Stream.of(s1, s2).filter(HttpTestSupport::is2xx).count();
        assertEquals(1, successes, "Exactly one concurrent transfer should succeed; statuses=" + s1 + "," + s2);
        assertEquals(0, new BigDecimal("300.00").compareTo(balance(source.id())),
                "Concurrent transfer caused lost update/overspend");
    }
}
