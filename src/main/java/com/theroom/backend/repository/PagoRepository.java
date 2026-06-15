package com.theroom.backend.repository;

import com.theroom.backend.entity.Pago;
import com.theroom.backend.enums.TipoDisciplina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByFechaPagoBetweenOrderByFechaPagoDesc(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(p) > 0 FROM Pago p WHERE p.usuario.id = :usuarioId AND p.paquete.disciplina = :disciplina AND p.paquete.numClases = 1")
    boolean existsVisitaPagadaPorUsuarioYDisciplina(@Param("usuarioId") Long usuarioId, @Param("disciplina") TipoDisciplina disciplina);
}
