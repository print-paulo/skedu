package com.skedu.app.notify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.skedu.app.notify.PlanoDeLembretes.Intensidade;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PlanoDeLembretesTest {

    @Test
    public void padrao_antecedencia3_seguePlanoCombinado() {
        // D-3 e D-2: 1 por dia
        assertEquals(Collections.singletonList(9), PlanoDeLembretes.horasDeHoje(3, 3, Intensidade.PADRAO, 9));
        assertEquals(Collections.singletonList(9), PlanoDeLembretes.horasDeHoje(2, 3, Intensidade.PADRAO, 9));
        // D-1: 2 avisos
        assertEquals(Arrays.asList(9, 18), PlanoDeLembretes.horasDeHoje(1, 3, Intensidade.PADRAO, 9));
        // dia da entrega: 3 avisos
        assertEquals(Arrays.asList(9, 13, 18), PlanoDeLembretes.horasDeHoje(0, 3, Intensidade.PADRAO, 9));
        // atrasada: 1 por dia
        assertEquals(Collections.singletonList(9), PlanoDeLembretes.horasDeHoje(-1, 3, Intensidade.PADRAO, 9));
    }

    @Test
    public void foraDaJanela_naoAvisa() {
        assertTrue(PlanoDeLembretes.horasDeHoje(4, 3, Intensidade.PADRAO, 9).isEmpty());
        assertTrue(PlanoDeLembretes.horasDeHoje(30, 7, Intensidade.INTENSO, 9).isEmpty());
    }

    @Test
    public void atrasadaMuitoTempo_paraDeAvisar() {
        assertEquals(1, PlanoDeLembretes.avisosPorDia(-PlanoDeLembretes.MAX_DIAS_ATRASO, 3, Intensidade.PADRAO));
        assertEquals(0, PlanoDeLembretes.avisosPorDia(-PlanoDeLembretes.MAX_DIAS_ATRASO - 1, 3, Intensidade.PADRAO));
    }

    @Test
    public void leve_avisaPouco() {
        // só no primeiro dia da janela, na véspera, no dia e atrasada
        assertEquals(1, PlanoDeLembretes.avisosPorDia(3, 3, Intensidade.LEVE));
        assertEquals(0, PlanoDeLembretes.avisosPorDia(2, 3, Intensidade.LEVE));
        assertEquals(1, PlanoDeLembretes.avisosPorDia(1, 3, Intensidade.LEVE));
        assertEquals(1, PlanoDeLembretes.avisosPorDia(0, 3, Intensidade.LEVE));
        assertEquals(1, PlanoDeLembretes.avisosPorDia(-2, 3, Intensidade.LEVE));
    }

    @Test
    public void intenso_avisaMais() {
        assertEquals(2, PlanoDeLembretes.avisosPorDia(2, 3, Intensidade.INTENSO));
        assertEquals(3, PlanoDeLembretes.avisosPorDia(1, 3, Intensidade.INTENSO));
        assertEquals(5, PlanoDeLembretes.avisosPorDia(0, 3, Intensidade.INTENSO));
        assertEquals(2, PlanoDeLembretes.avisosPorDia(-1, 3, Intensidade.INTENSO));
    }

    @Test
    public void antecedenciaUm_comecaNaVespera() {
        assertEquals(0, PlanoDeLembretes.avisosPorDia(2, 1, Intensidade.PADRAO));
        assertEquals(2, PlanoDeLembretes.avisosPorDia(1, 1, Intensidade.PADRAO));
    }

    @Test
    public void horaBase_ficaDentroDoLimite_enuncaPassaDaMeiaNoite() {
        // hora base absurda é corrigida para 6..14
        assertEquals(Collections.singletonList(6), PlanoDeLembretes.horasDeHoje(2, 3, Intensidade.PADRAO, 0));
        for (int base = 0; base <= 30; base++) {
            for (int h : PlanoDeLembretes.horasDeHoje(0, 3, Intensidade.INTENSO, base)) {
                assertTrue("hora " + h, h >= PlanoDeLembretes.HORA_BASE_MIN && h <= 23);
            }
        }
    }
}
