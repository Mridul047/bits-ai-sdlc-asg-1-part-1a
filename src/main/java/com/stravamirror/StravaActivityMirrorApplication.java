package com.stravamirror;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StravaActivityMirrorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StravaActivityMirrorApplication.class, args);
    }
}
