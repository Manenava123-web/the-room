package com.theroom.backend.service;

import com.theroom.backend.dto.*;
import com.theroom.backend.entity.Pago;
import com.theroom.backend.entity.Paquete;
import com.theroom.backend.entity.Usuario;
import com.theroom.backend.enums.TipoDisciplina;
import com.theroom.backend.enums.TipoPaquete;
import com.theroom.backend.exception.AppException;
import com.theroom.backend.repository.PagoRepository;
import com.theroom.backend.repository.PaqueteRepository;
import com.theroom.backend.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PagoService {

    private final UsuarioRepository usuarioRepository;
    private final PaqueteRepository paqueteRepository;
    private final PagoRepository pagoRepository;
    private final RestTemplate restTemplate;
    private final NotificacionService notificacionService;

    @Value("${openpay.merchant-id}")
    private String merchantId;

    @Value("${openpay.private-key}")
    private String privateKey;

    @Value("${openpay.sandbox:true}")
    private boolean sandbox;

    // ── Seed inicial desde el enum ─────────────────────────────
    @PostConstruct
    public void sembrarPaquetes() {
        if (paqueteRepository.count() > 0) return;
        Arrays.stream(TipoPaquete.values()).forEach(t ->
            paqueteRepository.save(Paquete.builder()
                .nombre(t.descripcion)
                .numClases(t.numClases)
                .precio(t.precio)
                .disciplina(t.disciplina)
                .vigenciaDias(t.vigenciaDias)
                .esMensual(t.name().endsWith("_MES"))
                .activo(true)
                .build())
        );
    }

    // ── Pago con tarjeta (clientes) ────────────────────────────
    @Transactional
    public PagoResponse procesarPaquete(String email, PagoRequest req) {
        Paquete paquete = resolverPaquete(req.getPaqueteId());

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        String transaccionId = cobrarOpenpay(
                req.getTokenId(),
                req.getDeviceSessionId(),
                paquete.getPrecio(),
                paquete.getNombre() + " - The Room Studio",
                "theroom-" + usuario.getId() + "-" + System.currentTimeMillis(),
                usuario
        );

        aplicarCreditos(usuario, paquete);
        usuarioRepository.save(usuario);

        pagoRepository.save(Pago.builder()
                .usuario(usuario)
                .usuarioNombre(usuario.getNombre() + " " + usuario.getApellido())
                .usuarioEmail(usuario.getEmail())
                .paqueteNombre(paquete.getNombre())
                .disciplina(paquete.getDisciplina())
                .monto(paquete.getPrecio())
                .metodo("TARJETA")
                .transaccionId(transaccionId)
                .clasesAgregadas(paquete.getNumClases())
                .build());

        notificacionService.enviarConfirmacion(usuario, paquete, transaccionId, "Tarjeta (OpenPay)");

        return PagoResponse.builder()
                .transaccionId(transaccionId)
                .status("completed")
                .monto(paquete.getPrecio().doubleValue())
                .clasesAgregadas(paquete.getNumClases())
                .creditosCycling(usuario.getCreditosCycling())
                .creditosCyclingVencen(usuario.getCreditosCyclingVencen())
                .creditosPilates(usuario.getCreditosPilates())
                .creditosPilatesVencen(usuario.getCreditosPilatesVencen())
                .build();
    }

    // ── Cobro en efectivo (admin) ──────────────────────────────
    @Transactional
    public CreditosDTO cobrarEfectivo(CobroEfectivoRequest request) {
        Paquete paquete = resolverPaquete(request.getPaqueteId());

        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        aplicarCreditos(usuario, paquete);
        usuarioRepository.save(usuario);

        pagoRepository.save(Pago.builder()
                .usuario(usuario)
                .usuarioNombre(usuario.getNombre() + " " + usuario.getApellido())
                .usuarioEmail(usuario.getEmail())
                .paqueteNombre(paquete.getNombre())
                .disciplina(paquete.getDisciplina())
                .monto(paquete.getPrecio())
                .metodo("EFECTIVO")
                .transaccionId(null)
                .clasesAgregadas(paquete.getNumClases())
                .build());

        notificacionService.enviarConfirmacion(usuario, paquete, null, "Efectivo");

        return toCreditosDTO(usuario);
    }

    // ── Consulta de créditos ───────────────────────────────────
    public CreditosDTO getMisCreditos(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        return toCreditosDTO(usuario);
    }

    public CreditosDTO getCreditosByUsuarioId(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AppException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        return toCreditosDTO(usuario);
    }

    // ── Listado público (solo activos) ─────────────────────────
    public List<PaqueteDTO> getPaquetes() {
        return paqueteRepository.findByActivoTrueOrderByDisciplinaAscNumClasesAsc()
                .stream().map(this::toPaqueteDTO).collect(Collectors.toList());
    }

    // ── Listado admin (todos) ──────────────────────────────────
    public List<PaqueteDTO> getPaquetesAdmin() {
        return paqueteRepository.findAllByOrderByDisciplinaAscNumClasesAsc()
                .stream().map(this::toPaqueteDTO).collect(Collectors.toList());
    }

    // ── CRUD admin ─────────────────────────────────────────────
    @Transactional
    public PaqueteDTO crearPaquete(PaqueteAdminRequest req) {
        validarRequest(req);
        Paquete p = paqueteRepository.save(Paquete.builder()
                .nombre(req.getNombre().trim())
                .numClases(req.getNumClases())
                .precio(req.getPrecio())
                .disciplina(TipoDisciplina.valueOf(req.getDisciplina()))
                .vigenciaDias(req.getVigenciaDias())
                .esMensual(req.isEsMensual())
                .activo(true)
                .build());
        return toPaqueteDTO(p);
    }

    @Transactional
    public PaqueteDTO editarPaquete(Long id, PaqueteAdminRequest req) {
        validarRequest(req);
        Paquete p = resolverPaqueteById(id);
        p.setNombre(req.getNombre().trim());
        p.setNumClases(req.getNumClases());
        p.setPrecio(req.getPrecio());
        p.setDisciplina(TipoDisciplina.valueOf(req.getDisciplina()));
        p.setVigenciaDias(req.getVigenciaDias());
        p.setEsMensual(req.isEsMensual());
        return toPaqueteDTO(paqueteRepository.save(p));
    }

    @Transactional
    public PaqueteDTO togglePaquete(Long id) {
        Paquete p = resolverPaqueteById(id);
        p.setActivo(!p.isActivo());
        return toPaqueteDTO(paqueteRepository.save(p));
    }

    @Transactional
    public void eliminarPaquete(Long id) {
        if (!paqueteRepository.existsById(id))
            throw new AppException("Paquete no encontrado", HttpStatus.NOT_FOUND);
        paqueteRepository.deleteById(id);
    }

    // ── Helpers privados ───────────────────────────────────────
    private Paquete resolverPaquete(Long id) {
        return paqueteRepository.findById(id)
                .filter(Paquete::isActivo)
                .orElseThrow(() -> new AppException("Paquete no encontrado o inactivo", HttpStatus.NOT_FOUND));
    }

    private Paquete resolverPaqueteById(Long id) {
        return paqueteRepository.findById(id)
                .orElseThrow(() -> new AppException("Paquete no encontrado", HttpStatus.NOT_FOUND));
    }

    private void validarRequest(PaqueteAdminRequest req) {
        if (req.getNombre() == null || req.getNombre().isBlank())
            throw new AppException("El nombre es obligatorio", HttpStatus.BAD_REQUEST);
        if (req.getNumClases() < 1)
            throw new AppException("El número de clases debe ser al menos 1", HttpStatus.BAD_REQUEST);
        if (req.getPrecio() == null || req.getPrecio().compareTo(BigDecimal.ZERO) <= 0)
            throw new AppException("El precio debe ser mayor a 0", HttpStatus.BAD_REQUEST);
        if (req.getVigenciaDias() < 1)
            throw new AppException("La vigencia debe ser al menos 1 día", HttpStatus.BAD_REQUEST);
        try { TipoDisciplina.valueOf(req.getDisciplina()); }
        catch (Exception e) { throw new AppException("Disciplina inválida", HttpStatus.BAD_REQUEST); }
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

    // Suma N días hábiles (Lun–Vie) a partir de una fecha
    private LocalDate sumarDiasHabiles(LocalDate inicio, int diasHabiles) {
        LocalDate fecha = inicio;
        int contados = 0;
        while (contados < diasHabiles) {
            fecha = fecha.plusDays(1);
            DayOfWeek dia = fecha.getDayOfWeek();
            if (dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY) {
                contados++;
            }
        }
        return fecha;
    }

    private CreditosDTO toCreditosDTO(Usuario u) {
        return CreditosDTO.builder()
                .creditosCycling(u.getCreditosCycling())
                .creditosCyclingVencen(u.getCreditosCyclingVencen())
                .creditosPilates(u.getCreditosPilates())
                .creditosPilatesVencen(u.getCreditosPilatesVencen())
                .build();
    }

    private PaqueteDTO toPaqueteDTO(Paquete p) {
        return PaqueteDTO.builder()
                .id(p.getId())
                .nombre(p.getNombre())
                .numClases(p.getNumClases())
                .precio(p.getPrecio().doubleValue())
                .disciplina(p.getDisciplina().name())
                .vigenciaDias(p.getVigenciaDias())
                .esMensual(p.isEsMensual())
                .activo(p.isActivo())
                .build();
    }

    // ── Historial de pagos (admin) ─────────────────────────────
    public Map<String, Object> getHistorial(String periodo) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime desde = switch (periodo) {
            case "semana" -> ahora.with(DayOfWeek.MONDAY).toLocalDate().atStartOfDay();
            case "mes"    -> ahora.withDayOfMonth(1).toLocalDate().atStartOfDay();
            default       -> ahora.toLocalDate().atStartOfDay();
        };

        List<Pago> pagos = pagoRepository.findByFechaPagoBetweenOrderByFechaPagoDesc(desde, ahora);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<PagoHistorialDTO> historial = pagos.stream()
                .map(p -> PagoHistorialDTO.builder()
                        .id(p.getId())
                        .usuarioNombre(p.getUsuarioNombre())
                        .usuarioEmail(p.getUsuarioEmail())
                        .paqueteNombre(p.getPaqueteNombre())
                        .disciplina(p.getDisciplina().name())
                        .monto(p.getMonto().doubleValue())
                        .metodo(p.getMetodo())
                        .transaccionId(p.getTransaccionId())
                        .clasesAgregadas(p.getClasesAgregadas())
                        .fechaPago(p.getFechaPago().format(fmt))
                        .build())
                .collect(Collectors.toList());

        double totalCobrado = pagos.stream().mapToDouble(p -> p.getMonto().doubleValue()).sum();
        int totalClases = pagos.stream().mapToInt(Pago::getClasesAgregadas).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("historial", historial);
        result.put("totalCobrado", totalCobrado);
        result.put("totalClases", totalClases);
        result.put("totalTransacciones", pagos.size());
        return result;
    }

    private String cobrarOpenpay(String tokenId, String deviceSessionId,
                                  BigDecimal monto, String descripcion, String orderId,
                                  Usuario usuario) {
        String base = sandbox
                ? "https://sandbox-api.openpay.mx/v1/"
                : "https://api.openpay.mx/v1/";

        String credentials = Base64.getEncoder()
                .encodeToString((privateKey + ":").getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + credentials);

        Map<String, Object> customer = new HashMap<>();
        customer.put("name", usuario.getNombre());
        customer.put("last_name", usuario.getApellido());
        customer.put("email", usuario.getEmail());
        if (usuario.getTelefono() != null && !usuario.getTelefono().isBlank())
            customer.put("phone_number", usuario.getTelefono());

        Map<String, Object> body = new HashMap<>();
        body.put("method", "card");
        body.put("source_id", tokenId);
        body.put("device_session_id", deviceSessionId);
        body.put("amount", monto);
        body.put("currency", "MXN");
        body.put("description", descripcion);
        body.put("order_id", orderId);
        body.put("customer", customer);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    base + merchantId + "/charges",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<?, ?> resp = response.getBody();
            if (resp == null) throw new AppException("Sin respuesta de OpenPay", HttpStatus.BAD_GATEWAY);
            return String.valueOf(resp.get("id"));
        } catch (HttpClientErrorException e) {
            throw new AppException("Error al procesar el pago: " + e.getResponseBodyAsString(), HttpStatus.PAYMENT_REQUIRED);
        }
    }
}
