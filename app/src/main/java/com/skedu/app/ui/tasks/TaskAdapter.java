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
import com.skedu.app.databinding.ItemSecaoBinding;
import com.skedu.app.databinding.ItemTaskBinding;
import com.skedu.app.model.Task;
import com.skedu.app.ui.common.Nomes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lista de tarefas (aba do grupo e "Minhas tarefas"): cartões de tarefa, opcionalmente
 * separados por cabeçalhos de tempo (Atrasadas, Hoje, Amanhã, Esta semana, Mais adiante).
 */
public class TaskAdapter extends ListAdapter<TaskAdapter.Linha, RecyclerView.ViewHolder> {

    public interface Acoes {
        void aoAbrir(Task tarefa);

        void aoAlternar(Task tarefa, boolean concluida);
    }

    /** Seções de tempo das tarefas pendentes. */
    public enum Secao {ATRASADAS, HOJE, AMANHA, SEMANA, DEPOIS}

    /** Uma linha da lista: ou um cabeçalho de seção ou uma tarefa. */
    public static final class Linha {
        final Secao secao;   // não nulo = cabeçalho
        final Task tarefa;   // não nulo = tarefa

        private Linha(Secao secao, Task tarefa) {
            this.secao = secao;
            this.tarefa = tarefa;
        }
    }

    private static final int TIPO_SECAO = 0;
    private static final int TIPO_TAREFA = 1;

    private final String uid;
    private final boolean mostrarGrupo;
    private final Acoes acoes;
    /** A lista está separada por cabeçalhos de tempo? */
    private boolean agrupado;

    public TaskAdapter(String uid, boolean mostrarGrupo, Acoes acoes) {
        super(DIFF);
        this.uid = uid;
        this.mostrarGrupo = mostrarGrupo;
        this.acoes = acoes;
    }

    /**
     * @param tarefas já filtradas e em ordem de prazo
     * @param agrupar true = insere cabeçalhos de tempo (para pendentes/urgentes); false = lista simples
     */
    public void submitTarefas(List<Task> tarefas, boolean agrupar) {
        // se o modo de agrupamento mudou, os cartões existentes precisam ser redesenhados
        boolean mudouModo = agrupado != agrupar;
        agrupado = agrupar;
        List<Linha> linhas = new ArrayList<>();
        Secao atual = null;
        for (Task t : tarefas) {
            if (agrupar) {
                Secao s = secaoDe(t);
                if (s != atual) {
                    linhas.add(new Linha(s, null));
                    atual = s;
                }
            }
            linhas.add(new Linha(null, t));
        }
        submitList(linhas);
        if (mudouModo) notifyDataSetChanged();
    }

