package com.skedu.app;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.auth.FirebaseAuth;

/** Única Activity do app: hospeda o NavHostFragment (login -> grupos -> detalhe do grupo). */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // respeita barra de status, de navegação e teclado
        View raiz = findViewById(R.id.nav_host);
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
    }
}
