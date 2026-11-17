package com.skedu.app.notify;

import android.content.Context;
import android.content.SharedPreferences;

import com.skedu.app.notify.PlanoDeLembretes.Intensidade;

/** Preferências de lembrete deste aparelho (a tela "Ajustes" grava aqui; o worker lê). */
public class LembretePrefs {

    public static final int[] OPCOES_ANTECEDENCIA = {1, 2, 3, 5, 7};
    public static final int ANTECEDENCIA_PADRAO = 3;
    public static final int HORA_BASE_PADRAO = 9;

    private static final String ARQUIVO = "skedu_lembretes";
    private static final String K_ATIVO = "lembretes_ativos";
    private static final String K_ANTECEDENCIA = "antecedencia_dias";
    private static final String K_INTENSIDADE = "intensidade";
    private static final String K_HORA_BASE = "hora_base";
    private static final String K_NOVAS = "novas_tarefas";
    private static final String K_PEDIU_PERMISSAO = "pediu_permissao_notificacao";

    private final SharedPreferences sp;

    public LembretePrefs(Context contexto) {
        sp = contexto.getApplicationContext().getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
    }

    public boolean lembretesAtivos() {
        return sp.getBoolean(K_ATIVO, true);
    }

    public void definirLembretesAtivos(boolean ativo) {
        sp.edit().putBoolean(K_ATIVO, ativo).apply();
    }

    public int antecedenciaDias() {
        return sp.getInt(K_ANTECEDENCIA, ANTECEDENCIA_PADRAO);
    }

    public void definirAntecedenciaDias(int dias) {
        sp.edit().putInt(K_ANTECEDENCIA, dias).apply();
    }

    public Intensidade intensidade() {
        try {
            return Intensidade.valueOf(sp.getString(K_INTENSIDADE, Intensidade.PADRAO.name()));
        } catch (IllegalArgumentException e) {
            return Intensidade.PADRAO;
        }
    }

    public void definirIntensidade(Intensidade intensidade) {
        sp.edit().putString(K_INTENSIDADE, intensidade.name()).apply();
    }

    public int horaBase() {
        return sp.getInt(K_HORA_BASE, HORA_BASE_PADRAO);
    }

    public void definirHoraBase(int hora) {
        sp.edit().putInt(K_HORA_BASE, hora).apply();
    }

    public boolean novasTarefasAtivas() {
        return sp.getBoolean(K_NOVAS, true);
    }

    public void definirNovasTarefasAtivas(boolean ativo) {
        sp.edit().putBoolean(K_NOVAS, ativo).apply();
    }

    /** Já mostramos a explicação e pedimos a permissão de notificação alguma vez? */
    public boolean jaPediuPermissao() {
        return sp.getBoolean(K_PEDIU_PERMISSAO, false);
    }

    public void marcarPediuPermissao() {
        sp.edit().putBoolean(K_PEDIU_PERMISSAO, true).apply();
    }
}
