package com.skedu.app.data;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.skedu.app.model.Anexo;
import com.skedu.app.model.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tarefas de um grupo: groups/{groupId}/tasks/{taskId}.
 * Qualquer membro cria, edita e apaga. Quem editou fica registrado ("editado por").
 */
public class TaskRepository {

    /** Algo que se pode parar (vários listeners juntos). */
    public interface Cancelavel {
        void cancelar();
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private CollectionReference tarefas(String groupId) {
        return db.collection("groups").document(groupId).collection("tasks");
    }

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    /** Tarefas de um grupo, em tempo real. */
    public ListenerRegistration observarDoGrupo(String groupId, GroupRepository.Callback<List<Task>> cb) {
        return tarefas(groupId).addSnapshotListener((snap, erro) -> {
            if (erro != null) {
                cb.onErro(FirestoreErros.traduzir(erro, "Não foi possível carregar as tarefas."));
                return;
            }
            cb.onSucesso(converter(groupId, snap));
        });
    }

    /**
     * Tarefas de vários grupos juntas (tela "Minhas tarefas"). Um listener por grupo; só
     * entrega a lista quando todos os grupos responderam, para não piscar com lista parcial.
     */
    public Cancelavel observarDeVarios(List<String> groupIds, GroupRepository.Callback<List<Task>> cb) {
        if (groupIds.isEmpty()) {
            cb.onSucesso(new ArrayList<>());
            return () -> {
            };
        }

        Map<String, List<Task>> porGrupo = new HashMap<>();
        List<ListenerRegistration> registros = new ArrayList<>();
        for (String gid : groupIds) {
            registros.add(tarefas(gid).addSnapshotListener((snap, erro) -> {
                if (erro != null) {
                    cb.onErro(FirestoreErros.traduzir(erro, "Não foi possível carregar as tarefas."));
                    return;
                }
                porGrupo.put(gid, converter(gid, snap));
                if (porGrupo.size() < groupIds.size()) return;

                List<Task> todas = new ArrayList<>();
                for (List<Task> parte : porGrupo.values()) todas.addAll(parte);
                cb.onSucesso(todas);
            }));
        }
        return () -> {
            for (ListenerRegistration r : registros) r.remove();
        };
    }

    /** Lê uma tarefa uma vez (tela de edição). */
    public void buscar(String groupId, String taskId, GroupRepository.Callback<Task> cb) {
        tarefas(groupId).document(taskId).get()
                .addOnSuccessListener(doc -> {
                    Task t = doc.exists() ? doc.toObject(Task.class) : null;
                    if (t == null) {
                        cb.onErro("Tarefa não encontrada. Ela pode ter sido excluída.");
                        return;
                    }
                    t.groupId = groupId;
                    cb.onSucesso(t);
                })
                .addOnFailureListener(e -> cb.onErro(FirestoreErros.traduzir(e,
                        "Não foi possível abrir a tarefa.")));
    }

    // ------------------------------------------------------------------
    // Escrita
    // ------------------------------------------------------------------

    public void criar(String groupId, Task t, String uid, String nomeUsuario,
                      GroupRepository.Callback<Void> cb) {
        Map<String, Object> dados = campos(t);
        // o tipo só é definido aqui: depois de criada a tarefa não muda de tipo
        dados.put("tipo", Task.TIPO_GRUPO.equals(t.tipo) ? Task.TIPO_GRUPO : Task.TIPO_INDIVIDUAL);
        dados.put("concluidaGrupo", false);
        dados.put("criadoPor", uid);
        dados.put("criadoPorNome", nomeUsuario);
        dados.put("criadoEm", FieldValue.serverTimestamp());
        dados.put("editadoPor", uid);
        dados.put("editadoPorNome", nomeUsuario);
        dados.put("atualizadoEm", FieldValue.serverTimestamp());
        dados.put("concluidaPor", new ArrayList<String>());

        tarefas(groupId).add(dados)
                .addOnSuccessListener(ref -> cb.onSucesso(null))
                .addOnFailureListener(e -> cb.onErro(FirestoreErros.traduzir(e,
                        "Não foi possível salvar a tarefa.")));
    }

