package com.pedidos360.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pedidos360.domain.Pedido;
import com.pedidos360.dto.PedidoRequest;
import com.pedidos360.messaging.PedidoEventPublisher;
import com.pedidos360.repository.PedidoRepository;

@Service
public class PedidoService {

    private final PedidoRepository repository;
    private final PedidoEventPublisher publisher;

    public PedidoService(PedidoRepository repository, PedidoEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public List<Pedido> listar() {
        return repository.findAll();
    }

    public Pedido obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado: " + id));
    }

    @Transactional
    public Pedido crear(PedidoRequest request) {
        Pedido pedido = new Pedido();
        pedido.setProducto(request.producto().trim());
        pedido.setCantidad(request.cantidad());
        pedido.setCliente(request.cliente().trim());
        pedido.setEstado("CREADO");
        repository.save(pedido);
        publisher.publicarCreado(pedido);
        return pedido;
    }

    @Transactional
    public Pedido actualizar(Long id, PedidoRequest request) {
        Pedido pedido = obtener(id);
        pedido.setProducto(request.producto().trim());
        pedido.setCantidad(request.cantidad());
        pedido.setCliente(request.cliente().trim());
        pedido.setEstado("ACTUALIZADO");
        repository.save(pedido);
        publisher.publicarActualizado(pedido);
        return pedido;
    }

    @Transactional
    public Pedido cancelar(Long id) {
        Pedido pedido = obtener(id);
        pedido.setEstado("CANCELADO");
        repository.save(pedido);
        publisher.publicarCancelado(pedido);
        return pedido;
    }

    @Transactional
    public void eliminar(Long id) {
        obtener(id);
        repository.deleteById(id);
    }
}
