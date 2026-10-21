package com.theroom.backend.service;

import com.theroom.backend.entity.Paquete;
import com.theroom.backend.entity.Usuario;
import com.theroom.backend.enums.TipoDisciplina;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Cálculo unificado de créditos y vigencia (zona CDMX).
 * <ul>
 *   <li>Indoor Cycling mensual: mismo día del mes siguiente (ej. 5 oct → 5 nov).</li>
 *   <li>Pilates mensual: 25 días hábiles (lun–vie) desde la compra.</li>
 *   <li>Demás paquetes: días hábiles según {@code vigenciaDias} del catálogo.</li>
 * </ul>
 */
@Component
public class CreditosService {

    public static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");

    /** Vigencia del plan mensual de Pilates (días hábiles lun–vie). */
    public static final int VIGENCIA_PILATES_MENSUAL_HABILES = 25;

    public LocalDate hoyMexico() {
        return LocalDate.now(ZONA_MX);
    }

    public void aplicarCreditos(Usuario usuario, Paquete paquete) {
        LocalDate fechaCompra = hoyMexico();
        if (paquete.getDisciplina() == TipoDisciplina.CYCLING) {
            aplicarDisciplina(
                    usuario.getCreditosCycling(),
                    usuario.getCreditosCyclingVencen(),
                    paquete.getNumClases(),
                    paquete,
                    fechaCompra,
                    usuario::setCreditosCycling,
                    usuario::setCreditosCyclingVencen);
        } else {
            aplicarDisciplina(
                    usuario.getCreditosPilates(),
                    usuario.getCreditosPilatesVencen(),
                    paquete.getNumClases(),
                    paquete,
                    fechaCompra,
                    usuario::setCreditosPilates,
                    usuario::setCreditosPilatesVencen);
        }
    }

    /**
     * Vencimiento del paquete mensual: mismo día del mes siguiente a la compra.
     */
    public LocalDate vencimientoMensualCalendario(LocalDate fechaCompra) {
        return fechaCompra.plusMonths(1);
    }

    public LocalDate calcularVencimiento(Paquete paquete, LocalDate fechaCompra) {
        if (paquete.getDisciplina() == TipoDisciplina.PILATES && paquete.isEsMensual()) {
            return sumarDiasHabiles(fechaCompra, VIGENCIA_PILATES_MENSUAL_HABILES);
        }
        if (paquete.isEsMensual()) {
            return vencimientoMensualCalendario(fechaCompra);
        }
        return sumarDiasHabiles(fechaCompra, paquete.getVigenciaDias());
    }

    /**
     * Suma N días hábiles (lun–vie). El día {@code inicio} no cuenta; el conteo empieza al día siguiente.
     */
    public LocalDate sumarDiasHabiles(LocalDate inicio, int diasHabiles) {
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

    private void aplicarDisciplina(
            int creditosActuales,
            LocalDate vencimientoActual,
            int clasesPaquete,
            Paquete paquete,
            LocalDate fechaCompra,
            java.util.function.IntConsumer setCreditos,
            java.util.function.Consumer<LocalDate> setVencimiento) {

        boolean vigente = vencimientoActual != null && !vencimientoActual.isBefore(fechaCompra);
        setCreditos.accept(vigente ? creditosActuales + clasesPaquete : clasesPaquete);
        setVencimiento.accept(calcularVencimiento(paquete, fechaCompra));
    }
}
