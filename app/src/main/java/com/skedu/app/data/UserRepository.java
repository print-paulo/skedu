package com.skedu.app.data;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {

    private UserRepository() {
    }

    /** Cria/atualiza users/{uid}. Usa merge para não apagar campos futuros (ex.: preferências de lembrete). */
    public static void salvarPerfil(FirebaseUser user, String nome) {
        if (user == null) return;
        Map<String, Object> dados = new HashMap<>();
        dados.put("nome", nome);
        dados.put("email", user.getEmail());
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .set(dados, SetOptions.merge());
    }

    /** Nome para mostrar nos grupos: displayName, ou a parte do e-mail antes do @. */
    public static String nomeDe(FirebaseUser user) {
        if (user == null) return "Usuário";
        String nome = user.getDisplayName();
        if (nome != null && !nome.trim().isEmpty()) return nome.trim();
        String email = user.getEmail();
        if (email != null && email.contains("@")) return email.substring(0, email.indexOf('@'));
        return "Usuário";
    }
}
