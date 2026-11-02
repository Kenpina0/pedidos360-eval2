package com.pedidos360.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ExchangeRequest(
        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{1,64}$",
                message = "El nombre solo admite letras, números, punto, guion y guion bajo (máx. 64)")
        String name,
        @NotBlank(message = "El tipo es obligatorio")
        @Pattern(regexp = "direct|topic|fanout", message = "El tipo debe ser direct, topic o fanout")
        String type
) {
}
