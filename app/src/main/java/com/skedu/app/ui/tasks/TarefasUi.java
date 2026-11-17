package com.skedu.app.ui.tasks;

import android.content.Context;
import android.view.View;

import com.google.firebase.Timestamp;
import com.skedu.app.R;
import com.skedu.app.databinding.ViewEstadoVazioBinding;
import com.skedu.app.model.Task;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Regras de apresentação de tarefas compartilhadas pelas telas (filtros, ordem, prazos, cores). */
public final class TarefasUi {

    public enum Filtro {PENDENTES, URGENTES, FEITAS, TODAS}

    /** "Urgente" = prioridade urgente OU vence em até 3 dias (inclui atrasadas). */
    public static final int DIAS_URGENCIA = 3;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private TarefasUi() {
    }

    // ------------------------------------------------------------------
    // Filtro e ordem
    // ------------------------------------------------------------------

    public static List<Task> filtrar(List<Task> todas, Filtro filtro, String uid) {
        List<Task> saida = new ArrayList<>();
        for (Task t : todas) {
            boolean feita = t.concluidaPeloUsuario(uid);
            boolean entra;
            switch (filtro) {
                case PENDENTES:
                    entra = !feita;
                    break;
                case URGENTES:
                    entra = !feita && urgente(t);
                    break;
                case FEITAS:
                    entra = feita;
                    break;
                default:
                    entra = true;
            }
            if (entra) saida.add(t);
        }
        ordenar(saida, uid);
        return saida;
    }

    /** Pendentes primeiro, do prazo mais próximo ao mais distante; as feitas ficam no fim. */
    public static void ordenar(List<Task> lista, String uid) {
        Collections.sort(lista, (a, b) -> {
            boolean fa = a.concluidaPeloUsuario(uid);
            boolean fb = b.concluidaPeloUsuario(uid);
            if (fa != fb) return fa ? 1 : -1;
            return compararPrazo(a.prazo, b.prazo);
        });
    }

    private static int compararPrazo(Timestamp a, Timestamp b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    /** Traduz o chip marcado para o filtro (if/else porque ids de recurso não são constantes). */
    public static Filtro filtroDe(int chipId) {
        if (chipId == R.id.chip_urgentes) return Filtro.URGENTES;
        if (chipId == R.id.chip_feitas) return Filtro.FEITAS;
        return Filtro.PENDENTES;
    }

    /** Mostra/esconde o estado vazio com o texto certo para o filtro atual. */
    public static void aplicarVazio(ViewEstadoVazioBinding vazio, Filtro filtro, boolean visivel) {
        vazio.getRoot().setVisibility(visivel ? View.VISIBLE : View.GONE);
        if (!visivel) return;
        switch (filtro) {
            case URGENTES:
                vazio.vazioTitulo.setText(R.string.tarefas_vazio_urgentes_titulo);
                vazio.vazioTexto.setText(R.string.tarefas_vazio_urgentes_texto);
                break;
            case FEITAS:
                vazio.vazioTitulo.setText(R.string.tarefas_vazio_feitas_titulo);
                vazio.vazioTexto.setText(R.string.tarefas_vazio_feitas_texto);
                break;
            default:
                vazio.vazioTitulo.setText(R.string.tarefas_vazio_pendentes_titulo);
                vazio.vazioTexto.setText(R.string.tarefas_vazio_pendentes_texto);
        }
    }

    // ------------------------------------------------------------------
    // Prazos
    // ------------------------------------------------------------------

    /** Dias até o prazo (negativo = atrasada). Sem prazo conta como distante. */
    public static long diasAte(Task t) {
        LocalDate d = t.dataPrazo();
        if (d == null) return Long.MAX_VALUE;
        return ChronoUnit.DAYS.between(LocalDate.now(), d);
    }

    public static boolean urgente(Task t) {
        return Task.URGENTE.equals(t.prioridade) || diasAte(t) <= DIAS_URGENCIA;
    }

    public static boolean atrasada(Task t, boolean feita) {
        return !feita && diasAte(t) < 0;
    }

    public static String formatarData(LocalDate d) {
        return d == null ? "" : DATA.format(d);
    }

    public static String formatarDataHora(Timestamp ts) {
        if (ts == null) return "";
        return DATA_HORA.format(ts.toDate().toInstant().atZone(java.time.ZoneId.systemDefault()));
    }

    /** Ex.: "850 KB", "2,3 MB". */
    public static String formatarTamanho(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024.0) + " KB";
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Ex.: "Entrega 24/09/2026 · em 3 dias". Tarefa já feita mostra só a data. */
    public static String textoPrazo(Context ctx, Task t, boolean feita) {
        String data = ctx.getString(R.string.prazo_data, formatarData(t.dataPrazo()));
        if (feita || t.dataPrazo() == null) return data;

        long dias = diasAte(t);
        String situacao;
        if (dias == 0) {
            situacao = ctx.getString(R.string.prazo_hoje);
        } else if (dias == 1) {
            situacao = ctx.getString(R.string.prazo_amanha);
        } else if (dias > 1) {
            situacao = ctx.getResources().getQuantityString(R.plurals.prazo_em_dias, (int) dias, (int) dias);
        } else {
            int atraso = (int) -dias;
            situacao = ctx.getResources().getQuantityString(R.plurals.prazo_atrasada_dias, atraso, atraso);
        }
        return ctx.getString(R.string.prazo_completo, data, situacao);
    }

    // ------------------------------------------------------------------
    // Prioridade
    // ------------------------------------------------------------------

    public static String rotuloPrioridade(Context ctx, String prioridade) {
        if (Task.URGENTE.equals(prioridade)) return ctx.getString(R.string.prioridade_urgente);
        if (Task.IMPORTANTE.equals(prioridade)) return ctx.getString(R.string.prioridade_importante);
        return ctx.getString(R.string.prioridade_normal);
    }

    /** Cor do texto do rótulo de prioridade. */
    public static int corPrioridade(Context ctx, String prioridade) {
        if (Task.URGENTE.equals(prioridade)) return ctx.getColor(R.color.skedu_priority_urgent);
        if (Task.IMPORTANTE.equals(prioridade)) return ctx.getColor(R.color.skedu_priority_important);
        return ctx.getColor(R.color.skedu_priority_normal);
    }

    /** Cor de fundo do rótulo de prioridade. */
    public static int fundoPrioridade(Context ctx, String prioridade) {
        if (Task.URGENTE.equals(prioridade)) return ctx.getColor(R.color.skedu_priority_urgent_container);
        if (Task.IMPORTANTE.equals(prioridade)) return ctx.getColor(R.color.skedu_priority_important_container);
        return ctx.getColor(R.color.skedu_priority_normal_container);
    }
}
