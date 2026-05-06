package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class PagoResponse {
    private String transaccionId;
    private String status;
    private double monto;
    private int clasesAgregadas;
    private int creditosCycling;
    private LocalDate creditosCyclingVencen;
    private int creditosPilates;
    private LocalDate creditosPilatesVencen;
}
