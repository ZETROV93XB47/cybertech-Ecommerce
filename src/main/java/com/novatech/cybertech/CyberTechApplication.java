package com.novatech.cybertech;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

@Slf4j
@EnableAsync
@EnableScheduling
@SpringBootApplication
@RequiredArgsConstructor
@EnableConfigurationProperties
public class CyberTechApplication {
    static void main(String[] args) {
        SpringApplication.run(CyberTechApplication.class, args);
    }
}
