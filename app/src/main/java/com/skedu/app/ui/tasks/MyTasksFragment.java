package com.skedu.app.ui.tasks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.skedu.app.R;
import com.skedu.app.data.GroupRepository;
import com.skedu.app.data.TaskRepository;
import com.skedu.app.data.UserRepository;
import com.skedu.app.databinding.FragmentMyTasksBinding;
import com.skedu.app.model.Group;
import com.skedu.app.model.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * "Minhas tarefas": junta as tarefas de todos os grupos do usuário.
 * Observa os grupos e, a cada mudança na lista de grupos, refaz os listeners de tarefas.
 */
public class MyTasksFragment extends Fragment {

    private FragmentMyTasksBinding binding;
    private final GroupRepository grupoRepo = new GroupRepository();
    private final TaskRepository tarefasRepo = new TaskRepository();
    private ListenerRegistration listenerGrupos;
    private TaskRepository.Cancelavel listenerTarefas;
    private TaskAdapter adapter;

    private String uid;
    private List<Group> grupos = new ArrayList<>();
    private List<String> idsEscutados = new ArrayList<>();
    private List<Task> todas = new ArrayList<>();
    private boolean temGrupos = true;
    private TarefasUi.Filtro filtro = TarefasUi.Filtro.PENDENTES;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMyTasksBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user == null ? null : user.getUid();

        adapter = new TaskAdapter(uid, true, new TaskAdapter.Acoes() {
            @Override
            public void aoAbrir(Task tarefa) {
                abrirFormulario(tarefa);
            }

            @Override
            public void aoAlternar(Task tarefa, boolean concluida) {
                FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
                if (usuario == null) return;
                tarefasRepo.alternarConclusao(tarefa, uid, UserRepository.nomeDe(usuario), concluida,
                        new GroupRepository.Callback<Void>() {
                            @Override
                            public void onSucesso(Void r) {
                            }

                            @Override
                            public void onErro(String mensagem) {
                                avisar(mensagem);
                            }
                        });
            }
        });
        binding.rvTarefas.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTarefas.setAdapter(adapter);

        binding.chipsFiltro.setOnCheckedStateChangeListener((grupoChips, ids) -> {
            filtro = TarefasUi.filtroDe(ids.isEmpty() ? View.NO_ID : ids.get(0));
            mostrar();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (uid == null) return;
        listenerGrupos = grupoRepo.observarMeusGrupos(uid, new GroupRepository.Callback<List<Group>>() {
            @Override
            public void onSucesso(List<Group> lista) {
                grupos = lista;
                temGrupos = !lista.isEmpty();
                reescutarTarefas();
                mostrar();
            }

            @Override
            public void onErro(String mensagem) {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) avisar(mensagem);
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (listenerGrupos != null) {
            listenerGrupos.remove();
            listenerGrupos = null;
        }
        pararTarefas();
        idsEscutados = new ArrayList<>();
    }

    /** Só recria os listeners se o conjunto de grupos mudou (nome/membros mudando não exige). */
    private void reescutarTarefas() {
        List<String> ids = new ArrayList<>();
        for (Group g : grupos) ids.add(g.id);
        if (listenerTarefas != null && Objects.equals(ids, idsEscutados)) return;

        pararTarefas();
        idsEscutados = ids;
        todas = new ArrayList<>();
        listenerTarefas = tarefasRepo.observarDeVarios(ids, new GroupRepository.Callback<List<Task>>() {
            @Override
            public void onSucesso(List<Task> tarefas) {
                todas = tarefas;
                mostrar();
            }

            @Override
            public void onErro(String mensagem) {
                avisar(mensagem);
            }
        });
    }

    private void pararTarefas() {
        if (listenerTarefas != null) {
            listenerTarefas.cancelar();
            listenerTarefas = null;
        }
    }

    private void mostrar() {
        if (binding == null) return;

        // nome e tamanho de cada grupo, para o cartão da tarefa
        Map<String, Group> porId = new HashMap<>();
        for (Group g : grupos) porId.put(g.id, g);
        for (Task t : todas) {
            Group g = porId.get(t.groupId);
            if (g != null) {
                t.grupoNome = g.nome;
                t.grupoMembros = (int) g.memberCount;
            }
        }

        List<Task> lista = TarefasUi.filtrar(todas, filtro, uid);
        adapter.submitList(lista);

        if (!temGrupos) {
            binding.vazio.getRoot().setVisibility(View.VISIBLE);
            binding.vazio.vazioTitulo.setText(R.string.minhas_sem_grupo_titulo);
            binding.vazio.vazioTexto.setText(R.string.minhas_sem_grupo_texto);
        } else {
            TarefasUi.aplicarVazio(binding.vazio, filtro, lista.isEmpty());
        }
    }

    private void abrirFormulario(Task tarefa) {
        NavController nav = NavHostFragment.findNavController(this);
        if (nav.getCurrentDestination() == null
                || nav.getCurrentDestination().getId() != R.id.myTasksFragment) return;
        Bundle args = new Bundle();
        args.putString(TaskFormFragment.ARG_GROUP_ID, tarefa.groupId);
        args.putString(TaskFormFragment.ARG_TASK_ID, tarefa.id);
        nav.navigate(R.id.action_mytasks_to_task_form, args);
    }

    private void avisar(String mensagem) {
        if (isAdded()) Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
