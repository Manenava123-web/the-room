package com.theroom.backend.repository;

import com.theroom.backend.entity.Usuario;
import com.theroom.backend.enums.RolUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    List<Usuario> findByRolOrderByNombreAsc(RolUsuario rol);
}
