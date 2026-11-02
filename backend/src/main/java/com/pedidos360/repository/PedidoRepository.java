package com.pedidos360.repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

import com.pedidos360.domain.Pedido;

/**
 * Repositorio en memoria. En producción se reemplaza por JPA/RDS sin cambiar
 * los servicios de negocio (separación de responsabilidades).
 */
@Repository
public class PedidoRepository {

    private final Map<Long, Pedido> pedidos = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public List<Pedido> findAll() {
        return pedidos.values().stream()
                .sorted(Comparator.comparing(Pedido::getId))
                .toList();
    }

    public Optional<Pedido> findById(Long id) {
        return Optional.ofNullable(pedidos.get(id));
    }

    public Pedido save(Pedido pedido) {
        if (pedido.getId() == null) {
            pedido.setId(sequence.incrementAndGet());
            pedido.setFechaCreacion(Instant.now());
        }
        pedidos.put(pedido.getId(), pedido);
        return pedido;
    }

    public void deleteById(Long id) {
        pedidos.remove(id);
    }
}
