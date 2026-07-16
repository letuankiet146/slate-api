package com.slatevn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SlateVnApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlateVnApplication.class, args);
    }
}
