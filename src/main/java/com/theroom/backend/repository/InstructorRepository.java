package com.theroom.backend.repository;

import com.theroom.backend.entity.Instructor;
import com.theroom.backend.enums.TipoClase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InstructorRepository extends JpaRepository<Instructor, Long> {
    List<Instructor> findByActivoTrue();
    List<Instructor> findByActivoTrueOrderByNombreAsc();
    List<Instructor> findAllByOrderByNombreAsc();

    @Query("SELECT i FROM Instructor i WHERE :tipo MEMBER OF i.especialidades AND i.activo = true")
    List<Instructor> findByEspecialidadAndActivoTrue(@Param("tipo") TipoClase tipo);
}
