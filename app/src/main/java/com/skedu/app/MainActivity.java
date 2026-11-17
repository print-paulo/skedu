package com.skedu.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.skedu.app.notify.Agendador;
import com.skedu.app.notify.LembretePrefs;
import com.skedu.app.notify.Notificacoes;
import com.skedu.app.ui.tasks.TaskFormFragment;

/**
 * Única Activity do app: hospeda o NavHostFragment (login -> grupos -> detalhe do grupo -> tarefa)
 * e a navegação inferior entre as telas principais (Grupos, Minhas tarefas e Ajustes).
 */
public class MainActivity extends AppCompatActivity {

    private NavController nav;

    private final ActivityResultLauncher<String> pedirNotificacoes =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedida -> {
                // sem ação: a tela de Ajustes mostra o estado e permite tentar de novo
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // respeita barra de status, de navegação e teclado
        View raiz = findViewById(R.id.raiz);
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (v, insets) -> {
            Insets barras = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        // tela inicial depende de o usuário já estar logado
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (navHost == null) return;
        nav = navHost.getNavController();
        NavGraph grafo = nav.getNavInflater().inflate(R.navigation.nav_graph);
        boolean logado = FirebaseAuth.getInstance().getCurrentUser() != null;
        grafo.setStartDestination(logado ? R.id.groupsFragment : R.id.loginFragment);
        nav.setGraph(grafo);

        configurarNavegacaoInferior();

        if (savedInstanceState == null) {
            // abriu o app: confere novas tarefas/lembretes sem esperar os 15 minutos
            if (logado) Agendador.verificarAgora(this);
            abrirTarefaDaNotificacao(getIntent());
        }
    }

    /** O app já estava aberto e o usuário tocou numa notificação. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        abrirTarefaDaNotificacao(intent);
    }

    // ------------------------------------------------------------------
    // Notificação -> abre a tarefa
    // ------------------------------------------------------------------

    private void abrirTarefaDaNotificacao(Intent intent) {
        if (nav == null || intent == null) return;
        String groupId = intent.getStringExtra(Notificacoes.EXTRA_GROUP_ID);
        String taskId = intent.getStringExtra(Notificacoes.EXTRA_TASK_ID);
        if (groupId == null || taskId == null) return;

        // consome os extras para a mesma notificação não reabrir a tarefa ao girar a tela
        intent.removeExtra(Notificacoes.EXTRA_GROUP_ID);
        intent.removeExtra(Notificacoes.EXTRA_TASK_ID);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        Bundle args = new Bundle();
        args.putString(TaskFormFragment.ARG_GROUP_ID, groupId);
        args.putString(TaskFormFragment.ARG_TASK_ID, taskId);
        nav.navigate(R.id.taskFormFragment, args);
    }

    // ------------------------------------------------------------------
    // Navegação inferior
    // ------------------------------------------------------------------

    private static boolean ehTelaPrincipal(int id) {
        return id == R.id.groupsFragment || id == R.id.myTasksFragment || id == R.id.settingsFragment;
    }

    private void configurarNavegacaoInferior() {
        BottomNavigationView barra = findViewById(R.id.bottom_nav);

        barra.setOnItemSelectedListener(item -> {
            int destino = item.getItemId();
            if (nav.getCurrentDestination() != null && nav.getCurrentDestination().getId() == destino) {
                return true;
            }
            if (destino == R.id.groupsFragment) {
                // Grupos é a base da pilha: voltar para ela
                nav.popBackStack(R.id.groupsFragment, false);
            } else {
                // as outras telas principais ficam logo acima de Grupos (voltar leva a Grupos)
                nav.navigate(destino, null, new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(R.id.groupsFragment, false)
                        .build());
            }
            return true;
        });

        // a barra só aparece nas telas principais e acompanha a tela atual
        nav.addOnDestinationChangedListener((controller, destino, args) -> {
            int id = destino.getId();
            boolean principal = ehTelaPrincipal(id);
            barra.setVisibility(principal ? View.VISIBLE : View.GONE);
            if (principal && barra.getSelectedItemId() != id) {
                barra.getMenu().findItem(id).setChecked(true);
            }
            if (id == R.id.groupsFragment) explicarEPedirNotificacoes();
        });
    }

    // ------------------------------------------------------------------
    // Permissão de notificação (Android 13+)
    // ------------------------------------------------------------------

    /** Na primeira vez que o usuário chega aos grupos, explica para que serve e pede a permissão. */
    private void explicarEPedirNotificacoes() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;

        LembretePrefs prefs = new LembretePrefs(this);
        if (prefs.jaPediuPermissao()) return;
        prefs.marcarPediuPermissao();

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permissao_titulo)
                .setMessage(R.string.permissao_texto)
                .setNegativeButton(R.string.permissao_agora_nao, null)
                .setPositiveButton(R.string.permissao_permitir, (d, w) ->
                        pedirNotificacoes.launch(Manifest.permission.POST_NOTIFICATIONS))
                .show();
    }
}
