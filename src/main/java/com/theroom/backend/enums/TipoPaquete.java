package com.theroom.backend.enums;

import java.math.BigDecimal;

public enum TipoPaquete {
    // Pilates Reformer
    PAQUETE_1  (1,  new BigDecimal("120.00"), "1 clase Pilates",                 TipoDisciplina.PILATES, 15),
    PAQUETE_3  (3,  new BigDecimal("330.00"), "3 clases Pilates",                TipoDisciplina.PILATES, 30),
    PAQUETE_6  (6,  new BigDecimal("600.00"), "6 clases Pilates",                TipoDisciplina.PILATES, 30),
    PAQUETE_10 (10, new BigDecimal("900.00"), "10 clases Pilates",               TipoDisciplina.PILATES, 30),
    PAQUETE_MES(20, new BigDecimal("500.00"), "Mensual Pilates (20 clases)",     TipoDisciplina.PILATES, 30),
    // Indoor Cycling
    CYCLING_1  (1,  new BigDecimal("65.00"),  "1 clase Indoor Cycling",          TipoDisciplina.CYCLING, 15),
    CYCLING_5  (5,  new BigDecimal("275.00"), "5 clases Indoor Cycling",         TipoDisciplina.CYCLING, 30),
    CYCLING_10 (10, new BigDecimal("370.00"), "10 clases Indoor Cycling",        TipoDisciplina.CYCLING, 30),
    CYCLING_MES(20, new BigDecimal("500.00"), "Mensual Indoor Cycling (20 clases)", TipoDisciplina.CYCLING, 30);

    public final int numClases;
    public final BigDecimal precio;
    public final String descripcion;
    public final TipoDisciplina disciplina;
    public final int vigenciaDias;

    TipoPaquete(int numClases, BigDecimal precio, String descripcion, TipoDisciplina disciplina, int vigenciaDias) {
        this.numClases    = numClases;
        this.precio       = precio;
        this.descripcion  = descripcion;
        this.disciplina   = disciplina;
        this.vigenciaDias = vigenciaDias;
    }
}
