package com.theroom.backend.repository;

import com.theroom.backend.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByFechaPagoBetweenOrderByFechaPagoDesc(LocalDateTime from, LocalDateTime to);
}
