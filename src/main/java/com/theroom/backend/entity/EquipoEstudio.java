package com.theroom.backend.entity;

import com.theroom.backend.enums.TipoClase;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "equipo_estudio")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EquipoEstudio {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_clase", nullable = false, length = 20)
    private TipoClase tipoClase;

    @Column(nullable = false)
    private int cantidad;

    @Column(nullable = false, length = 80)
    private String nombre;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "equipo_estudio_deshabilitado",
            joinColumns = @JoinColumn(name = "tipo_clase")
    )
    @Column(name = "numero_equipo", nullable = false)
    @Builder.Default
    private Set<Integer> deshabilitados = new HashSet<>();
}
