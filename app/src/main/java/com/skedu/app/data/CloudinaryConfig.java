package com.skedu.app.data;

import com.skedu.app.BuildConfig;

/**
 * Configuração do Cloudinary (envio de anexos).
 *
 * Os valores NÃO ficam no código: vêm do arquivo local.properties (ignorado pelo git),
 * lido pelo app/build.gradle.kts. Acrescente estas duas linhas a ele:
 *
 *     cloudinary.cloud_name=SEU_CLOUD_NAME
 *     cloudinary.upload_preset=SEU_UPLOAD_PRESET
 *
 *  - Cloud name: aparece no painel (console.cloudinary.com), em Settings > API Keys.
 *  - Upload preset: Settings > Upload > Upload presets > Add upload preset, com
 *    Signing mode = Unsigned. No preset, limite formatos e tamanho e use uma pasta própria.
 *
 * Nunca coloque aqui a API Secret: ela jamais deve ir dentro do app.
 * Sem esses valores o app compila normalmente e o botão de anexar avisa que não está configurado.
 */
public final class CloudinaryConfig {

    public static final String CLOUD_NAME = BuildConfig.CLOUDINARY_CLOUD_NAME;
    public static final String UPLOAD_PRESET = BuildConfig.CLOUDINARY_UPLOAD_PRESET;

    private CloudinaryConfig() {
    }

    public static boolean configurado() {
        return !CLOUD_NAME.trim().isEmpty() && !UPLOAD_PRESET.trim().isEmpty();
    }
}
