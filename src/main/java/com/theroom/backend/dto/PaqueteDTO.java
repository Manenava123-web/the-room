package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaqueteDTO {
    private Long id;
    private String nombre;
    private int numClases;
    private double precio;
    private String disciplina;
    private int vigenciaDias;
    private boolean esMensual;
    private boolean activo;
}
