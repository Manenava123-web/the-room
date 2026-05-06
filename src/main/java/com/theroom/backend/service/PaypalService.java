package com.theroom.backend.service;

import com.theroom.backend.dto.PagoResponse;
import com.theroom.backend.entity.Paquete;
import com.theroom.backend.entity.Usuario;
import com.theroom.backend.enums.TipoDisciplina;
import com.theroom.backend.exception.AppException;
import com.theroom.backend.repository.PaqueteRepository;
import com.theroom.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaypalService {

    private final RestTemplate restTemplate;
    private final PaqueteRepository paqueteRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    @Value("${paypal.client-id:}")
    private String clientId;

    @Value("${paypal.client-secret:}")
    private String clientSecret;

    @Value("${paypal.sandbox:true}")
    private boolean sandbox;

    // ── Token cache (no thread-safety needed for single-process sandbox) ──
    private volatile String cachedToken;
    private volatile long tokenExpiry;

    private String apiBase() {
        return sandbox ? "https://api-m.sandbox.paypal.com" : "https://api-m.paypal.com";
    }

    private synchronized String getAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken;
        }
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + credentials);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiBase() + "/v1/oauth2/token",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<?, ?> resp = response.getBody();
            if (resp == null) throw new AppException("Sin respuesta del token PayPal", HttpStatus.BAD_GATEWAY);
            cachedToken = String.valueOf(resp.get("access_token"));
            int expiresIn = resp.get("expires_in") instanceof Number n ? n.intValue() : 3600;
            tokenExpiry = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
            return cachedToken;
        } catch (HttpClientErrorException e) {
            throw new AppException("Error de autenticación PayPal: " + e.getResponseBodyAsString(), HttpStatus.BAD_GATEWAY);
        }
    }

    // ── Crear orden ────────────────────────────────────────────
    public String crearOrden(Long paqueteId) {
        Paquete paquete = paqueteRepository.findById(paqueteId)
                .filter(Paquete::isActivo)
                .orElseThrow(() -> new AppException("Paquete no encontrado o inactivo", HttpStatus.NOT_FOUND));

        String token = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        Map<String, Object> amount = new HashMap<>();
        amount.put("currency_code", "MXN");
        amount.put("value", paquete.getPrecio().setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString());

        Map<String, Object> unit = new HashMap<>();
        unit.put("amount", amount);
        unit.put("description", paquete.getNombre() + " — The Room Studio");

        Map<String, Object> orderBody = new HashMap<>();
        orderBody.put("intent", "CAPTURE");
        orderBody.put("purchase_units", List.of(unit));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiBase() + "/v2/checkout/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(orderBody, headers),
                    Map.class
            );
            Map<?, ?> resp = response.getBody();
            if (resp == null) throw new AppException("Sin respuesta al crear orden PayPal", HttpStatus.BAD_GATEWAY);
            return String.valueOf(resp.get("id"));
        } catch (HttpClientErrorException e) {
            throw new AppException("Error al crear orden PayPal: " + e.getResponseBodyAsString(), HttpStatus.BAD_GATEWAY);
        }
    }

    // ── Capturar orden y acreditar clases ──────────────────────
    @Transactional
    public PagoResponse capturarOrden(String orderId, Long paqueteId, String email) {
        String token = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiBase() + "/v2/checkout/orders/" + orderId + "/capture",
                    HttpMethod.POST,
                    new HttpEntity<>(new HashMap<>(), headers),
                    Map.class
            );
            Map<?, ?> resp = response.getBody();
            if (resp == null) throw new AppException("Sin respuesta al capturar orden PayPal", HttpStatus.BAD_GATEWAY);
            String status = String.valueOf(resp.get("status"));
            if (!"COMPLETED".equals(status)) {
                throw new AppException("Pago PayPal no completado (estado: " + status + ")", HttpStatus.PAYMENT_REQUIRED);
            }
        } catch (HttpClientErrorException e) {
            throw new AppException("Error al capturar pago PayPal: " + e.getResponseBodyAsString(), HttpStatus.PAYMENT_REQUIRED);
        }

        Paquete paquete = paqueteRepository.findById(paqueteId)
                .orElseThrow(() -> new AppException("Paquete no encontrado", HttpStatus.NOT_FOUND));

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        aplicarCreditos(usuario, paquete);
        usuarioRepository.save(usuario);

        notificacionService.enviarConfirmacion(usuario, paquete, orderId, "PayPal");

        return PagoResponse.builder()
                .transaccionId(orderId)
                .status("completed")
                .monto(paquete.getPrecio().doubleValue())
                .clasesAgregadas(paquete.getNumClases())
                .creditosCycling(usuario.getCreditosCycling())
                .creditosCyclingVencen(usuario.getCreditosCyclingVencen())
                .creditosPilates(usuario.getCreditosPilates())
                .creditosPilatesVencen(usuario.getCreditosPilatesVencen())
                .build();
    }

    private void aplicarCreditos(Usuario usuario, Paquete paquete) {
        LocalDate today = LocalDate.now();
        LocalDate nuevaVigencia = sumarDiasHabiles(today, paquete.getVigenciaDias());
        if (paquete.getDisciplina() == TipoDisciplina.CYCLING) {
            usuario.setCreditosCycling(usuario.getCreditosCycling() + paquete.getNumClases());
            LocalDate actual = usuario.getCreditosCyclingVencen();
            usuario.setCreditosCyclingVencen(
                    actual != null && actual.isAfter(nuevaVigencia) ? actual : nuevaVigencia);
        } else {
            usuario.setCreditosPilates(usuario.getCreditosPilates() + paquete.getNumClases());
            LocalDate actual = usuario.getCreditosPilatesVencen();
            usuario.setCreditosPilatesVencen(
                    actual != null && actual.isAfter(nuevaVigencia) ? actual : nuevaVigencia);
        }
    }

    private LocalDate sumarDiasHabiles(LocalDate inicio, int diasHabiles) {
        LocalDate fecha = inicio;
        int contados = 0;
        while (contados < diasHabiles) {
            fecha = fecha.plusDays(1);
            java.time.DayOfWeek dia = fecha.getDayOfWeek();
            if (dia != java.time.DayOfWeek.SATURDAY && dia != java.time.DayOfWeek.SUNDAY) {
                contados++;
            }
        }
        return fecha;
    }
}
