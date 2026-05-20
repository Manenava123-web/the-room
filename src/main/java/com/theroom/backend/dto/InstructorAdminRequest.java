package com.theroom.backend.dto;

import com.theroom.backend.enums.TipoClase;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class InstructorAdminRequest {
    private String nombre;
    private String apellido;
    private Set<TipoClase> especialidades = new HashSet<>();
    private String bio;
}
