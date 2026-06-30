package com.theroom.backend.dto;

import com.theroom.backend.enums.DiaSemana;
import com.theroom.backend.enums.TipoClase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaseEspaciosAdminDTO {

    private Long claseId;
    private TipoClase tipo;
    private DiaSemana diaSemana;
    private LocalDate fecha;
    private String hora;
    private String instructor;
    private int cupoTotal;
    private int cupoTomado;
    private int lugaresDisponibles;
    private boolean llena;

    /** La sesión de esta clase en esta fecha fue cancelada por el estudio. */
    private boolean sesionCancelada;

    /** Cada asiento del 1 al cupoTotal con su estado ocupado/libre. */
    private List<EspacioDTO> espacios;

    /** Reservaciones confirmadas sin número de lugar asignado. */
    private List<ReservaSinLugarDTO> sinLugar;

    /** Reservaciones canceladas para esta clase en esta fecha. */
    private List<ReservaSinLugarDTO> canceladas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EspacioDTO {
        private int numero;
        private boolean ocupado;
        private boolean deshabilitado;
        private Long reservacionId;
        private String usuarioNombre;
        private String usuarioEmail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservaSinLugarDTO {
        private Long reservacionId;
        private String usuarioNombre;
        private String usuarioEmail;
    }
}
