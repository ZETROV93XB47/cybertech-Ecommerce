package com.novatech.cybertech;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@EnableAsync
@SpringBootApplication
@RequiredArgsConstructor
@EnableConfigurationProperties
public class CyberTechApplication implements CommandLineRunner {
    private final ApplicationContext applicationContext;

    public static void main(String[] args) {
        SpringApplication.run(CyberTechApplication.class, args);
    }

    @Override
    public void run(String... args) {
        //log.info("Inspect the beans provided by Spring Boot:");
        //Arrays.stream(applicationContext.getBeanDefinitionNames()).sorted().forEach(beanName -> log.info("Bean name : {}", beanName));
    }
}
