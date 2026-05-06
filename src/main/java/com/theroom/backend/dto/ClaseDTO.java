package com.theroom.backend.dto;

import com.theroom.backend.enums.DiaSemana;
import com.theroom.backend.enums.TipoClase;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ClaseDTO {
    private Long id;
    private TipoClase tipo;
    private String instructor;
    private String hora;
    private DiaSemana diaSemana;
    private int cupoTotal;
    private int cupoTomado;
    private int lugaresDisponibles;
    private boolean llena;
}
