package com.theroom.backend.dto;

import com.theroom.backend.enums.DiaSemana;
import com.theroom.backend.enums.TipoClase;
import lombok.Data;

@Data
public class ClaseAdminRequest {
    private TipoClase tipo;
    private DiaSemana diaSemana;
    private String hora;
    private Long instructorId;
    private int cupoTotal;
}
