package com.skedu.app.ui.subjects;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.skedu.app.R;
import com.skedu.app.data.GroupRepository;
import com.skedu.app.data.SubjectRepository;
import com.skedu.app.databinding.DialogDisciplinaBinding;
import com.skedu.app.databinding.FragmentTabSubjectsBinding;
import com.skedu.app.model.Subject;
import com.skedu.app.ui.common.Formularios;

import java.util.List;

/** Aba "Disciplinas" do grupo: qualquer membro cria, edita e exclui. */
public class SubjectsTabFragment extends Fragment {

    private static final String ARG_GROUP_ID = "groupId";

    // cores sugeridas (ARGB) e seus nomes, para quem usa leitor de tela
    private static final int[] CORES = {
            0xFF0F766E, 0xFF2563EB, 0xFF7C3AED, 0xFFDB2777, 0xFFEA580C,
            0xFFB45309, 0xFFDC2626, 0xFF16A34A, 0xFF475569
    };
    @StringRes
    private static final int[] NOMES_CORES = {
            R.string.cor_verde_azulado, R.string.cor_azul, R.string.cor_roxo, R.string.cor_rosa,
            R.string.cor_laranja, R.string.cor_ambar, R.string.cor_vermelho, R.string.cor_verde,
            R.string.cor_cinza
    };

    public static SubjectsTabFragment nova(String groupId) {
        SubjectsTabFragment f = new SubjectsTabFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        f.setArguments(args);
        return f;
    }

    private FragmentTabSubjectsBinding binding;
    private final SubjectRepository repo = new SubjectRepository();
    private ListenerRegistration listener;
    private SubjectAdapter adapter;
    private String groupId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTabSubjectsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        groupId = requireArguments().getString(ARG_GROUP_ID);

        adapter = new SubjectAdapter(this::mostrarDialog);
        binding.rvDisciplinas.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDisciplinas.setAdapter(adapter);
        binding.fabNova.setOnClickListener(v -> mostrarDialog(null));

        binding.vazio.vazioTitulo.setText(R.string.disciplinas_vazio_titulo);
        binding.vazio.vazioTexto.setText(R.string.disciplinas_vazio_texto);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (groupId == null) return;
        listener = repo.observar(groupId, new GroupRepository.Callback<List<Subject>>() {
            @Override
            public void onSucesso(List<Subject> lista) {
                if (binding == null) return;
                adapter.submitList(lista);
                binding.vazio.getRoot().setVisibility(lista.isEmpty() ? View.VISIBLE : View.GONE);
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
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    // ------------------------------------------------------------------
    // Criar / editar
    // ------------------------------------------------------------------

    /** @param existente null = nova disciplina */
    private void mostrarDialog(@Nullable Subject existente) {
        DialogDisciplinaBinding campos = DialogDisciplinaBinding.inflate(getLayoutInflater());
        Formularios.limparErroAoDigitar(campos.tilNome, campos.etNome);

        int[] corEscolhida = {existente != null ? existente.cor : CORES[0]};
        if (existente != null) campos.etNome.setText(existente.nome);
        desenharCores(campos.llCores, corEscolhida);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existente == null ? R.string.dialog_disciplina_nova : R.string.dialog_disciplina_editar)
                .setView(campos.getRoot())
                .setPositiveButton(R.string.dialog_salvar, null)
                .setNegativeButton(R.string.dialog_cancelar, null);
        if (existente != null) {
            builder.setNeutralButton(R.string.dialog_excluir, (d, w) -> confirmarExclusao(existente));
        }
        AlertDialog dialog = builder.create();

        // listener próprio no botão para o diálogo não fechar quando o nome está vazio
        dialog.setOnShowListener(d -> {
            View salvar = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            salvar.setOnClickListener(v -> {
                String nome = Formularios.texto(campos.etNome);
                if (nome.isEmpty()) {
                    campos.tilNome.setError(getString(R.string.erro_nome_disciplina));
                    return;
                }
                dialog.dismiss();
                salvar(existente, nome, corEscolhida[0]);
            });
            Formularios.aoConcluir(campos.etNome, salvar::performClick);
        });
        dialog.show();
    }

    private void salvar(@Nullable Subject existente, String nome, int cor) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        GroupRepository.Callback<Void> resultado = new GroupRepository.Callback<Void>() {
            @Override
            public void onSucesso(Void r) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.disciplina_salva, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onErro(String mensagem) {
                avisar(mensagem);
            }
        };
        if (existente == null) {
            repo.criar(groupId, nome, cor, user.getUid(), resultado);
        } else {
            repo.atualizar(groupId, existente.id, nome, cor, resultado);
        }
    }

    // ------------------------------------------------------------------
    // Excluir
    // ------------------------------------------------------------------

    private void confirmarExclusao(Subject disciplina) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_excluir_disciplina_titulo)
                .setMessage(R.string.dialog_excluir_disciplina_texto)
                .setNegativeButton(R.string.dialog_cancelar, null)
                .setPositiveButton(R.string.dialog_excluir, (d, w) ->
                        repo.excluir(groupId, disciplina.id, new GroupRepository.Callback<Void>() {
                            @Override
                            public void onSucesso(Void r) {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(), R.string.disciplina_excluida,
                                            Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onErro(String mensagem) {
                                avisar(mensagem);
                            }
                        }))
                .show();
    }

    // ------------------------------------------------------------------
    // Seletor de cor (bolinhas)
    // ------------------------------------------------------------------

    private void desenharCores(LinearLayout caixa, int[] escolhida) {
        caixa.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;
        int tamanho = (int) (48 * dp);
        int margem = (int) (4 * dp);
        int folga = (int) (12 * dp);

        for (int i = 0; i < CORES.length; i++) {
            int cor = CORES[i];
            boolean marcada = cor == escolhida[0];
            String nome = getString(NOMES_CORES[i]);

            GradientDrawable bolinha = new GradientDrawable();
            bolinha.setShape(GradientDrawable.OVAL);
            bolinha.setColor(cor);

            ImageView v = new ImageView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tamanho, tamanho);
            lp.setMargins(margem, margem, margem, margem);
            v.setLayoutParams(lp);
            v.setBackground(bolinha);
            v.setPadding(folga, folga, folga, folga);
            if (marcada) {
                // o "check" mostra qual está escolhida (não depende só da cor)
                v.setImageResource(R.drawable.ic_skedu_mark);
                v.setColorFilter(Color.WHITE);
            }
            v.setContentDescription(getString(marcada ? R.string.cor_desc_selecionada : R.string.cor_desc, nome));
            v.setOnClickListener(x -> {
                escolhida[0] = cor;
                desenharCores(caixa, escolhida);
            });
            caixa.addView(v);
        }
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
