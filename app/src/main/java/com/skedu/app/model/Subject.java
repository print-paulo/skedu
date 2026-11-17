package com.skedu.app.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

/** Documento: groups/{groupId}/subjects/{subjectId}. Disciplinas são por grupo. */
@IgnoreExtraProperties
public class Subject {

    @DocumentId
    public String id;

    public String nome;

    /** Cor em ARGB (ex.: 0xFF0F766E). Só decorativa: o nome sempre aparece junto. */
    public int cor;

    public String criadoPor;

    public Subject() {
        // construtor vazio exigido pelo Firestore
    }
}
