# Grão — câmera vintage para Android

App Android (Java) que mostra a interface da câmera em um WebView (`app/src/main/assets/www/index.html`)
e se atualiza sozinho a partir das versões publicadas no GitHub.

## Gerar o APK pelo celular (GitHub Actions)

1. Crie um repositório **público** no GitHub, por exemplo `grao`, marcando "Add a README".
   Ele precisa ser público para o app conseguir baixar as atualizações.
2. No repositório: **Settings → Secrets and variables → Actions → New repository secret**.
   Crie os dois segredos do arquivo `chave-do-grao.txt`:
   - `KEYSTORE_PASSWORD`
   - `KEYSTORE_BASE64`
3. **Add file → Upload files** e envie o `grao-projeto.zip` sem mudar o nome.
4. **Add file → Create new file**, com o nome `.github/workflows/build.yml`,
   e cole o conteúdo do arquivo `build.yml`.
5. Abra a aba **Actions**. Em uns 5 minutos a versão aparece em **Releases**.
   Baixe o `grao-1.0.X.apk` no celular e instale.

## Lançar uma atualização

Envie um `grao-projeto.zip` novo no repositório. A mensagem que você escrever no envio
vira o texto de novidades. O GitHub gera a versão nova, e o app instalado mostra
"Atualização disponível" na próxima vez que for aberto, ou ao tocar em "Procurar atualização" nos ajustes.

Na primeira atualização, o Android pede para permitir que o Grão instale apps. Isso só acontece uma vez.

## Abrir no Android Studio (opcional, no computador)

Abra a pasta `grao-android`. Para gerar um APK assinado com a mesma chave:
coloque o `grao.jks` em `app/` e adicione `keystorePassword=SUA_SENHA` no `gradle.properties`.
Depois use **Build → Build APK(s)** com a variante `release`.
Builds `debug` usam outra chave e não atualizam o app instalado pelo GitHub.

## Onde mexer

- Visual, filmes e filtros: `app/src/main/assets/www/index.html` (lista `FILMS`).
- Atualização automática: `app/src/main/java/com/grao/camera/Updater.java`.
- Salvar na galeria e permissões: `MainActivity.java`.
