package com.skedu.app.ui.groups;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.ListenerRegistration;
import com.skedu.app.R;
import com.skedu.app.data.GroupRepository;
import com.skedu.app.databinding.FragmentGroupDetailBinding;
import com.skedu.app.model.Group;
import com.skedu.app.ui.subjects.SubjectsTabFragment;
import com.skedu.app.ui.tasks.TasksTabFragment;

/**
 * Tela do grupo: título + abas (Tarefas, Disciplinas, Membros).
 * Cada aba é um fragment filho que recebe o id do grupo.
 */
public class GroupDetailFragment extends Fragment {

    public static final String ARG_GROUP_ID = "groupId";
    private static final String ESTADO_ABA = "aba";

    private FragmentGroupDetailBinding binding;
    private GroupRepository repo;
    private ListenerRegistration listener;
    private String groupId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGroupDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repo = new GroupRepository();
        groupId = requireArguments().getString(ARG_GROUP_ID);

        binding.btnVoltar.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_tarefas));
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_disciplinas));
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_membros));
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mostrarAba(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        int aba = savedInstanceState == null ? 0 : savedInstanceState.getInt(ESTADO_ABA, 0);
        TabLayout.Tab inicial = binding.tabs.getTabAt(aba);
        if (inicial != null) inicial.select();
        // a aba 0 já vem selecionada por padrão e o listener não dispara: monta na mão
        if (aba == 0) mostrarAba(0);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) outState.putInt(ESTADO_ABA, binding.tabs.getSelectedTabPosition());
    }

    private void mostrarAba(int posicao) {
        if (!isAdded() || groupId == null) return;
        Fragment aba;
        switch (posicao) {
            case 1:
                aba = SubjectsTabFragment.nova(groupId);
                break;
            case 2:
                aba = MembersTabFragment.nova(groupId);
                break;
            default:
                aba = TasksTabFragment.nova(groupId);
        }
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.tab_container, aba)
                .commit();
    }

    // ------------------------------------------------------------------
    // Nome do grupo no título
    // ------------------------------------------------------------------

    @Override
    public void onStart() {
        super.onStart();
        observar();
    }

    @Override
    public void onStop() {
        super.onStop();
        pararDeObservar();
    }

    private void observar() {
        if (groupId == null || listener != null) return;
        listener = repo.observarGrupo(groupId, new GroupRepository.Callback<Group>() {
            @Override
            public void onSucesso(Group grupo) {
                if (binding == null) return;
                binding.tvNome.setText(grupo.nome);
            }

            @Override
            public void onErro(String mensagem) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Chamado pela aba Membros antes de sair do grupo (evita erro de permissão depois). */
    public void pararDeObservar() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    /** Chamado pela aba Membros se a saída falhar. */
    public void voltarAObservar() {
        observar();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
