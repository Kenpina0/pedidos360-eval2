package com.pedidos360.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pedidos360.dto.ExchangeRequest;
import com.pedidos360.dto.QueueRequest;
import com.pedidos360.service.RabbitAdminService;

import jakarta.validation.Valid;

/**
 * API REST de administración de RabbitMQ.
 * POST /api/admin/queues, DELETE /api/admin/queues/{name},
 * POST /api/admin/exchanges (indicador 13%).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class RabbitAdminController {

    private final RabbitAdminService service;

    public RabbitAdminController(RabbitAdminService service) {
        this.service = service;
    }

    @GetMapping("/queues")
    public Map<String, List<String>> listarColas() {
        return Map.of("queues", service.listarColas());
    }

    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> crearCola(@Valid @RequestBody QueueRequest request) {
        service.crearCola(request);
        return Map.of("message", "Cola creada correctamente", "name", request.name());
    }

    @DeleteMapping("/queues/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCola(@PathVariable String name) {
        service.eliminarCola(name);
    }

    @GetMapping("/exchanges")
    public Map<String, List<String>> listarExchanges() {
        return Map.of("exchanges", service.listarExchanges());
    }

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> crearExchange(@Valid @RequestBody ExchangeRequest request) {
        service.crearExchange(request);
        return Map.of("message", "Exchange creado correctamente", "name", request.name());
    }
}
