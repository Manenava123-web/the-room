package com.theroom.backend.dto;

import lombok.Data;

@Data
public class PaypalCapturaRequest {
    private String orderId;
    private Long paqueteId;
}
