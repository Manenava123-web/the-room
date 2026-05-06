package com.theroom.backend.service;

import com.theroom.backend.dto.ClaseEnCursoDTO;
import com.theroom.backend.dto.ReservacionAdminDTO;
import com.theroom.backend.dto.ReservacionAdminRequest;
import com.theroom.backend.dto.ReservacionDTO;
import com.theroom.backend.dto.ReservacionRequest;
import com.theroom.backend.entity.Clase;
import com.theroom.backend.entity.Reservacion;
import com.theroom.backend.entity.Usuario;
import com.theroom.backend.enums.DiaSemana;
import com.theroom.backend.enums.EstadoReservacion;
import com.theroom.backend.enums.TipoDisciplina;
import com.theroom.backend.enums.RolUsuario;
import com.theroom.backend.exception.AppException;
import com.theroom.backend.repository.ClaseRepository;
import com.theroom.backend.repository.ReservacionRepository;
import com.theroom.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservacionService {

    private final ReservacionRepository reservacionRepository;
    private final ClaseRepository claseRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${app.cycling.limite-mensual:900}")
    private int limiteMensualCycling;

    @Value("${app.pilates.limite-mensual:420}")
    private int limiteMensualPilates;

    @Transactional
    public ReservacionDTO crear(Long usuarioId, ReservacionRequest request) {
        Clase clase = claseRepository.findByIdForUpdate(request.getClaseId())
                .orElseThrow(() -> new AppException("Clase no encontrada", HttpStatus.NOT_FOUND));

        if (clase.isFull()) {
            throw new AppException("La clase está llena", HttpStatus.CONFLICT);
        }

        validarLimiteMensual(clase, request.getFecha());

        boolean yaReservado = reservacionRepository.existsByUsuarioIdAndClaseIdAndFechaAndEstado(
                usuarioId, clase.getId(), request.getFecha(), EstadoReservacion.CONFIRMADA);

        if (yaReservado) {
            throw new AppException("Ya tienes una reservación para esta clase en esa fecha", HttpStatus.CONFLICT);
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        if (usuario.getRol() == RolUsuario.CLIENTE) {
            validarCreditos(usuario, clase);
        }

        if (request.getLugarNumero() != null &&
            reservacionRepository.existsByClaseIdAndFechaAndLugarNumeroAndEstado(
                clase.getId(), request.getFecha(), request.getLugarNumero(), EstadoReservacion.CONFIRMADA)) {
            throw new AppException("Ese lugar ya está ocupado", HttpStatus.CONFLICT);
        }

        Reservacion reservacion = Reservacion.builder()
                .usuario(usuario)
                .clase(clase)
                .fecha(request.getFecha())
                .estado(EstadoReservacion.CONFIRMADA)
                .lugarNumero(request.getLugarNumero())
                .build();

        reservacionRepository.save(reservacion);

        if (usuario.getRol() == RolUsuario.CLIENTE) {
            descontarCredito(usuario, clase);
            usuarioRepository.save(usuario);
        }

        clase.setCupoTomado(clase.getCupoTomado() + 1);
        claseRepository.save(clase);

        return toDTO(reservacion);
    }

    @Transactional
    public void cancelar(Long usuarioId, Long reservacionId) {
        Reservacion reservacion = reservacionRepository.findById(reservacionId)
                .orElseThrow(() -> new AppException("Reservación no encontrada", HttpStatus.NOT_FOUND));

        if (!reservacion.getUsuario().getId().equals(usuarioId)) {
            throw new AppException("No autorizado", HttpStatus.FORBIDDEN);
        }

        if (reservacion.getEstado() != EstadoReservacion.CONFIRMADA) {
            throw new AppException("Solo se pueden cancelar reservaciones confirmadas", HttpStatus.BAD_REQUEST);
        }

        // Permitir cancelación hasta el inicio de la clase
        LocalDate hoy = LocalDate.now();
        LocalDate fecha = reservacion.getFecha();
        if (fecha.isBefore(hoy)) {
            throw new AppException("No puedes cancelar una clase que ya pasó", HttpStatus.BAD_REQUEST);
        }
        if (fecha.isEqual(hoy)) {
            LocalTime ahora = LocalTime.now();
            LocalTime inicioClase = LocalTime.parse(reservacion.getClase().getHora());
            if (!ahora.isBefore(inicioClase)) {
                throw new AppException("La clase ya comenzó, no es posible cancelar", HttpStatus.BAD_REQUEST);
            }
        }

        reservacion.setEstado(EstadoReservacion.CANCELADA);
        reservacion.setFechaCancelacion(LocalDateTime.now());
        reservacionRepository.save(reservacion);

        // Devolver crédito al cancelar con anticipación
        TipoDisciplina disc = reservacion.getClase().getTipo().getDisciplina();
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (usuario.getRol() == RolUsuario.CLIENTE) {
            if (disc == TipoDisciplina.CYCLING) {
                usuario.setCreditosCycling(usuario.getCreditosCycling() + 1);
            } else {
                usuario.setCreditosPilates(usuario.getCreditosPilates() + 1);
            }
            usuarioRepository.save(usuario);
        }

        // Liberar el cupo
        Clase clase = reservacion.getClase();
        clase.setCupoTomado(Math.max(0, clase.getCupoTomado() - 1));
        claseRepository.save(clase);
    }

    public List<ReservacionDTO> misReservaciones(Long usuarioId) {
        return reservacionRepository.findByUsuarioIdOrderByFechaDesc(usuarioId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<ReservacionDTO> misProximas(Long usuarioId) {
        return reservacionRepository.findProximasByUsuario(usuarioId, LocalDate.now())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ── Admin: obtener todas las reservaciones ────────────────
    public List<ReservacionAdminDTO> obtenerTodas() {
        return reservacionRepository.findAllOrdenadas()
                .stream().map(this::toAdminDTO).collect(Collectors.toList());
    }

    // ── Admin: crear reservación para un cliente específico ───
    @Transactional
    public ReservacionAdminDTO crearParaUsuario(ReservacionAdminRequest request) {
        Clase clase = claseRepository.findByIdForUpdate(request.getClaseId())
                .orElseThrow(() -> new AppException("Clase no encontrada", HttpStatus.NOT_FOUND));

        if (clase.isFull()) {
            throw new AppException("La clase está llena", HttpStatus.CONFLICT);
        }

        validarLimiteMensual(clase, request.getFecha());

        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        boolean yaReservado = reservacionRepository.existsByUsuarioIdAndClaseIdAndFechaAndEstado(
                usuario.getId(), clase.getId(), request.getFecha(), EstadoReservacion.CONFIRMADA);

        if (yaReservado) {
            throw new AppException("El usuario ya tiene una reservación para esta clase en esa fecha", HttpStatus.CONFLICT);
        }

        if (usuario.getRol() == RolUsuario.CLIENTE) {
            validarCreditos(usuario, clase);
        }

        if (request.getLugarNumero() != null &&
            reservacionRepository.existsByClaseIdAndFechaAndLugarNumeroAndEstado(
                clase.getId(), request.getFecha(), request.getLugarNumero(), EstadoReservacion.CONFIRMADA)) {
            throw new AppException("Ese lugar ya está ocupado", HttpStatus.CONFLICT);
        }

        Reservacion reservacion = Reservacion.builder()
                .usuario(usuario)
                .clase(clase)
                .fecha(request.getFecha())
                .estado(EstadoReservacion.CONFIRMADA)
                .lugarNumero(request.getLugarNumero())
                .build();

        reservacionRepository.save(reservacion);

        if (usuario.getRol() == RolUsuario.CLIENTE) {
            descontarCredito(usuario, clase);
            usuarioRepository.save(usuario);
        }

        clase.setCupoTomado(clase.getCupoTomado() + 1);
        claseRepository.save(clase);

        return toAdminDTO(reservacion);
    }

    // ── Admin: cancelar cualquier reservación ─────────────────
    @Transactional
    public void cancelarComoAdmin(Long reservacionId) {
        Reservacion reservacion = reservacionRepository.findById(reservacionId)
                .orElseThrow(() -> new AppException("Reservación no encontrada", HttpStatus.NOT_FOUND));

        if (reservacion.getEstado() != EstadoReservacion.CONFIRMADA) {
            throw new AppException("Solo se pueden cancelar reservaciones confirmadas", HttpStatus.BAD_REQUEST);
        }

        reservacion.setEstado(EstadoReservacion.CANCELADA);
        reservacion.setFechaCancelacion(LocalDateTime.now());
        reservacionRepository.save(reservacion);

        Long usuarioIdAdmin = reservacion.getUsuario().getId();
        TipoDisciplina discAdmin = reservacion.getClase().getTipo().getDisciplina();
        Usuario usuarioAdmin = usuarioRepository.findById(usuarioIdAdmin)
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (usuarioAdmin.getRol() == RolUsuario.CLIENTE) {
            if (discAdmin == TipoDisciplina.CYCLING) {
                usuarioAdmin.setCreditosCycling(usuarioAdmin.getCreditosCycling() + 1);
            } else {
                usuarioAdmin.setCreditosPilates(usuarioAdmin.getCreditosPilates() + 1);
            }
            usuarioRepository.save(usuarioAdmin);
        }

        Clase clase = reservacion.getClase();
        clase.setCupoTomado(Math.max(0, clase.getCupoTomado() - 1));
        claseRepository.save(clase);
    }

    private ReservacionDTO toDTO(Reservacion r) {
        String inst = r.getClase().getInstructor() != null
                ? r.getClase().getInstructor().getNombreCompleto() : null;
        return ReservacionDTO.builder()
                .id(r.getId())
                .claseId(r.getClase().getId())
                .tipoClase(r.getClase().getTipo())
                .instructor(inst)
                .hora(r.getClase().getHora())
                .fecha(r.getFecha())
                .estado(r.getEstado())
                .fechaCreacion(r.getFechaCreacion())
                .lugarNumero(r.getLugarNumero())
                .build();
    }

    // ── En curso ──────────────────────────────────────────────────────────────

    public List<ClaseEnCursoDTO> getClasesEnCurso() {
        ZoneId zona = ZoneId.of("America/Mexico_City");
        LocalDate hoy = LocalDate.now(zona);
        LocalTime ahora = LocalTime.now(zona);
        DiaSemana diaActual = mapearDia(hoy.getDayOfWeek());
        if (diaActual == null) return List.of();

        return claseRepository.findByDiaSemanaAndActivoTrue(diaActual)
                .stream()
                .filter(c -> estaEnCurso(c.getHora(), ahora))
                .map(c -> {
                    List<Reservacion> reservas = reservacionRepository
                            .findByClaseIdAndFechaAndEstado(c.getId(), hoy, EstadoReservacion.CONFIRMADA);
                    return toEnCursoDTO(c, reservas);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void registrarNoAsistio(Long reservacionId) {
        Reservacion r = reservacionRepository.findById(reservacionId)
                .orElseThrow(() -> new AppException("Reservación no encontrada", HttpStatus.NOT_FOUND));

        if (r.getEstado() != EstadoReservacion.CONFIRMADA)
            throw new AppException("Solo se pueden liberar reservaciones confirmadas", HttpStatus.BAD_REQUEST);

        r.setEstado(EstadoReservacion.NO_ASISTIO);
        r.setFechaCancelacion(LocalDateTime.now());
        reservacionRepository.save(r);

        // Libera el cupo sin devolver créditos — forfeit por no-show
        Clase clase = r.getClase();
        clase.setCupoTomado(Math.max(0, clase.getCupoTomado() - 1));
        claseRepository.save(clase);
    }

    private boolean estaEnCurso(String hora, LocalTime ahora) {
        LocalTime inicio = LocalTime.parse(hora);
        LocalTime fin = inicio.plusMinutes(75);
        return !ahora.isBefore(inicio) && ahora.isBefore(fin);
    }

    private DiaSemana mapearDia(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY    -> DiaSemana.LUNES;
            case TUESDAY   -> DiaSemana.MARTES;
            case WEDNESDAY -> DiaSemana.MIERCOLES;
            case THURSDAY  -> DiaSemana.JUEVES;
            case FRIDAY    -> DiaSemana.VIERNES;
            default        -> null;
        };
    }

    private ClaseEnCursoDTO toEnCursoDTO(Clase c, List<Reservacion> reservas) {
        String instructor = c.getInstructor() != null ? c.getInstructor().getNombreCompleto() : null;
        List<ClaseEnCursoDTO.LugarOcupadoDTO> lugares = reservas.stream()
                .map(r -> ClaseEnCursoDTO.LugarOcupadoDTO.builder()
                        .reservacionId(r.getId())
                        .usuarioId(r.getUsuario().getId())
                        .nombre(r.getUsuario().getNombre() + " " + r.getUsuario().getApellido())
                        .email(r.getUsuario().getEmail())
                        .lugarNumero(r.getLugarNumero())
                        .build())
                .collect(Collectors.toList());
        return ClaseEnCursoDTO.builder()
                .claseId(c.getId())
                .tipo(c.getTipo().name())
                .disciplina(c.getTipo().getDisciplina().name())
                .hora(c.getHora())
                .instructor(instructor)
                .cupoTotal(c.getCupoTotal())
                .cupoTomado(c.getCupoTomado())
                .reservaciones(lugares)
                .build();
    }

    private void validarLimiteMensual(Clase clase, LocalDate fecha) {
        TipoDisciplina disc = clase.getTipo().getDisciplina();
        int anio = fecha.getYear();
        int mes  = fecha.getMonthValue();

        if (disc == TipoDisciplina.CYCLING) {
            int total = reservacionRepository.countCyclingConfirmadasEnMes(anio, mes);
            if (total >= limiteMensualCycling) {
                throw new AppException(
                    "Se ha alcanzado el límite de " + limiteMensualCycling +
                    " reservaciones de Indoor Cycling para este mes. Intenta reservar para el mes siguiente.",
                    HttpStatus.CONFLICT);
            }
        } else {
            int total = reservacionRepository.countPilatesConfirmadasEnMes(anio, mes);
            if (total >= limiteMensualPilates) {
                throw new AppException(
                    "Se ha alcanzado el límite de " + limiteMensualPilates +
                    " reservaciones de Pilates para este mes. Intenta reservar para el mes siguiente.",
                    HttpStatus.CONFLICT);
            }
        }
    }

    private void validarCreditos(Usuario usuario, Clase clase) {
        TipoDisciplina disc = clase.getTipo().getDisciplina();
        if (disc == TipoDisciplina.CYCLING) {
            if (usuario.getCreditosCycling() < 1)
                throw new AppException(
                    "Sin clases de Indoor Cycling disponibles. Adquiere un paquete para continuar.",
                    HttpStatus.PAYMENT_REQUIRED);
        } else {
            if (usuario.getCreditosPilates() < 1)
                throw new AppException(
                    "Sin clases de Pilates disponibles. Adquiere un paquete para continuar.",
                    HttpStatus.PAYMENT_REQUIRED);
        }
    }

    private void descontarCredito(Usuario usuario, Clase clase) {
        TipoDisciplina disc = clase.getTipo().getDisciplina();
        if (disc == TipoDisciplina.CYCLING) {
            usuario.setCreditosCycling(usuario.getCreditosCycling() - 1);
        } else {
            usuario.setCreditosPilates(usuario.getCreditosPilates() - 1);
        }
    }

    private ReservacionAdminDTO toAdminDTO(Reservacion r) {
        String inst = r.getClase().getInstructor() != null
                ? r.getClase().getInstructor().getNombreCompleto() : null;
        return ReservacionAdminDTO.builder()
                .id(r.getId())
                .claseId(r.getClase().getId())
                .tipoClase(r.getClase().getTipo())
                .instructor(inst)
                .hora(r.getClase().getHora())
                .fecha(r.getFecha())
                .estado(r.getEstado())
                .fechaCreacion(r.getFechaCreacion())
                .usuarioId(r.getUsuario().getId())
                .usuarioNombre(r.getUsuario().getNombre() + " " + r.getUsuario().getApellido())
                .usuarioEmail(r.getUsuario().getEmail())
                .lugarNumero(r.getLugarNumero())
                .build();
    }
}
