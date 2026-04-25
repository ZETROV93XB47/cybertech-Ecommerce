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
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.4.2"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisTestContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:8.6.1")).withExposedPorts(6379);
    }


    @Bean
    @ServiceConnection
    ElasticsearchContainer elasticsearchContainer() {
        // Aligned with the Helm chart appVersion (elasticsearch-chart/Chart.yaml).
        // ES 8.x requires xpack.security.enabled=false for the single-node portfolio
        // setup so HTTP works without TLS/auth (matches the Helm env block).
        return new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.3"))
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
