package com.theroom.backend.dto;

import com.theroom.backend.enums.TipoClase;
import lombok.Data;

@Data
public class InstructorAdminRequest {
    private String nombre;
    private String apellido;
    private TipoClase especialidad;
    private String bio;
}
