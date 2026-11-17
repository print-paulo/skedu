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

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.skedu.app.R;
import com.skedu.app.data.UserRepository;
import com.skedu.app.databinding.FragmentRegisterBinding;

public class RegisterFragment extends Fragment {

    private FragmentRegisterBinding binding;
    private FirebaseAuth auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        auth = FirebaseAuth.getInstance();
        binding.btnCriar.setOnClickListener(v -> criarConta());
        binding.btnVoltar.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
    }

    private void criarConta() {
        String nome = texto(binding.etNome);
        String email = texto(binding.etEmail);
        String senha = texto(binding.etSenha);
        String confirmar = texto(binding.etConfirmar);

        binding.tilNome.setError(null);
        binding.tilEmail.setError(null);
        binding.tilSenha.setError(null);
        binding.tilConfirmar.setError(null);

        if (nome.isEmpty()) {
            binding.tilNome.setError("Informe seu nome");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError("E-mail inválido");
            return;
        }
        if (senha.length() < 6) {
            binding.tilSenha.setError("Mínimo de 6 caracteres");
            return;
        }
        if (!senha.equals(confirmar)) {
            binding.tilConfirmar.setError("As senhas não coincidem");
            return;
        }

        carregando(true);
        auth.createUserWithEmailAndPassword(email, senha).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                if (!isAdded() || binding == null) return;
                carregando(false);
                Toast.makeText(requireContext(), AuthErros.traduzir(task.getException()),
                        Toast.LENGTH_LONG).show();
                return;
            }

            FirebaseUser user = task.getResult().getUser();
            if (user == null) {
                if (!isAdded() || binding == null) return;
                carregando(false);
                return;
            }

            UserProfileChangeRequest pedido = new UserProfileChangeRequest.Builder()
                    .setDisplayName(nome)
                    .build();
            user.updateProfile(pedido).addOnCompleteListener(t2 -> {
                UserRepository.salvarPerfil(user, nome);
                if (!isAdded() || binding == null) return;
                carregando(false);
                NavController nav = NavHostFragment.findNavController(this);
                if (nav.getCurrentDestination() != null
                        && nav.getCurrentDestination().getId() == R.id.registerFragment) {
                    nav.navigate(R.id.action_register_to_groups);
                }
            });
        });
    }

    private void carregando(boolean sim) {
        binding.progress.setVisibility(sim ? View.VISIBLE : View.GONE);
        binding.btnCriar.setEnabled(!sim);
        binding.btnVoltar.setEnabled(!sim);
    }

    private static String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
