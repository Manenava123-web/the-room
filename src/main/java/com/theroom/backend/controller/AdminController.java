package com.theroom.backend.controller;

import com.theroom.backend.dto.*;
import com.theroom.backend.dto.ClaseEnCursoDTO;
import com.theroom.backend.enums.TipoClase;
import com.theroom.backend.entity.Instructor;
import com.theroom.backend.enums.RolUsuario;
import com.theroom.backend.repository.InstructorRepository;
import com.theroom.backend.repository.UsuarioRepository;
import com.theroom.backend.service.ClaseService;
import com.theroom.backend.service.InstructorService;
import com.theroom.backend.service.PagoService;
import com.theroom.backend.service.ReservacionService;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ReservacionService reservacionService;
    private final ClaseService claseService;
    private final InstructorService instructorService;
    private final PagoService pagoService;
    private final UsuarioRepository usuarioRepository;
    private final InstructorRepository instructorRepository;

    // GET /api/v1/admin/reservaciones — todas las reservaciones
    @GetMapping("/reservaciones")
    public ResponseEntity<List<ReservacionAdminDTO>> todasLasReservaciones() {
        return ResponseEntity.ok(reservacionService.obtenerTodas());
    }

    // POST /api/v1/admin/reservaciones — crear reservación para un cliente
    @PostMapping("/reservaciones")
    public ResponseEntity<ReservacionAdminDTO> crearParaUsuario(
            @Valid @RequestBody ReservacionAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservacionService.crearParaUsuario(request));
    }

    // DELETE /api/v1/admin/reservaciones/{id} — cancelar cualquier reservación
    @DeleteMapping("/reservaciones/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        reservacionService.cancelarComoAdmin(id);
        return ResponseEntity.noContent().build();
    }

    // PATCH /api/v1/admin/reservaciones/{id}/no-asistio — liberar lugar sin devolver crédito
    @PatchMapping("/reservaciones/{id}/no-asistio")
    public ResponseEntity<Void> noAsistio(@PathVariable Long id) {
        reservacionService.registrarNoAsistio(id);
        return ResponseEntity.noContent().build();
    }

    // GET /api/v1/admin/clases/en-curso — clases activas ahora con sus reservaciones confirmadas
    @GetMapping("/clases/en-curso")
    public ResponseEntity<List<ClaseEnCursoDTO>> clasesEnCurso() {
        return ResponseEntity.ok(reservacionService.getClasesEnCurso());
    }

    // ── CLASES ────────────────────────────────────────────────

    // GET /api/v1/admin/clases — todas las clases (activas e inactivas)
    @GetMapping("/clases")
    public ResponseEntity<List<ClaseAdminDTO>> todasLasClases() {
        return ResponseEntity.ok(claseService.obtenerTodasAdmin());
    }

    // POST /api/v1/admin/clases — crear nueva clase
    @PostMapping("/clases")
    public ResponseEntity<ClaseAdminDTO> crearClase(@RequestBody ClaseAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(claseService.crearClase(request));
    }

    // PUT /api/v1/admin/clases/{id} — editar clase
    @PutMapping("/clases/{id}")
    public ResponseEntity<ClaseAdminDTO> editarClase(
            @PathVariable Long id,
            @RequestBody ClaseAdminRequest request) {
        return ResponseEntity.ok(claseService.editarClase(id, request));
    }

    // DELETE /api/v1/admin/clases/{id} — eliminar clase (sin reservaciones)
    @DeleteMapping("/clases/{id}")
    public ResponseEntity<Void> eliminarClase(@PathVariable Long id) {
        claseService.eliminarClase(id);
        return ResponseEntity.noContent().build();
    }

    // PATCH /api/v1/admin/clases/{id}/instructor — cambiar o desasignar instructor
    @PatchMapping("/clases/{id}/instructor")
    public ResponseEntity<ClaseAdminDTO> cambiarInstructor(
            @PathVariable Long id,
            @RequestBody CambiarInstructorRequest request) {
        return ResponseEntity.ok(claseService.cambiarInstructor(id, request.getInstructorId()));
    }

    // PATCH /api/v1/admin/clases/{id}/toggle — activar / desactivar clase
    @PatchMapping("/clases/{id}/toggle")
    public ResponseEntity<ClaseAdminDTO> toggleActivo(@PathVariable Long id) {
        return ResponseEntity.ok(claseService.toggleActivo(id));
    }

    // GET /api/v1/admin/instructores — activos (para dropdowns)
    @GetMapping("/instructores")
    public ResponseEntity<List<Instructor>> instructores() {
        return ResponseEntity.ok(instructorRepository.findByActivoTrueOrderByNombreAsc());
    }

    // GET /api/v1/admin/instructores/todos — todos (gestión)
    @GetMapping("/instructores/todos")
    public ResponseEntity<List<Instructor>> todosInstructores() {
        return ResponseEntity.ok(instructorService.obtenerTodosAdmin());
    }

    // POST /api/v1/admin/instructores — crear instructor
    @PostMapping("/instructores")
    public ResponseEntity<Instructor> crearInstructor(@RequestBody InstructorAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(instructorService.crear(request));
    }

    // PUT /api/v1/admin/instructores/{id} — editar instructor
    @PutMapping("/instructores/{id}")
    public ResponseEntity<Instructor> editarInstructor(
            @PathVariable Long id, @RequestBody InstructorAdminRequest request) {
        return ResponseEntity.ok(instructorService.editar(id, request));
    }

    // PATCH /api/v1/admin/instructores/{id}/toggle — activar/desactivar
    @PatchMapping("/instructores/{id}/toggle")
    public ResponseEntity<Instructor> toggleInstructor(@PathVariable Long id) {
        return ResponseEntity.ok(instructorService.toggleActivo(id));
    }

    // POST /api/v1/admin/instructores/{id}/foto — subir foto
    @PostMapping("/instructores/{id}/foto")
    public ResponseEntity<Instructor> subirFoto(
            @PathVariable Long id, @RequestParam("foto") MultipartFile file) {
        return ResponseEntity.ok(instructorService.subirFoto(id, file));
    }

    // ── CRÉDITOS / COBRO EN EFECTIVO ──────────────────────────

    // POST /api/v1/admin/creditos/efectivo — registrar pago en efectivo y acreditar clases
    @PostMapping("/creditos/efectivo")
    public ResponseEntity<CreditosDTO> cobrarEfectivo(@RequestBody CobroEfectivoRequest request) {
        return ResponseEntity.ok(pagoService.cobrarEfectivo(request));
    }

    // GET /api/v1/admin/creditos/{usuarioId} — consultar créditos de un cliente
    @GetMapping("/creditos/{usuarioId}")
    public ResponseEntity<CreditosDTO> creditosDeCliente(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(pagoService.getCreditosByUsuarioId(usuarioId));
    }

    // ── EQUIPO DEL ESTUDIO ────────────────────────────────────

    // GET /api/v1/admin/equipo — ver equipo actual por disciplina
    @GetMapping("/equipo")
    public ResponseEntity<List<EquipoEstudioDTO>> getEquipo() {
        return ResponseEntity.ok(claseService.obtenerEquipo());
    }

    // PUT /api/v1/admin/equipo/{tipo} — actualizar cantidad de equipo
    @PutMapping("/equipo/{tipo}")
    public ResponseEntity<EquipoEstudioDTO> actualizarEquipo(
            @PathVariable TipoClase tipo,
            @RequestBody java.util.Map<String, Integer> body) {
        int cantidad = body.get("cantidad");
        if (cantidad < 1) throw new com.theroom.backend.exception.AppException("La cantidad debe ser al menos 1", org.springframework.http.HttpStatus.BAD_REQUEST);
        return ResponseEntity.ok(claseService.actualizarEquipo(tipo, cantidad));
    }

    // ── PAQUETES ──────────────────────────────────────────────

    // GET /api/v1/admin/paquetes — todos (activos e inactivos)
    @GetMapping("/paquetes")
    public ResponseEntity<List<PaqueteDTO>> getPaquetesAdmin() {
        return ResponseEntity.ok(pagoService.getPaquetesAdmin());
    }

    // POST /api/v1/admin/paquetes — crear paquete
    @PostMapping("/paquetes")
    public ResponseEntity<PaqueteDTO> crearPaquete(@RequestBody PaqueteAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoService.crearPaquete(request));
    }

    // PUT /api/v1/admin/paquetes/{id} — editar paquete
    @PutMapping("/paquetes/{id}")
    public ResponseEntity<PaqueteDTO> editarPaquete(
            @PathVariable Long id, @RequestBody PaqueteAdminRequest request) {
        return ResponseEntity.ok(pagoService.editarPaquete(id, request));
    }

    // PATCH /api/v1/admin/paquetes/{id}/toggle — activar/desactivar
    @PatchMapping("/paquetes/{id}/toggle")
    public ResponseEntity<PaqueteDTO> togglePaquete(@PathVariable Long id) {
        return ResponseEntity.ok(pagoService.togglePaquete(id));
    }

    // DELETE /api/v1/admin/paquetes/{id} — eliminar paquete
    @DeleteMapping("/paquetes/{id}")
    public ResponseEntity<Void> eliminarPaquete(@PathVariable Long id) {
        pagoService.eliminarPaquete(id);
        return ResponseEntity.noContent().build();
    }

    // ── USUARIOS ──────────────────────────────────────────────

    // GET /api/v1/admin/usuarios — lista de clientes registrados
    @GetMapping("/usuarios")
    public ResponseEntity<List<UsuarioClienteDTO>> clientes() {
        List<UsuarioClienteDTO> clientes = usuarioRepository
                .findByRolOrderByNombreAsc(RolUsuario.CLIENTE)
                .stream()
                .map(u -> UsuarioClienteDTO.builder()
                        .id(u.getId())
                        .nombre(u.getNombre())
                        .apellido(u.getApellido())
                        .email(u.getEmail())
                        .telefono(u.getTelefono())
                        .build())
                .toList();
        return ResponseEntity.ok(clientes);
    }

    // GET /api/v1/admin/usuarios/creditos — clientes con saldo de clases
    @GetMapping("/usuarios/creditos")
    public ResponseEntity<List<UsuarioCreditosDTO>> clientesConCreditos() {
        List<UsuarioCreditosDTO> result = usuarioRepository
                .findByRolOrderByNombreAsc(RolUsuario.CLIENTE)
                .stream()
                .map(u -> UsuarioCreditosDTO.builder()
                        .id(u.getId())
                        .nombre(u.getNombre())
                        .apellido(u.getApellido())
                        .email(u.getEmail())
                        .telefono(u.getTelefono())
                        .creditosCycling(u.getCreditosCycling())
                        .creditosCyclingVencen(u.getCreditosCyclingVencen())
                        .creditosPilates(u.getCreditosPilates())
                        .creditosPilatesVencen(u.getCreditosPilatesVencen())
                        .build())
                .toList();
        return ResponseEntity.ok(result);
    }

    // GET /api/v1/admin/pagos/historial?periodo=dia|semana|mes
    @GetMapping("/pagos/historial")
    public ResponseEntity<java.util.Map<String, Object>> historialPagos(
            @RequestParam(defaultValue = "dia") String periodo) {
        return ResponseEntity.ok(pagoService.getHistorial(periodo));
    }
}
