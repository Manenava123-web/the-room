package com.theroom.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaqueteAdminRequest {
    private String nombre;
    private int numClases;
    private BigDecimal precio;
    private String disciplina;   // "CYCLING" | "PILATES"
    private int vigenciaDias;
    private boolean esMensual;
}
