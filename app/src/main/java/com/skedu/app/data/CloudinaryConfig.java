package com.skedu.app.data;

/**
 * Configuração do Cloudinary (envio de anexos).
 *
 * Preencha com os dados do seu painel (console.cloudinary.com):
 *  - CLOUD_NAME: aparece no topo do painel / em Settings > API Keys ("Cloud name").
 *  - UPLOAD_PRESET: nome de um "Upload preset" com Signing mode = Unsigned
 *    (Settings > Upload > Upload presets > Add upload preset).
 *
 * Não coloque aqui a API Secret: ela nunca deve ir dentro do app.
 * O nome do preset não é segredo de senha, mas quem o conhece pode enviar arquivos para a sua
 * conta; por isso, no preset, limite os formatos e o tamanho e use uma pasta própria.
 */
public final class CloudinaryConfig {

    public static final String CLOUD_NAME = "";
    public static final String UPLOAD_PRESET = "";

    private CloudinaryConfig() {
    }

    public static boolean configurado() {
        return !CLOUD_NAME.trim().isEmpty() && !UPLOAD_PRESET.trim().isEmpty();
    }
}
