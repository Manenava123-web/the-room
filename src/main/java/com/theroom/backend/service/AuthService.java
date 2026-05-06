package com.theroom.backend.service;

import com.theroom.backend.dto.auth.AuthResponse;
import com.theroom.backend.dto.auth.LoginRequest;
import com.theroom.backend.dto.auth.RegisterRequest;
import com.theroom.backend.entity.PasswordResetToken;
import com.theroom.backend.entity.Usuario;
import com.theroom.backend.enums.RolUsuario;
import com.theroom.backend.exception.AppException;
import com.theroom.backend.repository.PasswordResetTokenRepository;
import com.theroom.backend.repository.UsuarioRepository;
import com.theroom.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final NotificacionService notificacionService;

    public AuthResponse register(RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new AppException("El correo ya está registrado", HttpStatus.CONFLICT);
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .apellido(request.getApellido())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .telefono(request.getTelefono())
                .rol(RolUsuario.CLIENTE)
                .activo(true)
                .build();

        usuarioRepository.save(usuario);
        String token = jwtUtil.generateToken(usuario);

        return buildResponse(usuario, token);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new AppException("Credenciales incorrectas", HttpStatus.UNAUTHORIZED);
        }

        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        if (!usuario.isActivo()) {
            throw new AppException("Cuenta desactivada. Contacta al estudio.", HttpStatus.FORBIDDEN);
        }

        String token = jwtUtil.generateToken(usuario);
        return buildResponse(usuario, token);
    }

    @Transactional
    public void solicitarReset(String email) {
        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            resetTokenRepository.deleteByEmail(email);

            PasswordResetToken reset = PasswordResetToken.builder()
                    .token(UUID.randomUUID().toString())
                    .email(email)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .used(false)
                    .build();
            resetTokenRepository.save(reset);

            notificacionService.enviarResetPassword(usuario, reset.getToken());
        });
    }

    @Transactional
    public void resetPassword(String token, String nuevaPassword) {
        PasswordResetToken reset = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new AppException("El enlace no es válido o ya expiró", HttpStatus.BAD_REQUEST));

        if (reset.isUsed()) {
            throw new AppException("Este enlace ya fue utilizado", HttpStatus.BAD_REQUEST);
        }
        if (reset.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException("El enlace ha expirado. Solicita uno nuevo.", HttpStatus.BAD_REQUEST);
        }

        Usuario usuario = usuarioRepository.findByEmail(reset.getEmail())
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);

        reset.setUsed(true);
        resetTokenRepository.save(reset);
    }

    private AuthResponse buildResponse(Usuario usuario, String token) {
        return AuthResponse.builder()
                .token(token)
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .email(usuario.getEmail())
                .rol(usuario.getRol())
                .build();
    }
}
