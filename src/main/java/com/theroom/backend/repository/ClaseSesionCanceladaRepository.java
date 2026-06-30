package com.theroom.backend.repository;

import com.theroom.backend.entity.ClaseSesionCancelada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ClaseSesionCanceladaRepository extends JpaRepository<ClaseSesionCancelada, Long> {

    boolean existsByClaseIdAndFecha(Long claseId, LocalDate fecha);

    List<ClaseSesionCancelada> findByFechaBetween(LocalDate desde, LocalDate hasta);
}
