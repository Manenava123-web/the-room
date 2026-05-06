package com.theroom.backend.entity;

import com.theroom.backend.enums.TipoClase;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "instructores")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Instructor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String apellido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoClase especialidad;

    @Column(length = 300)
    private String bio;

    @Column(name = "foto_url")
    private String fotoUrl;

    @Column(nullable = false)
    private boolean activo = true;

    public String getNombreCompleto() {
        return nombre + " " + apellido;
    }
}
