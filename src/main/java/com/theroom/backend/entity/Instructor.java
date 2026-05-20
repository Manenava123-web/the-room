package com.theroom.backend.entity;

import com.theroom.backend.enums.TipoClase;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "instructor_especialidades", joinColumns = @JoinColumn(name = "instructor_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "especialidad")
    @Builder.Default
    private Set<TipoClase> especialidades = new HashSet<>();

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
