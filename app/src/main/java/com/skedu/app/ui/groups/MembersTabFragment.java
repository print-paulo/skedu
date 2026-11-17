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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.skedu.app.R;
import com.skedu.app.data.GroupRepository;
import com.skedu.app.databinding.FragmentTabMembersBinding;
import com.skedu.app.model.Group;
import com.skedu.app.ui.common.Nomes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Aba "Membros" do grupo: lista de membros, convite (código + QR) e sair do grupo. */
public class MembersTabFragment extends Fragment {

    private static final String ARG_GROUP_ID = "groupId";

    public static MembersTabFragment nova(String groupId) {
        MembersTabFragment f = new MembersTabFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        f.setArguments(args);
        return f;
    }

    private FragmentTabMembersBinding binding;
    private final GroupRepository repo = new GroupRepository();
    private ListenerRegistration listener;
    private String groupId;
    private Group grupoAtual;
    private String codigoDoQrDesenhado;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTabMembersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        groupId = requireArguments().getString(ARG_GROUP_ID);
        binding.btnCompartilhar.setOnClickListener(v -> compartilhar());
        binding.tvCodigo.setOnClickListener(v -> copiarCodigo());
        binding.btnSairGrupo.setOnClickListener(v -> confirmarSaida());
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
                // o aviso de grupo indisponível já é dado pela tela principal do grupo
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        pararListener();
    }

    private void pararListener() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    private void mostrar(Group g) {
        binding.tvMembrosTitulo.setText("Membros (" + g.memberCount + "/" + GroupRepository.MAX_MEMBROS + ")");

        // lista de nomes, ordenada; marca o criador
        List<String> linhas = new ArrayList<>();
        if (g.membrosInfo != null) {
            for (Map.Entry<String, String> e : g.membrosInfo.entrySet()) {
                // só o primeiro nome (dois, se houver outro membro com o mesmo primeiro nome)
                String nome = Nomes.curtoNoGrupo(e.getValue(), g.membrosInfo.values());
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
        startActivity(Intent.createChooser(envio, getString(R.string.detalhe_compartilhar)));
    }

    private void copiarCodigo() {
        if (grupoAtual == null || grupoAtual.codigoConvite == null) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Código do grupo", grupoAtual.codigoConvite));
        Toast.makeText(requireContext(), "Código copiado!", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------
    // Sair do grupo
    // ------------------------------------------------------------------

    private void confirmarSaida() {
        if (grupoAtual == null) return;
        boolean unico = grupoAtual.memberCount <= 1;
        String texto = unico
                ? getString(R.string.dialog_sair_texto_unico)
                : getString(R.string.dialog_sair_texto, grupoAtual.nome);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_sair_titulo)
                .setMessage(texto)
                .setNegativeButton(R.string.dialog_cancelar, null)
                .setPositiveButton(R.string.dialog_sair_confirmar, (d, w) -> sair())
                .show();
    }

    private void sair() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || grupoAtual == null) return;

        // depois de sair o usuário perde o acesso ao grupo: para de escutar antes,
        // senão o listener devolveria um erro de permissão
        pararListener();
        if (getParentFragment() instanceof GroupDetailFragment) {
            ((GroupDetailFragment) getParentFragment()).pararDeObservar();
        }

        repo.sair(grupoAtual, user.getUid(), new GroupRepository.Callback<Void>() {
            @Override
            public void onSucesso(Void r) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.grupo_saiu, Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(MembersTabFragment.this)
                        .popBackStack(R.id.groupsFragment, false);
            }

            @Override
            public void onErro(String mensagem) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
                // falhou: volta a escutar o grupo
                onStart();
                if (getParentFragment() instanceof GroupDetailFragment) {
                    ((GroupDetailFragment) getParentFragment()).voltarAObservar();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        codigoDoQrDesenhado = null;
    }
}
