
package com.fss.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
// Authentication is JWT-based; no in-memory user (and no generated password in the logs).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FssBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(FssBackendApplication.class, args);
    }
}
