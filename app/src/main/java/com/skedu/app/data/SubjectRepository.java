package com.skedu.app.data;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.skedu.app.model.Subject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Disciplinas de um grupo: groups/{groupId}/subjects/{subjectId}. */
public class SubjectRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private CollectionReference disciplinas(String groupId) {
        return db.collection("groups").document(groupId).collection("subjects");
    }

    private CollectionReference tarefas(String groupId) {
        return db.collection("groups").document(groupId).collection("tasks");
    }

    /** Lista (em tempo real) as disciplinas do grupo, em ordem alfabética. */
    public ListenerRegistration observar(String groupId, GroupRepository.Callback<List<Subject>> cb) {
        return disciplinas(groupId).addSnapshotListener((snap, erro) -> {
            if (erro != null) {
                cb.onErro(FirestoreErros.traduzir(erro, "Não foi possível carregar as disciplinas."));
                return;
            }
            List<Subject> lista = new ArrayList<>();
            if (snap != null) {
                for (DocumentSnapshot d : snap.getDocuments()) {
                    Subject s = d.toObject(Subject.class);
                    if (s != null) lista.add(s);
                }
            }
            Collections.sort(lista, (a, b) -> semNulo(a.nome).compareToIgnoreCase(semNulo(b.nome)));
            cb.onSucesso(lista);
        });
    }

    public void criar(String groupId, String nome, int cor, String uid, GroupRepository.Callback<Void> cb) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("nome", nome);
        dados.put("cor", cor);
        dados.put("criadoPor", uid);
        disciplinas(groupId).add(dados)
                .addOnSuccessListener(ref -> cb.onSucesso(null))
                .addOnFailureListener(e -> cb.onErro(FirestoreErros.traduzir(e,
                        "Não foi possível criar a disciplina.")));
    }

    /** Atualiza a disciplina e copia o novo nome/cor para as tarefas que a usam. */
    public void atualizar(String groupId, String subjectId, String nome, int cor,
                          GroupRepository.Callback<Void> cb) {
        tarefas(groupId).whereEqualTo("subjectId", subjectId).get()
                .addOnSuccessListener(snap -> {
                    WriteBatch lote = db.batch();

                    Map<String, Object> disciplina = new HashMap<>();
                    disciplina.put("nome", nome);
                    disciplina.put("cor", cor);
                    lote.update(disciplinas(groupId).document(subjectId), disciplina);

                    for (DocumentSnapshot t : snap.getDocuments()) {
                        Map<String, Object> copia = new HashMap<>();
                        copia.put("disciplinaNome", nome);
                        copia.put("disciplinaCor", cor);
                        lote.update(t.getReference(), copia);
                    }
                    executar(lote, "Não foi possível salvar a disciplina.", cb);
                })
                .addOnFailureListener(e -> cb.onErro(FirestoreErros.traduzir(e,
                        "Não foi possível salvar a disciplina.")));
    }

    /** Exclui a disciplina; as tarefas dela continuam, só ficam "sem disciplina". */
    public void excluir(String groupId, String subjectId, GroupRepository.Callback<Void> cb) {
        tarefas(groupId).whereEqualTo("subjectId", subjectId).get()
                .addOnSuccessListener(snap -> {
                    WriteBatch lote = db.batch();
                    for (DocumentSnapshot t : snap.getDocuments()) {
                        Map<String, Object> limpa = new HashMap<>();
                        limpa.put("subjectId", "");
                        limpa.put("disciplinaNome", "");
                        limpa.put("disciplinaCor", 0);
                        lote.update(t.getReference(), limpa);
                    }
                    lote.delete(disciplinas(groupId).document(subjectId));
                    executar(lote, "Não foi possível excluir a disciplina.", cb);
                })
                .addOnFailureListener(e -> cb.onErro(FirestoreErros.traduzir(e,
                        "Não foi possível excluir a disciplina.")));
    }

    private static void executar(WriteBatch lote, String erroPadrao, GroupRepository.Callback<Void> cb) {
        lote.commit()
                .addOnSuccessListener(v -> cb.onSucesso(null))
                .addOnFailureListener(e -> cb.onErro(FirestoreErros.traduzir(e, erroPadrao)));
    }

    private static String semNulo(String s) {
        return s == null ? "" : s;
    }
}
