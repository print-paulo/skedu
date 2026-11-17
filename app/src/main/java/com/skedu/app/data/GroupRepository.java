package com.skedu.app.data;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.skedu.app.model.Group;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tudo que fala com o Firestore sobre grupos e convites.
 *
 * Estrutura:
 *   groups/{groupId}   -> dados do grupo (nome, codigoConvite, membros, ...)
 *   invites/{codigo}   -> { groupId }   (usado para achar o grupo a partir do código)
 */
public class GroupRepository {

    public static final int MAX_MEMBROS = 12;
    public static final int TAMANHO_CODIGO = 6;
    /** O QR Code carrega "SKEDU:ABC123" para não confundir com QR de outros apps. */
    public static final String QR_PREFIXO = "SKEDU:";

    // sem I, O, 0, 1 para evitar confusão ao digitar
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final SecureRandom random = new SecureRandom();

    public interface Callback<T> {
        void onSucesso(T resultado);

        void onErro(String mensagem);
    }

    // ------------------------------------------------------------------
    // Leitura em tempo real
    // ------------------------------------------------------------------

    /** Lista (em tempo real) os grupos em que o usuário é membro. */
    public ListenerRegistration observarMeusGrupos(String uid, Callback<List<Group>> cb) {
        return db.collection("groups")
                .whereArrayContains("membros", uid)
                .addSnapshotListener((snap, erro) -> {
                    if (erro != null) {
                        cb.onErro(traduzir(erro, "Não foi possível carregar seus grupos."));
                        return;
                    }
                    List<Group> lista = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Group g = d.toObject(Group.class);
                            if (g != null) lista.add(g);
                        }
                    }
                    Collections.sort(lista, (a, b) -> semNulo(a.nome).compareToIgnoreCase(semNulo(b.nome)));
                    cb.onSucesso(lista);
                });
    }

    /** Observa um grupo específico (tela de detalhe). */
    public ListenerRegistration observarGrupo(String groupId, Callback<Group> cb) {
        return db.collection("groups")
                .document(groupId)
                .addSnapshotListener((doc, erro) -> {
                    if (erro != null) {
                        cb.onErro(traduzir(erro, "Não foi possível carregar o grupo."));
                        return;
                    }
                    if (doc == null || !doc.exists()) {
                        cb.onErro("Grupo não encontrado.");
                        return;
                    }
                    Group g = doc.toObject(Group.class);
                    if (g == null) {
                        cb.onErro("Grupo inválido.");
                        return;
                    }
                    cb.onSucesso(g);
                });
    }

    // ------------------------------------------------------------------
    // Criar grupo
    // ------------------------------------------------------------------

    /** Cria o grupo + o convite numa transação. Devolve o id do grupo. */
    public void criarGrupo(String nome, String uid, String nomeUsuario, Callback<String> cb) {
        tentarCriar(nome, uid, nomeUsuario, 5, cb);
    }

    private void tentarCriar(String nome, String uid, String nomeUsuario,
                             int tentativasRestantes, Callback<String> cb) {
        String codigo = gerarCodigo();
        DocumentReference grupoRef = db.collection("groups").document();
        DocumentReference conviteRef = db.collection("invites").document(codigo);

        db.runTransaction(tx -> {
                    // se o código já existe, tenta outro
                    if (tx.get(conviteRef).exists()) {
                        throw new FirebaseFirestoreException("Código em uso",
                                FirebaseFirestoreException.Code.ALREADY_EXISTS);
                    }

                    Map<String, String> info = new HashMap<>();
                    info.put(uid, nomeUsuario);

                    Map<String, Object> grupo = new HashMap<>();
                    grupo.put("nome", nome);
                    grupo.put("codigoConvite", codigo);
                    grupo.put("criadoPor", uid);
                    grupo.put("membros", Collections.singletonList(uid));
                    grupo.put("membrosInfo", info);
                    grupo.put("memberCount", 1);
                    grupo.put("criadoEm", FieldValue.serverTimestamp());
                    tx.set(grupoRef, grupo);

                    Map<String, Object> convite = new HashMap<>();
                    convite.put("groupId", grupoRef.getId());
                    tx.set(conviteRef, convite);
                    return null;
                })
                .addOnSuccessListener(v -> cb.onSucesso(grupoRef.getId()))
                .addOnFailureListener(e -> {
                    boolean colisao = e instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) e).getCode()
                            == FirebaseFirestoreException.Code.ALREADY_EXISTS;
                    if (colisao && tentativasRestantes > 0) {
                        tentarCriar(nome, uid, nomeUsuario, tentativasRestantes - 1, cb);
                    } else {
                        cb.onErro(traduzirCriacao(e));
                    }
                });
    }

    // ------------------------------------------------------------------
    // Entrar em grupo (código digitado ou lido do QR)
    // ------------------------------------------------------------------

    /**
     * @param gruposAtuais ids dos grupos em que o usuário já está (para avisar "você já está nesse grupo")
     */
    public void entrarPorCodigo(String codigoBruto, String uid, String nomeUsuario,
                                Collection<String> gruposAtuais, Callback<String> cb) {
        String codigo = normalizarCodigo(codigoBruto);
        if (codigo == null) {
            cb.onErro("Código inválido. Ele tem 6 letras/números.");
            return;
        }

        db.collection("invites").document(codigo).get()
                .addOnSuccessListener(doc -> {
                    String groupId = doc.getString("groupId");
                    if (!doc.exists() || groupId == null) {
                        cb.onErro("Código não encontrado. Confira e tente de novo.");
                        return;
                    }
                    if (gruposAtuais.contains(groupId)) {
                        cb.onErro("Você já faz parte desse grupo.");
                        return;
                    }

                    // update atômico: as regras do Firestore garantem o limite de 12 e que
                    // o usuário só pode adicionar a si mesmo (veja firestore.rules)
                    Map<String, Object> up = new HashMap<>();
                    up.put("membros", FieldValue.arrayUnion(uid));
                    up.put("membrosInfo." + uid, nomeUsuario);
                    up.put("memberCount", FieldValue.increment(1));
                    up.put("joinCode", codigo); // prova de que o usuário conhece o código

                    db.collection("groups").document(groupId).update(up)
                            .addOnSuccessListener(v -> cb.onSucesso(groupId))
                            .addOnFailureListener(e -> cb.onErro(traduzir(e,
                                    "Não foi possível entrar. O grupo pode estar cheio (máximo de "
                                            + MAX_MEMBROS + " membros).")));
                })
                .addOnFailureListener(e -> cb.onErro(traduzir(e, "Não foi possível verificar o código.")));
    }

    // ------------------------------------------------------------------
    // Sair do grupo
    // ------------------------------------------------------------------

    /**
     * Remove o usuário do grupo. Se ele era o último membro, o grupo e o convite são excluídos
     * (as tarefas e disciplinas ficam inacessíveis, pois ninguém mais pode lê-las).
     */
    public void sair(Group grupo, String uid, Callback<Void> cb) {
        if (grupo.memberCount <= 1) {
            WriteBatch lote = db.batch();
            lote.delete(db.collection("groups").document(grupo.id));
            if (grupo.codigoConvite != null) {
                lote.delete(db.collection("invites").document(grupo.codigoConvite));
            }
            lote.commit()
                    .addOnSuccessListener(v -> cb.onSucesso(null))
                    .addOnFailureListener(e -> cb.onErro(traduzir(e, "Não foi possível sair do grupo.")));
            return;
        }

        Map<String, Object> up = new HashMap<>();
        up.put("membros", FieldValue.arrayRemove(uid));
        up.put("membrosInfo." + uid, FieldValue.delete());
        up.put("memberCount", FieldValue.increment(-1));
        db.collection("groups").document(grupo.id).update(up)
                .addOnSuccessListener(v -> cb.onSucesso(null))
                .addOnFailureListener(e -> cb.onErro(traduzir(e, "Não foi possível sair do grupo.")));
    }

    /** Aceita "abc123", " ABC123 " ou "SKEDU:ABC123". Devolve null se inválido. */
    public static String normalizarCodigo(String bruto) {
        if (bruto == null) return null;
        String c = bruto.trim().toUpperCase(Locale.ROOT);
        if (c.startsWith(QR_PREFIXO)) c = c.substring(QR_PREFIXO.length());
        if (c.length() != TAMANHO_CODIGO) return null;
        for (int i = 0; i < c.length(); i++) {
            if (ALFABETO.indexOf(c.charAt(i)) < 0) return null;
        }
        return c;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private String gerarCodigo() {
        StringBuilder sb = new StringBuilder(TAMANHO_CODIGO);
        for (int i = 0; i < TAMANHO_CODIGO; i++) {
            sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    private static String semNulo(String s) {
        return s == null ? "" : s;
    }

    private static String traduzirCriacao(Exception e) {
        if (e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "Sem permissão para criar o grupo. As regras do Firestore foram publicadas?";
        }
        return traduzir(e, "Não foi possível criar o grupo. Tente novamente.");
    }

    private static String traduzir(Exception e, String padrao) {
        if (e instanceof FirebaseNetworkException) return "Sem conexão com a internet.";
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code c = ((FirebaseFirestoreException) e).getCode();
            if (c == FirebaseFirestoreException.Code.UNAVAILABLE) return "Sem conexão com a internet.";
        }
        return padrao;
    }
}
