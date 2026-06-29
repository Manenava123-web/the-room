package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EquipoEstudioDTO {
    private String tipoClase;
    private String nombre;
    private int cantidad;
    private List<Integer> deshabilitados;
    private int disponibles;
    private int clasesAfectadas;
}
