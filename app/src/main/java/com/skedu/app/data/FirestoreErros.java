package com.skedu.app.data;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.FirebaseFirestoreException;

/** Converte erros do Firestore em mensagens claras em português. */
final class FirestoreErros {

    private FirestoreErros() {
    }

    static String traduzir(Exception e, String padrao) {
        if (e instanceof FirebaseNetworkException) return "Sem conexão com a internet.";
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code c = ((FirebaseFirestoreException) e).getCode();
            if (c == FirebaseFirestoreException.Code.UNAVAILABLE) return "Sem conexão com a internet.";
            if (c == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Sem permissão. Você ainda faz parte do grupo? As regras do Firestore foram publicadas?";
            }
        }
        return padrao;
    }
}
