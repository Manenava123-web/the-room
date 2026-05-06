package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class PagoHistorialDTO {
    private Long id;
    private String usuarioNombre;
    private String usuarioEmail;
    private String paqueteNombre;
    private String disciplina;
    private double monto;
    private String metodo;
    private String transaccionId;
    private int clasesAgregadas;
    private String fechaPago;
}
