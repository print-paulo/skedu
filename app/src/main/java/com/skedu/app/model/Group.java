package com.skedu.app.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.List;
import java.util.Map;

/**
 * Documento: groups/{groupId}
 * Campos públicos de propósito: o Firestore preenche direto via toObject(Group.class).
 */
@IgnoreExtraProperties
public class Group {

    @DocumentId
    public String id;

    public String nome;
    public String codigoConvite;
    public String criadoPor;

    /** UIDs de todos os membros (máx. 12). */
    public List<String> membros;

    /** uid -> nome de exibição (evita ler o perfil de cada membro). */
    public Map<String, String> membrosInfo;

    public long memberCount;

    public Group() {
        // construtor vazio exigido pelo Firestore
    }
}
