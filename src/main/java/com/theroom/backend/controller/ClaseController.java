package com.theroom.backend.controller;

import com.theroom.backend.dto.ClaseDTO;
import com.theroom.backend.dto.EquipoEstudioDTO;
import com.theroom.backend.enums.DiaSemana;
import com.theroom.backend.enums.TipoClase;
import com.theroom.backend.service.ClaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clases")
@RequiredArgsConstructor
public class ClaseController {

    private final ClaseService claseService;

    // GET /api/v1/clases
    @GetMapping
    public ResponseEntity<List<ClaseDTO>> todas() {
        return ResponseEntity.ok(claseService.obtenerTodas());
    }

    // GET /api/v1/clases/semana?offset=0
    @GetMapping("/semana")
    public ResponseEntity<List<ClaseDTO>> porSemana(@RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(claseService.obtenerPorSemana(offset));
    }

    // GET /api/v1/clases/dia?dia=LUNES
    @GetMapping("/dia")
    public ResponseEntity<List<ClaseDTO>> porDia(@RequestParam DiaSemana dia) {
        return ResponseEntity.ok(claseService.obtenerPorDia(dia));
    }

    // GET /api/v1/clases/tipo?tipo=SPINNING
    @GetMapping("/tipo")
    public ResponseEntity<List<ClaseDTO>> porTipo(@RequestParam TipoClase tipo) {
        return ResponseEntity.ok(claseService.obtenerPorTipo(tipo));
    }

    // GET /api/v1/clases/equipo — capacidad pública por disciplina
    @GetMapping("/equipo")
    public ResponseEntity<List<EquipoEstudioDTO>> equipoPublico() {
        return ResponseEntity.ok(claseService.obtenerEquipo());
    }

    // GET /api/v1/clases/{id}/lugares?fecha=YYYY-MM-DD
    @GetMapping("/{id}/lugares")
    public ResponseEntity<Map<String, Object>> lugares(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(claseService.obtenerLugares(id, fecha));
    }
}
