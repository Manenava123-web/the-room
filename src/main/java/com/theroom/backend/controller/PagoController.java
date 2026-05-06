package com.theroom.backend.controller;

import com.theroom.backend.dto.*;
import com.theroom.backend.service.PagoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @GetMapping("/paquetes")
    public ResponseEntity<List<PaqueteDTO>> getPaquetes() {
        return ResponseEntity.ok(pagoService.getPaquetes());
    }

    @PostMapping("/paquete")
    public ResponseEntity<PagoResponse> comprarPaquete(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PagoRequest request) {
        return ResponseEntity.ok(pagoService.procesarPaquete(userDetails.getUsername(), request));
    }

    @GetMapping("/mis-creditos")
    public ResponseEntity<CreditosDTO> misCreditos(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(pagoService.getMisCreditos(userDetails.getUsername()));
    }
}
