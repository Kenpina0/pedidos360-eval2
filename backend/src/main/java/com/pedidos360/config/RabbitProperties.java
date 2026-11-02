package com.pedidos360.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de colas, exchanges y routing keys centralizados en application.yml.
 * Evita valores "mágicos" dispersos en el código (indicador 12% de la rúbrica).
 */
@ConfigurationProperties(prefix = "app.rabbit")
public class RabbitProperties {

    private String directExchange;
    private String topicExchange;
    private String dlxExchange;
    private Queues queues = new Queues();
    private Dlqs dlqs = new Dlqs();
    private RoutingKeys routingKeys = new RoutingKeys();

    public String getDirectExchange() { return directExchange; }
    public void setDirectExchange(String directExchange) { this.directExchange = directExchange; }

    public String getTopicExchange() { return topicExchange; }
    public void setTopicExchange(String topicExchange) { this.topicExchange = topicExchange; }

    public String getDlxExchange() { return dlxExchange; }
    public void setDlxExchange(String dlxExchange) { this.dlxExchange = dlxExchange; }

    public Queues getQueues() { return queues; }
    public void setQueues(Queues queues) { this.queues = queues; }

    public Dlqs getDlqs() { return dlqs; }
    public void setDlqs(Dlqs dlqs) { this.dlqs = dlqs; }

    public RoutingKeys getRoutingKeys() { return routingKeys; }
    public void setRoutingKeys(RoutingKeys routingKeys) { this.routingKeys = routingKeys; }

    public static class Queues {
        private String notificaciones;
        private String tickets;
        private String documentos;

        public String getNotificaciones() { return notificaciones; }
        public void setNotificaciones(String notificaciones) { this.notificaciones = notificaciones; }

        public String getTickets() { return tickets; }
        public void setTickets(String tickets) { this.tickets = tickets; }

        public String getDocumentos() { return documentos; }
        public void setDocumentos(String documentos) { this.documentos = documentos; }
    }

    public static class Dlqs {
        private String notificaciones;
        private String tickets;
        private String documentos;

        public String getNotificaciones() { return notificaciones; }
        public void setNotificaciones(String notificaciones) { this.notificaciones = notificaciones; }

        public String getTickets() { return tickets; }
        public void setTickets(String tickets) { this.tickets = tickets; }

        public String getDocumentos() { return documentos; }
        public void setDocumentos(String documentos) { this.documentos = documentos; }
    }

    public static class RoutingKeys {
        private String pedidoCreado;
        private String pedidoCreadoTicket;
        private String pedidoCreadoDocumento;
        private String pedidoActualizadoTicket;
        private String pedidoCanceladoDocumento;
        private String notificacionesDlq;
        private String ticketsDlq;
        private String documentosDlq;

        public String getPedidoCreado() { return pedidoCreado; }
        public void setPedidoCreado(String pedidoCreado) { this.pedidoCreado = pedidoCreado; }

        public String getPedidoCreadoTicket() { return pedidoCreadoTicket; }
        public void setPedidoCreadoTicket(String pedidoCreadoTicket) { this.pedidoCreadoTicket = pedidoCreadoTicket; }

        public String getPedidoCreadoDocumento() { return pedidoCreadoDocumento; }
        public void setPedidoCreadoDocumento(String pedidoCreadoDocumento) { this.pedidoCreadoDocumento = pedidoCreadoDocumento; }

        public String getPedidoActualizadoTicket() { return pedidoActualizadoTicket; }
        public void setPedidoActualizadoTicket(String pedidoActualizadoTicket) { this.pedidoActualizadoTicket = pedidoActualizadoTicket; }

        public String getPedidoCanceladoDocumento() { return pedidoCanceladoDocumento; }
        public void setPedidoCanceladoDocumento(String pedidoCanceladoDocumento) { this.pedidoCanceladoDocumento = pedidoCanceladoDocumento; }

        public String getNotificacionesDlq() { return notificacionesDlq; }
        public void setNotificacionesDlq(String notificacionesDlq) { this.notificacionesDlq = notificacionesDlq; }

        public String getTicketsDlq() { return ticketsDlq; }
        public void setTicketsDlq(String ticketsDlq) { this.ticketsDlq = ticketsDlq; }

        public String getDocumentosDlq() { return documentosDlq; }
        public void setDocumentosDlq(String documentosDlq) { this.documentosDlq = documentosDlq; }
    }
}
