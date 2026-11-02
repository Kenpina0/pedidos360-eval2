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
 * Consumidor del dominio Documentos. Genera el comprobante del pedido.
 * Si el producto está vacío, el mensaje se rechaza y va a la DLQ.
 */
@Component
public class DocumentoConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentoConsumer.class);

    @RabbitListener(queues = "${app.rabbit.queues.documentos}")
    public void consumir(Pedido pedido, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if (pedido.getProducto() == null || pedido.getProducto().isBlank()) {
                throw new IllegalArgumentException("Producto vacío: no se puede generar documento");
            }
            log.info("[DOCUMENTOS] Comprobante generado para pedido {} - {}",
                    pedido.getId(), pedido.getProducto());
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("[DOCUMENTOS] Error en pedido {}: {}. Se envía a DLQ.",
                    pedido.getId(), ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
