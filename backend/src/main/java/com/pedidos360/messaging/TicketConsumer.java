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
 * Consumidor del dominio Tickets. Regla de negocio: pedidos con cantidad
 * mayor a 100 requieren revisión manual y se derivan a la DLQ.
 */
@Component
public class TicketConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketConsumer.class);

    @RabbitListener(queues = "${app.rabbit.queues.tickets}")
    public void consumir(Pedido pedido, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if (pedido.getCantidad() == null || pedido.getCantidad() > 100) {
                throw new IllegalArgumentException("Cantidad fuera de rango para ticket automático");
            }
            log.info("[TICKETS] Ticket generado para pedido {} ({} unidades)",
                    pedido.getId(), pedido.getCantidad());
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("[TICKETS] Error en pedido {}: {}. Se envía a DLQ.",
                    pedido.getId(), ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
