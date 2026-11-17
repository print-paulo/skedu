package com.skedu.app.ui.tasks;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.skedu.app.R;
import com.skedu.app.data.CloudinaryConfig;
import com.skedu.app.data.CloudinaryUploader;
import com.skedu.app.data.GroupRepository;
import com.skedu.app.data.SubjectRepository;
import com.skedu.app.data.TaskRepository;
import com.skedu.app.data.UserRepository;
import com.skedu.app.databinding.FragmentTaskFormBinding;
import com.skedu.app.databinding.ItemAnexoBinding;
import com.skedu.app.model.Anexo;
import com.skedu.app.model.Subject;
import com.skedu.app.model.Task;
import com.skedu.app.ui.common.Formularios;
import com.skedu.app.ui.common.Nomes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Criar ou editar uma tarefa. Qualquer membro do grupo pode; quem editou fica registrado.
 * Sem argumento "taskId" = nova tarefa.
 */
public class TaskFormFragment extends Fragment {

    public static final String ARG_GROUP_ID = "groupId";
    public static final String ARG_TASK_ID = "taskId";

    private FragmentTaskFormBinding binding;
    private final TaskRepository tarefasRepo = new TaskRepository();
    private final SubjectRepository disciplinasRepo = new SubjectRepository();
    private ListenerRegistration listenerDisciplinas;

    private String groupId;
    private String taskId;
    private boolean editando;

    private List<Subject> disciplinas = new ArrayList<>();
    private String subjectIdEscolhido = "";
    private LocalDate prazoEscolhido;
    private Task existente;

    // ---- anexos (Cloudinary) ----

    /** Um anexo na tela: já salvo na tarefa, enviando agora ou com erro. */
    private static final class ItemAnexo {
        Anexo anexo;            // preenchido quando o envio termina (ou veio da tarefa)
        String nome;            // nome provisório enquanto envia
        int percentual;
        boolean enviando;
        String erro;
        CloudinaryUploader.Envio envio;
        ItemAnexoBinding linha;
    }

    private final List<ItemAnexo> itensAnexo = new ArrayList<>();

    /** Há algo preenchido que ainda não foi salvo? (pede confirmação ao sair) */
    private boolean alterado;
    /** Carregando/salvando: bloqueia o botão de anexar. */
    private boolean ocupado;

