package com.skedu.app;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Única Activity do app: hospeda o NavHostFragment (login -> grupos -> detalhe do grupo -> tarefa)
 * e a navegação inferior entre as duas telas principais (Grupos e Minhas tarefas).
 */
public class MainActivity extends AppCompatActivity {

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
        NavController nav = navHost.getNavController();
        NavGraph grafo = nav.getNavInflater().inflate(R.navigation.nav_graph);
        boolean logado = FirebaseAuth.getInstance().getCurrentUser() != null;
        grafo.setStartDestination(logado ? R.id.groupsFragment : R.id.loginFragment);
        nav.setGraph(grafo);

        configurarNavegacaoInferior(nav);
    }

    private void configurarNavegacaoInferior(NavController nav) {
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
            boolean principal = id == R.id.groupsFragment || id == R.id.myTasksFragment;
            barra.setVisibility(principal ? View.VISIBLE : View.GONE);
            if (principal && barra.getSelectedItemId() != id) {
                barra.getMenu().findItem(id).setChecked(true);
            }
        });
    }
}
