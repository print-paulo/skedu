package com.skedu.app.ui.tasks;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.skedu.app.R;
import com.skedu.app.databinding.ItemTaskBinding;
import com.skedu.app.model.Task;
import com.skedu.app.ui.common.Nomes;

import java.util.Objects;

/** Lista de tarefas (aba do grupo e tela "Minhas tarefas"). */
public class TaskAdapter extends ListAdapter<Task, TaskAdapter.VH> {

    public interface Acoes {
        void aoAbrir(Task tarefa);

        void aoAlternar(Task tarefa, boolean concluida);
    }

    private final String uid;
    private final boolean mostrarGrupo;
    private final Acoes acoes;

    public TaskAdapter(String uid, boolean mostrarGrupo, Acoes acoes) {
        super(DIFF);
        this.uid = uid;
        this.mostrarGrupo = mostrarGrupo;
        this.acoes = acoes;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemTaskBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Task t = getItem(position);
        ItemTaskBinding b = holder.b;
        Context ctx = b.getRoot().getContext();
        boolean feita = t.concluidaPeloUsuario(uid);

        // título (riscado quando feita)
        b.tvTitulo.setText(t.titulo);
        int flags = b.tvTitulo.getPaintFlags();
        b.tvTitulo.setPaintFlags(feita ? flags | Paint.STRIKE_THRU_TEXT_FLAG
                : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
        b.tvTitulo.setAlpha(feita ? 0.6f : 1f);

        // checkbox: remove o listener antes de mudar o estado para não disparar à toa
        b.cbConcluida.setOnCheckedChangeListener(null);
        b.cbConcluida.setChecked(feita);
        b.cbConcluida.setContentDescription(ctx.getString(R.string.tarefa_marcar_feita, t.titulo));
        b.cbConcluida.setOnCheckedChangeListener((v, marcada) -> acoes.aoAlternar(t, marcada));

        // grupo (só na tela "Minhas tarefas")
        if (mostrarGrupo && t.grupoNome != null) {
            b.tvGrupo.setText(ctx.getString(R.string.tarefa_grupo, t.grupoNome));
            b.tvGrupo.setVisibility(View.VISIBLE);
        } else {
            b.tvGrupo.setVisibility(View.GONE);
        }

        // prioridade (texto + cor: a cor nunca é a única informação)
        b.tvPrioridade.setText(TarefasUi.rotuloPrioridade(ctx, t.prioridade));
        b.tvPrioridade.setTextColor(TarefasUi.corPrioridade(ctx, t.prioridade));
        b.tvPrioridade.setBackgroundTintList(ColorStateList.valueOf(TarefasUi.fundoPrioridade(ctx, t.prioridade)));

        // disciplina
        if (t.temDisciplina()) {
            b.vCor.setVisibility(View.VISIBLE);
            b.vCor.setBackgroundTintList(ColorStateList.valueOf(t.disciplinaCor));
            b.tvDisciplina.setVisibility(View.VISIBLE);
            b.tvDisciplina.setText(t.disciplinaNome);
        } else {
            b.vCor.setVisibility(View.GONE);
            b.tvDisciplina.setVisibility(View.GONE);
        }

        // prazo: laranja perto do vencimento, vermelho se atrasada (o texto também diz isso)
        b.tvPrazo.setText(TarefasUi.textoPrazo(ctx, t, feita));
        int corPrazo;
        if (TarefasUi.atrasada(t, feita)) {
            corPrazo = MaterialColors.getColor(b.tvPrazo, androidx.appcompat.R.attr.colorError);
        } else if (!feita && TarefasUi.diasAte(t) <= 1) {
            corPrazo = MaterialColors.getColor(b.tvPrazo, com.google.android.material.R.attr.colorTertiary);
        } else {
            corPrazo = MaterialColors.getColor(b.tvPrazo, com.google.android.material.R.attr.colorOnSurfaceVariant);
        }
        b.tvPrazo.setTextColor(corPrazo);

        // tarefa de grupo: uma marcação vale para todos; individual: quantos já fizeram
        if (t.ehDoGrupo()) {
            b.tvProgresso.setVisibility(View.VISIBLE);
            if (t.concluidaGrupo && t.concluidaGrupoPorNome != null) {
                b.tvProgresso.setText(ctx.getString(R.string.tarefa_grupo_feita_por,
                        Nomes.curto(t.concluidaGrupoPorNome)));
            } else {
                b.tvProgresso.setText(R.string.tarefa_grupo_vale_para_todos);
            }
        } else if (t.grupoMembros > 0) {
            b.tvProgresso.setVisibility(View.VISIBLE);
            b.tvProgresso.setText(ctx.getString(R.string.tarefa_progresso, t.totalConcluidas(), t.grupoMembros));
        } else {
            b.tvProgresso.setVisibility(View.GONE);
        }

        // quem criou / editou por último
        if (t.foiEditada() && t.editadoPorNome != null) {
            b.tvEditado.setText(ctx.getString(R.string.tarefa_editada_por, Nomes.curto(t.editadoPorNome)));
        } else {
            b.tvEditado.setText(ctx.getString(R.string.tarefa_criada_por, Nomes.curto(t.criadoPorNome)));
        }

        b.getRoot().setOnClickListener(v -> acoes.aoAbrir(t));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemTaskBinding b;

        VH(ItemTaskBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final DiffUtil.ItemCallback<Task> DIFF = new DiffUtil.ItemCallback<Task>() {
        @Override
        public boolean areItemsTheSame(@NonNull Task a, @NonNull Task b) {
            return Objects.equals(a.groupId, b.groupId) && Objects.equals(a.id, b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Task a, @NonNull Task b) {
            return Objects.equals(a.titulo, b.titulo)
                    && Objects.equals(a.prioridade, b.prioridade)
                    && Objects.equals(a.prazo, b.prazo)
                    && Objects.equals(a.subjectId, b.subjectId)
                    && Objects.equals(a.disciplinaNome, b.disciplinaNome)
                    && a.disciplinaCor == b.disciplinaCor
                    && Objects.equals(a.concluidaPor, b.concluidaPor)
                    && Objects.equals(a.tipo, b.tipo)
                    && a.concluidaGrupo == b.concluidaGrupo
                    && Objects.equals(a.concluidaGrupoPorNome, b.concluidaGrupoPorNome)
                    && Objects.equals(a.atualizadoEm, b.atualizadoEm)
                    && Objects.equals(a.editadoPorNome, b.editadoPorNome)
                    && Objects.equals(a.grupoNome, b.grupoNome)
                    && a.grupoMembros == b.grupoMembros;
        }
    };
}
