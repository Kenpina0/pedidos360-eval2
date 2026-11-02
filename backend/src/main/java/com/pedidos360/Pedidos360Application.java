package com.pedidos360;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Pedidos360Application {

    public static void main(String[] args) {
        SpringApplication.run(Pedidos360Application.class, args);
    }
}
