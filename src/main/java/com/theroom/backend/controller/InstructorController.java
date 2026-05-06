package com.theroom.backend.controller;

import com.theroom.backend.entity.Instructor;
import com.theroom.backend.enums.TipoClase;
import com.theroom.backend.service.InstructorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instructores")
@RequiredArgsConstructor
public class InstructorController {

    private final InstructorService instructorService;

    // GET /api/v1/instructores
    @GetMapping
    public ResponseEntity<List<Instructor>> todos() {
        return ResponseEntity.ok(instructorService.obtenerTodos());
    }

    // GET /api/v1/instructores/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Instructor> porId(@PathVariable Long id) {
        return ResponseEntity.ok(instructorService.obtenerPorId(id));
    }

    // GET /api/v1/instructores/especialidad?tipo=PILATES
    @GetMapping("/especialidad")
    public ResponseEntity<List<Instructor>> porEspecialidad(@RequestParam TipoClase tipo) {
        return ResponseEntity.ok(instructorService.obtenerPorEspecialidad(tipo));
    }
}
