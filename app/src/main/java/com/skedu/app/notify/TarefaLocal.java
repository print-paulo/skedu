package com.skedu.app.notify;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Cópia mínima de uma tarefa pendente, guardada no aparelho para avaliar os lembretes. */
public class TarefaLocal {

    public String groupId;
    public String grupoNome;
    public String id;
    public String titulo;
    public long prazoMillis;
    public String prioridade;
    public String tipo;

    public LocalDate dataPrazo() {
        return Instant.ofEpochMilli(prazoMillis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** Identificador único entre grupos. */
    public String chave() {
        return groupId + "/" + id;
    }
}
