package com.theroom.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaseEnCursoDTO {

    private Long claseId;
    private String tipo;
    private String disciplina;
    private String hora;
    private String instructor;
    private int cupoTotal;
    private int cupoTomado;
    private List<LugarOcupadoDTO> reservaciones;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LugarOcupadoDTO {
        private Long reservacionId;
        private Long usuarioId;
        private String nombre;
        private String email;
        private Integer lugarNumero;
    }
}
