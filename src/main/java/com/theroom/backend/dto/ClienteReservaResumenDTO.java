package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data @Builder
public class ClienteReservaResumenDTO {
    private Long reservacionId;
    private LocalDate fecha;
    private String diaSemana;
    private String hora;
    private String tipoClase;
    private Integer lugarNumero;
    private String instructor;
}
