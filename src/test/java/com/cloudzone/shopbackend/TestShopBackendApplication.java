package com.cloudzone.shopbackend;

import org.springframework.boot.SpringApplication;

public class TestShopBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(ShopBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
