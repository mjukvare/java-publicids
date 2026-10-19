package com.mjukvare.publicids.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// The starter scans this package and its subpackages for @PublicIdPrefix types at startup, so a missing or colliding
// prefix stops the application here rather than on the first request.
@SpringBootApplication
public class ShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
