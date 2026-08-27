package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ClienteMesPagoDTO {
    private String fechaPago;
    private String paqueteNombre;
    private String disciplina;
    private boolean esMensual;
    private double monto;
    private String metodo;
}
