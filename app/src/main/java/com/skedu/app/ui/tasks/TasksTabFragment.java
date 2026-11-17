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
import com.skedu.app.databinding.FragmentTabTasksBinding;
import com.skedu.app.model.Group;
import com.skedu.app.model.Task;

import java.util.ArrayList;
import java.util.List;

/** Aba "Tarefas" do grupo: lista com filtros e botão de nova tarefa. */
public class TasksTabFragment extends Fragment {

    private static final String ARG_GROUP_ID = "groupId";

    public static TasksTabFragment nova(String groupId) {
        TasksTabFragment f = new TasksTabFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        f.setArguments(args);
        return f;
    }

    private FragmentTabTasksBinding binding;
    private final TaskRepository tarefasRepo = new TaskRepository();
    private final GroupRepository grupoRepo = new GroupRepository();
    private ListenerRegistration listenerTarefas;
    private ListenerRegistration listenerGrupo;
    private TaskAdapter adapter;

    private String groupId;
    private String uid;
    private Group grupo;
    private List<Task> todas = new ArrayList<>();
    private TarefasUi.Filtro filtro = TarefasUi.Filtro.PENDENTES;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTabTasksBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        groupId = requireArguments().getString(ARG_GROUP_ID);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user == null ? null : user.getUid();

        adapter = new TaskAdapter(uid, false, new TaskAdapter.Acoes() {
            @Override
            public void aoAbrir(Task tarefa) {
                abrirFormulario(tarefa.id);
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
        binding.fabNova.setOnClickListener(v -> abrirFormulario(null));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (groupId == null) return;

        listenerGrupo = grupoRepo.observarGrupo(groupId, new GroupRepository.Callback<Group>() {
            @Override
            public void onSucesso(Group g) {
                grupo = g;
                mostrar();
            }

            @Override
            public void onErro(String mensagem) {
                // o aviso de grupo indisponível já é dado pela tela principal do grupo
            }
        });

        listenerTarefas = tarefasRepo.observarDoGrupo(groupId, new GroupRepository.Callback<List<Task>>() {
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

    @Override
    public void onStop() {
        super.onStop();
        if (listenerGrupo != null) {
            listenerGrupo.remove();
            listenerGrupo = null;
        }
        if (listenerTarefas != null) {
            listenerTarefas.remove();
            listenerTarefas = null;
        }
    }

    private void mostrar() {
        if (binding == null) return;
        if (grupo != null) {
            for (Task t : todas) {
                t.grupoNome = grupo.nome;
                t.grupoMembros = (int) grupo.memberCount;
            }
        }
        List<Task> lista = TarefasUi.filtrar(todas, filtro, uid);
        // pendentes/urgentes agrupam por tempo; "feitas" é lista simples
        adapter.submitTarefas(lista, filtro != TarefasUi.Filtro.FEITAS);
        TarefasUi.aplicarVazio(binding.vazio, filtro, lista.isEmpty());
    }

    private void abrirFormulario(@Nullable String taskId) {
        NavController nav = NavHostFragment.findNavController(this);
        if (nav.getCurrentDestination() == null
                || nav.getCurrentDestination().getId() != R.id.groupDetailFragment) return;
        Bundle args = new Bundle();
        args.putString(TaskFormFragment.ARG_GROUP_ID, groupId);
        if (taskId != null) args.putString(TaskFormFragment.ARG_TASK_ID, taskId);
        nav.navigate(R.id.action_detail_to_task_form, args);
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
