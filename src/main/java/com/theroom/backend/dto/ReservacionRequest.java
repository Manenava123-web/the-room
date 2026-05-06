package com.theroom.backend.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ReservacionRequest {

    @NotNull(message = "El ID de clase es obligatorio")
    private Long claseId;

    @NotNull(message = "La fecha es obligatoria")
    @FutureOrPresent(message = "No puedes reservar en una fecha pasada")
    private LocalDate fecha;

    private Integer lugarNumero;
}
