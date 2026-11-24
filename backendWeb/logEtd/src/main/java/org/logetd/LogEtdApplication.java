package org.logetd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogEtdApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogEtdApplication.class, args);
    }

}
