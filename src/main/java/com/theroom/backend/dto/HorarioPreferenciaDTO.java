package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class HorarioPreferenciaDTO {
    private String diaSemana;
    private String hora;
    private String tipoClase;
    private int veces;
}
