package com.skedu.app.ui.common;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Nome de pessoa para exibir na interface: só o primeiro nome.
 * O nome completo continua salvo no Firestore; isto só muda o que aparece na tela.
 */
public final class Nomes {

    private Nomes() {
    }

    /** "Paulo Henrique Nascimento Queiroz" -> "Paulo". Nulo ou vazio vira "?". */
    public static String curto(String nomeCompleto) {
        return curto(nomeCompleto, 1);
    }

    /** As primeiras {@code palavras} palavras do nome ("Paulo Henrique" com 2). */
    public static String curto(String nomeCompleto, int palavras) {
        if (nomeCompleto == null) return "?";
        String[] partes = nomeCompleto.trim().split("\\s+");
        if (partes.length == 0 || partes[0].isEmpty()) return "?";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(palavras, partes.length); i++) {
            if (i > 0) sb.append(' ');
            sb.append(partes[i]);
        }
        return sb.toString();
    }

    /**
     * Para listas com várias pessoas: primeiro nome, mas quem divide o primeiro nome com
     * outro membro aparece com dois nomes ("Paulo Henrique" e "Paulo Roberto") para não confundir.
     *
     * @param todosOsNomes nomes completos de todas as pessoas da lista
     */
    public static String curtoNoGrupo(String nomeCompleto, Collection<String> todosOsNomes) {
        String primeiro = curto(nomeCompleto, 1).toLowerCase(Locale.ROOT);
        Map<String, Integer> contagem = new HashMap<>();
        for (String n : todosOsNomes) {
            String chave = curto(n, 1).toLowerCase(Locale.ROOT);
            contagem.merge(chave, 1, Integer::sum);
        }
        Integer repetidos = contagem.get(primeiro);
        return curto(nomeCompleto, repetidos != null && repetidos > 1 ? 2 : 1);
    }
}
