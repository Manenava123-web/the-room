package com.theroom.backend.service;

import com.theroom.backend.entity.Paquete;
import com.theroom.backend.entity.Usuario;
import com.theroom.backend.enums.TipoDisciplina;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Cálculo unificado de créditos y vigencia.
 * La vigencia corre en días hábiles (lun–vie) desde la fecha de compra (zona CDMX).
 */
@Component
public class CreditosService {

    public static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");

    /** Vigencia del paquete mensual: 20 días hábiles (lun–vie) desde la compra. */
    public static final int VIGENCIA_MENSUAL_DIAS_HABILES = 20;

    public LocalDate hoyMexico() {
        return LocalDate.now(ZONA_MX);
    }

    public void aplicarCreditos(Usuario usuario, Paquete paquete) {
        LocalDate fechaCompra = hoyMexico();
        int vigenciaHabiles = vigenciaHabilesDePaquete(paquete);
        if (paquete.getDisciplina() == TipoDisciplina.CYCLING) {
            aplicarDisciplina(
                    usuario.getCreditosCycling(),
                    usuario.getCreditosCyclingVencen(),
                    paquete.getNumClases(),
                    vigenciaHabiles,
                    fechaCompra,
                    usuario::setCreditosCycling,
                    usuario::setCreditosCyclingVencen);
        } else {
            aplicarDisciplina(
                    usuario.getCreditosPilates(),
                    usuario.getCreditosPilatesVencen(),
                    paquete.getNumClases(),
                    vigenciaHabiles,
                    fechaCompra,
                    usuario::setCreditosPilates,
                    usuario::setCreditosPilatesVencen);
        }
    }

    /**
     * Paquete mensual → siempre 20 días hábiles.
     * Otros paquetes → {@code vigenciaDias} del catálogo (también en días hábiles).
     */
    public int vigenciaHabilesDePaquete(Paquete paquete) {
        if (paquete.isEsMensual()) {
            return VIGENCIA_MENSUAL_DIAS_HABILES;
        }
        return paquete.getVigenciaDias();
    }

    /**
     * Suma N días hábiles (lun–vie) a partir de {@code inicio}.
     * El día {@code inicio} no cuenta; el conteo empieza al día siguiente.
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
            int vigenciaDiasHabiles,
            LocalDate fechaCompra,
            java.util.function.IntConsumer setCreditos,
            java.util.function.Consumer<LocalDate> setVencimiento) {

        boolean vigente = vencimientoActual != null && !vencimientoActual.isBefore(fechaCompra);
        setCreditos.accept(vigente ? creditosActuales + clasesPaquete : clasesPaquete);

        // Cada compra fija la vigencia desde la fecha de pago (días hábiles lun–vie)
        setVencimiento.accept(sumarDiasHabiles(fechaCompra, vigenciaDiasHabiles));
    }
}
