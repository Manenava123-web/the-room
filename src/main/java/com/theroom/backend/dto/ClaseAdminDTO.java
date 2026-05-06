package com.theroom.backend.dto;

import com.theroom.backend.enums.DiaSemana;
import com.theroom.backend.enums.TipoClase;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ClaseAdminDTO {
    private Long id;
    private TipoClase tipo;
    private DiaSemana diaSemana;
    private String hora;
    private int cupoTotal;
    private int cupoTomado;
    private int lugaresDisponibles;
    private boolean llena;
    private boolean activo;
    private Long instructorId;
    private String instructorNombre;
}
