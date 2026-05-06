package com.theroom.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class SolicitarResetRequest {
    @NotBlank @Email
    private String email;
}
