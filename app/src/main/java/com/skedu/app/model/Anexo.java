package com.skedu.app.model;

import com.google.firebase.firestore.IgnoreExtraProperties;

/**
 * Arquivo anexado a uma tarefa. O arquivo fica no Cloudinary; o Firestore guarda só
 * estes dados (dentro da lista "anexos" da tarefa, no máximo 10).
 */
@IgnoreExtraProperties
public class Anexo {

    public String nome;
    /** Link https do arquivo no Cloudinary. */
    public String url;
    /** Tipo MIME (ex.: application/pdf, image/jpeg). */
    public String tipo;
    /** Tamanho em bytes. */
    public long tamanho;

    public Anexo() {
        // construtor vazio exigido pelo Firestore
    }

    public Anexo(String nome, String url, String tipo, long tamanho) {
        this.nome = nome;
        this.url = url;
        this.tipo = tipo;
        this.tamanho = tamanho;
    }
}
