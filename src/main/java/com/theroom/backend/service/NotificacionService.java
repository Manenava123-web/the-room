package com.theroom.backend.service;

import com.theroom.backend.entity.Paquete;
import com.theroom.backend.entity.Usuario;
import com.theroom.backend.enums.TipoDisciplina;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacionService {

    private final RestTemplate restTemplate;

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from:The Room Studio <onboarding@resend.dev>}")
    private String mailFrom;

    @Value("${notificaciones.studio-nombre:The Room Studio}")
    private String studioNombre;

    @Value("${app.frontend-url:http://localhost:8080}")
    private String frontendUrl;

    private static final String RESEND_URL = "https://api.resend.com/emails";

    public void enviarConfirmacion(Usuario usuario, Paquete paquete,
                                   String transaccionId, String metodo) {
        enviar(
            usuario.getEmail(),
            "¡Pago confirmado! — " + paquete.getNombre() + " · " + studioNombre,
            buildHtml(usuario, paquete, transaccionId, metodo),
            "confirmación de pago"
        );
    }

    public void enviarResetPassword(Usuario usuario, String token) {
        String link = frontendUrl + "/the-room.html?token=" + token;
        enviar(
            usuario.getEmail(),
            "Recuperación de contraseña — " + studioNombre,
            buildResetHtml(usuario, link),
            "recuperación de contraseña"
        );
    }

    private void enviar(String to, String subject, String html, String tipo) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("resend.api-key no configurado — email de {} no enviado a {}", tipo, to);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                "from",    mailFrom,
                "to",      List.of(to),
                "subject", subject,
                "html",    html
            );

            ResponseEntity<String> resp = restTemplate.exchange(
                RESEND_URL, HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
            );

            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("Email de {} enviado a {}", tipo, to);
            } else {
                log.error("Error al enviar email de {} a {}: {}", tipo, to, resp.getBody());
            }
        } catch (Exception e) {
            log.error("Error al enviar email de {} a {}: {}", tipo, to, e.getMessage());
        }
    }

    private String buildResetHtml(Usuario usuario, String link) {
        return """
            <!DOCTYPE html>
            <html><body style="font-family:Arial,sans-serif;background:#f5f0ea;margin:0;padding:24px">
            <div style="max-width:480px;margin:0 auto;background:#ffffff;border-top:4px solid #1a1a1a">
              <div style="padding:40px">
                <p style="font-size:10px;letter-spacing:.22em;color:#aaa;text-transform:uppercase;margin:0 0 4px">%s</p>
                <h1 style="font-family:Georgia,serif;font-size:28px;color:#1a1a1a;margin:0 0 16px;font-weight:400">Recupera tu contraseña</h1>
                <p style="font-size:14px;color:#555;margin:0 0 28px">
                  Hola <strong>%s</strong>, recibimos una solicitud para restablecer tu contraseña.<br>
                  Haz clic en el botón para continuar. El enlace expira en <strong>1 hora</strong>.
                </p>
                <a href="%s" style="display:inline-block;background:#1a1a1a;color:#ffffff;text-decoration:none;padding:14px 28px;font-size:14px;letter-spacing:.05em">
                  Cambiar contraseña
                </a>
                <p style="font-size:12px;color:#aaa;margin:28px 0 0">
                  Si no solicitaste este cambio, ignora este mensaje. Tu contraseña actual no ha cambiado.
                </p>
                <hr style="border:none;border-top:1px solid #eee;margin:28px 0 20px"/>
                <p style="font-size:11px;color:#ccc;margin:0;text-align:center">%s · Chilapa, Guerrero</p>
              </div>
            </div>
            </body></html>
            """.formatted(studioNombre, usuario.getNombre(), link, studioNombre);
    }

    private String buildHtml(Usuario usuario, Paquete paquete,
                              String transaccionId, String metodo) {
        LocalDate vence = paquete.getDisciplina() == TipoDisciplina.CYCLING
                ? usuario.getCreditosCyclingVencen()
                : usuario.getCreditosPilatesVencen();
        String vigencia = vence != null
                ? vence.getDayOfMonth() + "/" + vence.getMonthValue() + "/" + vence.getYear()
                : paquete.getVigenciaDias() + " días";

        String precio = String.format("$%,.2f", paquete.getPrecio().doubleValue());
        String clases = paquete.getNumClases() + " clase" + (paquete.getNumClases() > 1 ? "s" : "");
        String ref    = transaccionId != null ? transaccionId : "—";

        return """
            <!DOCTYPE html>
            <html><body style="font-family:Arial,sans-serif;background:#f5f0ea;margin:0;padding:24px">
            <div style="max-width:480px;margin:0 auto;background:#ffffff;border-top:4px solid #1a1a1a">
              <div style="padding:40px">
                <p style="font-size:10px;letter-spacing:.22em;color:#aaa;text-transform:uppercase;margin:0 0 4px">%s</p>
                <h1 style="font-family:Georgia,serif;font-size:28px;color:#1a1a1a;margin:0 0 32px;font-weight:400">¡Pago confirmado!</h1>
                <div style="background:#f5f0ea;padding:20px 24px;border-left:3px solid #1a1a1a;margin-bottom:28px">
                  <p style="margin:0 0 4px;font-size:11px;color:#999;text-transform:uppercase;letter-spacing:.1em">Paquete adquirido</p>
                  <p style="margin:0;font-size:19px;color:#1a1a1a;font-weight:500">%s</p>
                  <p style="margin:6px 0 0;font-size:13px;color:#666">%s &nbsp;·&nbsp; Válidas hasta %s</p>
                </div>
                <table width="100%%" style="border-collapse:collapse;margin-bottom:28px">
                  <tr>
                    <td style="padding:10px 0;border-bottom:1px solid #eee;font-size:13px;color:#888">Método de pago</td>
                    <td style="padding:10px 0;border-bottom:1px solid #eee;font-size:13px;color:#1a1a1a;text-align:right">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:10px 0;border-bottom:1px solid #eee;font-size:13px;color:#888">Total</td>
                    <td style="padding:10px 0;border-bottom:1px solid #eee;font-size:15px;font-weight:600;color:#1a1a1a;text-align:right">%s MXN</td>
                  </tr>
                  <tr>
                    <td style="padding:10px 0;font-size:13px;color:#888">Referencia</td>
                    <td style="padding:10px 0;font-size:11px;color:#bbb;text-align:right;font-family:monospace">%s</td>
                  </tr>
                </table>
                <p style="font-size:14px;color:#555;margin:0 0 36px">
                  Hola <strong>%s</strong>, ya puedes reservar tu lugar desde la app. ¡Te esperamos!
                </p>
                <hr style="border:none;border-top:1px solid #eee;margin:0 0 20px"/>
                <p style="font-size:11px;color:#ccc;margin:0;text-align:center">%s · Chilapa, Guerrero</p>
              </div>
            </div>
            </body></html>
            """.formatted(
                studioNombre,
                paquete.getNombre(),
                clases, vigencia,
                metodo,
                precio,
                ref,
                usuario.getNombre(),
                studioNombre
            );
    }
}
