package com.pedidos360.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.stereotype.Service;

import com.pedidos360.config.RabbitProperties;
import com.pedidos360.dto.ExchangeRequest;
import com.pedidos360.dto.QueueRequest;

/**
 * Encapsula toda la administración de colas y exchanges (indicador 10%).
 * Los controladores solo llaman a métodos de alto nivel.
 */
@Service
public class RabbitAdminService {

    private final AmqpAdmin amqpAdmin;
    private final RabbitProperties props;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitProperties props) {
        this.amqpAdmin = amqpAdmin;
        this.props = props;
    }

    public void crearCola(QueueRequest request) {
        String nombre = request.name().trim();
        String dlq = (request.dlq() == null || request.dlq().isBlank())
                ? nombre + ".dlq"
                : request.dlq().trim();

        Queue cola = QueueBuilder.durable(nombre)
                .deadLetterExchange(props.getDlxExchange())
                .deadLetterRoutingKey(dlq)
                .build();

        Queue colaMuerta = QueueBuilder.durable(dlq).build();

        amqpAdmin.declareQueue(cola);
        amqpAdmin.declareQueue(colaMuerta);
        amqpAdmin.declareBinding(BindingBuilder.bind(colaMuerta)
                .to(new DirectExchange(props.getDlxExchange()))
                .with(dlq));
    }

    public void eliminarCola(String nombre) {
        amqpAdmin.deleteQueue(nombre);
        amqpAdmin.deleteQueue(nombre + ".dlq");
    }

    public void crearExchange(ExchangeRequest request) {
        Exchange exchange = switch (request.type()) {
            case "direct" -> new DirectExchange(request.name(), true, false);
            case "topic" -> new TopicExchange(request.name(), true, false);
            case "fanout" -> new FanoutExchange(request.name(), true, false);
            default -> throw new IllegalArgumentException("Tipo de exchange no soportado");
        };
        amqpAdmin.declareExchange(exchange);
    }

    public List<String> listarColas() {
        List<String> colas = new ArrayList<>();
        colas.add(props.getQueues().getNotificaciones());
        colas.add(props.getQueues().getTickets());
        colas.add(props.getQueues().getDocumentos());
        colas.add(props.getDlqs().getNotificaciones());
        colas.add(props.getDlqs().getTickets());
        colas.add(props.getDlqs().getDocumentos());
        return colas;
    }

    public List<String> listarExchanges() {
        return List.of(props.getDirectExchange(), props.getTopicExchange(), props.getDlxExchange());
    }
}
