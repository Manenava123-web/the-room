package com.theroom.backend.dto.auth;

import com.theroom.backend.enums.RolUsuario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private RolUsuario rol;
}
