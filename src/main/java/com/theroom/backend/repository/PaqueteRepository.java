package com.theroom.backend.repository;

import com.theroom.backend.entity.Paquete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaqueteRepository extends JpaRepository<Paquete, Long> {
    List<Paquete> findByActivoTrueOrderByDisciplinaAscNumClasesAsc();
    List<Paquete> findAllByOrderByDisciplinaAscNumClasesAsc();
}
