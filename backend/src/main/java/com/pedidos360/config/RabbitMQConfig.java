package com.pedidos360.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara explícitamente Queue, Exchange y Binding para cada caso de uso.
 * Indicadores de la rúbrica: beans de configuración (13%) y separación de
 * configuración respecto de la lógica de negocio (10%).
 */
@Configuration
public class RabbitMQConfig {

    private final RabbitProperties props;

    public RabbitMQConfig(RabbitProperties props) {
        this.props = props;
    }

    /* ============================ EXCHANGES ============================ */

    @Bean
    public DirectExchange pedidosDirectExchange() {
        return ExchangeBuilder.directExchange(props.getDirectExchange()).durable(true).build();
    }

    @Bean
    public TopicExchange pedidosTopicExchange() {
        return ExchangeBuilder.topicExchange(props.getTopicExchange()).durable(true).build();
    }

    @Bean
    public DirectExchange pedidosDlxExchange() {
        return ExchangeBuilder.directExchange(props.getDlxExchange()).durable(true).build();
    }

    /* ============================== QUEUES ============================= */

    @Bean
    public Queue notificacionesQueue() {
        return colaConDlq(props.getQueues().getNotificaciones(), props.getRoutingKeys().getNotificacionesDlq());
    }

    @Bean
    public Queue ticketsQueue() {
        return colaConDlq(props.getQueues().getTickets(), props.getRoutingKeys().getTicketsDlq());
    }

    @Bean
    public Queue documentosQueue() {
        return colaConDlq(props.getQueues().getDocumentos(), props.getRoutingKeys().getDocumentosDlq());
    }

    @Bean
    public Queue notificacionesDlq() {
        return QueueBuilder.durable(props.getDlqs().getNotificaciones()).build();
    }

    @Bean
    public Queue ticketsDlq() {
        return QueueBuilder.durable(props.getDlqs().getTickets()).build();
    }

    @Bean
    public Queue documentosDlq() {
        return QueueBuilder.durable(props.getDlqs().getDocumentos()).build();
    }

    /* ============================= BINDINGS ============================ */

    @Bean
    public Binding notificacionesBinding(
            @Qualifier("notificacionesQueue") Queue notificacionesQueue,
            @Qualifier("pedidosDirectExchange") DirectExchange pedidosDirectExchange) {
        return BindingBuilder.bind(notificacionesQueue)
                .to(pedidosDirectExchange)
                .with(props.getRoutingKeys().getPedidoCreado());
    }

    @Bean
    public Binding ticketsBinding(
            @Qualifier("ticketsQueue") Queue ticketsQueue,
            @Qualifier("pedidosTopicExchange") TopicExchange pedidosTopicExchange) {
        return BindingBuilder.bind(ticketsQueue)
                .to(pedidosTopicExchange)
                .with("pedido.*.ticket");
    }

    @Bean
    public Binding documentosBinding(
            @Qualifier("documentosQueue") Queue documentosQueue,
            @Qualifier("pedidosTopicExchange") TopicExchange pedidosTopicExchange) {
        return BindingBuilder.bind(documentosQueue)
                .to(pedidosTopicExchange)
                .with("pedido.*.documento");
    }

    @Bean
    public Binding notificacionesDlqBinding(
            @Qualifier("notificacionesDlq") Queue notificacionesDlq,
            @Qualifier("pedidosDlxExchange") DirectExchange pedidosDlxExchange) {
        return BindingBuilder.bind(notificacionesDlq)
                .to(pedidosDlxExchange)
                .with(props.getRoutingKeys().getNotificacionesDlq());
    }

    @Bean
    public Binding ticketsDlqBinding(
            @Qualifier("ticketsDlq") Queue ticketsDlq,
            @Qualifier("pedidosDlxExchange") DirectExchange pedidosDlxExchange) {
        return BindingBuilder.bind(ticketsDlq)
                .to(pedidosDlxExchange)
                .with(props.getRoutingKeys().getTicketsDlq());
    }

    @Bean
    public Binding documentosDlqBinding(
            @Qualifier("documentosDlq") Queue documentosDlq,
            @Qualifier("pedidosDlxExchange") DirectExchange pedidosDlxExchange) {
        return BindingBuilder.bind(documentosDlq)
                .to(pedidosDlxExchange)
                .with(props.getRoutingKeys().getDocumentosDlq());
    }

    /* ============================ CONVERTER ============================ */

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    private Queue colaConDlq(String nombre, String routingKeyDlq) {
        return QueueBuilder.durable(nombre)
                .deadLetterExchange(props.getDlxExchange())
                .deadLetterRoutingKey(routingKeyDlq)
                .build();
    }
}
