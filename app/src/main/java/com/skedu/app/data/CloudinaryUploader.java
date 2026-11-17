package com.skedu.app.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import com.skedu.app.model.Anexo;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Envia um arquivo ao Cloudinary com "unsigned upload" (sem servidor e sem API Secret no app).
 * Os callbacks sempre chegam na thread principal.
 *
 * Limitação: apagar arquivo no Cloudinary exige chamada assinada (com a API Secret), que não
 * pode ficar no app. Por isso remover um anexo só tira o link da tarefa; o arquivo continua lá.
 */
public final class CloudinaryUploader {

    /** Limite do plano gratuito do Cloudinary para imagens e arquivos em geral. */
    public static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;

    public interface Ouvinte {
        /** 0 a 100. */
        void aoProgredir(int percentual);

        void aoConcluir(Anexo anexo);

        void aoFalhar(String mensagem);
    }

    public interface Envio {
        void cancelar();
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    private CloudinaryUploader() {
    }

    public static Envio enviar(Context contexto, Uri uri, Ouvinte ouvinte) {
        Context app = contexto.getApplicationContext();
        AtomicBoolean cancelado = new AtomicBoolean(false);
        HttpURLConnection[] conexao = new HttpURLConnection[1];

        EXECUTOR.execute(() -> {
            try {
                Anexo anexo = executar(app, uri, ouvinte, cancelado, conexao);
                if (!cancelado.get()) PRINCIPAL.post(() -> ouvinte.aoConcluir(anexo));
            } catch (ErroDeEnvio e) {
                if (!cancelado.get()) PRINCIPAL.post(() -> ouvinte.aoFalhar(e.getMessage()));
            } catch (Exception e) {
                if (!cancelado.get()) {
                    PRINCIPAL.post(() -> ouvinte.aoFalhar("Falha no envio. Verifique a conexão e tente de novo."));
                }
            }
        });

        return () -> {
            cancelado.set(true);
            HttpURLConnection c = conexao[0];
            if (c != null) c.disconnect();
        };
    }

    // ------------------------------------------------------------------

    private static Anexo executar(Context app, Uri uri, Ouvinte ouvinte, AtomicBoolean cancelado,
                                  HttpURLConnection[] conexaoSaida) throws Exception {
        if (!CloudinaryConfig.configurado()) {
            throw new ErroDeEnvio("Cloudinary não configurado. Preencha CloudinaryConfig.java.");
        }

        ContentResolver cr = app.getContentResolver();
        String nome = "arquivo";
        long tamanho = -1;
        try (Cursor c = cr.query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int iNome = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int iTam = c.getColumnIndex(OpenableColumns.SIZE);
                if (iNome >= 0 && !c.isNull(iNome)) nome = c.getString(iNome);
                if (iTam >= 0 && !c.isNull(iTam)) tamanho = c.getLong(iTam);
            }
        }
        String tipo = cr.getType(uri);
        if (tipo == null) tipo = "application/octet-stream";

        if (tamanho > TAMANHO_MAXIMO) {
            throw new ErroDeEnvio("O arquivo passa de 10 MB.");
        }

        String limite = UUID.randomUUID().toString();
        String nomeSeguro = nome.replace("\"", "'").replace("\r", " ").replace("\n", " ");
        byte[] inicio = ("--" + limite + "\r\n"
                + "Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n"
                + CloudinaryConfig.UPLOAD_PRESET + "\r\n"
                + "--" + limite + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + nomeSeguro + "\"\r\n"
                + "Content-Type: " + tipo + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] fim = ("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8);

        URL url = new URL("https://api.cloudinary.com/v1_1/" + CloudinaryConfig.CLOUD_NAME.trim()
                + "/auto/upload");
        HttpURLConnection conexao = (HttpURLConnection) url.openConnection();
        conexaoSaida[0] = conexao;
        conexao.setRequestMethod("POST");
        conexao.setDoOutput(true);
        conexao.setConnectTimeout(15_000);
        conexao.setReadTimeout(60_000);
        conexao.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + limite);
        if (tamanho >= 0) {
            conexao.setFixedLengthStreamingMode(inicio.length + tamanho + fim.length);
        } else {
            conexao.setChunkedStreamingMode(16 * 1024);
        }

        long enviados = 0;
        int ultimoPercentual = -1;
        try (OutputStream saida = conexao.getOutputStream();
             InputStream entrada = cr.openInputStream(uri)) {
            if (entrada == null) throw new ErroDeEnvio("Não foi possível ler o arquivo.");
            saida.write(inicio);

            byte[] buffer = new byte[16 * 1024];
            int lidos;
            while ((lidos = entrada.read(buffer)) != -1) {
                if (cancelado.get()) throw new IOException("cancelado");
                enviados += lidos;
                if (enviados > TAMANHO_MAXIMO) throw new ErroDeEnvio("O arquivo passa de 10 MB.");
                saida.write(buffer, 0, lidos);

                if (tamanho > 0) {
                    int percentual = (int) (enviados * 100 / tamanho);
                    if (percentual != ultimoPercentual) {
                        ultimoPercentual = percentual;
                        PRINCIPAL.post(() -> {
                            if (!cancelado.get()) ouvinte.aoProgredir(percentual);
                        });
                    }
                }
            }
            saida.write(fim);
            saida.flush();
        }

        int codigo = conexao.getResponseCode();
        InputStream fluxo = codigo < 400 ? conexao.getInputStream() : conexao.getErrorStream();
        String corpo = lerTudo(fluxo);
        conexao.disconnect();

        JSONObject json = new JSONObject(corpo.isEmpty() ? "{}" : corpo);
        if (codigo >= 400 || json.has("error")) {
            String mensagem = json.has("error") ? json.getJSONObject("error").optString("message", "") : "";
            throw new ErroDeEnvio(traduzir(mensagem));
        }

        String link = json.optString("secure_url", "");
        if (link.isEmpty()) throw new ErroDeEnvio("O Cloudinary não devolveu o link do arquivo.");
        long bytes = json.optLong("bytes", enviados);
        return new Anexo(nome, link, tipo, bytes);
    }

    private static String lerTudo(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream entrada = in; ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = entrada.read(buffer)) != -1) saida.write(buffer, 0, n);
            return saida.toString("UTF-8");
        }
    }

    private static String traduzir(String mensagemDoCloudinary) {
        String m = mensagemDoCloudinary == null ? "" : mensagemDoCloudinary.toLowerCase();
        if (m.contains("preset") && (m.contains("not found") || m.contains("disabled"))) {
            return "Preset do Cloudinary não encontrado. Confira o nome em CloudinaryConfig.java.";
        }
        if (m.contains("unsigned") || m.contains("signature")) {
            return "O preset do Cloudinary precisa estar como Unsigned.";
        }
        if (m.contains("file size") || m.contains("too large")) {
            return "O arquivo passa do tamanho permitido (10 MB).";
        }
        if (m.contains("cloud_name") || m.contains("cloud name")) {
            return "Cloud name do Cloudinary inválido. Confira CloudinaryConfig.java.";
        }
        if (m.contains("format") || m.contains("not allowed")) {
            return "Esse tipo de arquivo não é aceito.";
        }
        return mensagemDoCloudinary == null || mensagemDoCloudinary.isEmpty()
                ? "Não foi possível enviar o arquivo."
                : "Não foi possível enviar: " + mensagemDoCloudinary;
    }

    /** Erro com mensagem já pronta para mostrar ao usuário. */
    private static final class ErroDeEnvio extends Exception {
        ErroDeEnvio(String mensagem) {
            super(mensagem);
        }
    }
}
