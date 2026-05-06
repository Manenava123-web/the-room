package com.theroom.backend.repository;

import com.theroom.backend.entity.Clase;
import com.theroom.backend.enums.DiaSemana;
import com.theroom.backend.enums.TipoClase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClaseRepository extends JpaRepository<Clase, Long> {
    List<Clase> findByActivoTrue();
    List<Clase> findByDiaSemanaAndActivoTrue(DiaSemana diaSemana);
    List<Clase> findByTipoAndActivoTrue(TipoClase tipo);
    List<Clase> findByDiaSemanaInAndActivoTrue(List<DiaSemana> dias);
    List<Clase> findAllByOrderByDiaSemanaAscHoraAsc();

    // SELECT FOR UPDATE — bloquea la fila durante la transacción para evitar doble reservación
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Clase c WHERE c.id = :id")
    Optional<Clase> findByIdForUpdate(@Param("id") Long id);
}
