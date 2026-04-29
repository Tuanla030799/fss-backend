
package com.fss.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FssBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(FssBackendApplication.class, args);
    }
}
