package com.vaultx.user.context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIT {

    /** Local images → no remote pull */
    static final DockerImageName PG_IMG = DockerImageName.parse("postgres:15-alpine");

    static final DockerImageName KAFKA_IMG = DockerImageName.parse("confluentinc/cp-kafka:7.6.0");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PG_IMG).withReuse(true);

    @Container
    static final KafkaContainer kafka = new KafkaContainer(KAFKA_IMG).withReuse(true);

    @DynamicPropertySource
    static void injectProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    protected TestRestTemplate http;

    /**
     * Create HTTP headers with authorization
     */
    protected HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    /**
     * Create an HTTP entity with the given headers
     */
    protected <T> HttpEntity<T> createEntity(T body, HttpHeaders headers) {
        return new HttpEntity<>(body, headers);
    }

    /**
     * Create an HTTP entity with the given body
     */
    protected <T> HttpEntity<T> createEntity(T body) {
        return new HttpEntity<>(body);
    }
}
