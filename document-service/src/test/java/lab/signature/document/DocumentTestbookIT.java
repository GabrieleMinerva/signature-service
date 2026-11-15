package lab.signature.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.MICROS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;

@TestMethodOrder(OrderAnnotation.class)
class DocumentTestbookIT {
    private static final String DOCUMENTS = "/api/documents";
    private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString("demo:demo".getBytes(UTF_8));
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static final String PROJECT = "signature-it-" + UUID.randomUUID().toString().substring(0, 8);
    private static final Path ROOT = Path.of(System.getProperty("compose.root", "..")).toAbsolutePath().normalize();

    @TempDir
    static Path temp;

    private static Path override;
    private static String api;
    private static String management;

    @BeforeAll
    static void startStack() throws Exception {
        override = Files.writeString(temp.resolve("compose.yml"), """
                services:
                  rabbitmq:
                    ports: !override ["127.0.0.1::15672"]
                  document-service:
                    ports: !override ["127.0.0.1::8080"]
                    environment:
                      SPRING_SECURITY_USER_NAME: demo
                      SPRING_SECURITY_USER_PASSWORD: demo
                      SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT: 2500
                """);
        compose("up", "--build", "-d", "--wait", "--wait-timeout", "120");
        api = "http://" + compose("port", "document-service", "8080");
        management = "http://" + compose("port", "rabbitmq", "15672") + "/api";
        await("Document Service ready", () -> get(DOCUMENTS).statusCode() == 200);
    }

    @AfterAll
    static void stopStack() throws Exception {
        if (override != null) {
            compose("down", "--volumes", "--rmi", "local");
        }
    }

    @Test
    @Order(1)
    @DisplayName("API-06 Lists an empty archive, then includes the created document")
    void listsDocuments() throws Exception {
        var empty = get(DOCUMENTS);
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(body(empty).isArray()).isTrue();
        assertThat(body(empty)).isEmpty();

        var document = create();
        var list = get(DOCUMENTS);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(body(list)).hasSize(1);
        assertSameDocument(body(list).get(0), document);
    }

    @Test
    @DisplayName("API-02 Creates a draft with identifier, title, creation time and Location")
    void createsDraft() throws Exception {
        var response = post(DOCUMENTS, title("Accordo"));
        assertThat(response.statusCode()).isEqualTo(201);
        var document = body(response);
        assertThat(document.path("title").asText()).isEqualTo("Accordo");
        assertThat(document.path("status").asText()).isEqualTo("DRAFT");
        UUID id = UUID.fromString(document.path("id").asText());
        assertThat(Instant.parse(document.path("createdAt").asText())).isBeforeOrEqualTo(Instant.now().plusSeconds(5));
        assertLocation(response, DOCUMENTS + "/" + id);
    }

    @Test
    @DisplayName("API-03 Persists distinct documents with the same title")
    void allowsDuplicateTitles() throws Exception {
        var first = create();
        var second = create();
        assertThat(first.path("id")).isNotEqualTo(second.path("id"));
        assertSameDocument(body(get(path(first))), first);
        assertSameDocument(body(get(path(second))), second);
    }

    @ParameterizedTest(name = "API-04 Invalid title [{index}]")
    @ValueSource(strings = {"{}", "{\"title\":\"\"}", "{\"title\":\"   \"}", "overlength"})
    void rejectsInvalidTitle(String input) throws Exception {
        var before = body(get(DOCUMENTS));
        String payload = input.equals("overlength") ? title("a".repeat(201)) : input;
        assertThat(post(DOCUMENTS, payload).statusCode()).isEqualTo(400);
        assertThat(body(get(DOCUMENTS))).isEqualTo(before);
    }

    @Test
    @DisplayName("API-05 Reads one document with its current state")
    void getsDocument() throws Exception {
        var document = create();
        var response = get(path(document));
        assertThat(response.statusCode()).isEqualTo(200);
        assertSameDocument(body(response), document);
    }

