package com.pedidos360.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record QueueRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{1,64}$",
                message = "El nombre solo admite letras, números, punto, guion y guion bajo (máx. 64)")
        String name,
        Boolean durable,
        String dlq
) {
}
