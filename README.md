# Skedu

Aplicativo Android para organizar **tarefas e entregas em grupo**. Uma pessoa cadastra a entrega uma única vez e ela aparece para todo o grupo, com lembretes antes do prazo.

Projeto da disciplina de Desenvolvimento de Aplicativos Móveis (Unicid).

## Funcionalidades

- Login com e-mail e senha ou com conta Google.
- Grupos de até 12 pessoas, com convite por código de 6 caracteres ou QR Code.
- Disciplinas por grupo e tarefas com prazo, prioridade, descrição e até 10 anexos.
- Tarefas **individuais** (cada um marca a sua) e **em grupo** (uma marcação vale para todos).
- Tela "Minhas tarefas" com as entregas de todos os grupos, agrupadas por tempo.
- Lembretes de entrega e aviso de nova tarefa, configuráveis em Ajustes.
- Modo claro e escuro.

Material detalhado para apresentação: [`docs/apresentacao-skedu.md`](docs/apresentacao-skedu.md).

## Tecnologias

Java, Android (minSdk 26), Material 3, Navigation Component, ViewBinding, WorkManager, Firebase Authentication, Cloud Firestore, Cloudinary e ZXing.

## Como rodar o projeto

O repositório **não** traz os dados do Firebase nem do Cloudinary (eles são segredos de cada conta). Faça a configuração abaixo uma vez.

### 1. Abrir no Android Studio

Abra a pasta do projeto e deixe o Gradle sincronizar. Sem os passos 2 e 3 o build falha por falta do `google-services.json`.

### 2. Firebase

1. Em [console.firebase.google.com](https://console.firebase.google.com), crie um projeto (plano gratuito Spark).
2. Em **Authentication**, ative os provedores **E-mail/senha** e **Google**.
3. Em **Firestore Database**, crie o banco.
4. Adicione um app **Android** com o pacote `com.skedu.app`.
5. Cadastre o **SHA-1** do seu computador no app (necessário para o login com Google). No Android Studio: Gradle, `Skedu`, `app`, Tasks, android, `signingReport`.
6. Baixe o `google-services.json` **depois** de ativar o Google e cadastrar o SHA-1, e salve em `app/google-services.json`. O arquivo `app/google-services.json.example` mostra o formato esperado.
7. Em Firestore, aba **Regras**, cole o conteúdo de [`firestore.rules`](firestore.rules) e publique.

Cada pessoa que rodar o app pelo próprio computador precisa cadastrar o SHA-1 dele no Firebase.

### 3. Cloudinary (anexos)

Sem esta etapa o app funciona normalmente, só o envio de anexos fica desativado.

1. Crie uma conta gratuita em [cloudinary.com](https://cloudinary.com).
2. Em **Settings, Upload, Upload presets**, crie um preset com **Signing mode = Unsigned** (de preferência limitando formatos, tamanho e usando uma pasta).
3. Em **Settings, Security**, ative **Allow delivery of PDF and ZIP files** (senão PDFs sobem mas não abrem).
4. Acrescente ao arquivo `local.properties`, na raiz do projeto (ele já é ignorado pelo git):

```properties
cloudinary.cloud_name=SEU_CLOUD_NAME
cloudinary.upload_preset=SEU_UPLOAD_PRESET
```

Nunca coloque a API Secret do Cloudinary no projeto.

## O que não vai para o git

| Arquivo | Motivo |
|---|---|
| `app/google-services.json` | Dados do seu projeto Firebase, incluindo a chave de API |
| `local.properties` | Caminho do SDK e dados do Cloudinary |
| `.idea/` | Configurações da IDE |

Se for publicar o projeto, restrinja também a chave de API do Firebase ao app (pacote e SHA-1) no [Google Cloud Console](https://console.cloud.google.com), em **APIs e serviços, Credenciais**. As regras do Firestore (`firestore.rules`) é que protegem os dados.

## Licenças

Fonte Plus Jakarta Sans sob licença SIL OFL (veja `docs/licenses/`). Ícones baseados nos Material Icons (Apache 2.0).