    public void editar(String groupId, String taskId, Task t, String uid, String nomeUsuario,
                       GroupRepository.Callback<Void> cb) {
        Map<String, Object> dados = campos(t);
        dados.put("editadoPor", uid);
        dados.put("editadoPorNome", nomeUsuario);
        dados.put("atualizadoEm", FieldValue.serverTimestamp());

        tarefas(groupId).document(taskId).update(dados)
                .addOnSuccessListener(v -> cb.onSucesso(null))
                .addOnFailureListener(e -> cb.onErro(FirestoreErros.traduzir(e,
                        "Não foi possível salvar a tarefa.")));
    }

    /**
     * Grava só a lista de anexos de uma tarefa existente (usado ao enviar/remover um arquivo,
     * para não depender de o usuário apertar "Salvar"). Conta como edição: registra quem mexeu.
     */
    public void atualizarAnexos(String groupId, String taskId, List<Anexo> anexos, String uid,
                                String nomeUsuario, GroupRepository.Callback<Void> cb) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("anexos", anexosParaMapas(anexos));
        dados.put("editadoPor", uid);
        dados.put("editadoPorNome", nomeUsuario);
        dados.put("atualizadoEm", FieldValue.serverTimestamp());

        tarefas(groupId).document(taskId).update(dados)
                .addOnSuccessListener(v -> cb.onSucesso(null))
                .addOnFailureListener(e -> cb.onErro(FirestoreErros.traduzir(e,
                        "Não foi possível salvar os anexos na tarefa.")));
    }

    /**
     * Marca/desmarca como feita.
     * - Tarefa individual: só para o usuário (continua pendente para os outros).
     * - Tarefa de grupo: vale para todos; qualquer membro pode marcar ou desmarcar.
     */
    public void alternarConclusao(Task t, String uid, String nomeUsuario, boolean concluir,
                                  GroupRepository.Callback<Void> cb) {
        DocumentReference ref = tarefas(t.groupId).document(t.id);

        com.google.android.gms.tasks.Task<Void> operacao;
        if (t.ehDoGrupo()) {
            Map<String, Object> m = new HashMap<>();
            m.put("concluidaGrupo", concluir);
            m.put("concluidaGrupoPor", concluir ? uid : FieldValue.delete());
            m.put("concluidaGrupoPorNome", concluir ? nomeUsuario : FieldValue.delete());
            m.put("concluidaGrupoEm", concluir ? FieldValue.serverTimestamp() : FieldValue.delete());
            operacao = ref.update(m);
        } else {
            Object valor = concluir ? FieldValue.arrayUnion(uid) : FieldValue.arrayRemove(uid);
            operacao = ref.update("concluidaPor", valor);
        }
        operacao.addOnSuccessListener(v -> cb.onSucesso(null))
                .addOnFailureListener(e -> cb.onErro(FirestoreErros.traduzir(e,
                        "Não foi possível atualizar a tarefa.")));
    }

    public void excluir(String groupId, String taskId, GroupRepository.Callback<Void> cb) {
        tarefas(groupId).document(taskId).delete()
                .addOnSuccessListener(v -> cb.onSucesso(null))
                .addOnFailureListener(e -> cb.onErro(FirestoreErros.traduzir(e,
                        "Não foi possível excluir a tarefa.")));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static Map<String, Object> campos(Task t) {
        Map<String, Object> m = new HashMap<>();
        m.put("titulo", t.titulo);
        m.put("descricao", t.descricao == null ? "" : t.descricao);
        m.put("subjectId", t.subjectId == null ? "" : t.subjectId);
        m.put("disciplinaNome", t.disciplinaNome == null ? "" : t.disciplinaNome);
        m.put("disciplinaCor", t.disciplinaCor);
        m.put("prioridade", t.prioridade);
        m.put("prazo", t.prazo);

        m.put("anexos", anexosParaMapas(t.anexos));
        return m;
    }

    private static List<Map<String, Object>> anexosParaMapas(List<Anexo> anexos) {
        List<Map<String, Object>> lista = new ArrayList<>();
        if (anexos == null) return lista;
        for (Anexo a : anexos) {
            Map<String, Object> item = new HashMap<>();
            item.put("nome", a.nome);
            item.put("url", a.url);
            item.put("tipo", a.tipo);
            item.put("tamanho", a.tamanho);
            lista.add(item);
        }
        return lista;
    }

    private static List<Task> converter(String groupId, QuerySnapshot snap) {
        List<Task> lista = new ArrayList<>();
        if (snap == null) return lista;
        for (DocumentSnapshot d : snap.getDocuments()) {
            Task t = d.toObject(Task.class);
            if (t == null) continue;
            t.groupId = groupId;
            lista.add(t);
        }
        return lista;
    }
}
