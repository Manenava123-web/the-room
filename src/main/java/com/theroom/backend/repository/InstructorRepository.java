package com.theroom.backend.repository;

import com.theroom.backend.entity.Instructor;
import com.theroom.backend.enums.TipoClase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstructorRepository extends JpaRepository<Instructor, Long> {
    List<Instructor> findByActivoTrue();
    List<Instructor> findByActivoTrueOrderByNombreAsc();
    List<Instructor> findByEspecialidadAndActivoTrue(TipoClase especialidad);
    List<Instructor> findAllByOrderByNombreAsc();
}
