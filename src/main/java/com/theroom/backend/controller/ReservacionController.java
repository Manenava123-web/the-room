package com.theroom.backend.controller;

import com.theroom.backend.dto.ReservacionDTO;
import com.theroom.backend.dto.ReservacionRequest;
import com.theroom.backend.entity.Usuario;
import com.theroom.backend.service.ReservacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservaciones")
@RequiredArgsConstructor
public class ReservacionController {

    private final ReservacionService reservacionService;

    // POST /api/v1/reservaciones
    @PostMapping
    public ResponseEntity<ReservacionDTO> crear(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody ReservacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservacionService.crear(usuario.getId(), request));
    }

    // DELETE /api/v1/reservaciones/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable Long id) {
        reservacionService.cancelar(usuario.getId(), id);
        return ResponseEntity.noContent().build();
    }

    // GET /api/v1/reservaciones/mis
    @GetMapping("/mis")
    public ResponseEntity<List<ReservacionDTO>> misReservaciones(
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(reservacionService.misReservaciones(usuario.getId()));
    }

    // GET /api/v1/reservaciones/proximas
    @GetMapping("/proximas")
    public ResponseEntity<List<ReservacionDTO>> misProximas(
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(reservacionService.misProximas(usuario.getId()));
    }
}
