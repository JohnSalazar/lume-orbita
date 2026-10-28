# lume órbita — guia de uso e contribuição

QA: [0.4.0](../../qa/i18n-validation.md) · [0.4.1](../../qa/konka-041-validation.md) · [0.4.2](../../qa/clock-locale-042.md)

Launcher original para **Android TV e Google TV 11 ou superior**, desenvolvido com Views nativas em Kotlin. Sem root ou firmware modificado. Idiomas preservam o design e as funções.

## Idioma
Na engrenagem superior, abra **Idioma**. Automático usa o primeiro idioma compatível da TV; sem correspondência, usa inglês dos Estados Unidos. A escolha manual fica salva após reiniciar; selecione Automático para voltar ao idioma do sistema. A alteração vale apenas para o Lume e seu protetor de tela. Configurações do Android, outros apps, títulos e sinopses publicados pelas plataformas não são traduzidos.

São 21 idiomas e variantes regionais: en-US, en-GB, en-CA, pt-BR, pt-PT, es-ES, fr-FR, zh-CN, hi-IN, ar, bn-BD, ru-RU, ur-PK, ja-JP, pnb-Arab-PK, mr-IN, de-DE, it-IT, ko-KR, id-ID e tr-TR. Chinês simplificado; punjabi ocidental em Shahmukhi. Árabe, urdu e punjabi ocidental espelham o layout e a navegação horizontal controlada pelo app.

## Controle e uso diário
- Setas movem o foco. OK abre um favorito; na biblioteca, adiciona ou remove o favorito. Segure OK para mover, ver informações ou abrir a confirmação oficial de desinstalação.
- Continuar assistindo e Sugestões trocam a prévia pelo foco no cabeçalho. Esquerda/direita percorrem cabeçalhos e controles próximos; para baixo ou OK entra no card lembrado. Conteúdos e capas dependem do compartilhamento dos apps instalados e da permissão concedida.
- A ordem dos favoritos é preservada. Nomes e ícones vêm dos apps instalados. Instalar aplicativos abre a Google Play; não há instalação silenciosa de APK.
- Rede abre as configurações do Android. HDMI depende do fabricante; Input no controle continua sendo alternativa.
- Cinema escurece e aquece o launcher e pausa efeitos. Não muda ajustes de imagem da TV. A agenda opcional 20h–6h usa o horário local da TV.
- Atmosferas permitem escolha manual ou diária. O protetor configura inatividade, intervalo, escurecimento e relógio. Fotos online ficam na RAM, sem cache de fotos em disco do app. Paisagens procedurais alternam sem internet. Setas, OK ou Voltar encerram. O Android controla proteção fora do launcher e standby.

## Instalar, atualizar e recuperar
Ative a depuração autorizada pelas configurações oficiais. Confira o serial exato antes de cada comando ADB; substitua DEVICE_SERIAL pelo dispositivo confirmado:

Antes e depois de `adb install -r`, registre e confira o launcher padrão escolhido e o destino do botão Home. A atualização pode alterar essa seleção em alguns aparelhos; não presuma sua preservação. Mantenha outro launcher funcional disponível e, se necessário, restaure a escolha pelo seletor do Android.

```sh
adb devices
adb -s DEVICE_SERIAL install -r app-release.apk
```

Atualizações precisam do mesmo pacote e certificado de assinatura para preservar preferências. Guarde a chave em segredo. Outra assinatura não atualiza a instalação existente. Não limpe os dados para trocar idioma.

Escolha o Lume como Home somente pelo seletor oficial, quando o fabricante permitir. Teste Home a partir de outro app e standby separadamente. Antes de desinstalar o launcher padrão, instale e selecione outro compatível e confirme Home. Depois remova o Lume pelos ajustes do Android. Sem root, alteração de firmware ou remoção de componentes do sistema. Home e retorno do repouso variam por fabricante.

## Build, testes e licenças
Requer JDK 17, SDK Android Platform 35 e Build Tools 36.0.0. Configure JAVA_HOME e ANDROID_HOME. Na raiz do projeto, gere os recursos antes da validação e do build:

```sh
python tools/build_locale_resources.py
cd src/android
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

No Windows, use gradlew.bat. APK em app/build/outputs/apk/release/app-release.apk. Na raiz do projeto:

```sh
python tools/verify_locales.py
```

Traduções ficam nos catálogos common/activity e em tools/translations. Regenere recursos após alterações, mantendo IDs e placeholders. LocaleSmokeInstrumentation é um runner exclusivo de testes; não altera idioma global nem Home. Use dispositivo ou emulador dedicado. Um AVD de telefone não certifica integrações de TV ou controle físico.

Código original sob MIT, copyright 2026 Joao Salazar. Consulte LICENSE e THIRD_PARTY_NOTICES.md. Fotos, marcas e conteúdo de terceiros mantêm seus direitos; autores e URLs das licenças não mudam. Contas privadas de streaming não são acessadas nem traduzidas. Disponibilidade, fontes, desempenho e compatibilidade exigem testes específicos.

Revisão linguística nativa e QA por idioma continuam necessários. Consulte resultados reais em docs/qa; este guia não declara testes concluídos. Instalação: docs/installation.md. Build e assinatura: docs/build.md.
