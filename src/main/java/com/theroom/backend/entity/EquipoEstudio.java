package com.theroom.backend.entity;

import com.theroom.backend.enums.TipoClase;
import jakarta.persistence.*;
import lombok.*;

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
}
