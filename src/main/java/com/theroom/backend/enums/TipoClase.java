package com.theroom.backend.enums;

public enum TipoClase {
    SPINNING,
    PILATES;

    public TipoDisciplina getDisciplina() {
        return this == SPINNING ? TipoDisciplina.CYCLING : TipoDisciplina.PILATES;
    }
}
