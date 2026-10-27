# lume órbita — guia de utilização e contribuição

QA: [0.4.0](../../qa/i18n-validation.md)

Launcher original para **Android TV e Google TV 11 ou posterior**, com Views nativas em Kotlin. Não exige root nem firmware modificado. O design e as funcionalidades mantêm-se entre idiomas.

## Idioma
Na engrenagem superior, abra **Idioma**. Automático segue o primeiro idioma compatível da TV; na ausência de correspondência, usa inglês dos Estados Unidos. A escolha manual fica guardada após reiniciar. Escolha Automático para regressar ao idioma do sistema. Só o Lume e a sua proteção de ecrã mudam: as definições do Android, outras aplicações e os títulos ou sinopses das plataformas não são traduzidos.

Idiomas e variantes regionais disponíveis: en-US, en-GB, en-CA, pt-BR, pt-PT, es-ES, fr-FR, zh-CN, hi-IN, ar, bn-BD, ru-RU, ur-PK, ja-JP, pnb-Arab-PK, mr-IN, de-DE, it-IT, ko-KR, id-ID e tr-TR. O chinês é simplificado; o punjabi ocidental usa Shahmukhi. Árabe, urdu e punjabi ocidental espelham a disposição e a navegação horizontal própria da aplicação.

## Comando e utilização diária
- As setas movem o foco. OK abre um favorito; na biblioteca, adiciona ou remove um favorito. Mantenha OK premido para mover, consultar informações ou abrir a confirmação oficial de desinstalação.
- Continuar a ver e Sugestões mudam a pré-visualização ao focar o cabeçalho. Esquerda/direita percorrem os cabeçalhos e os controlos próximos; baixo ou OK entra no cartão memorizado. Conteúdo e capas dependem do que as aplicações instaladas partilham e da autorização concedida.
- A ordem dos favoritos mantém-se. Nomes e ícones vêm das aplicações instaladas. Instalar aplicações abre a Google Play; o Lume não instala APKs silenciosamente.
- Rede abre as definições do Android. HDMI depende do fabricante; Input no comando continua a ser uma alternativa.
- Cinema escurece e aquece o launcher e suspende efeitos. Não altera a imagem da TV. O horário opcional 20h–6h segue a hora local da TV.
- As atmosferas permitem rotação diária ou escolha manual. A proteção de ecrã configura inatividade, intervalo, escurecimento e relógio. As fotografias online ficam na RAM, sem cache de fotos em disco da aplicação. Sem internet, alternam paisagens generativas. Setas, OK ou Voltar encerram. O Android controla a proteção noutras aplicações e o modo de espera.

## Instalação, atualização e recuperação
Ative a depuração autorizada nas definições oficiais. Confirme o serial do alvo antes de cada comando ADB; substitua DEVICE_SERIAL pelo dispositivo confirmado:

Antes e depois de `adb install -r`, registe e confirme o launcher predefinido e o destino do botão Home. A atualização pode alterar esta seleção em alguns dispositivos; não pressuponha que será mantida. Tenha outro launcher funcional disponível e, se necessário, reponha a escolha no seletor do Android.

```sh
adb devices
adb -s DEVICE_SERIAL install -r app-release.apk
```

A atualização deve manter o pacote e o certificado de assinatura para preservar as preferências. Mantenha a chave privada. Uma assinatura diferente não atualiza a instalação existente. Não limpe os dados para mudar de idioma.

Use o seletor oficial para escolher o Lume como ecrã inicial, apenas quando o fabricante o permitir. Teste Home a partir de outra aplicação e o regresso do repouso em separado. Antes de desinstalar o launcher predefinido, instale e selecione outro compatível e teste Home. Remova depois o Lume nas definições do Android. Não são necessárias alterações de firmware, root nem remoção de componentes do sistema. O comportamento depende do fabricante.

## Compilação, testes e licenças
Requer JDK 17, Android SDK Platform 35 e Build Tools 36.0.0. Configure JAVA_HOME e ANDROID_HOME. Na raiz do projeto, gere os recursos antes da verificação e da compilação:

```sh
python tools/build_locale_resources.py
cd src/android
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

No Windows, use gradlew.bat. O ficheiro APK fica em app/build/outputs/apk/release/app-release.apk. Na raiz do projeto:

```sh
python tools/verify_locales.py
```

As traduções estão nos catálogos common/activity e em tools/translations. Volte a gerar os recursos após alterações, mantendo os IDs e os marcadores de formatação. LocaleSmokeInstrumentation é um runner de testes; não muda o idioma global nem Home. Use um dispositivo ou emulador dedicado. Um AVD de telemóvel não certifica as integrações de TV nem o comando físico.

Código original sob MIT, copyright 2026 Joao Salazar. Consulte LICENSE e THIRD_PARTY_NOTICES.md. Fotografias, marcas e conteúdo de terceiros mantêm os respetivos direitos; autores e endereços de licença não mudam. Contas privadas de streaming não são acedidas nem traduzidas. Compatibilidade, tipos de letra e desempenho exigem validação por dispositivo.

A revisão linguística nativa e o QA por idioma continuam necessários. Resultados reais em docs/qa; este guia não declara testes concluídos. Instalação: docs/installation.md. Compilação e assinatura: docs/build.md.
