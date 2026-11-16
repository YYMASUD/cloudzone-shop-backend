package com.cloudzone.shopbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point. {@code @ConfigurationPropertiesScan} picks up the typed
 * {@code app.*} property records (e.g. {@code common.security.JwtProperties}) without
 * needing {@code @EnableConfigurationProperties} on each one.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopBackendApplication.class, args);
    }

}
