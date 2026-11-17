package com.skedu.app;

import android.app.Application;

import com.skedu.app.notify.Agendador;
import com.skedu.app.notify.Notificacoes;

/** Roda uma vez ao abrir o app: prepara os canais de notificação e o trabalho de lembretes. */
public class SkeduApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Notificacoes.criarCanais(this);
        Agendador.reagendar(this);
    }
}
