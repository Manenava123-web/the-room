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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final NotificacionService notificacionService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String telefono = (request.getTelefono() != null && !request.getTelefono().isBlank())
                ? request.getTelefono().trim() : null;

        if (!dominioExiste(email)) {
            throw new AppException(
                "El dominio del correo no es válido. Revisa que no haya errores de escritura (ej. .con en lugar de .com).",
                HttpStatus.BAD_REQUEST
            );
        }
        if (usuarioRepository.existsByEmail(email)) {
            throw new AppException("El correo ya está registrado", HttpStatus.CONFLICT);
        }
        try {
            Usuario usuario = Usuario.builder()
                    .nombre(request.getNombre().trim())
                    .apellido(request.getApellido().trim())
                    .email(email)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .telefono(telefono)
                    .rol(RolUsuario.CLIENTE)
                    .activo(true)
                    .build();

            usuarioRepository.save(usuario);
            String token = jwtUtil.generateToken(usuario);
            return buildResponse(usuario, token);
        } catch (DataIntegrityViolationException e) {
            throw new AppException("El correo o teléfono ya está registrado", HttpStatus.CONFLICT);
        }
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new AppException("Credenciales incorrectas", HttpStatus.UNAUTHORIZED);
        }

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        if (!usuario.isActivo()) {
            throw new AppException("Cuenta desactivada. Contacta al estudio.", HttpStatus.FORBIDDEN);
        }

        String token = jwtUtil.generateToken(usuario);
        return buildResponse(usuario, token);
    }

    @Transactional
    public void solicitarReset(String email) {
        String emailNorm = email.trim().toLowerCase();
        usuarioRepository.findByEmail(emailNorm).ifPresent(usuario -> {
            resetTokenRepository.deleteByEmail(emailNorm);

            PasswordResetToken reset = PasswordResetToken.builder()
                    .token(UUID.randomUUID().toString())
                    .email(emailNorm)
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

    /**
     * Verifica via DNS que el dominio del correo exista y pueda recibir emails.
     * Si el DNS no responde en 3 s se permite el registro (fail-open) para no
     * bloquear usuarios legítimos por problemas de red en el servidor.
     */
    private boolean dominioExiste(String email) {
        String dominio = email.substring(email.indexOf('@') + 1);
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    InetAddress.getByName(dominio);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
            return future.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Timeout verificando dominio '{}' — se permite el registro", dominio);
            return true;
        } catch (Exception e) {
            log.warn("Error verificando dominio '{}': {} — se permite el registro", dominio, e.getMessage());
            return true;
        }
    }
}
