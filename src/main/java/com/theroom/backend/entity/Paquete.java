package com.theroom.backend.entity;

import com.theroom.backend.enums.TipoDisciplina;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "paquetes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Paquete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(name = "num_clases", nullable = false)
    private int numClases;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDisciplina disciplina;

    @Column(name = "vigencia_dias", nullable = false)
    private int vigenciaDias;

    @Column(name = "es_mensual", nullable = false)
    private boolean esMensual = false;

    @Column(nullable = false)
    private boolean activo = true;
}
