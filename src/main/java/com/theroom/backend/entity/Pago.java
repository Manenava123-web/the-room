package com.theroom.backend.entity;

import com.theroom.backend.enums.TipoDisciplina;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "usuario_nombre", nullable = false, length = 160)
    private String usuarioNombre;

    @Column(name = "usuario_email", nullable = false, length = 120)
    private String usuarioEmail;

    @Column(name = "paquete_nombre", nullable = false, length = 120)
    private String paqueteNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDisciplina disciplina;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 20)
    private String metodo;

    @Column(name = "transaccion_id", length = 120)
    private String transaccionId;

    @Column(name = "clases_agregadas", nullable = false)
    private int clasesAgregadas;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    @PrePersist
    protected void onCreate() {
        if (fechaPago == null) fechaPago = LocalDateTime.now();
    }
}
