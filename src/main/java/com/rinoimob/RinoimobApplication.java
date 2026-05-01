package com.rinoimob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RinoimobApplication {

    public static void main(String[] args) {
        SpringApplication.run(RinoimobApplication.class, args);
    }

}
