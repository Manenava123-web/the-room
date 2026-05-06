package com.theroom.backend.service;

import com.theroom.backend.dto.ClaseAdminDTO;
import com.theroom.backend.dto.ClaseAdminRequest;
import com.theroom.backend.dto.ClaseDTO;
import com.theroom.backend.dto.EquipoEstudioDTO;
import com.theroom.backend.entity.Clase;
import com.theroom.backend.entity.EquipoEstudio;
import com.theroom.backend.entity.Instructor;
import com.theroom.backend.enums.DiaSemana;
import com.theroom.backend.enums.TipoClase;
import com.theroom.backend.exception.AppException;
import com.theroom.backend.repository.ClaseRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClaseService {

    private final ClaseRepository claseRepository;
    private final InstructorRepository instructorRepository;
    private final ReservacionRepository reservacionRepository;
    private final EquipoEstudioRepository equipoRepository;

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
                .map(e -> EquipoEstudioDTO.builder()
                        .tipoClase(e.getTipoClase().name())
                        .nombre(e.getNombre())
                        .cantidad(e.getCantidad())
                        .clasesAfectadas((int) claseRepository.findByTipoAndActivoTrue(e.getTipoClase()).stream().count())
                        .build())
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

        return EquipoEstudioDTO.builder()
                .tipoClase(tipo.name())
                .nombre(equipo.getNombre())
                .cantidad(nuevaCantidad)
                .clasesAfectadas(clases.size())
                .build();
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
        LocalDate lunes = LocalDate.now()
                .with(DayOfWeek.MONDAY)
                .plusWeeks(offset);

        java.util.Map<DiaSemana, LocalDate> fechaPorDia = new java.util.HashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate fecha = lunes.plusDays(i);
            fechaPorDia.put(toDiaSemana(fecha.getDayOfWeek()), fecha);
        }

        return claseRepository.findByDiaSemanaInAndActivoTrue(new ArrayList<>(fechaPorDia.keySet()))
                .stream()
                .map(c -> {
                    LocalDate fecha = fechaPorDia.get(c.getDiaSemana());
                    int tomados = reservacionRepository.countConfirmadasByClaseAndFecha(c.getId(), fecha);
                    int disponibles = Math.max(0, c.getCupoTotal() - tomados);
                    return ClaseDTO.builder()
                            .id(c.getId())
                            .tipo(c.getTipo())
                            .instructor(c.getInstructor() != null ? c.getInstructor().getNombreCompleto() : null)
                            .hora(c.getHora())
                            .diaSemana(c.getDiaSemana())
                            .cupoTotal(c.getCupoTotal())
                            .cupoTomado(tomados)
                            .lugaresDisponibles(disponibles)
                            .llena(disponibles == 0)
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
                .activo(true)
                .build();
        return toAdminDTO(claseRepository.save(clase));
    }

    // ── Admin: editar clase existente ──────────────────────────
    @Transactional
    public ClaseAdminDTO editarClase(Long id, ClaseAdminRequest request) {
        Clase clase = claseRepository.findById(id)
                .orElseThrow(() -> new AppException("Clase no encontrada", HttpStatus.NOT_FOUND));
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
        return java.util.Map.of("cupoTotal", clase.getCupoTotal(), "ocupados", ocupados);
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
        return ClaseDTO.builder()
                .id(clase.getId())
                .tipo(clase.getTipo())
                .instructor(clase.getInstructor() != null ? clase.getInstructor().getNombreCompleto() : null)
                .hora(clase.getHora())
                .diaSemana(clase.getDiaSemana())
                .cupoTotal(clase.getCupoTotal())
                .cupoTomado(clase.getCupoTomado())
                .lugaresDisponibles(clase.getLugaresDisponibles())
                .llena(clase.isFull())
                .build();
    }

    private ClaseAdminDTO toAdminDTO(Clase clase) {
        return ClaseAdminDTO.builder()
                .id(clase.getId())
                .tipo(clase.getTipo())
                .diaSemana(clase.getDiaSemana())
                .hora(clase.getHora())
                .cupoTotal(clase.getCupoTotal())
                .cupoTomado(clase.getCupoTomado())
                .lugaresDisponibles(clase.getLugaresDisponibles())
                .llena(clase.isFull())
                .activo(clase.isActivo())
                .instructorId(clase.getInstructor() != null ? clase.getInstructor().getId() : null)
                .instructorNombre(clase.getInstructor() != null ? clase.getInstructor().getNombreCompleto() : null)
                .build();
    }
}
