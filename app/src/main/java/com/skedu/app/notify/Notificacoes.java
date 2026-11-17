package com.skedu.app.notify;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.skedu.app.MainActivity;
import com.skedu.app.R;
import com.skedu.app.ui.common.Nomes;

/** Canais e notificações do app (lembretes de entrega, nova tarefa e teste). */
public final class Notificacoes {

    public static final String CANAL_LEMBRETES = "lembretes";
    public static final String CANAL_NOVAS = "novas_tarefas";

    // extras usados para abrir a tarefa ao tocar na notificação / marcar como feita
    public static final String EXTRA_GROUP_ID = "skedu.groupId";
    public static final String EXTRA_TASK_ID = "skedu.taskId";
    public static final String EXTRA_TIPO = "skedu.tipo";
    public static final String EXTRA_NOTIF_ID = "skedu.notifId";

    private Notificacoes() {
    }

    // ------------------------------------------------------------------
    // Canais e permissão
    // ------------------------------------------------------------------

    /** Cria os canais (Android 8+). Pode ser chamado várias vezes. */
    public static void criarCanais(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);

        NotificationChannel lembretes = new NotificationChannel(CANAL_LEMBRETES,
                ctx.getString(R.string.canal_lembretes_nome), NotificationManager.IMPORTANCE_HIGH);
        lembretes.setDescription(ctx.getString(R.string.canal_lembretes_desc));

        NotificationChannel novas = new NotificationChannel(CANAL_NOVAS,
                ctx.getString(R.string.canal_novas_nome), NotificationManager.IMPORTANCE_DEFAULT);
        novas.setDescription(ctx.getString(R.string.canal_novas_desc));

        nm.createNotificationChannel(lembretes);
        nm.createNotificationChannel(novas);
    }

    /** O app pode mostrar notificações? (permissão no Android 13+ e chave geral ligada) */
    public static boolean permitido(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    // ------------------------------------------------------------------
    // Notificações
    // ------------------------------------------------------------------

    /** Lembrete de uma tarefa pendente. {@code dias}: 0 = hoje, negativo = atrasada. */
    public static void lembrete(Context ctx, TarefaLocal t, long dias) {
        String situacao;
        if (dias < 0) {
            int atraso = (int) -dias;
            situacao = ctx.getResources().getQuantityString(R.plurals.lembrete_atrasada, atraso, atraso);
        } else {
            String quando;
            if (dias == 0) {
                quando = ctx.getString(R.string.prazo_hoje);
            } else if (dias == 1) {
                quando = ctx.getString(R.string.prazo_amanha);
            } else {
                quando = ctx.getResources().getQuantityString(R.plurals.prazo_em_dias, (int) dias, (int) dias);
            }
            situacao = ctx.getString(R.string.lembrete_entrega, quando);
        }
        String texto = t.grupoNome == null || t.grupoNome.isEmpty()
                ? situacao : ctx.getString(R.string.prazo_completo, situacao, t.grupoNome);

        int id = idDe(t.groupId, t.id);
        NotificationCompat.Builder b = base(ctx, CANAL_LEMBRETES, t.titulo, texto)
                .setContentIntent(abrirTarefa(ctx, t.groupId, t.id, id))
                .addAction(0, ctx.getString(R.string.acao_marcar_feita),
                        marcarFeita(ctx, t.groupId, t.id, t.tipo, id));
        publicar(ctx, id, b);
    }

    /** Aviso de que outra pessoa criou uma tarefa no grupo. */
    public static void novaTarefa(Context ctx, String groupId, String taskId, String autorNome,
                                  String titulo, String grupoNome) {
        String tituloNotif = ctx.getString(R.string.nova_tarefa_titulo, Nomes.curto(autorNome));
        String texto = grupoNome == null || grupoNome.isEmpty()
                ? titulo : ctx.getString(R.string.prazo_completo, titulo, grupoNome);

        int id = idDe(groupId, taskId) ^ 0x4E4F5641; // diferente do lembrete da mesma tarefa
        NotificationCompat.Builder b = base(ctx, CANAL_NOVAS, tituloNotif, texto)
                .setContentIntent(abrirTarefa(ctx, groupId, taskId, id));
        publicar(ctx, id, b);
    }

    /** Notificação de teste (botão em Ajustes). */
    public static void teste(Context ctx) {
        NotificationCompat.Builder b = base(ctx, CANAL_LEMBRETES,
                ctx.getString(R.string.teste_titulo), ctx.getString(R.string.teste_texto))
                .setContentIntent(PendingIntent.getActivity(ctx, 1, new Intent(ctx, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        publicar(ctx, 1, b);
    }

    // ------------------------------------------------------------------

    private static NotificationCompat.Builder base(Context ctx, String canal, String titulo, String texto) {
        return new NotificationCompat.Builder(ctx, canal)
                .setSmallIcon(R.drawable.ic_stat_skedu)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setColor(ctx.getColor(R.color.skedu_primary))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
    }

    @SuppressLint("MissingPermission") // a permissão é conferida em permitido()
    private static void publicar(Context ctx, int id, NotificationCompat.Builder b) {
        if (!permitido(ctx)) return;
        NotificationManagerCompat.from(ctx).notify(id, b.build());
    }

    private static PendingIntent abrirTarefa(Context ctx, String groupId, String taskId, int codigo) {
        Intent intent = new Intent(ctx, MainActivity.class)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_TASK_ID, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, codigo, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent marcarFeita(Context ctx, String groupId, String taskId, String tipo, int notifId) {
        Intent intent = new Intent(ctx, MarcarFeitaReceiver.class)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TIPO, tipo)
                .putExtra(EXTRA_NOTIF_ID, notifId);
        // código diferente do da abertura, senão um PendingIntent sobrescreve o outro
        return PendingIntent.getBroadcast(ctx, notifId + 1, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static int idDe(String groupId, String taskId) {
        return (groupId + "/" + taskId).hashCode();
    }
}
