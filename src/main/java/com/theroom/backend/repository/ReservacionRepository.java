package com.theroom.backend.repository;

import com.theroom.backend.entity.Reservacion;
import com.theroom.backend.enums.EstadoReservacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservacionRepository extends JpaRepository<Reservacion, Long> {

    List<Reservacion> findByUsuarioIdOrderByFechaDesc(Long usuarioId);

    List<Reservacion> findByClaseIdAndFechaAndEstado(Long claseId, LocalDate fecha, EstadoReservacion estado);

    Optional<Reservacion> findByUsuarioIdAndClaseIdAndFecha(Long usuarioId, Long claseId, LocalDate fecha);

    boolean existsByUsuarioIdAndClaseIdAndFechaAndEstado(
            Long usuarioId, Long claseId, LocalDate fecha, EstadoReservacion estado);

    @Query("SELECT COUNT(r) FROM Reservacion r WHERE r.clase.id = :claseId AND r.fecha = :fecha AND r.estado = 'CONFIRMADA'")
    int countConfirmadasByClaseAndFecha(@Param("claseId") Long claseId, @Param("fecha") LocalDate fecha);

    @Query("SELECT r FROM Reservacion r WHERE r.usuario.id = :usuarioId AND r.fecha >= :desde AND r.estado = 'CONFIRMADA' ORDER BY r.fecha ASC")
    List<Reservacion> findProximasByUsuario(@Param("usuarioId") Long usuarioId, @Param("desde") LocalDate desde);

    @Query("SELECT r FROM Reservacion r ORDER BY r.fecha DESC, r.fechaCreacion DESC")
    List<Reservacion> findAllOrdenadas();

    boolean existsByClaseId(Long claseId);

    @Query("SELECT r.lugarNumero FROM Reservacion r WHERE r.clase.id = :claseId AND r.fecha = :fecha AND r.estado = 'CONFIRMADA' AND r.lugarNumero IS NOT NULL")
    List<Integer> findLugaresOcupadosByClaseAndFecha(@Param("claseId") Long claseId, @Param("fecha") LocalDate fecha);

    boolean existsByClaseIdAndFechaAndLugarNumeroAndEstado(Long claseId, LocalDate fecha, Integer lugarNumero, EstadoReservacion estado);

    @Query("SELECT COUNT(r) FROM Reservacion r WHERE r.clase.tipo = 'SPINNING' AND YEAR(r.fecha) = :anio AND MONTH(r.fecha) = :mes AND r.estado = 'CONFIRMADA'")
    int countCyclingConfirmadasEnMes(@Param("anio") int anio, @Param("mes") int mes);

    @Query("SELECT COUNT(r) FROM Reservacion r WHERE r.clase.tipo = 'PILATES' AND YEAR(r.fecha) = :anio AND MONTH(r.fecha) = :mes AND r.estado = 'CONFIRMADA'")
    int countPilatesConfirmadasEnMes(@Param("anio") int anio, @Param("mes") int mes);
}
