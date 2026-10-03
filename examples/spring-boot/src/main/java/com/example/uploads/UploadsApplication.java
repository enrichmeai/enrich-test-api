package com.example.uploads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** A small service that stores uploaded files in an S3 bucket. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class UploadsApplication {

  public static void main(String[] args) {
    SpringApplication.run(UploadsApplication.class, args);
  }
}
