package com.theroom.backend.dto;

import lombok.Data;

@Data
public class PagoRequest {
    private Long paqueteId;
    private String tokenId;
    private String deviceSessionId;
}
