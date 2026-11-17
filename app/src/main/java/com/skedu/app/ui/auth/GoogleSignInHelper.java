package com.skedu.app.ui.auth;

import android.app.Activity;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.skedu.app.R;

/**
 * Login com Google via Credential Manager + Firebase Auth.
 * Precisa de: provedor Google ativado no Firebase, SHA-1 cadastrado e google-services.json atualizado
 * (é ele que gera o R.string.default_web_client_id).
 */
public class GoogleSignInHelper {

    public interface Resultado {
        void onSucesso(FirebaseUser user);

        void onErro(String mensagem);
    }

    private static final String TAG = "GoogleSignInHelper";

    private GoogleSignInHelper() {
    }

    public static void entrar(Activity activity, Resultado resultado) {
        CredentialManager gerenciador = CredentialManager.create(activity);

        GetSignInWithGoogleOption opcao = new GetSignInWithGoogleOption.Builder(
                activity.getString(R.string.default_web_client_id)).build();

        GetCredentialRequest pedido = new GetCredentialRequest.Builder()
                .addCredentialOption(opcao)
                .build();

        gerenciador.getCredentialAsync(
                activity,
                pedido,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse resposta) {
                        tratarCredencial(resposta.getCredential(), resultado);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Log.e(TAG, "Falha no Credential Manager: " + e.getType(), e);
                        if (e instanceof GetCredentialCancellationException) {
                            resultado.onErro("Login com Google cancelado.");
                        } else if (e instanceof NoCredentialException) {
                            resultado.onErro("Nenhuma conta Google disponível ou SHA-1 não cadastrado no Firebase.");
                        } else {
                            resultado.onErro("Erro no login com Google: " + e.getType());
                        }
                    }
                });
    }

    private static void tratarCredencial(Credential credencial, Resultado resultado) {
        if (credencial instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credencial.getType())) {
            try {
                GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(credencial.getData());
                AuthCredential authCredencial = GoogleAuthProvider.getCredential(google.getIdToken(), null);
                FirebaseAuth.getInstance().signInWithCredential(authCredencial)
                        .addOnCompleteListener(task -> {
                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                            if (task.isSuccessful() && user != null) {
                                resultado.onSucesso(user);
                            } else {
                                resultado.onErro(AuthErros.traduzir(task.getException()));
                            }
                        });
            } catch (Exception e) {
                resultado.onErro("Não foi possível ler a conta Google.");
            }
        } else {
            resultado.onErro("Tipo de credencial não suportado.");
        }
    }
}