    private static Secao secaoDe(Task t) {
        long dias = TarefasUi.diasAte(t);
        if (dias < 0) return Secao.ATRASADAS;
        if (dias == 0) return Secao.HOJE;
        if (dias == 1) return Secao.AMANHA;
        if (dias <= 7) return Secao.SEMANA;
        return Secao.DEPOIS;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).secao != null ? TIPO_SECAO : TIPO_TAREFA;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TIPO_SECAO) {
            return new VHSecao(ItemSecaoBinding.inflate(inflater, parent, false));
        }
        return new VHTarefa(ItemTaskBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Linha linha = getItem(position);
        if (holder instanceof VHSecao) {
            mostrarSecao(((VHSecao) holder).b, linha.secao);
        } else {
            mostrarTarefa(((VHTarefa) holder).b, linha.tarefa);
        }
    }

    // ------------------------------------------------------------------

    private void mostrarSecao(ItemSecaoBinding b, Secao secao) {
        int texto;
        switch (secao) {
            case ATRASADAS:
                texto = R.string.secao_atrasadas;
                break;
            case HOJE:
                texto = R.string.secao_hoje;
                break;
            case AMANHA:
                texto = R.string.secao_amanha;
                break;
            case SEMANA:
                texto = R.string.secao_semana;
                break;
            default:
                texto = R.string.secao_depois;
        }
        b.tvSecao.setText(texto);
        // atrasadas em vermelho (o texto "Atrasadas" também diz isso)
        b.tvSecao.setTextColor(MaterialColors.getColor(b.tvSecao, secao == Secao.ATRASADAS
                ? androidx.appcompat.R.attr.colorError
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    private void mostrarTarefa(ItemTaskBinding b, Task t) {
        Context ctx = b.getRoot().getContext();
        boolean feita = t.concluidaPeloUsuario(uid);
        long dias = TarefasUi.diasAte(t);

        // ---- bloco da data: a cor conta a urgência ----
        LocalDate data = t.dataPrazo();
        b.tvDia.setText(data == null ? "–" : String.valueOf(data.getDayOfMonth()));
        b.tvMes.setText(data == null ? "" : TarefasUi.mesCurto(data));
        int fundoAttr;
        int textoAttr;
        if (feita) {
            fundoAttr = com.google.android.material.R.attr.colorSurfaceVariant;
            textoAttr = com.google.android.material.R.attr.colorOnSurfaceVariant;
        } else if (TarefasUi.atrasada(t, false)) {
            fundoAttr = com.google.android.material.R.attr.colorErrorContainer;
            textoAttr = com.google.android.material.R.attr.colorOnErrorContainer;
        } else if (dias <= 1) {
            fundoAttr = com.google.android.material.R.attr.colorTertiaryContainer;
            textoAttr = com.google.android.material.R.attr.colorOnTertiaryContainer;
        } else {
            fundoAttr = com.google.android.material.R.attr.colorSecondaryContainer;
            textoAttr = com.google.android.material.R.attr.colorOnSecondaryContainer;
        }
        int corTexto = MaterialColors.getColor(b.cardData, textoAttr);
        b.cardData.setCardBackgroundColor(MaterialColors.getColor(b.cardData, fundoAttr));
        b.tvDia.setTextColor(corTexto);
        b.tvMes.setTextColor(corTexto);

        // ---- título (riscado quando feita) ----
        b.tvTitulo.setText(t.titulo);
        int flags = b.tvTitulo.getPaintFlags();
        b.tvTitulo.setPaintFlags(feita ? flags | Paint.STRIKE_THRU_TEXT_FLAG
                : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
        b.tvTitulo.setAlpha(feita ? 0.6f : 1f);

        // ---- checkbox: remove o listener antes de mudar o estado para não disparar à toa ----
        b.cbConcluida.setOnCheckedChangeListener(null);
        b.cbConcluida.setChecked(feita);
        b.cbConcluida.setContentDescription(ctx.getString(R.string.tarefa_marcar_feita, t.titulo));
        b.cbConcluida.setOnCheckedChangeListener((v, marcada) -> acoes.aoAlternar(t, marcada));

        // ---- grupo (só em "Minhas tarefas") ----
        if (mostrarGrupo && t.grupoNome != null) {
            b.tvGrupo.setText(ctx.getString(R.string.tarefa_grupo, t.grupoNome));
            b.tvGrupo.setVisibility(View.VISIBLE);
        } else {
            b.tvGrupo.setVisibility(View.GONE);
        }

        // ---- prazo em palavras: laranja perto do vencimento, vermelho se atrasada ----
        // sob o cabeçalho "Hoje"/"Amanhã" repetir a palavra no cartão não acrescenta nada
        Secao secao = secaoDe(t);
        boolean redundante = agrupado && !feita && (secao == Secao.HOJE || secao == Secao.AMANHA);
        b.tvPrazo.setVisibility(redundante ? View.GONE : View.VISIBLE);
        b.tvPrazo.setText(TarefasUi.textoRelativo(ctx, t, feita));
        int corPrazo;
        if (TarefasUi.atrasada(t, feita)) {
            corPrazo = MaterialColors.getColor(b.tvPrazo, androidx.appcompat.R.attr.colorError);
        } else if (!feita && dias <= 1) {
            corPrazo = MaterialColors.getColor(b.tvPrazo, com.google.android.material.R.attr.colorTertiary);
        } else {
            corPrazo = MaterialColors.getColor(b.tvPrazo, com.google.android.material.R.attr.colorOnSurfaceVariant);
        }
        b.tvPrazo.setTextColor(corPrazo);

        // ---- prioridade (só quando destoa do normal) e disciplina ----
        boolean temPrioridade = !Task.NORMAL.equals(t.prioridade) && t.prioridade != null;
        boolean temDisciplina = t.temDisciplina();
        b.llMeta.setVisibility(temPrioridade || temDisciplina ? View.VISIBLE : View.GONE);

        b.tvPrioridade.setVisibility(temPrioridade ? View.VISIBLE : View.GONE);
        if (temPrioridade) {
            b.tvPrioridade.setText(TarefasUi.rotuloPrioridade(ctx, t.prioridade));
            b.tvPrioridade.setTextColor(TarefasUi.corPrioridade(ctx, t.prioridade));
            b.tvPrioridade.setBackgroundTintList(
                    ColorStateList.valueOf(TarefasUi.fundoPrioridade(ctx, t.prioridade)));
        }

        b.vCor.setVisibility(temDisciplina ? View.VISIBLE : View.GONE);
        b.tvDisciplina.setVisibility(temDisciplina ? View.VISIBLE : View.GONE);
        if (temDisciplina) {
            b.vCor.setBackgroundTintList(ColorStateList.valueOf(t.disciplinaCor));
            b.tvDisciplina.setText(t.disciplinaNome);
        }

        // ---- situação: tarefa em grupo ou quantos já fizeram ----
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

        // ---- rodapé discreto: anexos e quem criou/editou ----
        if (t.totalAnexos() > 0) {
            b.tvAnexos.setVisibility(View.VISIBLE);
            b.tvAnexos.setText(ctx.getResources().getQuantityString(R.plurals.tarefa_anexos,
                    t.totalAnexos(), t.totalAnexos()));
        } else {
            b.tvAnexos.setVisibility(View.GONE);
        }
        if (t.foiEditada() && t.editadoPorNome != null) {
            b.tvEditado.setText(ctx.getString(R.string.tarefa_editada_por, Nomes.curto(t.editadoPorNome)));
        } else {
            b.tvEditado.setText(ctx.getString(R.string.tarefa_criada_por, Nomes.curto(t.criadoPorNome)));
        }

        b.getRoot().setOnClickListener(v -> acoes.aoAbrir(t));
    }

    static class VHTarefa extends RecyclerView.ViewHolder {
        final ItemTaskBinding b;

        VHTarefa(ItemTaskBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static class VHSecao extends RecyclerView.ViewHolder {
        final ItemSecaoBinding b;

        VHSecao(ItemSecaoBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final DiffUtil.ItemCallback<Linha> DIFF = new DiffUtil.ItemCallback<Linha>() {
        @Override
        public boolean areItemsTheSame(@NonNull Linha a, @NonNull Linha b) {
            if (a.secao != null || b.secao != null) return a.secao == b.secao;
            return Objects.equals(a.tarefa.groupId, b.tarefa.groupId) && Objects.equals(a.tarefa.id, b.tarefa.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Linha la, @NonNull Linha lb) {
            if (la.secao != null) return true; // cabeçalho: só o tipo importa
            Task a = la.tarefa;
            Task b = lb.tarefa;
            return Objects.equals(a.titulo, b.titulo)
                    && Objects.equals(a.prioridade, b.prioridade)
                    && Objects.equals(a.prazo, b.prazo)
                    && Objects.equals(a.subjectId, b.subjectId)
                    && Objects.equals(a.disciplinaNome, b.disciplinaNome)
                    && a.disciplinaCor == b.disciplinaCor
                    && Objects.equals(a.concluidaPor, b.concluidaPor)
                    && Objects.equals(a.tipo, b.tipo)
                    && a.totalAnexos() == b.totalAnexos()
                    && a.concluidaGrupo == b.concluidaGrupo
                    && Objects.equals(a.concluidaGrupoPorNome, b.concluidaGrupoPorNome)
                    && Objects.equals(a.atualizadoEm, b.atualizadoEm)
                    && Objects.equals(a.editadoPorNome, b.editadoPorNome)
                    && Objects.equals(a.grupoNome, b.grupoNome)
                    && a.grupoMembros == b.grupoMembros;
        }
    };
}