    @Test
    @DisplayName("API-07 Distinguishes unknown IDs from malformed IDs")
    void rejectsUnknownAndMalformedIds() throws Exception {
        String unknown = DOCUMENTS + "/" + UUID.randomUUID();
        String malformed = DOCUMENTS + "/invalid";
        assertThat(get(unknown).statusCode()).isEqualTo(404);
        assertThat(post(unknown + "/sign", null).statusCode()).isEqualTo(404);
        assertThat(get(malformed).statusCode()).isEqualTo(400);
        assertThat(post(malformed + "/sign", null).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("FIR-01 Accepts the signature request and eventually shows SIGNED")
    void completesSignature() throws Exception {
        String path = path(create());
        acceptSignature(path);
        awaitStatus(path, "SIGNED");
    }

    @Test
    @DisplayName("FIR-02 Concurrent requests publish one message and reject one request")
    void acceptsOneConcurrentRequest() throws Exception {
        String path = path(create());
        compose("stop", "signature-service");
        try {
            await("request queue empty", () -> ready("signature.requests") == 0);
            var request = request(api + path + "/sign", "POST", null);
            var first = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            var second = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            assertThat(List.of(first.get(20, TimeUnit.SECONDS).statusCode(),
                    second.get(20, TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(202, 409);
            await("one request queued", () -> ready("signature.requests") == 1);
        } finally {
            compose("start", "signature-service");
        }
        awaitStatus(path, "SIGNED");
    }

    @Test
    @DisplayName("FIR-03 Rejects another signature request for a signed document")
    void rejectsSigningSignedDocument() throws Exception {
        String path = path(create());
        acceptSignature(path);
        awaitStatus(path, "SIGNED");
        assertThat(post(path + "/sign", null).statusCode()).isEqualTo(409);
        assertThat(status(path)).isEqualTo("SIGNED");
    }

    @Test
    @DisplayName("FIR-04 Acknowledges a replay of the same result without changing the signed document")
    void toleratesRepeatedSignatureResult() throws Exception {
        var document = create();
        String path = path(document);
        compose("stop", "signature-service");
        try {
            acceptSignature(path);
            String fetch = """
                    {"count":1,"ackmode":"ack_requeue_false","encoding":"auto"}
                    """;
            var queued = send(management + "/queues/%2F/signature.requests/get", "POST", fetch);
            assertThat(queued.statusCode()).isEqualTo(200);
            assertThat(body(queued)).hasSize(1);
            String payload = body(queued).get(0).path("payload").asText();
            assertThat(JSON.readTree(payload).path("documentId")).isEqualTo(document.path("id"));
            String message = JSON.writeValueAsString(Map.of(
                    "properties", Map.of("content_type", "application/json"),
                    "routing_key", "signature.results", "payload", payload, "payload_encoding", "string"));
            long before = acknowledgements("signature.results");
            publishResult(message);
            awaitStatus(path, "SIGNED");
            await("first result acknowledged", () -> acknowledgements("signature.results") > before);
            var signed = body(get(path));

            long afterFirst = acknowledgements("signature.results");
            publishResult(message);
            await("replayed result acknowledged", () -> acknowledgements("signature.results") > afterFirst);
            assertThat(body(get(path))).isEqualTo(signed);
        } finally {
            compose("start", "signature-service");
        }
    }

    @Test
    @DisplayName("OPS-01 Documents remain accessible while the signature worker is offline")
    void queuesWhileWorkerIsOffline() throws Exception {
        compose("stop", "signature-service");
        String path;
        try {
            path = path(create());
            acceptSignature(path);
            await("request queued while worker is offline", () -> ready("signature.requests") == 1);
            assertThat(status(path)).isEqualTo("PENDING_SIGNATURE");
            assertThat(post(path + "/sign", null).statusCode()).isEqualTo(409);
        } finally {
            compose("start", "signature-service");
        }
        awaitStatus(path, "SIGNED");
    }

    @Test
    @Order(Integer.MAX_VALUE - 1)
    @DisplayName("OPS-02 Broker outage leaves a pending document without automatic republication")
    void reportsBrokerOutage() throws Exception {
        String path = path(create());
        compose("stop", "rabbitmq");
        try {
            assertThat(post(path + "/sign", null).statusCode()).isEqualTo(503);
            assertThat(status(path)).isEqualTo("PENDING_SIGNATURE");
        } finally {
            compose("start", "rabbitmq");
            management = "http://" + compose("port", "rabbitmq", "15672") + "/api";
            await("RabbitMQ consumers reconnected", () ->
                    queue("signature.requests").path("consumers").asInt() == 1
                            && queue("signature.results").path("consumers").asInt() == 1);
        }

        // A new request can make a full round trip after recovery.
        String probe = path(create());
        acceptSignature(probe);
        awaitStatus(probe, "SIGNED");
        assertThat(status(path)).isEqualTo("PENDING_SIGNATURE");
    }

    @Test
    @DisplayName("OPS-03 Populates Redis on a miss and repopulates it after expiry")
    void populatesCacheAfterExpiry() throws Exception {
        var document = create();
        String path = path(document);
        String key = cacheKey(document);
        assertThat(redis("EXISTS", key)).isEqualTo("0");
        assertThat(status(path)).isEqualTo("DRAFT");
        assertThat(redis("GET", key)).isEqualTo("DRAFT");
        assertThat(Integer.parseInt(redis("TTL", key))).isBetween(1, 300);

        assertThat(redis("PEXPIRE", key, "1")).isEqualTo("1");
        await("Redis key expired", () -> redis("EXISTS", key).equals("0"));
        assertThat(status(path)).isEqualTo("DRAFT");
        assertThat(redis("GET", key)).isEqualTo("DRAFT");
        assertThat(Integer.parseInt(redis("TTL", key))).isBetween(1, 300);
    }

    @Test
    @DisplayName("OPS-04 Invalidates the cached draft and repopulates Redis with SIGNED")
    void invalidatesCacheOnSignature() throws Exception {
        var document = create();
        String path = path(document);
        String key = cacheKey(document);
        assertThat(status(path)).isEqualTo("DRAFT");
        assertThat(redis("GET", key)).isEqualTo("DRAFT");
        acceptSignature(path);

        // Read the uncached list while waiting, so polling cannot repopulate a stale status.
        await("PostgreSQL status SIGNED and Redis key invalidated", () ->
                listedAsSigned(document.path("id").asText()) && redis("EXISTS", key).equals("0"));
        assertThat(status(path)).isEqualTo("SIGNED");
        assertThat(redis("GET", key)).isEqualTo("SIGNED");
    }

    @Test
    @DisplayName("OPS-05 Falls back to PostgreSQL with Redis offline")
    void toleratesRedisOutage() throws Exception {
        String path = path(create());
        compose("stop", "redis");
        try {
            assertThat(status(path)).isEqualTo("DRAFT");
        } finally {
            compose("start", "redis");
            await("Redis ready", () -> redis("PING").equals("PONG"));
        }
    }

    @Test
    @Order(Integer.MAX_VALUE)
    @DisplayName("OPS-06 Redis cannot serve the document during a PostgreSQL outage")
    void reportsPostgresOutage() throws Exception {
        var document = create();
        String path = path(document);
        assertThat(status(path)).isEqualTo("DRAFT");
        assertThat(redis("GET", cacheKey(document))).isEqualTo("DRAFT");
        compose("stop", "postgres");
        try {
            assertThat(get(path).statusCode()).isEqualTo(503);
            assertThat(post(DOCUMENTS, title("Unavailable")).statusCode()).isEqualTo(503);
        } finally {
            compose("start", "postgres");
            await("PostgreSQL connection restored", () -> get(path).statusCode() == 200);
        }
    }

    private static JsonNode create() throws Exception {
        var response = post(DOCUMENTS, title("Accordo"));
        assertThat(response.statusCode()).isEqualTo(201);
        return body(response);
    }

    private static String title(String title) throws IOException {
        return JSON.writeValueAsString(Map.of("title", title));
    }

    private static String path(JsonNode document) {
        return DOCUMENTS + "/" + document.path("id").asText();
    }

    private static String cacheKey(JsonNode document) {
        return "document:status:" + document.path("id").asText();
    }

    private static void acceptSignature(String path) throws Exception {
        var response = post(path + "/sign", null);
        assertThat(response.statusCode()).isEqualTo(202);
        assertLocation(response, path);
    }

    private static void assertLocation(HttpResponse<String> response, String path) {
        assertThat(response.headers().firstValue("Location")).contains(path);
    }

    private static void assertSameDocument(JsonNode actual, JsonNode expected) {
        for (String field : List.of("id", "title", "status")) {
            assertThat(actual.path(field)).as(field).isEqualTo(expected.path(field));
        }
        // PostgreSQL stores timestamps with microsecond precision.
        assertThat(Instant.parse(actual.path("createdAt").asText()))
                .isCloseTo(Instant.parse(expected.path("createdAt").asText()), within(1, MICROS));
    }

    private static void awaitStatus(String path, String expected) throws Exception {
        if (expected.equals("SIGNED")) {
            String id = path.substring(DOCUMENTS.length() + 1);
            await(path + " is signed in PostgreSQL", () -> listedAsSigned(id));
        }
        await(path + " reaches " + expected, () -> status(path).equals(expected));
    }

    private static String status(String path) throws Exception {
        var response = get(path);
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response).path("status").asText();
    }

    private static boolean listedAsSigned(String id) throws Exception {
        for (JsonNode document : body(get(DOCUMENTS))) {
            if (document.path("id").asText().equals(id)) {
                return document.path("status").asText().equals("SIGNED");
            }
        }
        return false;
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return send(api + path, "GET", null);
    }

    private static HttpResponse<String> post(String path, String payload) throws Exception {
        return send(api + path, "POST", payload);
    }

    private static HttpResponse<String> send(String url, String method, String payload) throws Exception {
        return HTTP.send(request(url, method, payload), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest request(String url, String method, String payload) {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).header("Authorization", AUTH);
        if (payload == null) {
            return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(payload)).build();
    }

    private static JsonNode body(HttpResponse<String> response) throws IOException {
        return JSON.readTree(response.body());
    }

    private static JsonNode queue(String queue) throws Exception {
        var response = send(management + "/queues/%2F/" + queue, "GET", null);
        if (response.statusCode() != 200) {
            throw new IOException("Queue " + queue + " unavailable: " + response.statusCode());
        }
        return body(response);
    }

    private static int ready(String queue) throws Exception {
        return queue(queue).path("messages_ready").asInt(-1);
    }

    private static long acknowledgements(String queue) throws Exception {
        return queue(queue).path("message_stats").path("ack").asLong(0);
    }

    private static void publishResult(String message) throws Exception {
        var response = send(management + "/exchanges/%2F/signature/publish", "POST", message);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response).path("routed").asBoolean()).isTrue();
    }

    private static String redis(String... args) throws Exception {
        var command = new ArrayList<>(List.of("exec", "-T", "redis", "redis-cli", "--raw"));
        command.addAll(List.of(args));
        return compose(command.toArray(String[]::new));
    }

    private static String compose(String... args) throws Exception {
        var command = new ArrayList<>(List.of("docker", "compose", "-p", PROJECT,
                "-f", "docker-compose.yml", "-f", override.toString()));
        command.addAll(List.of(args));
        Path output = Files.createTempFile(temp, "compose-", ".log");
        try {
            var process = new ProcessBuilder(command).directory(ROOT.toFile())
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if (!process.waitFor(6, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                fail("Compose command timed out: %s", command);
            }
            String result = Files.readString(output).strip();
            assertThat(process.exitValue()).as("Compose command %s%n%s", command, result).isZero();
            return result;
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static void await(String description, Condition condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        IOException lastError = null;
        do {
            try {
                if (condition.isMet()) {
                    return;
                }
                lastError = null;
            } catch (IOException error) {
                lastError = error;
            }
            Thread.sleep(250);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Timed out waiting for " + description, lastError);
    }

    @FunctionalInterface
    private interface Condition {
        boolean isMet() throws Exception;
    }
}