    // precisa ser registrado antes de o fragment iniciar; por isso fica no campo
    private final ActivityResultLauncher<String[]> seletorArquivos =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(),
                    this::aoEscolherArquivos);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTaskFormBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        groupId = requireArguments().getString(ARG_GROUP_ID);
        taskId = requireArguments().getString(ARG_TASK_ID);
        editando = taskId != null;

        binding.tvFormTitulo.setText(editando ? R.string.form_titulo_editar : R.string.form_titulo_nova);
        binding.btnExcluir.setVisibility(editando ? View.VISIBLE : View.GONE);

        binding.btnVoltar.setOnClickListener(v -> tentarVoltar());
        // o gesto/botão "voltar" do sistema passa pela mesma confirmação
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        tentarVoltar();
                    }
                });

        // qualquer mudança nos campos marca o formulário como "não salvo"
        Formularios.aoMudar(binding.etTitulo, () -> alterado = true);
        Formularios.aoMudar(binding.etDescricao, () -> alterado = true);
        binding.toggleTipo.addOnButtonCheckedListener((g, id, marcado) -> alterado = true);
        binding.togglePrioridade.addOnButtonCheckedListener((g, id, marcado) -> alterado = true);
        binding.etPrazo.setOnClickListener(v -> abrirCalendario());
        binding.tilPrazo.setEndIconOnClickListener(v -> abrirCalendario());
        binding.btnSalvar.setOnClickListener(v -> salvar());
        binding.btnExcluir.setOnClickListener(v -> confirmarExclusao());
        binding.btnAddAnexo.setOnClickListener(v -> escolherArquivos());
        desenharAnexos();

        Formularios.limparErroAoDigitar(binding.tilTitulo, binding.etTitulo);

        // texto de ajuda acompanha o tipo escolhido
        binding.toggleTipo.addOnButtonCheckedListener((grupo, id, marcado) -> {
            if (marcado) atualizarAjudaTipo();
        });
        atualizarAjudaTipo();

        montarDropdown();

        if (editando) {
            carregando(true);
            tarefasRepo.buscar(groupId, taskId, new GroupRepository.Callback<Task>() {
                @Override
                public void onSucesso(Task t) {
                    if (binding == null) return;
                    carregando(false);
                    preencher(t);
                }

                @Override
                public void onErro(String mensagem) {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
                    voltar();
                }
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (groupId == null) return;
        listenerDisciplinas = disciplinasRepo.observar(groupId, new GroupRepository.Callback<List<Subject>>() {
            @Override
            public void onSucesso(List<Subject> lista) {
                if (binding == null) return;
                disciplinas = lista;
                atualizarDropdown();
            }

            @Override
            public void onErro(String mensagem) {
                if (isAdded()) Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (listenerDisciplinas != null) {
            listenerDisciplinas.remove();
            listenerDisciplinas = null;
        }
    }

    // ------------------------------------------------------------------
    // Campos
    // ------------------------------------------------------------------

    private void preencher(Task t) {
        existente = t;

        itensAnexo.clear();
        if (t.anexos != null) {
            for (Anexo a : t.anexos) {
                ItemAnexo item = new ItemAnexo();
                item.anexo = a;
                itensAnexo.add(item);
            }
        }
        desenharAnexos();

        binding.etTitulo.setText(t.titulo);
        binding.etDescricao.setText(t.descricao);
        subjectIdEscolhido = t.subjectId == null ? "" : t.subjectId;
        atualizarDropdown();

        prazoEscolhido = t.dataPrazo();
        mostrarPrazo();

        // o tipo é definido na criação: ao editar só mostra
        binding.toggleTipo.check(t.ehDoGrupo() ? R.id.btn_tipo_grupo : R.id.btn_tipo_individual);
        binding.btnTipoIndividual.setEnabled(false);
        binding.btnTipoGrupo.setEnabled(false);
        binding.tvTipoAjuda.setText(getString(R.string.tipo_fixo_ajuda));

        if (Task.URGENTE.equals(t.prioridade)) {
            binding.togglePrioridade.check(R.id.btn_urgente);
        } else if (Task.IMPORTANTE.equals(t.prioridade)) {
            binding.togglePrioridade.check(R.id.btn_importante);
        } else {
            binding.togglePrioridade.check(R.id.btn_normal);
        }

        // quem criou e quem editou por último
        StringBuilder historico = new StringBuilder();
        historico.append(getString(R.string.historico_criada, Nomes.curto(t.criadoPorNome),
                TarefasUi.formatarDataHora(t.criadoEm)));
        if (t.foiEditada()) {
            historico.append("\n").append(getString(R.string.historico_editada, Nomes.curto(t.editadoPorNome),
                    TarefasUi.formatarDataHora(t.atualizadoEm)));
        }
        binding.tvHistorico.setText(historico);
        binding.tvHistorico.setVisibility(View.VISIBLE);

        // preencher os campos dispara os "ouvintes de mudança": o que veio do servidor não conta
        alterado = false;
    }

    private void montarDropdown() {
        binding.etDisciplina.setOnItemClickListener((parent, v, posicao, id) -> {
            // posição 0 = "Sem disciplina"
            subjectIdEscolhido = posicao == 0 ? "" : disciplinas.get(posicao - 1).id;
            alterado = true;
        });
        atualizarDropdown();
    }

    private void atualizarDropdown() {
        List<String> nomes = new ArrayList<>();
        nomes.add(getString(R.string.disciplina_nenhuma));
        String selecionada = nomes.get(0);
        for (Subject s : disciplinas) {
            nomes.add(s.nome);
            if (s.id.equals(subjectIdEscolhido)) selecionada = s.nome;
        }
        binding.etDisciplina.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, nomes));
        // false = não filtra a lista pelo texto atual
        binding.etDisciplina.setText(selecionada, false);
    }

    private void abrirCalendario() {
        LocalDate base = prazoEscolhido != null ? prazoEscolhido : LocalDate.now();
        // o seletor trabalha com meia-noite em UTC
        long selecao = base.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        MaterialDatePicker<Long> seletor = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.datepicker_titulo)
                .setSelection(selecao)
                .build();
        seletor.addOnPositiveButtonClickListener(ms -> {
            prazoEscolhido = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate();
            alterado = true;
            mostrarPrazo();
        });
        seletor.show(getChildFragmentManager(), "prazo");
    }

    private void mostrarPrazo() {
        if (binding == null) return;
        binding.etPrazo.setText(TarefasUi.formatarData(prazoEscolhido));
        binding.tilPrazo.setError(null);
    }

    private boolean tipoGrupoEscolhido() {
        return binding.toggleTipo.getCheckedButtonId() == R.id.btn_tipo_grupo;
    }

    private void atualizarAjudaTipo() {
        if (binding == null || editando) return;
        binding.tvTipoAjuda.setText(tipoGrupoEscolhido()
                ? R.string.tipo_grupo_ajuda : R.string.tipo_individual_ajuda);
    }

    private String prioridadeEscolhida() {
        int id = binding.togglePrioridade.getCheckedButtonId();
        if (id == R.id.btn_urgente) return Task.URGENTE;
        if (id == R.id.btn_importante) return Task.IMPORTANTE;
        return Task.NORMAL;
    }

    // ------------------------------------------------------------------
    // Salvar / excluir
    // ------------------------------------------------------------------

    private void salvar() {
        String titulo = Formularios.texto(binding.etTitulo);
        binding.tilTitulo.setError(null);
        binding.tilPrazo.setError(null);

        if (titulo.isEmpty()) {
            binding.tilTitulo.setError(getString(R.string.erro_titulo));
            return;
        }
        if (prazoEscolhido == null) {
            binding.tilPrazo.setError(getString(R.string.erro_prazo));
            return;
        }
        // não salva com arquivo ainda subindo (senão ele ficaria de fora da tarefa)
        for (ItemAnexo item : itensAnexo) {
            if (item.enviando) {
                Toast.makeText(requireContext(), R.string.anexos_aguarde, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Task t = new Task();
        t.anexos = new ArrayList<>();
        for (ItemAnexo item : itensAnexo) {
            if (item.anexo != null) t.anexos.add(item.anexo);
        }
        t.titulo = titulo;
        t.descricao = Formularios.texto(binding.etDescricao);
        t.prioridade = prioridadeEscolhida();
        t.tipo = tipoGrupoEscolhido() ? Task.TIPO_GRUPO : Task.TIPO_INDIVIDUAL;
        t.subjectId = subjectIdEscolhido;
        t.disciplinaNome = "";
        t.disciplinaCor = 0;
        for (Subject s : disciplinas) {
            if (s.id.equals(subjectIdEscolhido)) {
                t.disciplinaNome = s.nome;
                t.disciplinaCor = s.cor;
            }
        }
        // prazo = fim do dia escolhido, no fuso do aparelho
        Date fimDoDia = Date.from(prazoEscolhido.atTime(LocalTime.of(23, 59, 59))
                .atZone(ZoneId.systemDefault()).toInstant());
        t.prazo = new Timestamp(fimDoDia);

        carregando(true);
        GroupRepository.Callback<Void> resultado = new GroupRepository.Callback<Void>() {
            @Override
            public void onSucesso(Void r) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.tarefa_salva, Toast.LENGTH_SHORT).show();
                voltar();
            }

            @Override
            public void onErro(String mensagem) {
                if (!isAdded() || binding == null) return;
                carregando(false);
                Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
            }
        };

        String nome = UserRepository.nomeDe(user);
        if (editando) {
            tarefasRepo.editar(groupId, taskId, t, user.getUid(), nome, resultado);
        } else {
            tarefasRepo.criar(groupId, t, user.getUid(), nome, resultado);
        }
    }

    private void confirmarExclusao() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_excluir_tarefa_titulo)
                .setMessage(R.string.dialog_excluir_tarefa_texto)
                .setNegativeButton(R.string.dialog_cancelar, null)
                .setPositiveButton(R.string.dialog_excluir, (d, w) -> excluir())
                .show();
    }

    private void excluir() {
        carregando(true);
        tarefasRepo.excluir(groupId, taskId, new GroupRepository.Callback<Void>() {
            @Override
            public void onSucesso(Void r) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.tarefa_excluida, Toast.LENGTH_SHORT).show();
                voltar();
            }

            @Override
            public void onErro(String mensagem) {
                if (!isAdded() || binding == null) return;
                carregando(false);
                Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void carregando(boolean sim) {
        ocupado = sim;
        binding.progress.setVisibility(sim ? View.VISIBLE : View.GONE);
        binding.btnSalvar.setEnabled(!sim);
        binding.btnExcluir.setEnabled(!sim);
        // enquanto a tarefa carrega não dá para anexar (o carregamento refaria a lista)
        binding.btnAddAnexo.setEnabled(!sim && contarAtivos() < Task.MAX_ANEXOS);
    }

    /** Botão/gesto de voltar: pede confirmação se houver algo não salvo. */
    private void tentarVoltar() {
        if (!alterado) {
            voltar();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_descartar_titulo)
                .setMessage(R.string.dialog_descartar_texto)
                .setNegativeButton(R.string.dialog_continuar_editando, null)
                .setPositiveButton(R.string.dialog_descartar_confirmar, (d, w) -> voltar())
                .show();
    }

    private void voltar() {
        NavHostFragment.findNavController(this).popBackStack();
    }

    // ------------------------------------------------------------------
    // Anexos
    // ------------------------------------------------------------------

    private int contarAtivos() {
        int n = 0;
        for (ItemAnexo item : itensAnexo) {
            if (item.erro == null) n++;
        }
        return n;
    }

    private void escolherArquivos() {
        if (!CloudinaryConfig.configurado()) {
            Toast.makeText(requireContext(), R.string.anexos_sem_config, Toast.LENGTH_LONG).show();
            return;
        }
        if (contarAtivos() >= Task.MAX_ANEXOS) {
            Toast.makeText(requireContext(), R.string.anexos_limite, Toast.LENGTH_SHORT).show();
            return;
        }
        seletorArquivos.launch(new String[]{"*/*"});
    }

    private void aoEscolherArquivos(List<Uri> uris) {
        if (binding == null || uris == null || uris.isEmpty()) return;

        for (Uri uri : uris) {
            if (contarAtivos() >= Task.MAX_ANEXOS) {
                Toast.makeText(requireContext(), R.string.anexos_limite, Toast.LENGTH_SHORT).show();
                break;
            }
            ItemAnexo item = new ItemAnexo();
            item.nome = uri.getLastPathSegment();
            item.enviando = true;
            itensAnexo.add(item);
            // tarefa nova ainda não existe: o anexo só vale se salvar (pede confirmação ao sair)
            if (!editando) alterado = true;

            item.envio = CloudinaryUploader.enviar(requireContext(), uri, new CloudinaryUploader.Ouvinte() {
                @Override
                public void aoProgredir(int percentual) {
                    item.percentual = percentual;
                    if (binding != null && item.linha != null) mostrarLinha(item);
                }

                @Override
                public void aoConcluir(Anexo anexo) {
                    item.anexo = anexo;
                    item.enviando = false;
                    item.envio = null;
                    if (binding == null) return;
                    desenharAnexos();
                    // tarefa que já existe: o arquivo entra nela agora, sem esperar o "Salvar"
                    if (editando) persistirAnexos();
                }

                @Override
                public void aoFalhar(String mensagem) {
                    item.erro = mensagem;
                    item.enviando = false;
                    item.envio = null;
                    if (binding != null) desenharAnexos();
                }
            });
        }
        desenharAnexos();
    }

    /** Refaz a lista de anexos (chamado quando algo entra, sai ou termina de enviar). */
    private void desenharAnexos() {
        if (binding == null) return;
        binding.llAnexos.removeAllViews();
        for (ItemAnexo item : itensAnexo) {
            ItemAnexoBinding linha = ItemAnexoBinding.inflate(getLayoutInflater(), binding.llAnexos, false);
            item.linha = linha;
            mostrarLinha(item);

            linha.btnRemover.setOnClickListener(v -> {
                if (item.envio != null) item.envio.cancelar();
                boolean estavaSalvo = item.anexo != null;
                itensAnexo.remove(item);
                desenharAnexos();
                if (editando && estavaSalvo) persistirAnexos();
            });
            linha.getRoot().setOnClickListener(v -> {
                if (item.anexo != null) abrirAnexo(item.anexo);
            });
            binding.llAnexos.addView(linha.getRoot());
        }
        binding.tvAnexosTitulo.setText(getString(R.string.anexos_titulo, contarAtivos(), Task.MAX_ANEXOS));
        binding.btnAddAnexo.setEnabled(!ocupado && contarAtivos() < Task.MAX_ANEXOS);
    }

    /** Tarefa existente: grava a lista de anexos atual no Firestore. */
    private void persistirAnexos() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        // existente == null: a tarefa ainda não terminou de carregar
        if (user == null || !editando || existente == null) return;

        List<Anexo> lista = new ArrayList<>();
        for (ItemAnexo item : itensAnexo) {
            if (item.anexo != null) lista.add(item.anexo);
        }
        tarefasRepo.atualizarAnexos(groupId, taskId, lista, user.getUid(), UserRepository.nomeDe(user),
                new GroupRepository.Callback<Void>() {
                    @Override
                    public void onSucesso(Void r) {
                    }

                    @Override
                    public void onErro(String mensagem) {
                        if (isAdded()) Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void mostrarLinha(ItemAnexo item) {
        ItemAnexoBinding l = item.linha;
        String nome = item.anexo != null ? item.anexo.nome : item.nome;
        l.tvNome.setText(nome);
        l.btnRemover.setContentDescription(getString(R.string.anexos_remover, nome));

        if (item.erro != null) {
            l.tvDetalhe.setText(item.erro);
            l.tvDetalhe.setTextColor(MaterialColors.getColor(l.getRoot(), androidx.appcompat.R.attr.colorError));
            l.progress.setVisibility(View.GONE);
        } else if (item.enviando) {
            l.tvDetalhe.setText(getString(R.string.anexos_enviando, item.percentual));
            l.progress.setVisibility(View.VISIBLE);
            l.progress.setProgressCompat(item.percentual, true);
        } else {
            l.tvDetalhe.setText(item.anexo == null ? "" : TarefasUi.formatarTamanho(item.anexo.tamanho));
            l.progress.setVisibility(View.GONE);
        }
    }

    private void abrirAnexo(Anexo anexo) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(anexo.url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.anexos_abrir_erro, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // sair da tela cancela os envios em andamento
        for (ItemAnexo item : itensAnexo) {
            if (item.envio != null) item.envio.cancelar();
        }
        binding = null;
    }
}
