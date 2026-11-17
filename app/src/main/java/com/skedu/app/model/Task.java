package com.skedu.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Documento: groups/{groupId}/tasks/{taskId}.
 * A tarefa é uma só para o grupo; "concluída" é individual (lista concluidaPor).
 * O nome/cor da disciplina ficam copiados aqui para a tela "Minhas tarefas" não
 * precisar ler as disciplinas de cada grupo.
 */
@IgnoreExtraProperties
public class Task {

    public static final String URGENTE = "urgente";
    public static final String IMPORTANTE = "importante";
    public static final String NORMAL = "normal";

    public static final int MAX_ANEXOS = 10;

    /** Cada pessoa marca a sua como feita (padrão; tarefas antigas, sem o campo, são assim). */
    public static final String TIPO_INDIVIDUAL = "individual";
    /** Uma marcação vale para o grupo inteiro. */
    public static final String TIPO_GRUPO = "grupo";

    @DocumentId
    public String id;

    public String titulo;
    public String descricao;

    public String subjectId;
    public String disciplinaNome;
    public int disciplinaCor;

    public String prioridade;

    /** Prazo de entrega (sempre o fim do dia escolhido). */
    public Timestamp prazo;

    public String criadoPor;
    public String criadoPorNome;
    public Timestamp criadoEm;

    public String editadoPor;
    public String editadoPorNome;
    public Timestamp atualizadoEm;

    /** Arquivos anexados (Cloudinary), no máximo {@link #MAX_ANEXOS}. Tarefas antigas: nulo. */
    public List<Anexo> anexos;

    /** "individual" ou "grupo". Não muda depois de criada. */
    public String tipo;

    /** Tarefa individual: UIDs de quem já fez. */
    public List<String> concluidaPor;

    /** Tarefa de grupo: uma marcação vale para todos. */
    public boolean concluidaGrupo;
    public String concluidaGrupoPor;
    public String concluidaGrupoPorNome;
    public Timestamp concluidaGrupoEm;

    // ---- preenchidos só no app, não vão para o Firestore ----

    @Exclude
    public String groupId;
    @Exclude
    public String grupoNome;
    @Exclude
    public int grupoMembros;

    public Task() {
        // construtor vazio exigido pelo Firestore
    }

    public int totalAnexos() {
        return anexos == null ? 0 : anexos.size();
    }

    public boolean ehDoGrupo() {
        return TIPO_GRUPO.equals(tipo);
    }

    /** Está feita para este usuário? (tarefa de grupo: feita para todos) */
    public boolean concluidaPeloUsuario(String uid) {
        if (ehDoGrupo()) return concluidaGrupo;
        return uid != null && concluidaPor != null && concluidaPor.contains(uid);
    }

    public int totalConcluidas() {
        return concluidaPor == null ? 0 : concluidaPor.size();
    }

    public boolean temDisciplina() {
        return subjectId != null && !subjectId.isEmpty();
    }

    /** Foi editada depois de criada? */
    public boolean foiEditada() {
        return criadoEm != null && atualizadoEm != null && criadoEm.compareTo(atualizadoEm) != 0;
    }

    /** Dia do prazo no fuso do aparelho; null se não tiver prazo. */
    public LocalDate dataPrazo() {
        if (prazo == null) return null;
        return prazo.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
