package com.skedu.app.ui.groups;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.firestore.ListenerRegistration;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.skedu.app.data.GroupRepository;
import com.skedu.app.databinding.FragmentGroupDetailBinding;
import com.skedu.app.model.Group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tela do grupo: nome, membros e convite (código + QR).
 * Nos próximos lotes entram aqui as abas de Tarefas e Disciplinas.
 */
public class GroupDetailFragment extends Fragment {

    public static final String ARG_GROUP_ID = "groupId";

    private FragmentGroupDetailBinding binding;
    private GroupRepository repo;
    private ListenerRegistration listener;
    private String groupId;
    private Group grupoAtual;
    private String codigoDoQrDesenhado;

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
        binding.btnCompartilhar.setOnClickListener(v -> compartilhar());
        binding.tvCodigo.setOnClickListener(v -> copiarCodigo());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (groupId == null) return;
        listener = repo.observarGrupo(groupId, new GroupRepository.Callback<Group>() {
            @Override
            public void onSucesso(Group grupo) {
                if (binding == null) return;
                grupoAtual = grupo;
                mostrar(grupo);
            }

            @Override
            public void onErro(String mensagem) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
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

    private void mostrar(Group g) {
        binding.tvNome.setText(g.nome);
        binding.tvMembrosTitulo.setText("Membros (" + g.memberCount + "/" + GroupRepository.MAX_MEMBROS + ")");

        // lista de nomes, ordenada; marca o criador
        List<String> linhas = new ArrayList<>();
        if (g.membrosInfo != null) {
            for (Map.Entry<String, String> e : g.membrosInfo.entrySet()) {
                String nome = e.getValue() == null ? "Sem nome" : e.getValue();
                boolean criador = e.getKey().equals(g.criadoPor);
                linhas.add("• " + nome + (criador ? " (criador)" : ""));
            }
        }
        Collections.sort(linhas, String.CASE_INSENSITIVE_ORDER);
        binding.tvMembros.setText(String.join("\n", linhas));

        binding.tvCodigo.setText(g.codigoConvite);
        desenharQr(g.codigoConvite);
    }

    private void desenharQr(String codigo) {
        if (codigo == null || codigo.equals(codigoDoQrDesenhado)) return;
        try {
            Bitmap bmp = new BarcodeEncoder().encodeBitmap(
                    GroupRepository.QR_PREFIXO + codigo, BarcodeFormat.QR_CODE, 600, 600);
            binding.ivQr.setImageBitmap(bmp);
            codigoDoQrDesenhado = codigo;
        } catch (WriterException e) {
            Toast.makeText(requireContext(), "Não foi possível gerar o QR Code.", Toast.LENGTH_SHORT).show();
        }
    }

    private void compartilhar() {
        if (grupoAtual == null) return;
        Intent envio = new Intent(Intent.ACTION_SEND);
        envio.setType("text/plain");
        envio.putExtra(Intent.EXTRA_TEXT, "Entre no meu grupo \"" + grupoAtual.nome
                + "\" no Skedu! Código de convite: " + grupoAtual.codigoConvite);
        startActivity(Intent.createChooser(envio, "Compartilhar convite"));
    }

    private void copiarCodigo() {
        if (grupoAtual == null || grupoAtual.codigoConvite == null) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Código do grupo", grupoAtual.codigoConvite));
        Toast.makeText(requireContext(), "Código copiado!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        codigoDoQrDesenhado = null;
    }
}
