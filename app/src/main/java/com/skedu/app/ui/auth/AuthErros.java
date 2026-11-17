package com.skedu.app.ui.auth;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/** Converte erros do Firebase Auth em mensagens claras em português. */
public class AuthErros {

    private AuthErros() {
    }

    public static String traduzir(Exception e) {
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            return "E-mail ou senha incorretos.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "Já existe uma conta com esse e-mail.";
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "Senha fraca. Use pelo menos 6 caracteres.";
        }
        if (e instanceof FirebaseNetworkException) {
            return "Sem conexão com a internet.";
        }
        return "Algo deu errado. Tente novamente.";
    }
}
