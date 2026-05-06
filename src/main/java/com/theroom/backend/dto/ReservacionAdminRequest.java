package com.theroom.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ReservacionAdminRequest {

    @NotNull(message = "El ID de usuario es obligatorio")
    private Long usuarioId;

    @NotNull(message = "El ID de clase es obligatorio")
    private Long claseId;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    private Integer lugarNumero;
}
