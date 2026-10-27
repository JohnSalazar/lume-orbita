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

Versão de avaliação **0.4.0**, com 21 idiomas. [APK e notas](https://github.com/JohnSalazar/lume-orbita/releases/tag/v0.4.0) · [Validação executada e limites](docs/qa/i18n-validation.md). A tradução não implica certificação linguística nativa; a repetição física desta versão na KONKA permanece pendente.

## Idiomas / Languages

Em Configurações > Idioma, escolha Automático ou uma língua manual. Automático usa a primeira preferência compatível da TV; fallback **English (United States)**. A escolha afeta somente o Lume. Nomes e descrições de conteúdo publicados pelas plataformas mantêm o idioma de origem.

Guias de uso, instalação, atualização, recuperação, build e limitações:

[English US](docs/i18n/en-US/README.md) · [English UK](docs/i18n/en-GB/README.md) · [English Canada](docs/i18n/en-CA/README.md) · [Português Brasil](docs/i18n/pt-BR/README.md) · [Português Portugal](docs/i18n/pt-PT/README.md) · [Español](docs/i18n/es-ES/README.md) · [Français](docs/i18n/fr-FR/README.md) · [简体中文](docs/i18n/zh-CN/README.md) · [हिन्दी](docs/i18n/hi-IN/README.md) · [العربية](docs/i18n/ar/README.md) · [বাংলা](docs/i18n/bn-BD/README.md) · [Русский](docs/i18n/ru-RU/README.md) · [اردو](docs/i18n/ur-PK/README.md) · [日本語](docs/i18n/ja-JP/README.md) · [پنجابی شاہ مکھی](docs/i18n/pnb-Arab-PK/README.md) · [मराठी](docs/i18n/mr-IN/README.md) · [Deutsch](docs/i18n/de-DE/README.md) · [Italiano](docs/i18n/it-IT/README.md) · [한국어](docs/i18n/ko-KR/README.md) · [Bahasa Indonesia](docs/i18n/id-ID/README.md) · [Türkçe](docs/i18n/tr-TR/README.md).

Mandarim usa escrita simplificada; punjabi ocidental usa Shahmukhi. Árabe, urdu e punjabi ocidental usam RTL. O seletor fica dentro do aplicativo; não modifica o idioma global do Android.

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

Na raiz, `python tools/build_locale_resources.py` regenera os recursos a partir dos catálogos revisáveis; `python tools/verify_locales.py` verifica cobertura de chaves, placeholders e guias. `python tools/verify_locales.py --self-check` testa o próprio verificador com fixtures isoladas.

[Build e assinatura](docs/build.md) · [Instalação, Home e restauração](docs/installation.md) · [Validação e limites](docs/validation.md) · [Arquitetura](docs/architecture.md)

## Limites importantes

Validado em uma KONKA com Android TV 11 e interface 720p. Outros modelos, resoluções e versões precisam de testes; não é uma garantia universal. Home e retorno do standby são comportamentos distintos e dependem do fabricante.

O app não acessa contas privadas de streaming. Cards, progresso, imagens e links dependem do que cada plataforma compartilha; podem estar ausentes ou desatualizados. A permissão opcional para conteúdo compartilhado exige autorização no Android.

Imagens online são carregadas em RAM, sem cache de fotos em disco pelo aplicativo. Após encerramento do processo, precisam ser carregadas novamente. O limite da LRU não representa toda a memória usada por Views e decodificação.

## Licença

Código original sob [MIT](LICENSE), copyright 2026 Joao Salazar. Fotografias, bibliotecas, ferramentas e marcas de terceiros mantêm seus próprios direitos: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Esta publicação contém a versão Android. Protótipos antigos, registros particulares de testes, chaves de assinatura e backups de aplicativos de terceiros não fazem parte do repositório.
