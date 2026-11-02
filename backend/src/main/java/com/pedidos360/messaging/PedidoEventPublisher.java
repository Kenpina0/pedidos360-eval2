package com.pedidos360.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.pedidos360.config.RabbitProperties;
import com.pedidos360.domain.Pedido;

/**
 * Publica eventos de dominio. La lógica de negocio no conoce nombres de colas
 * ni exchanges: solo invoca métodos de alto nivel.
 */
@Component
public class PedidoEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties props;

    public PedidoEventPublisher(RabbitTemplate rabbitTemplate, RabbitProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    public void publicarCreado(Pedido pedido) {
        // Exchange direct: notificación puntual al crear.
        rabbitTemplate.convertAndSend(props.getDirectExchange(),
                props.getRoutingKeys().getPedidoCreado(), pedido);
        // Exchange topic: ticket y documento se enrutan por patrón.
        rabbitTemplate.convertAndSend(props.getTopicExchange(),
                props.getRoutingKeys().getPedidoCreadoTicket(), pedido);
        rabbitTemplate.convertAndSend(props.getTopicExchange(),
                props.getRoutingKeys().getPedidoCreadoDocumento(), pedido);
    }

    public void publicarActualizado(Pedido pedido) {
        rabbitTemplate.convertAndSend(props.getTopicExchange(),
                props.getRoutingKeys().getPedidoActualizadoTicket(), pedido);
    }

    public void publicarCancelado(Pedido pedido) {
        rabbitTemplate.convertAndSend(props.getTopicExchange(),
                props.getRoutingKeys().getPedidoCanceladoDocumento(), pedido);
    }
}
