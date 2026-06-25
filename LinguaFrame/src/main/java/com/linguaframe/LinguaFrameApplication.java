package com.linguaframe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LinguaFrameApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinguaFrameApplication.class, args);
    }

}
