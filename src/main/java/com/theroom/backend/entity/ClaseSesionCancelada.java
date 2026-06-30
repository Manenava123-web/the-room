package com.theroom.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "clase_sesion_cancelada",
        uniqueConstraints = @UniqueConstraint(columnNames = {"clase_id", "fecha"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaseSesionCancelada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clase_id", nullable = false)
    private Clase clase;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "cancelada_en", nullable = false)
    private LocalDateTime canceladaEn;
}
