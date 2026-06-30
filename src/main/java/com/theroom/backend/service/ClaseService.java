package com.theroom.backend.service;

import com.theroom.backend.dto.ClaseAdminDTO;
import com.theroom.backend.dto.ClaseAdminRequest;
import com.theroom.backend.dto.ClaseDTO;
import com.theroom.backend.dto.ClaseEspaciosAdminDTO;
import com.theroom.backend.dto.EquipoEstudioDTO;
import com.theroom.backend.entity.Clase;
import com.theroom.backend.entity.ClaseSesionCancelada;
import com.theroom.backend.entity.EquipoEstudio;
import com.theroom.backend.entity.Instructor;
import com.theroom.backend.entity.Reservacion;
import com.theroom.backend.enums.DiaSemana;
import com.theroom.backend.enums.EstadoReservacion;
import com.theroom.backend.enums.TipoClase;
import com.theroom.backend.exception.AppException;
import com.theroom.backend.repository.ClaseRepository;
import com.theroom.backend.repository.ClaseSesionCanceladaRepository;
import com.theroom.backend.repository.EquipoEstudioRepository;
import com.theroom.backend.repository.InstructorRepository;
import com.theroom.backend.repository.ReservacionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClaseService {

    private final ClaseRepository claseRepository;
    private final InstructorRepository instructorRepository;
    private final ReservacionRepository reservacionRepository;
    private final EquipoEstudioRepository equipoRepository;
    private final ClaseSesionCanceladaRepository sesionCanceladaRepository;

    @PostConstruct
    @Transactional
    public void sembrarEquipo() {
        if (!equipoRepository.existsById(TipoClase.SPINNING)) {
            equipoRepository.save(EquipoEstudio.builder()
                    .tipoClase(TipoClase.SPINNING).nombre("Indoor Cycling").cantidad(15).build());
        }
        if (!equipoRepository.existsById(TipoClase.PILATES)) {
            equipoRepository.save(EquipoEstudio.builder()
                    .tipoClase(TipoClase.PILATES).nombre("Pilates Reformer").cantidad(7).build());
        }
        // Sincroniza clases activas que no coincidan con el equipo registrado
        equipoRepository.findAll().forEach(equipo -> {
            List<Clase> desfasadas = claseRepository.findByTipoAndActivoTrue(equipo.getTipoClase())
                    .stream().filter(c -> c.getCupoTotal() != equipo.getCantidad())
                    .collect(Collectors.toList());
            if (!desfasadas.isEmpty()) {
                desfasadas.forEach(c -> c.setCupoTotal(equipo.getCantidad()));
                claseRepository.saveAll(desfasadas);
            }
        });
    }

    public List<EquipoEstudioDTO> obtenerEquipo() {
        return equipoRepository.findAll().stream()
                .map(this::toEquipoDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public EquipoEstudioDTO actualizarEquipo(TipoClase tipo, int nuevaCantidad) {
        EquipoEstudio equipo = equipoRepository.findById(tipo)
                .orElseThrow(() -> new AppException("Tipo de clase no encontrado", HttpStatus.NOT_FOUND));
        equipo.setCantidad(nuevaCantidad);
        equipoRepository.save(equipo);

        List<Clase> clases = claseRepository.findByTipoAndActivoTrue(tipo);
        clases.forEach(c -> c.setCupoTotal(nuevaCantidad));
        claseRepository.saveAll(clases);

        equipo.setDeshabilitados(equipo.getDeshabilitados().stream()
                .filter(n -> n <= nuevaCantidad)
                .collect(Collectors.toCollection(HashSet::new)));
        equipoRepository.save(equipo);

        return toEquipoDTO(equipo, clases.size());
    }

    @Transactional
    public EquipoEstudioDTO actualizarEquipoDeshabilitado(TipoClase tipo, List<Integer> deshabilitados) {
        EquipoEstudio equipo = equipoRepository.findById(tipo)
                .orElseThrow(() -> new AppException("Tipo de clase no encontrado", HttpStatus.NOT_FOUND));

        Set<Integer> normalizados = deshabilitados == null
                ? Collections.emptySet()
                : deshabilitados.stream()
                        .filter(n -> n != null)
                        .collect(Collectors.toCollection(HashSet::new));

        normalizados.forEach(n -> {
            if (n < 1 || n > equipo.getCantidad()) {
                throw new AppException("El equipo " + n + " no existe para " + equipo.getNombre(), HttpStatus.BAD_REQUEST);
            }
        });

        if (!normalizados.isEmpty() && reservacionRepository.existsConfirmadaFuturaEnLugares(
                tipo,
                new ArrayList<>(normalizados),
                LocalDate.now(ZoneId.of("America/Mexico_City")))) {
            throw new AppException("No puedes deshabilitar un equipo con reservaciones futuras confirmadas. Libera o mueve esas reservaciones primero.", HttpStatus.CONFLICT);
        }

        equipo.setDeshabilitados(normalizados);
        equipoRepository.save(equipo);
        return toEquipoDTO(equipo);
    }

    public List<ClaseDTO> obtenerTodas() {
        return claseRepository.findByActivoTrue()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<ClaseDTO> obtenerPorTipo(TipoClase tipo) {
        return claseRepository.findByTipoAndActivoTrue(tipo)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Devuelve las clases de los 7 días de la semana indicada por offset.
     * offset=0 => semana actual, offset=1 => próxima semana, etc.
     * Los lugares disponibles se calculan contra reservaciones confirmadas
     * de la fecha concreta, no contra cupoTomado global de la entidad.
     */
    public List<ClaseDTO> obtenerPorSemana(int offset) {
        LocalDate lunes = LocalDate.now(ZoneId.of("America/Mexico_City"))
                .with(DayOfWeek.MONDAY)
                .plusWeeks(offset);

        java.util.Map<DiaSemana, LocalDate> fechaPorDia = new java.util.HashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate fecha = lunes.plusDays(i);
            fechaPorDia.put(toDiaSemana(fecha.getDayOfWeek()), fecha);
        }

        java.util.Set<String> sesionesCanceladas = clavesSesionesCanceladas(lunes, lunes.plusDays(6));

        return claseRepository.findByDiaSemanaInAndActivoTrue(new ArrayList<>(fechaPorDia.keySet()))
                .stream()
                .map(c -> {
                    LocalDate fecha = fechaPorDia.get(c.getDiaSemana());
                    boolean cancelada = sesionesCanceladas.contains(claveSesion(c.getId(), fecha));
                    int tomados = reservacionRepository.countConfirmadasByClaseAndFecha(c.getId(), fecha);
                    int disponibles = cancelada ? 0 : Math.max(0, capacidadOperativa(c) - tomados);
                    return ClaseDTO.builder()
                            .id(c.getId())
                            .tipo(c.getTipo())
                            .instructor(c.getInstructor() != null ? c.getInstructor().getNombreCompleto() : null)
                            .hora(c.getHora())
                            .diaSemana(c.getDiaSemana())
                            .cupoTotal(c.getCupoTotal())
                            .cupoTomado(tomados)
                            .lugaresDisponibles(disponibles)
                            .llena(cancelada || disponibles == 0)
                            .masterClass(c.isMasterClass())
                            .sesionCancelada(cancelada)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<ClaseDTO> obtenerPorDia(DiaSemana dia) {
        return claseRepository.findByDiaSemanaAndActivoTrue(dia)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Clase obtenerEntidad(Long id) {
        return claseRepository.findById(id)
                .orElseThrow(() -> new AppException("Clase no encontrada", HttpStatus.NOT_FOUND));
    }

    // ── Admin: todas las clases (activas e inactivas) ──────────
    public List<ClaseAdminDTO> obtenerTodasAdmin() {
        return claseRepository.findAllByOrderByDiaSemanaAscHoraAsc()
                .stream().map(this::toAdminDTO).collect(Collectors.toList());
    }

    // ── Admin: cambiar o desasignar instructor ─────────────────
    @Transactional
    public ClaseAdminDTO cambiarInstructor(Long claseId, Long instructorId) {
        Clase clase = claseRepository.findById(claseId)
                .orElseThrow(() -> new AppException("Clase no encontrada", HttpStatus.NOT_FOUND));

        if (instructorId == null) {
            clase.setInstructor(null);
        } else {
            Instructor instructor = instructorRepository.findById(instructorId)
                    .orElseThrow(() -> new AppException("Instructor no encontrado", HttpStatus.NOT_FOUND));
            clase.setInstructor(instructor);
        }
        return toAdminDTO(claseRepository.save(clase));
    }

    // ── Admin: crear nueva clase ───────────────────────────────
    @Transactional
    public ClaseAdminDTO crearClase(ClaseAdminRequest request) {
        boolean master = Boolean.TRUE.equals(request.getMasterClass());
        validarHorarioClase(request, master, null);
        int cupo = equipoRepository.findById(request.getTipo())
                .map(EquipoEstudio::getCantidad)
                .orElse(request.getCupoTotal());
        Instructor instructor = resolverInstructor(request.getInstructorId());
        Clase clase = Clase.builder()
                .tipo(request.getTipo())
                .diaSemana(request.getDiaSemana())
                .hora(request.getHora())
                .instructor(instructor)
                .cupoTotal(cupo)
                .cupoTomado(0)
                .activo(!master)
                .masterClass(master)
                .build();
        return toAdminDTO(claseRepository.save(clase));
    }

    // ── Admin: editar clase existente ──────────────────────────
    @Transactional
    public ClaseAdminDTO editarClase(Long id, ClaseAdminRequest request) {
        Clase clase = claseRepository.findById(id)
                .orElseThrow(() -> new AppException("Clase no encontrada", HttpStatus.NOT_FOUND));
        validarHorarioClase(request, clase.isMasterClass(), id);
        int cupo = equipoRepository.findById(request.getTipo())
                .map(EquipoEstudio::getCantidad)
                .orElse(request.getCupoTotal());
        clase.setTipo(request.getTipo());
        clase.setDiaSemana(request.getDiaSemana());
        clase.setHora(request.getHora());
        clase.setInstructor(resolverInstructor(request.getInstructorId()));
        clase.setCupoTotal(cupo);
        return toAdminDTO(claseRepository.save(clase));
    }

    // ── Admin: eliminar clase (solo sin reservaciones) ─────────
    @Transactional
    public void eliminarClase(Long id) {
        if (!claseRepository.existsById(id))
            throw new AppException("Clase no encontrada", HttpStatus.NOT_FOUND);
        if (reservacionRepository.existsByClaseId(id))
            throw new AppException("La clase tiene reservaciones y no puede eliminarse", HttpStatus.CONFLICT);
        claseRepository.deleteById(id);
    }

    // ── Admin: activar / desactivar clase ──────────────────────
    @Transactional
    public ClaseAdminDTO toggleActivo(Long claseId) {
        Clase clase = claseRepository.findById(claseId)
                .orElseThrow(() -> new AppException("Clase no encontrada", HttpStatus.NOT_FOUND));
        clase.setActivo(!clase.isActivo());
        return toAdminDTO(claseRepository.save(clase));
    }

    // ── Lugares ocupados para una clase en una fecha ───────────
    public java.util.Map<String, Object> obtenerLugares(Long claseId, LocalDate fecha) {
        Clase clase = claseRepository.findById(claseId)
                .orElseThrow(() -> new AppException("Clase no encontrada", HttpStatus.NOT_FOUND));
        java.util.List<Integer> ocupados = reservacionRepository.findLugaresOcupadosByClaseAndFecha(claseId, fecha);
        return java.util.Map.of(
                "cupoTotal", clase.getCupoTotal(),
                "ocupados", ocupados,
                "deshabilitados", deshabilitadosPorTipo(clase.getTipo()));
    }

    // ── Admin: diagrama de espacios por semana ─────────────────
    public List<ClaseEspaciosAdminDTO> obtenerEspaciosPorSemana(int offset) {
        LocalDate lunes = LocalDate.now(ZoneId.of("America/Mexico_City"))
                .with(DayOfWeek.MONDAY)
                .plusWeeks(offset);

        Map<DiaSemana, LocalDate> fechaPorDia = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate fecha = lunes.plusDays(i);
            fechaPorDia.put(toDiaSemana(fecha.getDayOfWeek()), fecha);
        }

        Set<String> sesionesCanceladas = clavesSesionesCanceladas(lunes, lunes.plusDays(6));

        return claseRepository.findByDiaSemanaInAndActivoTrue(new ArrayList<>(fechaPorDia.keySet()))
                .stream()
                .map(c -> {
                    LocalDate fecha = fechaPorDia.get(c.getDiaSemana());
                    boolean cancelada = sesionesCanceladas.contains(claveSesion(c.getId(), fecha));
                    List<Reservacion> reservas = reservacionRepository
                            .findByClaseIdAndFechaAndEstado(c.getId(), fecha, EstadoReservacion.CONFIRMADA);

                    Map<Integer, Reservacion> porLugar = reservas.stream()
                            .filter(r -> r.getLugarNumero() != null)
                            .collect(Collectors.toMap(Reservacion::getLugarNumero, r -> r));

                    Set<Integer> deshabilitados = deshabilitadosPorTipo(c.getTipo());
                    List<ClaseEspaciosAdminDTO.EspacioDTO> espacios = new ArrayList<>();
                    for (int n = 1; n <= c.getCupoTotal(); n++) {
                        Reservacion r = porLugar.get(n);
                        espacios.add(ClaseEspaciosAdminDTO.EspacioDTO.builder()
                                .numero(n)
                                .ocupado(r != null)
                                .deshabilitado(deshabilitados.contains(n))
                                .reservacionId(r != null ? r.getId() : null)
                                .usuarioNombre(r != null ? r.getUsuario().getNombre() + " " + r.getUsuario().getApellido() : null)
                                .usuarioEmail(r != null ? r.getUsuario().getEmail() : null)
                                .build());
                    }

                    List<ClaseEspaciosAdminDTO.ReservaSinLugarDTO> sinLugar = reservas.stream()
                            .filter(r -> r.getLugarNumero() == null)
                            .map(r -> ClaseEspaciosAdminDTO.ReservaSinLugarDTO.builder()
                                    .reservacionId(r.getId())
                                    .usuarioNombre(r.getUsuario().getNombre() + " " + r.getUsuario().getApellido())
                                    .usuarioEmail(r.getUsuario().getEmail())
                                    .build())
                            .collect(Collectors.toList());

                    List<ClaseEspaciosAdminDTO.ReservaSinLugarDTO> canceladas = reservacionRepository
                            .findByClaseIdAndFechaAndEstado(c.getId(), fecha, EstadoReservacion.CANCELADA)
                            .stream()
                            .map(r -> ClaseEspaciosAdminDTO.ReservaSinLugarDTO.builder()
                                    .reservacionId(r.getId())
                                    .usuarioNombre(r.getUsuario().getNombre() + " " + r.getUsuario().getApellido())
                                    .usuarioEmail(r.getUsuario().getEmail())
                                    .build())
                            .collect(Collectors.toList());

                    int tomados = reservas.size();
                    int disponibles = cancelada ? 0 : Math.max(0, capacidadOperativa(c, deshabilitados) - tomados);

                    return ClaseEspaciosAdminDTO.builder()
                            .claseId(c.getId())
                            .tipo(c.getTipo())
                            .diaSemana(c.getDiaSemana())
                            .fecha(fecha)
                            .hora(c.getHora())
                            .instructor(c.getInstructor() != null ? c.getInstructor().getNombreCompleto() : null)
                            .cupoTotal(c.getCupoTotal())
                            .cupoTomado(tomados)
                            .lugaresDisponibles(disponibles)
                            .llena(cancelada || disponibles == 0)
                            .sesionCancelada(cancelada)
                            .espacios(espacios)
                            .sinLugar(sinLugar)
                            .canceladas(canceladas)
                            .build();
                })
                .sorted(Comparator.comparing(ClaseEspaciosAdminDTO::getFecha)
                        .thenComparing(ClaseEspaciosAdminDTO::getHora))
                .collect(Collectors.toList());
    }

    private void validarHorarioClase(ClaseAdminRequest request, boolean masterClass, Long excludeId) {
        request.setHora(normalizarHora(request.getHora()));

        EnumSet<DiaSemana> laboral = EnumSet.of(
                DiaSemana.LUNES, DiaSemana.MARTES, DiaSemana.MIERCOLES,
                DiaSemana.JUEVES, DiaSemana.VIERNES);
        EnumSet<DiaSemana> finde = EnumSet.of(DiaSemana.SABADO, DiaSemana.DOMINGO);

        if (masterClass) {
            if (!finde.contains(request.getDiaSemana()))
                throw new AppException("Una Master Class solo puede programarse sábado o domingo", HttpStatus.BAD_REQUEST);
            boolean conflicto = excludeId == null
                    ? claseRepository.existsByDiaSemanaAndHoraAndMasterClassTrue(
                            request.getDiaSemana(), request.getHora())
                    : claseRepository.existsByDiaSemanaAndHoraAndMasterClassTrueAndIdNot(
                            request.getDiaSemana(), request.getHora(), excludeId);
            if (conflicto)
                throw new AppException(
                        "Ya existe una Master Class en ese día y horario. Elige otro horario.",
                        HttpStatus.CONFLICT);
        } else if (!laboral.contains(request.getDiaSemana())) {
            throw new AppException("Las clases regulares solo pueden ser de lunes a viernes", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizarHora(String hora) {
        if (hora == null || !hora.matches("^\\d{1,2}:\\d{2}$"))
            throw new AppException("La hora debe tener formato HH:mm", HttpStatus.BAD_REQUEST);
        String[] parts = hora.split(":");
        int hh = Integer.parseInt(parts[0]);
        int mm = Integer.parseInt(parts[1]);
        if (hh > 23 || mm > 59)
            throw new AppException("La hora indicada no es válida", HttpStatus.BAD_REQUEST);
        return String.format("%02d:%02d", hh, mm);
    }

    private Instructor resolverInstructor(Long instructorId) {
        if (instructorId == null) return null;
        return instructorRepository.findById(instructorId)
                .orElseThrow(() -> new AppException("Instructor no encontrado", HttpStatus.NOT_FOUND));
    }

    private DiaSemana toDiaSemana(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY    -> DiaSemana.LUNES;
            case TUESDAY   -> DiaSemana.MARTES;
            case WEDNESDAY -> DiaSemana.MIERCOLES;
            case THURSDAY  -> DiaSemana.JUEVES;
            case FRIDAY    -> DiaSemana.VIERNES;
            case SATURDAY  -> DiaSemana.SABADO;
            case SUNDAY    -> DiaSemana.DOMINGO;
        };
    }

    public ClaseDTO toDTO(Clase clase) {
        int disponibles = Math.max(0, capacidadOperativa(clase) - clase.getCupoTomado());
        return ClaseDTO.builder()
                .id(clase.getId())
                .tipo(clase.getTipo())
                .instructor(clase.getInstructor() != null ? clase.getInstructor().getNombreCompleto() : null)
                .hora(clase.getHora())
                .diaSemana(clase.getDiaSemana())
                .cupoTotal(clase.getCupoTotal())
                .cupoTomado(clase.getCupoTomado())
                .lugaresDisponibles(disponibles)
                .llena(disponibles == 0)
                .masterClass(clase.isMasterClass())
                .build();
    }

    private ClaseAdminDTO toAdminDTO(Clase clase) {
        int disponibles = Math.max(0, capacidadOperativa(clase) - clase.getCupoTomado());
        return ClaseAdminDTO.builder()
                .id(clase.getId())
                .tipo(clase.getTipo())
                .diaSemana(clase.getDiaSemana())
                .hora(clase.getHora())
                .cupoTotal(clase.getCupoTotal())
                .cupoTomado(clase.getCupoTomado())
                .lugaresDisponibles(disponibles)
                .llena(disponibles == 0)
                .activo(clase.isActivo())
                .masterClass(clase.isMasterClass())
                .instructorId(clase.getInstructor() != null ? clase.getInstructor().getId() : null)
                .instructorNombre(clase.getInstructor() != null ? clase.getInstructor().getNombreCompleto() : null)
                .build();
    }

    private EquipoEstudioDTO toEquipoDTO(EquipoEstudio equipo) {
        int clasesAfectadas = (int) claseRepository.findByTipoAndActivoTrue(equipo.getTipoClase()).stream().count();
        return toEquipoDTO(equipo, clasesAfectadas);
    }

    private EquipoEstudioDTO toEquipoDTO(EquipoEstudio equipo, int clasesAfectadas) {
        List<Integer> deshabilitados = equipo.getDeshabilitados().stream()
                .sorted()
                .collect(Collectors.toList());
        return EquipoEstudioDTO.builder()
                .tipoClase(equipo.getTipoClase().name())
                .nombre(equipo.getNombre())
                .cantidad(equipo.getCantidad())
                .deshabilitados(deshabilitados)
                .disponibles(Math.max(0, equipo.getCantidad() - deshabilitados.size()))
                .clasesAfectadas(clasesAfectadas)
                .build();
    }

    private Set<Integer> deshabilitadosPorTipo(TipoClase tipo) {
        return equipoRepository.findById(tipo)
                .map(EquipoEstudio::getDeshabilitados)
                .map(HashSet::new)
                .orElseGet(HashSet::new);
    }

    private int capacidadOperativa(Clase clase) {
        return capacidadOperativa(clase, deshabilitadosPorTipo(clase.getTipo()));
    }

    private int capacidadOperativa(Clase clase, Set<Integer> deshabilitados) {
        long dentroDeRango = deshabilitados.stream()
                .filter(n -> n >= 1 && n <= clase.getCupoTotal())
                .count();
        return Math.max(0, clase.getCupoTotal() - (int) dentroDeRango);
    }

    private Set<String> clavesSesionesCanceladas(LocalDate desde, LocalDate hasta) {
        return sesionCanceladaRepository.findByFechaBetween(desde, hasta).stream()
                .map(sc -> claveSesion(sc.getClase().getId(), sc.getFecha()))
                .collect(Collectors.toSet());
    }

    private String claveSesion(Long claseId, LocalDate fecha) {
        return claseId + "|" + fecha;
    }
}
