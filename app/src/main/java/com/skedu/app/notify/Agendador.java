package com.skedu.app.notify;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Liga e desliga o trabalho em segundo plano conforme as preferências. */
public final class Agendador {

    private static final String TRABALHO_PERIODICO = "skedu_lembretes_periodico";
    private static final String TRABALHO_AGORA = "skedu_lembretes_agora";

    private Agendador() {
    }

    /**
     * Garante o trabalho periódico (a cada 15 min, o mínimo do Android) se algum aviso está
     * ligado; cancela se todos estão desligados. Seguro chamar sempre que algo mudar.
     */
    public static void reagendar(Context ctx) {
        LembretePrefs prefs = new LembretePrefs(ctx);
        WorkManager wm = WorkManager.getInstance(ctx);

        if (!prefs.lembretesAtivos() && !prefs.novasTarefasAtivas()) {
            wm.cancelUniqueWork(TRABALHO_PERIODICO);
            return;
        }
        // sem restrição de rede de propósito: offline ainda dá para avisar do que já está no aparelho
        PeriodicWorkRequest pedido = new PeriodicWorkRequest.Builder(
                LembretesWorker.class, 15, TimeUnit.MINUTES).build();
        wm.enqueueUniquePeriodicWork(TRABALHO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, pedido);
    }

    /** Roda uma verificação agora (ao abrir o app e ao mudar ajustes), sem esperar os 15 min. */
    public static void verificarAgora(Context ctx) {
        LembretePrefs prefs = new LembretePrefs(ctx);
        if (!prefs.lembretesAtivos() && !prefs.novasTarefasAtivas()) return;
        WorkManager.getInstance(ctx).enqueueUniqueWork(TRABALHO_AGORA, ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(LembretesWorker.class).build());
    }
}
