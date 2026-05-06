package com.theroom.backend.controller;

import com.theroom.backend.dto.auth.AuthResponse;
import com.theroom.backend.dto.auth.LoginRequest;
import com.theroom.backend.dto.auth.RegisterRequest;
import com.theroom.backend.dto.auth.ResetPasswordRequest;
import com.theroom.backend.dto.auth.SolicitarResetRequest;
import com.theroom.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/solicitar-reset")
    public ResponseEntity<Void> solicitarReset(@Valid @RequestBody SolicitarResetRequest request) {
        authService.solicitarReset(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNuevaPassword());
        return ResponseEntity.ok().build();
    }
}
