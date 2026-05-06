package com.theroom.backend.service;

import com.theroom.backend.dto.InstructorAdminRequest;
import com.theroom.backend.entity.Instructor;
import com.theroom.backend.enums.TipoClase;
import com.theroom.backend.exception.AppException;
import com.theroom.backend.repository.InstructorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InstructorService {

    private final InstructorRepository instructorRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public List<Instructor> obtenerTodos() {
        return instructorRepository.findByActivoTrue();
    }

    public List<Instructor> obtenerPorEspecialidad(TipoClase tipo) {
        return instructorRepository.findByEspecialidadAndActivoTrue(tipo);
    }

    public Instructor obtenerPorId(Long id) {
        return instructorRepository.findById(id)
                .orElseThrow(() -> new AppException("Instructor no encontrado", HttpStatus.NOT_FOUND));
    }

    // ── Admin: todos (incluye inactivos) ──────────────────────
    public List<Instructor> obtenerTodosAdmin() {
        return instructorRepository.findAllByOrderByNombreAsc();
    }

    // ── Admin: crear instructor ────────────────────────────────
    public Instructor crear(InstructorAdminRequest request) {
        Instructor instructor = Instructor.builder()
                .nombre(request.getNombre())
                .apellido(request.getApellido())
                .especialidad(request.getEspecialidad())
                .bio(request.getBio())
                .activo(true)
                .build();
        return instructorRepository.save(instructor);
    }

    // ── Admin: editar instructor ───────────────────────────────
    public Instructor editar(Long id, InstructorAdminRequest request) {
        Instructor instructor = obtenerPorId(id);
        instructor.setNombre(request.getNombre());
        instructor.setApellido(request.getApellido());
        instructor.setEspecialidad(request.getEspecialidad());
        instructor.setBio(request.getBio());
        return instructorRepository.save(instructor);
    }

    // ── Admin: activar / desactivar ───────────────────────────
    public Instructor toggleActivo(Long id) {
        Instructor instructor = obtenerPorId(id);
        instructor.setActivo(!instructor.isActivo());
        return instructorRepository.save(instructor);
    }

    // ── Admin: subir foto ─────────────────────────────────────
    public Instructor subirFoto(Long id, MultipartFile file) {
        Instructor instructor = obtenerPorId(id);
        try {
            Path dir = Paths.get(uploadDir, "instructores");
            Files.createDirectories(dir);

            String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String filename = "instructor_" + id + (ext != null ? "." + ext : "");
            file.transferTo(dir.resolve(filename));

            instructor.setFotoUrl("/uploads/instructores/" + filename);
            return instructorRepository.save(instructor);
        } catch (IOException e) {
            throw new AppException("Error al guardar la foto", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
