package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class DemandaHorarioDTO {
    private String diaSemana;
    private String hora;
    private String tipoClase;
    private int clientesConReserva;
    private int clientesHabituales;
    private int indiceDemanda;
}
