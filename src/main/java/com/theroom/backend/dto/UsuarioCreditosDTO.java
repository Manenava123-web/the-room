package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data @Builder
public class UsuarioCreditosDTO {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private int creditosCycling;
    private LocalDate creditosCyclingVencen;
    private int creditosPilates;
    private LocalDate creditosPilatesVencen;
}
