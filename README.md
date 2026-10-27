# lume órbita

Launcher original para **Android TV e Google TV 11 ou posterior**, escrito em Kotlin com Views nativas. Interface de vidro translúcido, navegação por controle remoto e personalização sem root.

## Funcionalidades

- Biblioteca dos apps iniciáveis instalados, ícones do próprio aparelho e favoritos reordenáveis.
- Continuar assistindo e Sugestões com os dados que as plataformas disponibilizam ao Android.
- Navegação livre nos títulos; prévia destacada da última capa sem transferir foco. Baixo/OK entram na capa.
- Materiais leves, foco com halo, favoritos RGB, modo Cinema local e agenda opcional.
- 30 atmosferas, rotação diária e proteção de tela com fotos online ou arte procedural offline.
- Acesso às configurações Android, rede, Google Play e entradas disponibilizadas pelo fabricante.
- Seleção como Home pelas possibilidades do Android/OEM, sem interceptação global ou firmware modificado.

Versão inicial pública: **0.3.4**. [Baixar o APK e verificar SHA-256](https://github.com/JohnSalazar/lume-orbita/releases/tag/v0.3.4).

## Compilar e testar

Requisitos: JDK 17, Android SDK Platform 35 e Build Tools 36.0.0. Gradle 8.11.1 vem pelo wrapper; não é necessária instalação global.

Configure `JAVA_HOME` e `ANDROID_HOME` para as suas instalações. No Windows PowerShell:

```powershell
cd src/android
.\gradlew.bat --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

Linux/macOS:

```sh
cd src/android
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

APK local: `src/android/app/build/outputs/apk/release/app-release.apk`.

[Build e assinatura](docs/build.md) · [Instalação, Home e restauração](docs/installation.md) · [Validação e limites](docs/validation.md) · [Arquitetura](docs/architecture.md)

## Limites importantes

Validado em uma KONKA com Android TV 11 e interface 720p. Outros modelos, resoluções e versões precisam de testes; não é uma garantia universal. Home e retorno do standby são comportamentos distintos e dependem do fabricante.

O app não acessa contas privadas de streaming. Cards, progresso, imagens e links dependem do que cada plataforma compartilha; podem estar ausentes ou desatualizados. A permissão opcional para conteúdo compartilhado exige autorização no Android.

Imagens online são carregadas em RAM, sem cache de fotos em disco pelo aplicativo. Após encerramento do processo, precisam ser carregadas novamente. O limite da LRU não representa toda a memória usada por Views e decodificação.

## Licença

Código original sob [MIT](LICENSE), copyright 2026 Joao Salazar. Fotografias, bibliotecas, ferramentas e marcas de terceiros mantêm seus próprios direitos: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Esta publicação contém a versão Android. Protótipos antigos, registros particulares de testes, chaves de assinatura e backups de aplicativos de terceiros não fazem parte do repositório.
