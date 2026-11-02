package br.com.cactus.guard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class CactusGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(CactusGuardApplication.class, args);
    }
}
