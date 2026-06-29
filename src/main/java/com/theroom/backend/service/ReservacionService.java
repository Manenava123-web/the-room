package com.theroom.backend.service;

import com.theroom.backend.dto.ClaseEnCursoDTO;
import com.theroom.backend.dto.ReservacionAdminDTO;
import com.theroom.backend.dto.ReservacionAdminRequest;
import com.theroom.backend.dto.ReservacionDTO;
import com.theroom.backend.dto.ReservacionRequest;
import com.theroom.backend.entity.Clase;
import com.theroom.backend.entity.EquipoEstudio;
import com.theroom.backend.entity.Reservacion;
import com.theroom.backend.entity.Usuario;
import com.theroom.backend.enums.DiaSemana;
import com.theroom.backend.enums.EstadoReservacion;
import com.theroom.backend.enums.TipoClase;
import com.theroom.backend.enums.TipoDisciplina;
import com.theroom.backend.enums.RolUsuario;
import com.theroom.backend.exception.AppException;
import com.theroom.backend.repository.ClaseRepository;
import com.theroom.backend.repository.EquipoEstudioRepository;
import com.theroom.backend.repository.PagoRepository;
import com.theroom.backend.repository.ReservacionRepository;
import com.theroom.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservacionService {

    private final ReservacionRepository reservacionRepository;
    private final ClaseRepository claseRepository;
    private final EquipoEstudioRepository equipoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoRepository pagoRepository;
    private final NotificacionService notificacionService;

    @Value("${app.cycling.limite-mensual:900}")
    private int limiteMensualCycling;

    @Value("${app.pilates.limite-mensual:420}")
    private int limiteMensualPilates;

    @Value("${app.cancelacion.cycling-horas:3}")
    private int horasCancelarCycling;

    @Value("${app.cancelacion.pilates-horas:24}")
    private int horasCancelarPilates;

    @Transactional
    public ReservacionDTO crear(Long usuarioId, ReservacionRequest request) {
        Clase clase = claseRepository.findByIdForUpdate(request.getClaseId())
                .orElseThrow(() -> new AppException("Clase no encontrada", HttpStatus.NOT_FOUND));

        Set<Integer> deshabilitados = deshabilitadosPorTipo(clase.getTipo());
        int tomadosEnFecha = reservacionRepository.countConfirmadasByClaseAndFecha(clase.getId(), request.getFecha());
        if (tomadosEnFecha >= capacidadOperativa(clase, deshabilitados)) {
            throw new AppException("La clase está llena", HttpStatus.CONFLICT);
        }

        validarLimiteMensual(clase, request.getFecha());

        boolean esInvitado = request.getNombreInvitado() != null && !request.getNombreInvitado().isBlank();
        if (!esInvitado) {
            boolean yaReservado = reservacionRepository
                    .existsByUsuarioIdAndClaseIdAndFechaAndEstadoAndNombreInvitadoIsNull(
                            usuarioId, clase.getId(), request.getFecha(), EstadoReservacion.CONFIRMADA);
            if (yaReservado) {
                throw new AppException("Ya tienes una reservación para esta clase en esa fecha", HttpStatus.CONFLICT);
            }
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        if (usuario.getRol() == RolUsuario.CLIENTE) {
            validarCreditos(usuario, clase);
            if (esInvitado) {
                validarClaseDeVisitaPagada(usuarioId, clase.getTipo().getDisciplina());
            }
        }

        if (request.getLugarNumero() != null) {
            validarLugarReservable(clase, request.getLugarNumero(), deshabilitados);
            if (reservacionRepository.existsByClaseIdAndFechaAndLugarNumeroAndEstado(
                    clase.getId(), request.getFecha(), request.getLugarNumero(), EstadoReservacion.CONFIRMADA)) {
                throw new AppException("Ese lugar ya está ocupado", HttpStatus.CONFLICT);
            }
        }

        String nombreInvitado = esInvitado ? request.getNombreInvitado().trim() : null;

        Reservacion reservacion = Reservacion.builder()
                .usuario(usuario)
                .clase(clase)
                .fecha(request.getFecha())
                .estado(EstadoReservacion.CONFIRMADA)
                .lugarNumero(request.getLugarNumero())
                .nombreInvitado(nombreInvitado)
                .build();

        reservacionRepository.save(reservacion);

        if (usuario.getRol() == RolUsuario.CLIENTE) {
            descontarCredito(usuario, clase);
            usuarioRepository.save(usuario);
        }

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
        ZoneId zona = ZoneId.of("America/Mexico_City");
        LocalDate hoy = LocalDate.now(zona);
        LocalDate fecha = reservacion.getFecha();
        if (fecha.isBefore(hoy)) {
            throw new AppException("No puedes cancelar una clase que ya pasó", HttpStatus.BAD_REQUEST);
        }
        boolean esCycling = reservacion.getClase().getTipo() == TipoClase.SPINNING;
        if (esCycling) {
            LocalDateTime limiteCancel = LocalDateTime.of(fecha, LocalTime.parse(reservacion.getClase().getHora()))
                    .minusHours(horasCancelarCycling);
            if (!LocalDateTime.now(zona).isBefore(limiteCancel)) {
                throw new AppException("Solo puedes cancelar tu clase de Cycling hasta " + horasCancelarCycling + " horas antes del inicio", HttpStatus.BAD_REQUEST);
            }
        } else {
            LocalDateTime limiteCancel = LocalDateTime.of(fecha, LocalTime.parse(reservacion.getClase().getHora())).minusHours(horasCancelarPilates);
            if (!LocalDateTime.now(zona).isBefore(limiteCancel)) {
                throw new AppException("Solo puedes cancelar tu clase de Pilates hasta " + horasCancelarPilates + " horas antes del inicio", HttpStatus.BAD_REQUEST);
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
    }

    public List<ReservacionDTO> misReservaciones(Long usuarioId) {
        return reservacionRepository.findByUsuarioIdOrderByFechaDesc(usuarioId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<ReservacionDTO> misProximas(Long usuarioId) {
        return reservacionRepository.findProximasByUsuario(usuarioId, LocalDate.now(ZoneId.of("America/Mexico_City")))
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

        Set<Integer> deshabilitados = deshabilitadosPorTipo(clase.getTipo());
        int tomadosEnFechaAdmin = reservacionRepository.countConfirmadasByClaseAndFecha(clase.getId(), request.getFecha());
        if (tomadosEnFechaAdmin >= capacidadOperativa(clase, deshabilitados)) {
            throw new AppException("La clase está llena", HttpStatus.CONFLICT);
        }

        validarLimiteMensual(clase, request.getFecha());

        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        boolean esInvitadoAdmin = request.getNombreInvitado() != null && !request.getNombreInvitado().isBlank();
        if (!esInvitadoAdmin) {
            boolean yaReservado = reservacionRepository
                    .existsByUsuarioIdAndClaseIdAndFechaAndEstadoAndNombreInvitadoIsNull(
                            usuario.getId(), clase.getId(), request.getFecha(), EstadoReservacion.CONFIRMADA);
            if (yaReservado) {
                throw new AppException("El usuario ya tiene una reservación para esta clase en esa fecha", HttpStatus.CONFLICT);
            }
        }

        if (usuario.getRol() == RolUsuario.CLIENTE) {
            validarCreditos(usuario, clase);
            if (esInvitadoAdmin) {
                validarClaseDeVisitaPagada(usuario.getId(), clase.getTipo().getDisciplina());
            }
        }

        if (request.getLugarNumero() != null) {
            validarLugarReservable(clase, request.getLugarNumero(), deshabilitados);
            if (reservacionRepository.existsByClaseIdAndFechaAndLugarNumeroAndEstado(
                    clase.getId(), request.getFecha(), request.getLugarNumero(), EstadoReservacion.CONFIRMADA)) {
                throw new AppException("Ese lugar ya está ocupado", HttpStatus.CONFLICT);
            }
        }

        String nombreInvitadoAdmin = esInvitadoAdmin ? request.getNombreInvitado().trim() : null;

        Reservacion reservacion = Reservacion.builder()
                .usuario(usuario)
                .clase(clase)
                .fecha(request.getFecha())
                .estado(EstadoReservacion.CONFIRMADA)
                .lugarNumero(request.getLugarNumero())
                .nombreInvitado(nombreInvitadoAdmin)
                .build();

        reservacionRepository.save(reservacion);

        if (usuario.getRol() == RolUsuario.CLIENTE) {
            descontarCredito(usuario, clase);
            usuarioRepository.save(usuario);
        }

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

        ZoneId zonaAdmin = ZoneId.of("America/Mexico_City");
        LocalDateTime ahora = LocalDateTime.now(zonaAdmin);
        LocalDateTime inicioClase = LocalDateTime.of(
                reservacion.getFecha(),
                LocalTime.parse(reservacion.getClase().getHora()));
        if (!ahora.isBefore(inicioClase)) {
            throw new AppException("No se puede cancelar una clase que ya inició o terminó", HttpStatus.BAD_REQUEST);
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
    }

    // ── Admin: cancelar todas las reservaciones de una clase en una fecha ──
    @Transactional
    public int cancelarClasePorFecha(Long claseId, LocalDate fecha) {
        Clase clase = claseRepository.findById(claseId)
                .orElseThrow(() -> new AppException("Clase no encontrada", HttpStatus.NOT_FOUND));

        List<Reservacion> confirmadas = reservacionRepository
                .findByClaseIdAndFechaAndEstado(claseId, fecha, EstadoReservacion.CONFIRMADA);

        if (confirmadas.isEmpty()) return 0;

        TipoDisciplina disc = clase.getTipo().getDisciplina();
        LocalDateTime ahora = LocalDateTime.now();
        String instructor = clase.getInstructor() != null ? clase.getInstructor().getNombreCompleto() : null;

        for (Reservacion r : confirmadas) {
            r.setEstado(EstadoReservacion.CANCELADA);
            r.setFechaCancelacion(ahora);
            reservacionRepository.save(r);

            Usuario u = r.getUsuario();
            if (u.getRol() == RolUsuario.CLIENTE) {
                if (disc == TipoDisciplina.CYCLING) {
                    u.setCreditosCycling(u.getCreditosCycling() + 1);
                    if (u.getCreditosCyclingVencen() != null) {
                        u.setCreditosCyclingVencen(u.getCreditosCyclingVencen().plusDays(1));
                    }
                } else {
                    u.setCreditosPilates(u.getCreditosPilates() + 1);
                    if (u.getCreditosPilatesVencen() != null) {
                        u.setCreditosPilatesVencen(u.getCreditosPilatesVencen().plusDays(1));
                    }
                }
                usuarioRepository.save(u);

                // Enviar correo después de que la transacción confirme
                final Usuario uFinal = u;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        notificacionService.enviarCancelacionClase(
                                uFinal, clase.getTipo(), fecha, clase.getHora(), instructor);
                    }
                });
            }
        }

        return confirmadas.size();
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
                .nombreInvitado(r.getNombreInvitado())
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
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Mexico_City"));
        TipoDisciplina disc = clase.getTipo().getDisciplina();
        if (disc == TipoDisciplina.CYCLING) {
            LocalDate vence = usuario.getCreditosCyclingVencen();
            if (usuario.getCreditosCycling() < 1 || vence == null || hoy.isAfter(vence))
                throw new AppException(
                    "Sin clases de Indoor Cycling disponibles. Adquiere un paquete para continuar.",
                    HttpStatus.PAYMENT_REQUIRED);
        } else {
            LocalDate vence = usuario.getCreditosPilatesVencen();
            if (usuario.getCreditosPilates() < 1 || vence == null || hoy.isAfter(vence))
                throw new AppException(
                    "Sin clases de Pilates disponibles. Adquiere un paquete para continuar.",
                    HttpStatus.PAYMENT_REQUIRED);
        }
    }

    private void validarClaseDeVisitaPagada(Long usuarioId, TipoDisciplina disciplina) {
        if (!pagoRepository.existsVisitaPagadaPorUsuarioYDisciplina(usuarioId, disciplina)) {
            String disc = disciplina == TipoDisciplina.CYCLING ? "Indoor Cycling" : "Pilates";
            throw new AppException(
                "Para reservar a un amig@ en " + disc + " primero debe pagarse una clase de visita (paquete de 1 clase).",
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

    private Set<Integer> deshabilitadosPorTipo(TipoClase tipo) {
        return equipoRepository.findById(tipo)
                .map(EquipoEstudio::getDeshabilitados)
                .orElse(Set.of());
    }

    private int capacidadOperativa(Clase clase, Set<Integer> deshabilitados) {
        long dentroDeRango = deshabilitados.stream()
                .filter(n -> n >= 1 && n <= clase.getCupoTotal())
                .count();
        return Math.max(0, clase.getCupoTotal() - (int) dentroDeRango);
    }

    private void validarLugarReservable(Clase clase, Integer lugarNumero, Set<Integer> deshabilitados) {
        if (lugarNumero < 1 || lugarNumero > clase.getCupoTotal()) {
            throw new AppException("El lugar seleccionado no existe", HttpStatus.BAD_REQUEST);
        }
        if (deshabilitados.contains(lugarNumero)) {
            String equipo = clase.getTipo() == TipoClase.SPINNING ? "bicicleta" : "reformer";
            throw new AppException("Ese " + equipo + " está deshabilitado por mantenimiento", HttpStatus.CONFLICT);
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
                .nombreInvitado(r.getNombreInvitado())
                .build();
    }
}
