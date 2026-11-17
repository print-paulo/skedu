package com.skedu.app.ui.groups;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.skedu.app.data.GroupRepository;
import com.skedu.app.databinding.ItemGroupBinding;
import com.skedu.app.model.Group;

import java.util.ArrayList;
import java.util.List;

public class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.VH> {

    public interface AoClicar {
        void aoClicar(Group grupo);
    }

    private final List<Group> itens = new ArrayList<>();
    private final AoClicar aoClicar;

    public GroupsAdapter(AoClicar aoClicar) {
        this.aoClicar = aoClicar;
    }

    public void atualizar(List<Group> novos) {
        itens.clear();
        itens.addAll(novos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGroupBinding b = ItemGroupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Group g = itens.get(position);
        holder.b.tvNome.setText(g.nome);
        holder.b.tvInfo.setText(g.memberCount + "/" + GroupRepository.MAX_MEMBROS
                + (g.memberCount == 1 ? " membro" : " membros")
                + " · código " + g.codigoConvite);
        holder.b.getRoot().setOnClickListener(v -> aoClicar.aoClicar(g));
    }

    @Override
    public int getItemCount() {
        return itens.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemGroupBinding b;

        VH(ItemGroupBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
