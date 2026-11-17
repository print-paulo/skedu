package com.skedu.app.notify;

import java.util.ArrayList;
import java.util.List;

/**
 * Regra de QUANDO avisar de uma tarefa pendente. Lógica pura (sem Android), por isso testável.
 *
 * O dia é dividido em fases pelos dias que faltam até a entrega:
 * <pre>
 *   antecipada : de "antecedência" dias antes até 2 dias antes
 *   véspera    : 1 dia antes
 *   dia        : dia da entrega
 *   atrasada   : depois do prazo, até {@link #MAX_DIAS_ATRASO} dias
 * </pre>
 * A intensidade define quantos avisos por dia em cada fase. Os avisos de um dia saem a partir
 * da "hora base" (ex.: 9h), nunca de madrugada.
 */
public final class PlanoDeLembretes {

    public enum Intensidade {LEVE, PADRAO, INTENSO}

    /** Depois disso para de avisar de tarefa atrasada (evita aviso eterno de coisa abandonada). */
    public static final int MAX_DIAS_ATRASO = 14;

    /** Hora base permitida: de 6h a 14h (o último aviso do dia sai até 9h depois, antes de 24h). */
    public static final int HORA_BASE_MIN = 6;
    public static final int HORA_BASE_MAX = 14;

    private PlanoDeLembretes() {
    }

    /**
     * Quantos avisos devem sair HOJE para uma tarefa.
     *
     * @param dias        dias até a entrega (0 = hoje, negativo = atrasada)
     * @param antecedencia quantos dias antes começa a avisar (1 a 7)
     */
    public static int avisosPorDia(long dias, int antecedencia, Intensidade i) {
        if (dias < 0) {
            if (-dias > MAX_DIAS_ATRASO) return 0;
            return i == Intensidade.INTENSO ? 2 : 1;
        }
        if (dias == 0) {
            if (i == Intensidade.LEVE) return 1;
            return i == Intensidade.PADRAO ? 3 : 5;
        }
        if (dias == 1) {
            if (i == Intensidade.LEVE) return 1;
            return i == Intensidade.PADRAO ? 2 : 3;
        }
        if (dias > antecedencia) return 0;
        // fase antecipada (2 dias ou mais)
        if (i == Intensidade.LEVE) return dias == antecedencia ? 1 : 0;
        return i == Intensidade.PADRAO ? 1 : 2;
    }

    /** Horas do dia (0-23) em que os avisos de hoje saem. Vazio = nenhum aviso hoje. */
    public static List<Integer> horasDeHoje(long dias, int antecedencia, Intensidade i, int horaBase) {
        int base = Math.max(HORA_BASE_MIN, Math.min(HORA_BASE_MAX, horaBase));
        List<Integer> horas = new ArrayList<>();
        for (int deslocamento : deslocamentos(avisosPorDia(dias, antecedencia, i))) {
            horas.add(base + deslocamento);
        }
        return horas;
    }

    /** Distribui N avisos ao longo do dia, o último 9h depois do primeiro. */
    private static int[] deslocamentos(int quantidade) {
        switch (quantidade) {
            case 1:
                return new int[]{0};
            case 2:
                return new int[]{0, 9};
            case 3:
                return new int[]{0, 4, 9};
            case 5:
                return new int[]{0, 2, 4, 6, 9};
            default:
                return new int[]{};
        }
    }
}
