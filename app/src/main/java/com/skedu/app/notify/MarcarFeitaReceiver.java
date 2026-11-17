package com.skedu.app.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.skedu.app.R;
import com.skedu.app.data.GroupRepository;
import com.skedu.app.data.TaskRepository;
import com.skedu.app.data.UserRepository;
import com.skedu.app.model.Task;

/** Botão "Marcar como feita" da notificação: marca sem abrir o app. */
public class MarcarFeitaReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String groupId = intent.getStringExtra(Notificacoes.EXTRA_GROUP_ID);
        String taskId = intent.getStringExtra(Notificacoes.EXTRA_TASK_ID);
        int notifId = intent.getIntExtra(Notificacoes.EXTRA_NOTIF_ID, 0);
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (groupId == null || taskId == null || usuario == null) return;

        Task t = new Task();
        t.groupId = groupId;
        t.id = taskId;
        t.tipo = intent.getStringExtra(Notificacoes.EXTRA_TIPO); // define como a conclusão é gravada

        // o Firestore responde de forma assíncrona: goAsync mantém o receiver vivo até lá
        PendingResult pendente = goAsync();
        new TaskRepository().alternarConclusao(t, usuario.getUid(), UserRepository.nomeDe(usuario), true,
                new GroupRepository.Callback<Void>() {
                    @Override
                    public void onSucesso(Void r) {
                        NotificationManagerCompat.from(ctx).cancel(notifId);
                        new LembreteCache(ctx).remover(groupId, taskId);
                        Toast.makeText(ctx, R.string.toast_marcada_feita, Toast.LENGTH_SHORT).show();
                        pendente.finish();
                    }

                    @Override
                    public void onErro(String mensagem) {
                        Toast.makeText(ctx, mensagem, Toast.LENGTH_LONG).show();
                        pendente.finish();
                    }
                });
    }
}
