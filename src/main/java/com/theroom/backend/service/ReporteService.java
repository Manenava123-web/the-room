package com.theroom.backend.service;

import com.theroom.backend.dto.*;
import com.theroom.backend.entity.Pago;
import com.theroom.backend.entity.Reservacion;
import com.theroom.backend.entity.Usuario;
import com.theroom.backend.enums.EstadoReservacion;
import com.theroom.backend.enums.TipoClase;
import com.theroom.backend.enums.TipoDisciplina;
import com.theroom.backend.repository.PagoRepository;
import com.theroom.backend.repository.ReservacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter FMT_PAGO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PagoRepository pagoRepository;
    private final ReservacionRepository reservacionRepository;

    @Transactional(readOnly = true)
    public ClientesMesReporteDTO getClientesActivosPagadoresMes(Integer anio, Integer mes, String disciplina) {
        YearMonth ym = resolverMes(anio, mes);
        LocalDateTime desde = ym.atDay(1).atStartOfDay();
        LocalDateTime hasta = finDeMes(ym);

        List<Pago> pagos = pagoRepository.findPagosClientesActivosEnPeriodo(desde, hasta);
        Map<Long, List<Pago>> pagosPorUsuario = pagos.stream()
                .collect(Collectors.groupingBy(p -> p.getUsuario().getId(), LinkedHashMap::new, Collectors.toList()));

        TipoDisciplina filtroDisc = parseDisciplina(disciplina);
        if (filtroDisc != null) {
            pagosPorUsuario.entrySet().removeIf(e ->
                    e.getValue().stream().noneMatch(p -> p.getDisciplina() == filtroDisc));
        }

        List<Long> usuarioIds = new ArrayList<>(pagosPorUsuario.keySet());
        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate historialDesde = hoy.minusMonths(3);

        Map<Long, List<Reservacion>> proximasPorUsuario = usuarioIds.isEmpty()
                ? Map.of()
                : reservacionRepository.findConfirmadasFuturasByUsuarioIds(usuarioIds, hoy).stream()
                        .collect(Collectors.groupingBy(r -> r.getUsuario().getId()));

        Map<Long, List<Reservacion>> historialPorUsuario = usuarioIds.isEmpty()
                ? Map.of()
                : reservacionRepository.findConfirmadasByUsuarioIdsEnRango(usuarioIds, historialDesde, hoy).stream()
                        .collect(Collectors.groupingBy(r -> r.getUsuario().getId()));

        List<ClienteActivoMesDTO> clientes = pagosPorUsuario.entrySet().stream()
                .map(e -> construirCliente(
                        e.getValue().get(0).getUsuario(),
                        e.getValue(),
                        proximasPorUsuario.getOrDefault(e.getKey(), List.of()),
                        historialPorUsuario.getOrDefault(e.getKey(), List.of()),
                        filtroDisc))
                .sorted(Comparator.comparing(c -> c.getNombre() + " " + c.getApellido(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        double totalCobrado = clientes.stream()
                .flatMap(c -> c.getPagosMes().stream())
                .mapToDouble(ClienteMesPagoDTO::getMonto)
                .sum();

        int totalCycling = (int) clientes.stream()
                .filter(c -> c.getPagosMes().stream().anyMatch(p -> "CYCLING".equals(p.getDisciplina())))
                .count();
        int totalPilates = (int) clientes.stream()
                .filter(c -> c.getPagosMes().stream().anyMatch(p -> "PILATES".equals(p.getDisciplina())))
                .count();

        List<DemandaHorarioDTO> demanda = calcularDemanda(clientes);

        String mesLabel = ym.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "MX"))
                + " " + ym.getYear();

        return ClientesMesReporteDTO.builder()
                .anio(ym.getYear())
                .mes(ym.getMonthValue())
                .mesLabel(mesLabel)
                .totalClientes(clientes.size())
                .totalCycling(totalCycling)
                .totalPilates(totalPilates)
                .totalCobrado(totalCobrado)
                .demandaHorarios(demanda)
                .clientes(clientes)
                .build();
    }

    private ClienteActivoMesDTO construirCliente(
            Usuario u,
            List<Pago> pagosUsuario,
            List<Reservacion> proximas,
            List<Reservacion> historial,
            TipoDisciplina filtroDisc) {

        List<ClienteMesPagoDTO> pagosMes = pagosUsuario.stream()
                .sorted(Comparator.comparing(Pago::getFechaPago).reversed())
                .map(p -> ClienteMesPagoDTO.builder()
                        .fechaPago(p.getFechaPago().format(FMT_PAGO))
                        .paqueteNombre(p.getPaqueteNombre())
                        .disciplina(p.getDisciplina().name())
                        .esMensual(p.getPaquete() != null && p.getPaquete().isEsMensual())
                        .monto(p.getMonto().doubleValue())
                        .metodo(p.getMetodo())
                        .build())
                .toList();

        List<Reservacion> proximasFiltradas = filtrarReservasPorDisciplina(proximas, filtroDisc);
        List<Reservacion> historialFiltrado = filtrarReservasPorDisciplina(historial, filtroDisc);

        List<ClienteReservaResumenDTO> reservasProximas = proximasFiltradas.stream()
                .sorted(Comparator.comparing(Reservacion::getFecha).thenComparing(r -> r.getClase().getHora()))
                .map(this::toReservaResumen)
                .toList();

        List<HorarioPreferenciaDTO> habituales = calcularHabituales(historialFiltrado, proximasFiltradas);

        return ClienteActivoMesDTO.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .email(u.getEmail())
                .telefono(u.getTelefono())
                .pagosMes(pagosMes)
                .creditosCycling(u.getCreditosCycling())
                .creditosCyclingVencen(u.getCreditosCyclingVencen())
                .creditosPilates(u.getCreditosPilates())
                .creditosPilatesVencen(u.getCreditosPilatesVencen())
                .reservasProximas(reservasProximas)
                .horariosHabituales(habituales)
                .build();
    }

    private List<Reservacion> filtrarReservasPorDisciplina(List<Reservacion> reservas, TipoDisciplina filtro) {
        if (filtro == null) return reservas;
        TipoClase tipo = filtro == TipoDisciplina.CYCLING ? TipoClase.SPINNING : TipoClase.PILATES;
        return reservas.stream().filter(r -> r.getClase().getTipo() == tipo).toList();
    }

    private ClienteReservaResumenDTO toReservaResumen(Reservacion r) {
        return ClienteReservaResumenDTO.builder()
                .reservacionId(r.getId())
                .fecha(r.getFecha())
                .diaSemana(r.getClase().getDiaSemana().name())
                .hora(r.getClase().getHora())
                .tipoClase(r.getClase().getTipo().name())
                .lugarNumero(r.getLugarNumero())
                .instructor(r.getClase().getInstructor() != null
                        ? r.getClase().getInstructor().getNombreCompleto() : null)
                .build();
    }

    private List<HorarioPreferenciaDTO> calcularHabituales(
            List<Reservacion> historial,
            List<Reservacion> proximas) {

        Map<String, Integer> conteo = new HashMap<>();

        for (Reservacion r : historial) {
            if (r.getEstado() != EstadoReservacion.CONFIRMADA) continue;
            conteo.merge(claveHorario(r), 1, Integer::sum);
        }
        for (Reservacion r : proximas) {
            conteo.merge(claveHorario(r), 2, Integer::sum);
        }

        return conteo.entrySet().stream()
                .map(e -> {
                    String[] partes = e.getKey().split("\\|");
                    return HorarioPreferenciaDTO.builder()
                            .diaSemana(partes[0])
                            .hora(partes[1])
                            .tipoClase(partes[2])
                            .veces(e.getValue())
                            .build();
                })
                .sorted(Comparator.comparingInt(HorarioPreferenciaDTO::getVeces).reversed()
                        .thenComparing(HorarioPreferenciaDTO::getDiaSemana)
                        .thenComparing(HorarioPreferenciaDTO::getHora))
                .limit(5)
                .toList();
    }

    private List<DemandaHorarioDTO> calcularDemanda(List<ClienteActivoMesDTO> clientes) {
        Map<String, Set<Long>> conReserva = new HashMap<>();
        Map<String, Set<Long>> habituales = new HashMap<>();

        for (ClienteActivoMesDTO c : clientes) {
            for (ClienteReservaResumenDTO r : c.getReservasProximas()) {
                conReserva.computeIfAbsent(claveHorario(r.getDiaSemana(), r.getHora(), r.getTipoClase()), k -> new HashSet<>())
                        .add(c.getId());
            }
            for (HorarioPreferenciaDTO h : c.getHorariosHabituales()) {
                habituales.computeIfAbsent(claveHorario(h.getDiaSemana(), h.getHora(), h.getTipoClase()), k -> new HashSet<>())
                        .add(c.getId());
            }
        }

        Set<String> todasClaves = new HashSet<>();
        todasClaves.addAll(conReserva.keySet());
        todasClaves.addAll(habituales.keySet());

        return todasClaves.stream()
                .map(clave -> {
                    String[] p = clave.split("\\|");
                    int res = conReserva.getOrDefault(clave, Set.of()).size();
                    int hab = habituales.getOrDefault(clave, Set.of()).size();
                    return DemandaHorarioDTO.builder()
                            .diaSemana(p[0])
                            .hora(p[1])
                            .tipoClase(p[2])
                            .clientesConReserva(res)
                            .clientesHabituales(hab)
                            .indiceDemanda(res * 3 + hab)
                            .build();
                })
                .sorted(Comparator.comparingInt(DemandaHorarioDTO::getIndiceDemanda).reversed()
                        .thenComparing(DemandaHorarioDTO::getDiaSemana)
                        .thenComparing(DemandaHorarioDTO::getHora))
                .toList();
    }

    private String claveHorario(Reservacion r) {
        return claveHorario(
                r.getClase().getDiaSemana().name(),
                r.getClase().getHora(),
                r.getClase().getTipo().name());
    }

    private String claveHorario(String dia, String hora, String tipo) {
        return dia + "|" + hora + "|" + tipo;
    }

    private YearMonth resolverMes(Integer anio, Integer mes) {
        LocalDate hoy = LocalDate.now(ZONA);
        int y = anio != null ? anio : hoy.getYear();
        int m = mes != null ? mes : hoy.getMonthValue();
        return YearMonth.of(y, m);
    }

    private LocalDateTime finDeMes(YearMonth ym) {
        YearMonth actual = YearMonth.now(ZONA);
        if (ym.equals(actual)) {
            return LocalDateTime.now(ZONA);
        }
        return ym.atEndOfMonth().atTime(23, 59, 59);
    }

    private TipoDisciplina parseDisciplina(String disciplina) {
        if (disciplina == null || disciplina.isBlank() || "ALL".equalsIgnoreCase(disciplina)) {
            return null;
        }
        return TipoDisciplina.valueOf(disciplina.toUpperCase());
    }
}
