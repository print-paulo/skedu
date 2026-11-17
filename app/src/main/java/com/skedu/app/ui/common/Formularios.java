package com.skedu.app.ui.common;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Pequenos comportamentos de formulário reaproveitados nas telas. */
public final class Formularios {

    private Formularios() {
    }

    /** Some com o erro do campo assim que o usuário volta a digitar. */
    public static void limparErroAoDigitar(TextInputLayout layout, TextInputEditText campo) {
        campo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (layout.getError() != null) {
                    layout.setError(null);
                }
            }
        });
    }

    /** Executa a ação quando o usuário toca em "concluir" no teclado. */
    public static void aoConcluir(TextInputEditText campo, Runnable acao) {
        campo.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                acao.run();
                return true;
            }
            return false;
        });
    }

    public static String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }
}
