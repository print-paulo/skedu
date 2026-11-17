package com.skedu.app.notify;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.skedu.app.model.Task;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Roda a cada ~15 minutos em segundo plano (sem servidor, tudo no aparelho).
 *
 * 1. Novas tarefas: pergunta por grupo "algo criado depois do último que vi?" (consulta barata,
 *    uma leitura por grupo) e avisa das criadas por outras pessoas.
 * 2. Lembretes de prazo: mantém uma cópia local das tarefas pendentes (atualizada no máximo
 *    de hora em hora, para caber na cota gratuita do Firestore) e dispara os avisos do plano.
 *    Antes de avisar, confere aquela tarefa no servidor: se já foi feita ou apagada, não avisa.
 */
public class LembretesWorker extends Worker {

    private static final String TAG = "LembretesWorker";
    private static final long ATUALIZAR_CACHE_A_CADA_MS = TimeUnit.HOURS.toMillis(1);
    private static final long ESPERA_MAX_SEG = 30;

    public LembretesWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) return Result.success();

        LembretePrefs prefs = new LembretePrefs(ctx);
        if (!prefs.lembretesAtivos() && !prefs.novasTarefasAtivas()) return Result.success();

        LembreteCache cache = new LembreteCache(ctx);
        cache.garantirUsuario(usuario.getUid());
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // grupos do usuário; sem conexão cai no erro e tenta de novo no próximo ciclo
        List<DocumentSnapshot> grupos;
        try {
            grupos = Tasks.await(db.collection("groups")
                    .whereArrayContains("membros", usuario.getUid()).get(), ESPERA_MAX_SEG, TimeUnit.SECONDS)
                    .getDocuments();
        } catch (Exception e) {
            Log.w(TAG, "Sem acesso aos grupos agora: " + e.getMessage());
            // mesmo offline, os lembretes já copiados no aparelho ainda podem sair
            if (prefs.lembretesAtivos()) avaliarLembretes(ctx, prefs, cache, db, usuario.getUid());
            return Result.success();
        }

        try {
            if (prefs.novasTarefasAtivas()) verificarNovas(ctx, db, cache, usuario.getUid(), grupos);
        } catch (Exception e) {
            Log.w(TAG, "Falha ao verificar novas tarefas: " + e.getMessage());
        }

        if (prefs.lembretesAtivos()) {
            try {
                atualizarCacheSeNecessario(db, cache, usuario.getUid(), grupos);
            } catch (Exception e) {
                Log.w(TAG, "Falha ao atualizar as tarefas locais: " + e.getMessage());
            }
            avaliarLembretes(ctx, prefs, cache, db, usuario.getUid());
        }
        return Result.success();
    }

    // ------------------------------------------------------------------
    // 1. Novas tarefas
    // ------------------------------------------------------------------

    private void verificarNovas(Context ctx, FirebaseFirestore db, LembreteCache cache, String uid,
                                List<DocumentSnapshot> grupos) throws Exception {
        for (DocumentSnapshot grupo : grupos) {
            String groupId = grupo.getId();
            String grupoNome = grupo.getString("nome");
            CollectionReference tarefas = db.collection("groups").document(groupId).collection("tasks");

            long base = cache.baseNovas(groupId);
            if (base < 0) {
                // primeira vez neste grupo: só marca o ponto de partida, sem avisar do passado
                QuerySnapshot ultima = Tasks.await(tarefas.orderBy("criadoEm", Query.Direction.DESCENDING)
                        .limit(1).get(), ESPERA_MAX_SEG, TimeUnit.SECONDS);
                Timestamp ts = ultima.isEmpty() ? Timestamp.now() : ultima.getDocuments().get(0).getTimestamp("criadoEm");
                cache.salvarBaseNovas(groupId, codificar(ts == null ? Timestamp.now() : ts));
                continue;
            }

            QuerySnapshot novas = Tasks.await(tarefas.whereGreaterThan("criadoEm", decodificar(base)).get(),
                    ESPERA_MAX_SEG, TimeUnit.SECONDS);
            long maior = base;
            for (DocumentSnapshot t : novas.getDocuments()) {
                Timestamp criado = t.getTimestamp("criadoEm");
                if (criado == null) continue;
                maior = Math.max(maior, codificar(criado));
                if (uid.equals(t.getString("criadoPor"))) continue; // a própria pessoa não precisa ser avisada
                Notificacoes.novaTarefa(ctx, groupId, t.getId(), t.getString("criadoPorNome"),
                        t.getString("titulo"), grupoNome);
            }
            if (maior != base) cache.salvarBaseNovas(groupId, maior);
        }
    }

    // Timestamp <-> long (segundos * 1e9 + nanos) para não perder precisão na comparação
    private static long codificar(Timestamp ts) {
        return ts.getSeconds() * 1_000_000_000L + ts.getNanoseconds();
    }

    private static Timestamp decodificar(long valor) {
        return new Timestamp(valor / 1_000_000_000L, (int) (valor % 1_000_000_000L));
    }

    // ------------------------------------------------------------------
    // 2. Lembretes de prazo
    // ------------------------------------------------------------------

    private void atualizarCacheSeNecessario(FirebaseFirestore db, LembreteCache cache, String uid,
                                            List<DocumentSnapshot> grupos) throws Exception {
        long agora = System.currentTimeMillis();
        if (agora - cache.atualizadoEm() < ATUALIZAR_CACHE_A_CADA_MS) return;

        List<TarefaLocal> pendentes = new ArrayList<>();
        for (DocumentSnapshot grupo : grupos) {
            QuerySnapshot tarefas = Tasks.await(db.collection("groups").document(grupo.getId())
                    .collection("tasks").get(), ESPERA_MAX_SEG, TimeUnit.SECONDS);
            for (DocumentSnapshot d : tarefas.getDocuments()) {
                Task t = d.toObject(Task.class);
                if (t == null || t.prazo == null || t.concluidaPeloUsuario(uid)) continue;
                pendentes.add(paraLocal(grupo.getId(), grupo.getString("nome"), d.getId(), t));
            }
        }
        cache.salvar(pendentes);
        cache.marcarAtualizadoEm(agora);
    }

    private static TarefaLocal paraLocal(String groupId, String grupoNome, String taskId, Task t) {
        TarefaLocal l = new TarefaLocal();
        l.groupId = groupId;
        l.grupoNome = grupoNome;
        l.id = taskId;
        l.titulo = t.titulo;
        l.prazoMillis = t.prazo.toDate().getTime();
        l.prioridade = t.prioridade;
        l.tipo = t.tipo == null ? Task.TIPO_INDIVIDUAL : t.tipo;
        return l;
    }

    private void avaliarLembretes(Context ctx, LembretePrefs prefs, LembreteCache cache,
                                  FirebaseFirestore db, String uid) {
        LocalDate hoje = LocalDate.now();
        int horaAgora = LocalTime.now().getHour();
        String dataTexto = hoje.toString();
        Set<String> jaDisparados = cache.disparados();

        for (TarefaLocal t : cache.ler()) {
            long dias = ChronoUnit.DAYS.between(hoje, t.dataPrazo());
            List<Integer> horas = PlanoDeLembretes.horasDeHoje(dias, prefs.antecedenciaDias(),
                    prefs.intensidade(), prefs.horaBase());

            // avisos de hoje cuja hora já chegou; se o aparelho ficou desligado e perdeu vários,
            // sai um só (o mais recente) e todos contam como entregues
            List<String> chaves = new ArrayList<>();
            boolean algumNovo = false;
            for (int hora : horas) {
                if (hora > horaAgora) continue;
                String chave = dataTexto + "|" + t.groupId + "|" + t.id + "|" + hora;
                chaves.add(chave);
                if (!jaDisparados.contains(chave)) algumNovo = true;
            }
            if (!algumNovo) continue;
            cache.registrarDisparos(chaves);

            TarefaLocal fresca = confirmarNoServidor(db, cache, uid, t);
            if (fresca != null) {
                long diasAtuais = ChronoUnit.DAYS.between(hoje, fresca.dataPrazo());
                Notificacoes.lembrete(ctx, fresca, diasAtuais);
            }
        }
    }

    /**
     * Confere a tarefa no servidor antes de avisar. Devolve null se já foi feita ou apagada
     * (e tira do cache). Sem conexão, usa a cópia local: melhor avisar de mais do que de menos.
     */
    private TarefaLocal confirmarNoServidor(FirebaseFirestore db, LembreteCache cache, String uid, TarefaLocal t) {
        try {
            DocumentSnapshot d = Tasks.await(db.collection("groups").document(t.groupId)
                    .collection("tasks").document(t.id).get(), 15, TimeUnit.SECONDS);
            Task atual = d.exists() ? d.toObject(Task.class) : null;
            if (atual == null || atual.prazo == null || atual.concluidaPeloUsuario(uid)) {
                cache.remover(t.groupId, t.id);
                return null;
            }
            return paraLocal(t.groupId, t.grupoNome, t.id, atual);
        } catch (Exception e) {
            return t;
        }
    }
}
