package com.pedidos360.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PedidoRequest(
        @NotBlank(message = "El producto es obligatorio") String producto,
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mayor o igual a 1") Integer cantidad,
        @NotBlank(message = "El cliente es obligatorio") String cliente
) {
}
