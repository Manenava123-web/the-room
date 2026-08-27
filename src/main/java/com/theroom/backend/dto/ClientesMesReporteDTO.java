package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class ClientesMesReporteDTO {
    private int anio;
    private int mes;
    private String mesLabel;
    private int totalClientes;
    private int totalCycling;
    private int totalPilates;
    private double totalCobrado;
    private List<DemandaHorarioDTO> demandaHorarios;
    private List<ClienteActivoMesDTO> clientes;
}
