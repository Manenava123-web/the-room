package com.theroom.backend.repository;

import com.theroom.backend.entity.EquipoEstudio;
import com.theroom.backend.enums.TipoClase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipoEstudioRepository extends JpaRepository<EquipoEstudio, TipoClase> {
}
