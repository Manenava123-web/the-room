package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class CreditosDTO {
    private int creditosCycling;
    private LocalDate creditosCyclingVencen;
    private int creditosPilates;
    private LocalDate creditosPilatesVencen;
}
