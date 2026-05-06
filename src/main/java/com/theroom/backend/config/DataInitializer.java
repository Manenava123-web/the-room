package com.theroom.backend.config;

import com.theroom.backend.entity.Clase;
import com.theroom.backend.entity.Instructor;
import com.theroom.backend.entity.Reservacion;
import com.theroom.backend.entity.Usuario;
import com.theroom.backend.enums.*;
import com.theroom.backend.repository.ClaseRepository;
import com.theroom.backend.repository.EquipoEstudioRepository;
import com.theroom.backend.repository.InstructorRepository;
import com.theroom.backend.repository.ReservacionRepository;
import com.theroom.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UsuarioRepository usuarioRepo;
    private final InstructorRepository instructorRepo;
    private final ClaseRepository claseRepo;
    private final ReservacionRepository reservacionRepo;
    private final EquipoEstudioRepository equipoRepo;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner seedDatabase() {
        return args -> {
            if (usuarioRepo.count() > 0) {
                log.info("Base de datos ya inicializada — omitiendo seed.");
                return;
            }

            log.info("Iniciando seed de la base de datos...");

            // ─────────────────────────────────────────
            // USUARIOS
            // ─────────────────────────────────────────
            Usuario admin = usuarioRepo.save(Usuario.builder()
                    .nombre("Admin")
                    .apellido("The Room")
                    .email("admin@theroom.mx")
                    .password(passwordEncoder.encode("Admin123!"))
                    .telefono("7471000000")
                    .rol(RolUsuario.ADMIN)
                    .activo(true)
                    .build());

            Usuario maria = usuarioRepo.save(Usuario.builder()
                    .nombre("María")
                    .apellido("González López")
                    .email("maria@gmail.com")
                    .password(passwordEncoder.encode("Cliente123!"))
                    .telefono("7471111111")
                    .rol(RolUsuario.CLIENTE)
                    .activo(true)
                    .build());

            Usuario jose = usuarioRepo.save(Usuario.builder()
                    .nombre("José")
                    .apellido("Martínez Ruiz")
                    .email("jose@gmail.com")
                    .password(passwordEncoder.encode("Cliente123!"))
                    .telefono("7472222222")
                    .rol(RolUsuario.CLIENTE)
                    .activo(true)
                    .build());

            Usuario ana = usuarioRepo.save(Usuario.builder()
                    .nombre("Ana")
                    .apellido("Pérez Sánchez")
                    .email("ana@gmail.com")
                    .password(passwordEncoder.encode("Cliente123!"))
                    .telefono("7473333333")
                    .rol(RolUsuario.CLIENTE)
                    .activo(true)
                    .build());

            log.info("Usuarios creados: {}", usuarioRepo.count());

            // ─────────────────────────────────────────
            // INSTRUCTORES
            // ─────────────────────────────────────────
            Instructor sharon = instructorRepo.save(Instructor.builder()
                    .nombre("Sharon Yumiko")
                    .apellido("García")
                    .especialidad(TipoClase.PILATES)
                    .bio("Instructora certificada en Pilates Reformer. Especialista en rehabilitación y fortalecimiento de núcleo.")
                    .activo(true)
                    .build());

            Instructor carlos = instructorRepo.save(Instructor.builder()
                    .nombre("Carlos")
                    .apellido("Ramírez")
                    .especialidad(TipoClase.SPINNING)
                    .bio("Instructor de Indoor Cycling certificado. 6 años motivando a sus alumnos al ritmo de la música.")
                    .activo(true)
                    .build());

            Instructor andrea = instructorRepo.save(Instructor.builder()
                    .nombre("Andrea")
                    .apellido("Morales")
                    .especialidad(TipoClase.SPINNING)
                    .bio("Apasionada del fitness y el ciclismo indoor. Crea experiencias únicas de alto rendimiento.")
                    .activo(true)
                    .build());

            log.info("Instructores creados: {}", instructorRepo.count());

            // ─────────────────────────────────────────
            // CLASES (horario semanal fijo)
            // ─────────────────────────────────────────
            int cupoSpinning = equipoRepo.findById(TipoClase.SPINNING)
                    .map(e -> e.getCantidad()).orElse(15);
            int cupoPilates  = equipoRepo.findById(TipoClase.PILATES)
                    .map(e -> e.getCantidad()).orElse(7);

            List<Clase> clases = claseRepo.saveAll(List.of(

                // ── PILATES 09:00 (Lun–Vie · Sharon) ──
                clase(TipoClase.PILATES, sharon, "09:00", DiaSemana.LUNES,     cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "09:00", DiaSemana.MARTES,    cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "09:00", DiaSemana.MIERCOLES, cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "09:00", DiaSemana.JUEVES,    cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "09:00", DiaSemana.VIERNES,   cupoPilates, 0),

                // ── PILATES 17:00 (Lun–Vie · Sharon) ──
                clase(TipoClase.PILATES, sharon, "17:00", DiaSemana.LUNES,     cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "17:00", DiaSemana.MARTES,    cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "17:00", DiaSemana.MIERCOLES, cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "17:00", DiaSemana.JUEVES,    cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "17:00", DiaSemana.VIERNES,   cupoPilates, 0),

                // ── PILATES 20:00 (Lun–Vie · Sharon) ──
                clase(TipoClase.PILATES, sharon, "20:00", DiaSemana.LUNES,     cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "20:00", DiaSemana.MARTES,    cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "20:00", DiaSemana.MIERCOLES, cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "20:00", DiaSemana.JUEVES,    cupoPilates, 0),
                clase(TipoClase.PILATES, sharon, "20:00", DiaSemana.VIERNES,   cupoPilates, 0),

                // ── CYCLING 06:00 (Lun–Vie · Carlos / Andrea) ──
                clase(TipoClase.SPINNING, carlos, "06:00", DiaSemana.LUNES,     cupoSpinning, 0),
                clase(TipoClase.SPINNING, andrea, "06:00", DiaSemana.MARTES,    cupoSpinning, 0),
                clase(TipoClase.SPINNING, carlos, "06:00", DiaSemana.MIERCOLES, cupoSpinning, 0),
                clase(TipoClase.SPINNING, andrea, "06:00", DiaSemana.JUEVES,    cupoSpinning, 0),
                clase(TipoClase.SPINNING, carlos, "06:00", DiaSemana.VIERNES,   cupoSpinning, 0),

                // ── CYCLING 18:00 (Lun–Vie · Andrea / Carlos) ──
                clase(TipoClase.SPINNING, andrea, "18:00", DiaSemana.LUNES,     cupoSpinning, 0),
                clase(TipoClase.SPINNING, carlos, "18:00", DiaSemana.MARTES,    cupoSpinning, 0),
                clase(TipoClase.SPINNING, andrea, "18:00", DiaSemana.MIERCOLES, cupoSpinning, 0),
                clase(TipoClase.SPINNING, carlos, "18:00", DiaSemana.JUEVES,    cupoSpinning, 0),
                clase(TipoClase.SPINNING, andrea, "18:00", DiaSemana.VIERNES,   cupoSpinning, 0),

                // ── CYCLING 19:00 (Lun–Vie · Carlos / Andrea) ──
                clase(TipoClase.SPINNING, carlos, "19:00", DiaSemana.LUNES,     cupoSpinning, 0),
                clase(TipoClase.SPINNING, andrea, "19:00", DiaSemana.MARTES,    cupoSpinning, 0),
                clase(TipoClase.SPINNING, carlos, "19:00", DiaSemana.MIERCOLES, cupoSpinning, 0),
                clase(TipoClase.SPINNING, andrea, "19:00", DiaSemana.JUEVES,    cupoSpinning, 0),
                clase(TipoClase.SPINNING, carlos, "19:00", DiaSemana.VIERNES,   cupoSpinning, 0)
            ));

            log.info("Clases creadas: {}", claseRepo.count());

            // ─────────────────────────────────────────
            // RESERVACIONES de muestra
            // ─────────────────────────────────────────
            LocalDate hoy        = LocalDate.now();
            LocalDate manana     = hoy.plusDays(1);
            LocalDate enDosDias  = hoy.plusDays(2);

            // Buscar clases concretas para asignar reservaciones
            Clase spinningLunes1800 = clases.stream()
                    .filter(c -> c.getTipo() == TipoClase.SPINNING
                              && c.getDiaSemana() == DiaSemana.LUNES
                              && c.getHora().equals("18:00"))
                    .findFirst().orElse(clases.get(0));

            Clase pilatesMartes0730 = clases.stream()
                    .filter(c -> c.getTipo() == TipoClase.PILATES
                              && c.getDiaSemana() == DiaSemana.MARTES
                              && c.getHora().equals("07:30"))
                    .findFirst().orElse(clases.get(1));

            reservacionRepo.saveAll(List.of(
                reservacion(maria, spinningLunes1800, manana,    EstadoReservacion.CONFIRMADA),
                reservacion(jose,  spinningLunes1800, manana,    EstadoReservacion.CONFIRMADA),
                reservacion(ana,   pilatesMartes0730, enDosDias, EstadoReservacion.CONFIRMADA),
                reservacion(maria, pilatesMartes0730, enDosDias, EstadoReservacion.CONFIRMADA)
            ));

            log.info("Reservaciones creadas: {}", reservacionRepo.count());
            log.info("✓ Seed completado. Credenciales de acceso:");
            log.info("  Admin  → admin@theroom.mx  / Admin123!");
            log.info("  Client → maria@gmail.com   / Cliente123!");
            log.info("  Client → jose@gmail.com    / Cliente123!");
            log.info("  Client → ana@gmail.com     / Cliente123!");
        };
    }

    // ── Helpers ──────────────────────────────────
    private Clase clase(TipoClase tipo, Instructor instructor,
                        String hora, DiaSemana dia,
                        int cupoTotal, int cupoTomado) {
        return Clase.builder()
                .tipo(tipo)
                .instructor(instructor)
                .hora(hora)
                .diaSemana(dia)
                .cupoTotal(cupoTotal)
                .cupoTomado(cupoTomado)
                .activo(true)
                .build();
    }

    private Reservacion reservacion(Usuario usuario, Clase clase,
                                    LocalDate fecha, EstadoReservacion estado) {
        return Reservacion.builder()
                .usuario(usuario)
                .clase(clase)
                .fecha(fecha)
                .estado(estado)
                .build();
    }
}
