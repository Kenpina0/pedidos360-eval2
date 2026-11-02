package com.pedidos360.messaging;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.pedidos360.domain.Pedido;
import com.rabbitmq.client.Channel;

/**
 * Consumidor del dominio Notificaciones.
 * ACK explícito en éxito; NACK sin requeue en error para derivar a la DLQ.
 */
@Component
public class NotificacionConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionConsumer.class);

    @RabbitListener(queues = "${app.rabbit.queues.notificaciones}")
    public void consumir(Pedido pedido, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if (pedido.getCliente() == null || pedido.getCliente().isBlank()) {
                throw new IllegalArgumentException("Pedido sin cliente: no se puede notificar");
            }
            log.info("[NOTIFICACIONES] Pedido {} notificado a {}", pedido.getId(), pedido.getCliente());
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("[NOTIFICACIONES] Error en pedido {}: {}. Se envía a DLQ.",
                    pedido.getId(), ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
