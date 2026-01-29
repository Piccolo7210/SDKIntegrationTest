package org.neurotecfinger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SivsDriverApplication {
    public static void main(String[] args) {
        SpringApplication.run(SivsDriverApplication.class, args);
    }
}