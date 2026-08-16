package com.novatech.cybertech;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {


//    @Bean
//    @ServiceConnection
//    KafkaContainer kafkaContainer() {
//        return new KafkaContainer(DockerImageName.parse("apache/kafka-native:latest"));
//    }

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.4.2")).withReuse(true);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisTestContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:8.6.1")).withExposedPorts(6379);
    }


    @Bean
    @ServiceConnection
    ElasticsearchContainer elasticsearchContainer() {
        // The container's MAJOR version MUST match the elasticsearch-java client major, otherwise the
        // client's `Accept/Content-Type: application/vnd.elasticsearch+json;compatible-with=<major>`
        // negotiation header is rejected by the server with a 400 on the very first `indices.exists`
        // HEAD request — which the client surfaces as "Expecting a response body, but none was sent",
        // failing every IT at Spring context load. Spring Boot 4.0.4 -> spring-data-elasticsearch
        // 6.0.4 pulls co.elastic.clients:elasticsearch-java:9.2.6, so the test server is pinned to a
        // matching ES 9.x image. (NB: the prod Helm chart appVersion should be bumped to 9.x too for
        // parity — tracked separately; it does not affect this test harness.)
        // ES 9.x still honours xpack.security.enabled=false for the single-node portfolio setup so
        // plain HTTP works without TLS/auth.
        return new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.2.6"))
                .withEnv("xpack.security.enabled", "false")
                .withEnv("xpack.security.http.ssl.enabled", "false")
                .withEnv("discovery.type", "single-node");
    }

    @Bean
    @ServiceConnection
    MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer(DockerImageName.parse("mongo:7.0"));
    }


}
