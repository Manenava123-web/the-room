package com.theroom.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class UsuarioClienteDTO {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
}
