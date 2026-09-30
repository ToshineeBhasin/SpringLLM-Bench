package org.springllm.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.DisplayName.class)
class Task1JwtRbacBlackBoxTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String BASE_URL =
            System.getProperty("baseUrl", "http://localhost:8080").replaceAll("/+$", "");

    private static String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.com";
    }

    private static HttpResponse<String> postJson(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String path, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static boolean is2xx(int status) {
        return status >= 200 && status < 300;
    }

    private static boolean is4xx(int status) {
        return status >= 400 && status < 500;
    }

    private static void register(String email, String password) throws Exception {
        String body = JSON.createObjectNode()
                .put("email", email)
                .put("password", password)
                .toString();
        HttpResponse<String> response = postJson("/api/auth/register", body);
        assertTrue(is2xx(response.statusCode()),
                () -> "Registration should succeed but got " + response.statusCode() + ": " + response.body());
    }

    private static String login(String email, String password) throws Exception {
        String body = JSON.createObjectNode()
                .put("email", email)
                .put("password", password)
                .toString();
        HttpResponse<String> response = postJson("/api/auth/login", body);
        assertTrue(is2xx(response.statusCode()),
                () -> "Login should succeed but got " + response.statusCode() + ": " + response.body());

        String token = findToken(response.body());
        assertNotNull(token, () -> "Login succeeded but no JWT-like token field was found. Body: " + response.body());
        assertEquals(3, token.split("\\.").length,
                () -> "Returned token does not look like a JWT: " + token);
        return token;
    }

    private static String findToken(String body) {
        try {
            JsonNode root = JSON.readTree(body);
            String[] preferredNames = {"accessToken", "access_token", "token", "jwt", "jwtToken"};
            for (String name : preferredNames) {
                JsonNode n = root.get(name);
                if (n != null && n.isTextual()) return n.asText();
            }
            return findJwtRecursively(root);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String findJwtRecursively(JsonNode node) {
        if (node == null) return null;
        if (node.isTextual()) {
            String value = node.asText();
            if (value.split("\\.").length == 3) return value;
            return null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                String found = findJwtRecursively(it.next().getValue());
                if (found != null) return found;
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findJwtRecursively(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String tamperSignature(String jwt) {
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "Expected a three-part JWT");
        String sig = parts[2];
        assertFalse(sig.isEmpty(), "JWT signature must not be empty");
        char first = sig.charAt(0);
        char replacement = first == 'A' ? 'B' : 'A';
        parts[2] = replacement + sig.substring(1);
        return String.join(".", parts);
    }

    @Test
    @Tag("functional")
    @DisplayName("T1.01 valid registration succeeds")
    void validRegistrationSucceeds() throws Exception {
        register(uniqueEmail("valid"), "Password123!");
    }

    @Test
    @Tag("functional")
    @DisplayName("T1.02 duplicate email is rejected with 4xx")
    void duplicateEmailRejected() throws Exception {
        String email = uniqueEmail("duplicate");
        register(email, "Password123!");

        String body = JSON.createObjectNode()
                .put("email", email)
                .put("password", "Password123!")
                .toString();
        HttpResponse<String> second = postJson("/api/auth/register", body);

        assertTrue(is4xx(second.statusCode()),
                () -> "Duplicate registration should be rejected with 4xx, got "
                        + second.statusCode() + ": " + second.body());
    }

    @Test
    @Tag("functional")
    @DisplayName("T1.03 malformed email is rejected")
    void malformedEmailRejected() throws Exception {
        String body = JSON.createObjectNode()
                .put("email", "not-an-email")
                .put("password", "Password123!")
                .toString();
        HttpResponse<String> response = postJson("/api/auth/register", body);

        assertTrue(is4xx(response.statusCode()),
                () -> "Malformed email should produce 4xx, got " + response.statusCode());
    }

    @Test
    @Tag("functional")
    @DisplayName("T1.04 blank password is rejected")
    void blankPasswordRejected() throws Exception {
        String body = JSON.createObjectNode()
                .put("email", uniqueEmail("blank"))
                .put("password", "")
                .toString();
        HttpResponse<String> response = postJson("/api/auth/register", body);

        assertTrue(is4xx(response.statusCode()),
                () -> "Blank password should produce 4xx, got " + response.statusCode());
    }

    @Test
    @Tag("functional")
    @DisplayName("T1.05 correct credentials return JWT")
    void correctCredentialsReturnJwt() throws Exception {
        String email = uniqueEmail("login");
        register(email, "Password123!");
        login(email, "Password123!");
    }

    @Test
    @Tag("functional")
    @DisplayName("T1.06 wrong password returns 401")
    void wrongPasswordReturns401() throws Exception {
        String email = uniqueEmail("wrongpwd");
        register(email, "Password123!");

        String body = JSON.createObjectNode()
                .put("email", email)
                .put("password", "DefinitelyWrong123!")
                .toString();
        HttpResponse<String> response = postJson("/api/auth/login", body);

        assertEquals(401, response.statusCode(),
                () -> "Wrong password should return 401, got " + response.statusCode() + ": " + response.body());
    }

    @Test
    @Tag("functional")
    @DisplayName("T1.07 profile rejects unauthenticated request")
    void profileRejectsUnauthenticatedRequest() throws Exception {
        HttpResponse<String> response = get("/api/user/profile", null);
        assertEquals(401, response.statusCode(),
                () -> "Unauthenticated profile access should be 401, got " + response.statusCode());
    }

    @Test
    @Tag("functional")
    @DisplayName("T1.08 valid USER token can access profile")
    void validUserTokenCanAccessProfile() throws Exception {
        String email = uniqueEmail("profile");
        register(email, "Password123!");
        String token = login(email, "Password123!");

        HttpResponse<String> response = get("/api/user/profile", token);
        assertTrue(is2xx(response.statusCode()),
                () -> "Valid authenticated user should access profile, got "
                        + response.statusCode() + ": " + response.body());
    }

    @Test
    @Tag("security")
    @DisplayName("T1.09 USER cannot access ADMIN endpoint")
    void userCannotAccessAdminEndpoint() throws Exception {
        String email = uniqueEmail("rbac");
        register(email, "Password123!");
        String token = login(email, "Password123!");

        HttpResponse<String> response = get("/api/admin/users", token);
        assertEquals(403, response.statusCode(),
                () -> "USER must receive 403 from ADMIN endpoint, got "
                        + response.statusCode() + ": " + response.body());
    }

    @Test
    @Tag("security")
    @DisplayName("T1.10 malformed JWT is rejected")
    void malformedJwtRejected() throws Exception {
        HttpResponse<String> response = get("/api/user/profile", "this.is.not-a-valid-jwt");
        assertTrue(response.statusCode() == 401 || response.statusCode() == 403,
                () -> "Malformed token must be rejected, got " + response.statusCode());
    }

    @Test
    @Tag("security")
    @DisplayName("T1.11 signature-tampered JWT is rejected")
    void tamperedJwtRejected() throws Exception {
        String email = uniqueEmail("tamper");
        register(email, "Password123!");
        String token = login(email, "Password123!");
        String tampered = tamperSignature(token);

        HttpResponse<String> response = get("/api/user/profile", tampered);
        assertTrue(response.statusCode() == 401 || response.statusCode() == 403,
                () -> "Tampered JWT must be rejected, got " + response.statusCode());
    }

    @Test
    @Tag("security")
    @DisplayName("T1.12 registration cannot escalate role to ADMIN")
    void registrationCannotEscalateRole() throws Exception {
        String email = uniqueEmail("escalate");
        String body = JSON.createObjectNode()
                .put("email", email)
                .put("password", "Password123!")
                .put("role", "ADMIN")
                .toString();

        HttpResponse<String> registration = postJson("/api/auth/register", body);
        assertTrue(is2xx(registration.statusCode()),
                () -> "Registration with unknown/extra role field may ignore the field, "
                        + "but registration itself should remain usable. Got "
                        + registration.statusCode() + ": " + registration.body());

        String token = login(email, "Password123!");
        HttpResponse<String> admin = get("/api/admin/users", token);

        assertEquals(403, admin.statusCode(),
                () -> "Client-supplied role caused privilege escalation; ADMIN endpoint returned "
                        + admin.statusCode() + ": " + admin.body());
    }

    @Test
    @Tag("security")
    @DisplayName("T1.13 profile response does not expose password")
    void profileDoesNotExposePassword() throws Exception {
        String email = uniqueEmail("exposure");
        register(email, "Password123!");
        String token = login(email, "Password123!");

        HttpResponse<String> response = get("/api/user/profile", token);
        assertTrue(is2xx(response.statusCode()), "Profile request must succeed");

        String lower = response.body().toLowerCase(Locale.ROOT);
        assertFalse(lower.contains("\"password\""),
                () -> "Profile response exposes a password field: " + response.body());
        assertFalse(response.body().contains("Password123!"),
                () -> "Profile response exposes the raw password: " + response.body());
    }
}

