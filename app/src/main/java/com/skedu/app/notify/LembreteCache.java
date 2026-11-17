package com.skedu.app.notify;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Estado local do sistema de lembretes (no aparelho, não vai para o Firestore):
 * tarefas pendentes copiadas, avisos já disparados e o "ponto de partida" de novas tarefas.
 */
public class LembreteCache {

    private static final String ARQUIVO = "skedu_lembretes_cache";
    private static final String K_USUARIO = "uid";
    private static final String K_TAREFAS = "tarefas";
    private static final String K_ATUALIZADO_EM = "atualizado_em";
    private static final String K_DISPARADOS = "disparados";
    private static final String PREFIXO_BASE = "base_";

    private final SharedPreferences sp;

    public LembreteCache(Context contexto) {
        sp = contexto.getApplicationContext().getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
    }

    /** Se outra pessoa entrou neste aparelho, descarta o estado da anterior. */
    public void garantirUsuario(String uid) {
        if (!uid.equals(sp.getString(K_USUARIO, null))) {
            sp.edit().clear().putString(K_USUARIO, uid).apply();
        }
    }

    // ---- tarefas pendentes ----

    public List<TarefaLocal> ler() {
        List<TarefaLocal> lista = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(K_TAREFAS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                TarefaLocal t = new TarefaLocal();
                t.groupId = o.getString("g");
                t.grupoNome = o.optString("gn", "");
                t.id = o.getString("id");
                t.titulo = o.optString("t", "");
                t.prazoMillis = o.getLong("p");
                t.prioridade = o.optString("pr", "normal");
                t.tipo = o.optString("tp", "individual");
                lista.add(t);
            }
        } catch (JSONException e) {
            // cache corrompido: recomeça vazio (o próximo ciclo recarrega)
            return new ArrayList<>();
        }
        return lista;
    }

    public void salvar(List<TarefaLocal> tarefas) {
        JSONArray arr = new JSONArray();
        try {
            for (TarefaLocal t : tarefas) {
                JSONObject o = new JSONObject();
                o.put("g", t.groupId);
                o.put("gn", t.grupoNome);
                o.put("id", t.id);
                o.put("t", t.titulo);
                o.put("p", t.prazoMillis);
                o.put("pr", t.prioridade);
                o.put("tp", t.tipo);
                arr.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        sp.edit().putString(K_TAREFAS, arr.toString()).apply();
    }

    public void remover(String groupId, String taskId) {
        List<TarefaLocal> restantes = new ArrayList<>();
        for (TarefaLocal t : ler()) {
            if (!(t.groupId.equals(groupId) && t.id.equals(taskId))) restantes.add(t);
        }
        salvar(restantes);
    }

    public long atualizadoEm() {
        return sp.getLong(K_ATUALIZADO_EM, 0L);
    }

    public void marcarAtualizadoEm(long millis) {
        sp.edit().putLong(K_ATUALIZADO_EM, millis).apply();
    }

    // ---- avisos já disparados ("data|grupo|tarefa|hora") ----

    public Set<String> disparados() {
        return new HashSet<>(sp.getStringSet(K_DISPARADOS, new HashSet<>()));
    }

    /** Registra os avisos e apaga os de mais de 3 dias, para o conjunto não crescer sem fim. */
    public void registrarDisparos(List<String> chaves) {
        Set<String> todos = disparados();
        todos.addAll(chaves);

        String corte = LocalDate.now().minusDays(3).toString();
        for (Iterator<String> it = todos.iterator(); it.hasNext(); ) {
            String chave = it.next();
            int barra = chave.indexOf('|');
            // a data (aaaa-mm-dd) vem primeiro e ordena como texto
            if (barra > 0 && chave.substring(0, barra).compareTo(corte) < 0) it.remove();
        }
        sp.edit().putStringSet(K_DISPARADOS, todos).apply();
    }

    // ---- ponto de partida de "nova tarefa" por grupo (segundos*1e9 + nanos do criadoEm) ----

    /** -1 = ainda não há ponto de partida para o grupo. */
    public long baseNovas(String groupId) {
        return sp.getLong(PREFIXO_BASE + groupId, -1L);
    }

    public void salvarBaseNovas(String groupId, long segundosENanos) {
        sp.edit().putLong(PREFIXO_BASE + groupId, segundosENanos).apply();
    }

    /** Esquece os pontos de partida (ao religar o aviso de novas tarefas, para não avisar o passado). */
    public void esquecerBases() {
        SharedPreferences.Editor ed = sp.edit();
        for (String chave : new ArrayList<>(sp.getAll().keySet())) {
            if (chave.startsWith(PREFIXO_BASE)) ed.remove(chave);
        }
        ed.apply();
    }
}
