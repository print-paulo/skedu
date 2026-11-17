package com.skedu.app.ui.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.skedu.app.R;
import com.skedu.app.data.UserRepository;
import com.skedu.app.databinding.FragmentLoginBinding;
import com.skedu.app.ui.common.Formularios;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private FirebaseAuth auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        auth = FirebaseAuth.getInstance();

        binding.btnEntrar.setOnClickListener(v -> entrarComEmail());
        binding.btnGoogle.setOnClickListener(v -> entrarComGoogle());
        binding.btnEsqueciSenha.setOnClickListener(v -> recuperarSenha());
        binding.btnCriarConta.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_login_to_register));

        Formularios.limparErroAoDigitar(binding.tilEmail, binding.etEmail);
        Formularios.limparErroAoDigitar(binding.tilSenha, binding.etSenha);
        Formularios.aoConcluir(binding.etSenha, this::entrarComEmail);
    }

    @Override
    public void onStart() {
        super.onStart();
        // se já estiver logado (ex.: login terminou com o app em segundo plano), segue em frente
        if (auth != null && auth.getCurrentUser() != null) {
            irParaGrupos();
        }
    }

    private void entrarComEmail() {
        String email = texto(binding.etEmail);
        String senha = texto(binding.etSenha);

        binding.tilEmail.setError(null);
        binding.tilSenha.setError(null);

        if (email.isEmpty()) {
            binding.tilEmail.setError("Informe o e-mail");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError("E-mail inválido");
            return;
        }
        if (senha.isEmpty()) {
            binding.tilSenha.setError("Informe a senha");
            return;
        }

        carregando(true);
        auth.signInWithEmailAndPassword(email, senha).addOnCompleteListener(task -> {
            if (!isAdded() || binding == null) return;
            carregando(false);
            if (task.isSuccessful()) {
                irParaGrupos();
            } else {
                // erro junto do campo, não em um aviso que some
                binding.tilSenha.setError(AuthErros.traduzir(task.getException()));
            }
        });
    }

    private void entrarComGoogle() {
        carregando(true);
        GoogleSignInHelper.entrar(requireActivity(), new GoogleSignInHelper.Resultado() {
            @Override
            public void onSucesso(FirebaseUser user) {
                UserRepository.salvarPerfil(user, UserRepository.nomeDe(user));
                if (!isAdded() || binding == null) return;
                carregando(false);
                irParaGrupos();
            }

            @Override
            public void onErro(String mensagem) {
                if (!isAdded() || binding == null) return;
                carregando(false);
                Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void recuperarSenha() {
        String email = texto(binding.etEmail);
        binding.tilEmail.setError(null);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError("Digite seu e-mail para receber o link");
            return;
        }
        auth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            if (!isAdded()) return;
            // mensagem neutra: não revela se o e-mail existe
            Toast.makeText(requireContext(),
                    "Se o e-mail estiver cadastrado, enviamos um link para redefinir a senha.",
                    Toast.LENGTH_LONG).show();
        });
    }

    private void irParaGrupos() {
        NavController nav = NavHostFragment.findNavController(this);
        if (nav.getCurrentDestination() != null
                && nav.getCurrentDestination().getId() == R.id.loginFragment) {
            nav.navigate(R.id.action_login_to_groups);
        }
    }

    private void carregando(boolean sim) {
        binding.progress.setVisibility(sim ? View.VISIBLE : View.GONE);
        binding.btnEntrar.setEnabled(!sim);
        binding.btnGoogle.setEnabled(!sim);
        binding.btnCriarConta.setEnabled(!sim);
        binding.btnEsqueciSenha.setEnabled(!sim);
    }

    private static String texto(com.google.android.material.textfield.TextInputEditText campo) {
        return Formularios.texto(campo);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
