package com.vaultx.user.context;

import com.vaultx.user.context.util.TestCredentialsGenerator;
import com.vaultx.user.context.util.TestCredentialsGenerator.TestCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIT {

    static final DockerImageName PG_IMG = DockerImageName.parse("postgres:15-alpine");
    public static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PG_IMG)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2));
    static final DockerImageName KAFKA_IMG = DockerImageName.parse("confluentinc/cp-kafka:7.6.0");
    public static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(KAFKA_IMG)
            .withStartupTimeout(Duration.ofMinutes(3))
            .waitingFor(Wait.forListeningPort());
    static final DockerImageName REDIS_IMG = DockerImageName.parse("redis:7-alpine");
    public static final GenericContainer<?> redis =
            new GenericContainer<>(REDIS_IMG).withExposedPorts(6379).waitingFor(Wait.forListeningPort());

    static {
        postgres.start();
        kafka.start();
        redis.start();
    }

    protected WebSocketStompClient stompClient;
    protected StompSession stompSession;
    @Autowired
    protected TestRestTemplate http;
    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void injectProps(DynamicPropertyRegistry r) {
        // Postgres
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);

        // Kafka
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
        r.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);
        r.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        r.add("spring.kafka.consumer.group-id", () -> "test-consumer-group");

        // Redis
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    protected void setupWebSocketClient() throws ExecutionException, InterruptedException, TimeoutException {
        String token;
        if (this instanceof PrivateChatIT) {
            token = ((PrivateChatIT) this).senderToken;
        } else if (this instanceof ChatRequestIT) {
            token = ((ChatRequestIT) this).senderToken;
        } else {
            throw new IllegalStateException("No authentication token available for WebSocket connection");
        }

        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        this.stompClient = new WebSocketStompClient(webSocketClient);
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add("Authorization", "Bearer " + token);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        String wsUrl = "ws://localhost:" + port + "/ws";

        this.stompSession = stompClient
                .connectAsync(wsUrl, handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {
                })
                .get(5, TimeUnit.SECONDS);
    }

    protected <T> CompletableFuture<T> subscribe(String destination, Class<T> responseType) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();

        stompSession.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return responseType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                completableFuture.complete((T) payload);
            }
        });

        return completableFuture;
    }

    protected HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    protected <T> HttpEntity<T> createEntity(T body, HttpHeaders headers) {
        return new HttpEntity<>(body, headers);
    }

    protected <T> HttpEntity<T> createEntity(T body) {
        return new HttpEntity<>(body);
    }

    // Delegating to TestCredentialsGenerator methods to maintain backward compatibility
    protected String generateUniqueEmail(String prefix) {
        return TestCredentialsGenerator.generateUniqueEmail(prefix);
    }

    protected String generateUniqueEmail() {
        return TestCredentialsGenerator.generateUniqueEmail();
    }

    protected String generateUniqueUsername(String prefix) {
        return TestCredentialsGenerator.generateUniqueUsername(prefix);
    }

    protected String generateUniqueUsername() {
        return TestCredentialsGenerator.generateUniqueUsername();
    }

    protected String getTestPassword() {
        return TestCredentialsGenerator.getTestPassword();
    }

    protected TestCredentials generateTestCredentials() {
        return TestCredentialsGenerator.generateTestCredentials();
    }

    protected TestCredentials generateTestCredentials(String emailPrefix, String usernamePrefix) {
        return TestCredentialsGenerator.generateTestCredentials(emailPrefix, usernamePrefix);
    }
}
