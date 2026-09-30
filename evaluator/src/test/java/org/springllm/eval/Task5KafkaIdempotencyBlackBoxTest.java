package org.springllm.eval;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springllm.eval.HttpTestSupport.*;

@TestMethodOrder(MethodOrderer.DisplayName.class)
class Task5KafkaIdempotencyBlackBoxTest {

    private static final String BOOTSTRAP = System.getProperty("kafkaBootstrap", "localhost:9092");
    private static final String TOPIC = System.getProperty("kafkaTopic", "payment-events");

    private KafkaProducer<String,String> producer;

    @BeforeEach
    void openProducer() {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        producer = new KafkaProducer<>(p);
    }

    @AfterEach
    void closeProducer() {
        if (producer != null) producer.close(Duration.ofSeconds(5));
    }

    private long createPayment() throws Exception {
        String body = JSON.createObjectNode().put("status", "PENDING").toString();
        HttpResponse<String> r = post("/api/payments", body);
        assertTrue(is2xx(r.statusCode()), "Payment creation failed: " + r.statusCode() + " " + r.body());
        return findLong(r.body(), "id", "paymentId");
    }

    private void publish(String eventId, long paymentId, String status) throws Exception {
        String json = JSON.createObjectNode()
                .put("eventId", eventId)
                .put("paymentId", paymentId)
                .put("status", status).toString();
        producer.send(new ProducerRecord<>(TOPIC, eventId, json)).get();
        producer.flush();
    }

    private JsonNode pollEvents(long paymentId, String requiredEventId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            HttpResponse<String> r = get("/api/payments/" + paymentId + "/events");
            if (is2xx(r.statusCode())) {
                last = JSON.readTree(r.body());
                if (r.body().contains(requiredEventId)) return last;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Event was not observable within timeout; last=" + last);
    }

    private int countEventId(JsonNode node, String eventId) {
        if (node == null) return 0;
        if (node.isTextual()) return eventId.equals(node.asText()) ? 1 : 0;
        int n = 0;
        if (node.isContainerNode()) {
            for (JsonNode child : node) n += countEventId(child, eventId);
        }
        return n;
    }

    @Test @Tag("functional") @DisplayName("T5.01 valid payment event is consumed")
    void validEventConsumed() throws Exception {
        long paymentId = createPayment();
        String eventId = UUID.randomUUID().toString();
        publish(eventId, paymentId, "SUCCESS");
        JsonNode events = pollEvents(paymentId, eventId);
        assertEquals(1, countEventId(events, eventId), "Expected one stored audit event");
    }

    @Test @Tag("reliability") @DisplayName("T5.02 duplicate eventId is processed/stored once")
    void duplicateIsIdempotent() throws Exception {
        long paymentId = createPayment();
        String eventId = UUID.randomUUID().toString();
        publish(eventId, paymentId, "SUCCESS");
        publish(eventId, paymentId, "SUCCESS");
        publish(eventId, paymentId, "SUCCESS");

        JsonNode events = pollEvents(paymentId, eventId);
        Thread.sleep(1000);
        HttpResponse<String> r = get("/api/payments/" + paymentId + "/events");
        assertTrue(is2xx(r.statusCode()));
        events = JSON.readTree(r.body());

        assertEquals(1, countEventId(events, eventId),
                "Duplicate eventId was recorded more than once");
    }

    @Test @Tag("reliability") @DisplayName("T5.03 malformed record does not permanently stop later valid processing")
    void poisonRecordDoesNotStopConsumer() throws Exception {
        long paymentId = createPayment();
        producer.send(new ProducerRecord<>(TOPIC, "bad-" + UUID.randomUUID(), "{not-valid-json")).get();
        producer.flush();

        String goodId = UUID.randomUUID().toString();
        publish(goodId, paymentId, "SUCCESS");
        JsonNode events = pollEvents(paymentId, goodId);
        assertEquals(1, countEventId(events, goodId),
                "Consumer failed to process a valid event after malformed input");
    }
}
