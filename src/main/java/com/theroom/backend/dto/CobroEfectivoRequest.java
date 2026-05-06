package com.theroom.backend.dto;

import lombok.Data;

@Data
public class CobroEfectivoRequest {
    private Long usuarioId;
    private Long paqueteId;
}
