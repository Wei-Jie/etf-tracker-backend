package com.etftracker.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EtfTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EtfTrackerApplication.class, args);
    }
}
