package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EquipoEstudioDTO {
    private String tipoClase;
    private String nombre;
    private int cantidad;
    private int clasesAfectadas;
}
