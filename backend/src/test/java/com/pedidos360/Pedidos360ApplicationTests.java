package com.pedidos360;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class Pedidos360ApplicationTests {

    @Test
    void contextLoads() {
        // Verifica que el contexto Spring arranca sin Cognito ni RabbitMQ.
    }
}
