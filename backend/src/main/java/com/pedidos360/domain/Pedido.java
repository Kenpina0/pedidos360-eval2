package com.pedidos360.domain;

import java.time.Instant;

public class Pedido {

    private Long id;
    private String producto;
    private Integer cantidad;
    private String cliente;
    private String estado;
    private Instant fechaCreacion;

    public Pedido() {
    }

    public Pedido(Long id, String producto, Integer cantidad, String cliente, String estado, Instant fechaCreacion) {
        this.id = id;
        this.producto = producto;
        this.cantidad = cantidad;
        this.cliente = cliente;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    @Override
    public String toString() {
        return "Pedido{id=" + id + ", producto='" + producto + "', cantidad=" + cantidad
                + ", cliente='" + cliente + "', estado='" + estado + "'}";
    }
}
