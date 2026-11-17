package com.skedu.app.ui.settings;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.skedu.app.R;
import com.skedu.app.databinding.FragmentSettingsBinding;
import com.skedu.app.notify.Agendador;
import com.skedu.app.notify.LembreteCache;
import com.skedu.app.notify.LembretePrefs;
import com.skedu.app.notify.Notificacoes;
import com.skedu.app.notify.PlanoDeLembretes;
import com.skedu.app.notify.PlanoDeLembretes.Intensidade;

import java.util.ArrayList;
import java.util.List;

/** Ajustes dos lembretes: quando e quanto avisar, e o estado das notificações do sistema. */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private LembretePrefs prefs;
    /** O Android só mostra o pedido de permissão algumas vezes; depois, só pelos ajustes do sistema. */
    private boolean permissaoNegada;

    private final ActivityResultLauncher<String> pedirPermissao =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedida -> {
                permissaoNegada = !concedida;
                atualizarEstadoNotificacoes();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        prefs = new LembretePrefs(requireContext());

        montarAntecedencia();
        montarHorario();
        montarIntensidade();

        binding.switchLembretes.setChecked(prefs.lembretesAtivos());
        binding.switchLembretes.setOnCheckedChangeListener((v, ligado) -> {
            prefs.definirLembretesAtivos(ligado);
            habilitarOpcoes(ligado);
            mudou();
        });
        habilitarOpcoes(prefs.lembretesAtivos());

        binding.switchNovas.setChecked(prefs.novasTarefasAtivas());
        binding.switchNovas.setOnCheckedChangeListener((v, ligado) -> {
            prefs.definirNovasTarefasAtivas(ligado);
            // ao religar, o "ponto de partida" recomeça: não avisa de tarefas do período desligado
            if (ligado) new LembreteCache(requireContext()).esquecerBases();
            mudou();
        });

        binding.btnNotif.setOnClickListener(v -> aoTocarNotificacoes());
        binding.btnBateria.setOnClickListener(v -> abrirAjustesDeBateria());
        binding.btnTeste.setOnClickListener(v -> enviarTeste());
    }

    @Override
    public void onResume() {
        super.onResume();
        // o usuário pode ter mudado a permissão nos ajustes do sistema e voltado
        atualizarEstadoNotificacoes();
    }

    // ------------------------------------------------------------------
    // Opções de lembrete
    // ------------------------------------------------------------------

    private void montarAntecedencia() {
        List<String> itens = new ArrayList<>();
        int escolhido = 0;
        for (int i = 0; i < LembretePrefs.OPCOES_ANTECEDENCIA.length; i++) {
            int dias = LembretePrefs.OPCOES_ANTECEDENCIA[i];
            itens.add(getResources().getQuantityString(R.plurals.ajustes_dias_antes, dias, dias));
            if (dias == prefs.antecedenciaDias()) escolhido = i;
        }
        binding.etAntecedencia.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, itens));
        // false = não filtra a lista pelo texto atual
        binding.etAntecedencia.setText(itens.get(escolhido), false);
        binding.etAntecedencia.setOnItemClickListener((p, v, posicao, id) -> {
            prefs.definirAntecedenciaDias(LembretePrefs.OPCOES_ANTECEDENCIA[posicao]);
            mudou();
        });
    }

    private void montarHorario() {
        List<String> itens = new ArrayList<>();
        for (int h = PlanoDeLembretes.HORA_BASE_MIN; h <= PlanoDeLembretes.HORA_BASE_MAX; h++) {
            itens.add(getString(R.string.ajustes_hora_formato, h));
        }
        int hora = Math.max(PlanoDeLembretes.HORA_BASE_MIN,
                Math.min(PlanoDeLembretes.HORA_BASE_MAX, prefs.horaBase()));
        binding.etHorario.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, itens));
        binding.etHorario.setText(itens.get(hora - PlanoDeLembretes.HORA_BASE_MIN), false);
        binding.etHorario.setOnItemClickListener((p, v, posicao, id) -> {
            prefs.definirHoraBase(PlanoDeLembretes.HORA_BASE_MIN + posicao);
            mudou();
        });
    }

    private void montarIntensidade() {
        Intensidade atual = prefs.intensidade();
        binding.toggleIntensidade.check(atual == Intensidade.LEVE ? R.id.btn_leve
                : atual == Intensidade.INTENSO ? R.id.btn_intenso : R.id.btn_padrao);
        mostrarAjudaIntensidade(atual);

        binding.toggleIntensidade.addOnButtonCheckedListener((g, id, marcado) -> {
            if (!marcado) return;
            Intensidade nova = id == R.id.btn_leve ? Intensidade.LEVE
                    : id == R.id.btn_intenso ? Intensidade.INTENSO : Intensidade.PADRAO;
            prefs.definirIntensidade(nova);
            mostrarAjudaIntensidade(nova);
            mudou();
        });
    }

    private void mostrarAjudaIntensidade(Intensidade i) {
        binding.tvIntensidadeAjuda.setText(i == Intensidade.LEVE ? R.string.intensidade_leve_ajuda
                : i == Intensidade.INTENSO ? R.string.intensidade_intenso_ajuda
                : R.string.intensidade_padrao_ajuda);
    }

    /** Desativa (e esmaece) as opções quando os lembretes estão desligados. */
    private void habilitarOpcoes(boolean ligado) {
        binding.llOpcoes.setAlpha(ligado ? 1f : 0.5f);
        binding.etAntecedencia.setEnabled(ligado);
        binding.tilAntecedencia.setEnabled(ligado);
        binding.etHorario.setEnabled(ligado);
        binding.tilHorario.setEnabled(ligado);
        binding.btnLeve.setEnabled(ligado);
        binding.btnPadrao.setEnabled(ligado);
        binding.btnIntenso.setEnabled(ligado);
    }

    /** Qualquer ajuste muda o que o trabalho em segundo plano precisa fazer. */
    private void mudou() {
        Agendador.reagendar(requireContext());
        Agendador.verificarAgora(requireContext());
    }

    // ------------------------------------------------------------------
    // Notificações do sistema, bateria e teste
    // ------------------------------------------------------------------

    private void atualizarEstadoNotificacoes() {
        if (binding == null) return;
        boolean permitido = Notificacoes.permitido(requireContext());
        binding.tvNotifStatus.setText(permitido ? R.string.ajustes_notif_ok : R.string.ajustes_notif_bloqueadas);
        // com tudo permitido o botão não é necessário
        binding.btnNotif.setVisibility(permitido ? View.GONE : View.VISIBLE);
        binding.btnNotif.setText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissaoNegada
                ? R.string.ajustes_notif_permitir : R.string.ajustes_notif_abrir);
    }

    private void aoTocarNotificacoes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissaoNegada) {
            pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            abrirAjustesDeNotificacao();
        }
    }

    private void abrirAjustesDeNotificacao() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        iniciarSeExistir(intent);
    }

    private void abrirAjustesDeBateria() {
        // lista de apps com otimização de bateria; não exige permissão especial
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        if (!iniciarSeExistir(intent)) {
            // alguns aparelhos não têm essa tela: cai nos detalhes do app
            iniciarSeExistir(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().getPackageName(), null)));
        }
    }

    private boolean iniciarSeExistir(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    private void enviarTeste() {
        if (!Notificacoes.permitido(requireContext())) {
            Toast.makeText(requireContext(), R.string.ajustes_teste_bloqueado, Toast.LENGTH_LONG).show();
            return;
        }
        Notificacoes.teste(requireContext());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
