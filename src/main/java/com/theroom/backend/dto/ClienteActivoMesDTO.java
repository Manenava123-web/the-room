package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data @Builder
public class ClienteActivoMesDTO {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private List<ClienteMesPagoDTO> pagosMes;
    private int creditosCycling;
    private LocalDate creditosCyclingVencen;
    private int creditosPilates;
    private LocalDate creditosPilatesVencen;
    private List<ClienteReservaResumenDTO> reservasProximas;
    private List<HorarioPreferenciaDTO> horariosHabituales;
}
