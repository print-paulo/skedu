package com.skedu.app.ui.groups;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.skedu.app.R;
import com.skedu.app.data.GroupRepository;
import com.skedu.app.data.UserRepository;
import com.skedu.app.databinding.FragmentGroupsBinding;
import com.skedu.app.model.Group;

import java.util.ArrayList;
import java.util.List;

public class GroupsFragment extends Fragment {

    private FragmentGroupsBinding binding;
    private GroupRepository repo;
    private GroupsAdapter adapter;
    private ListenerRegistration listener;
    private final List<String> idsAtuais = new ArrayList<>();

    // precisa ser registrado antes do fragment iniciar; por isso fica aqui no campo
    private final ActivityResultLauncher<ScanOptions> leitorQr =
            registerForActivityResult(new ScanContract(), resultado -> {
                if (resultado.getContents() != null) {
                    entrarComCodigo(resultado.getContents());
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGroupsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repo = new GroupRepository();

        adapter = new GroupsAdapter(grupo -> {
            Bundle args = new Bundle();
            args.putString(GroupDetailFragment.ARG_GROUP_ID, grupo.id);
            NavHostFragment.findNavController(this).navigate(R.id.action_groups_to_detail, args);
        });
        binding.rvGrupos.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvGrupos.setAdapter(adapter);

        binding.btnCriar.setOnClickListener(v -> mostrarDialogCriar());
        binding.btnEntrarCodigo.setOnClickListener(v -> mostrarDialogEntrar());
        binding.btnSair.setOnClickListener(v -> sair());
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            irParaLogin();
            return;
        }
        listener = repo.observarMeusGrupos(user.getUid(), new GroupRepository.Callback<List<Group>>() {
            @Override
            public void onSucesso(List<Group> grupos) {
                if (binding == null) return;
                idsAtuais.clear();
                for (Group g : grupos) idsAtuais.add(g.id);
                adapter.atualizar(grupos);
                binding.tvVazio.setVisibility(grupos.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onErro(String mensagem) {
                // após sair da conta o listener pode falhar: ignora
                if (!isAdded() || FirebaseAuth.getInstance().getCurrentUser() == null) return;
                Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        pararListener();
    }

    // ------------------------------------------------------------------
    // Criar grupo
    // ------------------------------------------------------------------

    private void mostrarDialogCriar() {
        EditText campo = novoCampo("Nome do grupo (ex.: ADS 2º sem)");
        campo.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Criar grupo")
                .setView(comPadding(campo))
                .setPositiveButton("Criar", (d, w) -> criarGrupo(campo.getText().toString().trim()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void criarGrupo(String nome) {
        if (nome.isEmpty()) {
            Toast.makeText(requireContext(), "Informe o nome do grupo.", Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        repo.criarGrupo(nome, user.getUid(), UserRepository.nomeDe(user), new GroupRepository.Callback<String>() {
            @Override
            public void onSucesso(String groupId) {
                if (!isAdded()) return;
                abrirGrupo(groupId);
            }

            @Override
            public void onErro(String mensagem) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------------
    // Entrar com código ou QR
    // ------------------------------------------------------------------

    private void mostrarDialogEntrar() {
        EditText campo = novoCampo("Código de convite (6 caracteres)");
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        campo.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(20)});

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Entrar em um grupo")
                .setView(comPadding(campo))
                .setPositiveButton("Entrar", (d, w) -> entrarComCodigo(campo.getText().toString()))
                .setNeutralButton("Escanear QR", (d, w) -> abrirLeitorQr())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void abrirLeitorQr() {
        ScanOptions op = new ScanOptions();
        op.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        op.setPrompt("Aponte para o QR Code do convite");
        op.setBeepEnabled(false);
        op.setOrientationLocked(false);
        leitorQr.launch(op);
    }

    private void entrarComCodigo(String codigo) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        repo.entrarPorCodigo(codigo, user.getUid(), UserRepository.nomeDe(user), idsAtuais,
                new GroupRepository.Callback<String>() {
                    @Override
                    public void onSucesso(String groupId) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Você entrou no grupo!", Toast.LENGTH_SHORT).show();
                        abrirGrupo(groupId);
                    }

                    @Override
                    public void onErro(String mensagem) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ------------------------------------------------------------------
    // Navegação e utilidades
    // ------------------------------------------------------------------

    private void abrirGrupo(String groupId) {
        NavController nav = NavHostFragment.findNavController(this);
        if (nav.getCurrentDestination() == null
                || nav.getCurrentDestination().getId() != R.id.groupsFragment) return;
        Bundle args = new Bundle();
        args.putString(GroupDetailFragment.ARG_GROUP_ID, groupId);
        nav.navigate(R.id.action_groups_to_detail, args);
    }

    private void sair() {
        pararListener();
        FirebaseAuth.getInstance().signOut();
        irParaLogin();
    }

    private void irParaLogin() {
        NavController nav = NavHostFragment.findNavController(this);
        if (nav.getCurrentDestination() != null
                && nav.getCurrentDestination().getId() == R.id.groupsFragment) {
            nav.navigate(R.id.action_groups_to_login);
        }
    }

    private void pararListener() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    private EditText novoCampo(String dica) {
        EditText campo = new EditText(requireContext());
        campo.setHint(dica);
        campo.setSingleLine(true);
        return campo;
    }

    private FrameLayout comPadding(View filho) {
        int px = (int) (24 * getResources().getDisplayMetrics().density);
        FrameLayout caixa = new FrameLayout(requireContext());
        caixa.setPadding(px, px / 2, px, 0);
        caixa.addView(filho);
        return caixa;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
