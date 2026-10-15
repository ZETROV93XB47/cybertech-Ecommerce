package com.novatech.cybertech;

import com.novatech.cybertech.logger.CybertechStartupLogger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties
public class CyberTechApplication {
    static void main(String[] args) {
        final SpringApplication application = new SpringApplication(CyberTechApplication.class);
        // Registered programmatically, not as a @Component: CybertechStartupLogger also listens
        // for ApplicationEnvironmentPreparedEvent, which fires before component scanning runs.
        application.addListeners(new CybertechStartupLogger());
        application.run(args);
    }
}
