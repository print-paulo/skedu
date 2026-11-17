package com.skedu.app.ui.subjects;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.skedu.app.databinding.ItemSubjectBinding;
import com.skedu.app.model.Subject;

import java.util.Objects;

public class SubjectAdapter extends ListAdapter<Subject, SubjectAdapter.VH> {

    public interface AoClicar {
        void aoClicar(Subject disciplina);
    }

    private final AoClicar aoClicar;

    public SubjectAdapter(AoClicar aoClicar) {
        super(DIFF);
        this.aoClicar = aoClicar;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemSubjectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Subject s = getItem(position);
        holder.b.tvNome.setText(s.nome);
        holder.b.vCor.setBackgroundTintList(ColorStateList.valueOf(s.cor));
        holder.b.getRoot().setOnClickListener(v -> aoClicar.aoClicar(s));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemSubjectBinding b;

        VH(ItemSubjectBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final DiffUtil.ItemCallback<Subject> DIFF = new DiffUtil.ItemCallback<Subject>() {
        @Override
        public boolean areItemsTheSame(@NonNull Subject a, @NonNull Subject b) {
            return Objects.equals(a.id, b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Subject a, @NonNull Subject b) {
            return Objects.equals(a.nome, b.nome) && a.cor == b.cor;
        }
    };
}
