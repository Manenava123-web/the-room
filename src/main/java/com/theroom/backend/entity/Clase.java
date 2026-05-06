package com.theroom.backend.entity;

import com.theroom.backend.enums.DiaSemana;
import com.theroom.backend.enums.TipoClase;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "clases")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Clase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoClase tipo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "instructor_id", nullable = true)
    private Instructor instructor;

    // Hora en formato "HH:mm" (ej. "06:00", "18:00")
    @Column(nullable = false, length = 5)
    private String hora;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    private DiaSemana diaSemana;

    @Column(name = "cupo_total", nullable = false)
    private int cupoTotal;

    @Column(name = "cupo_tomado", nullable = false)
    private int cupoTomado = 0;

    @Column(nullable = false)
    private boolean activo = true;

    public int getLugaresDisponibles() {
        return cupoTotal - cupoTomado;
    }

    public boolean isFull() {
        return cupoTomado >= cupoTotal;
    }
}
