package org.springllm.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

final class HttpTestSupport {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    static final String BASE_URL =
            System.getProperty("baseUrl", "http://localhost:8080").replaceAll("/+$", "");

    private HttpTestSupport() {}

    static HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(20))
                .GET().build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> getBearer(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    static boolean is2xx(int s) { return s >= 200 && s < 300; }
    static boolean is4xx(int s) { return s >= 400 && s < 500; }

    static long findLong(String body, String... names) throws Exception {
        JsonNode root = JSON.readTree(body);
        for (String n : names) {
            JsonNode hit = findByName(root, n);
            if (hit != null && hit.canConvertToLong()) return hit.asLong();
        }
        throw new AssertionError("Could not find numeric field " + String.join("/", names) + " in: " + body);
    }

    static BigDecimal findDecimal(String body, String... names) throws Exception {
        JsonNode root = JSON.readTree(body);
        for (String n : names) {
            JsonNode hit = findByName(root, n);
            if (hit != null && hit.isNumber()) return hit.decimalValue();
        }
        throw new AssertionError("Could not find numeric field " + String.join("/", names) + " in: " + body);
    }

    static String findString(String body, String... names) throws Exception {
        JsonNode root = JSON.readTree(body);
        for (String n : names) {
            JsonNode hit = findByName(root, n);
            if (hit != null && hit.isTextual()) return hit.asText();
        }
        return null;
    }

    static JsonNode findByName(JsonNode node, String name) {
        if (node == null) return null;
        if (node.isObject()) {
            JsonNode direct = node.get(name);
            if (direct != null) return direct;
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                JsonNode r = findByName(it.next().getValue(), name);
                if (r != null) return r;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode r = findByName(child, name);
                if (r != null) return r;
            }
        }
        return null;
    }
}
