package com.theroom.backend.dto;

import com.theroom.backend.enums.EstadoReservacion;
import com.theroom.backend.enums.TipoClase;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder
public class ReservacionDTO {
    private Long id;
    private Long claseId;
    private TipoClase tipoClase;
    private String instructor;
    private String hora;
    private LocalDate fecha;
    private EstadoReservacion estado;
    private LocalDateTime fechaCreacion;
    private Integer lugarNumero;
}
