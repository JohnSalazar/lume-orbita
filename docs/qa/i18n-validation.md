# Validação 0.4.0 — internacionalização

Data: 27/10/2026. Resultado: **verificado com ressalvas**, sem promessa de ausência universal de bugs ou certificação linguística nativa.

## Pacote observado

- Android TV/Google TV 11+ declarado: minSdk 30, targetSdk 35, versionCode 26, versão 0.4.0.
- APK release não depurável, 10.104.961 bytes. SHA-256: `27D73D8DDDC1698A5CFED12BAD97E6AF4C5F816E3CD2C6B135B13F9863D9DC44`.
- Certificado SHA-256: `c435b38a840b6381b56c2f71f6e914f7958218762139b47ecd9607793cd67cdb`, igual ao pacote anterior. Certificado de avaliação existente; não é uma entrega certificada para Google Play.
- Testes realizados em AVD dedicado, Android API 35, imagem de telefone Google APIs/Play Store, perfil 1280×720. A imagem não é Android TV. Renderização observada: SwiftShader; WHPX confirma CPU, não aceleração gráfica ou fluidez da TV.

## Gates executados

| Verificação | Resultado observado |
|---|---|
| Gradle unit/build/lint | BUILD SUCCESSFUL; 140 testes JVM, sem falhas/erros/skips |
| JaCoCo do core | 392/393 linhas (99,75%); 680/715 ramos (95,10%) |
| Lint release | 0 erros, 16 avisos; sem desabilitar MissingTranslation/ExtraTranslation |
| Catálogos e guias | 21/21 catálogos, 260 chaves cada; 21/21 guias; zero erros estruturais |
| Verificador estrutural | 12 testes isolados do próprio verificador aprovados |
| Auditor de runtime | 9 testes de contrato positivos/negativos aprovados; 37/39 linhas (94,87%) por trace, sem métrica de ramos; fixtures sintéticas não representam renderização Android |
| Textos Android compilados | 5.460 valores carregados no app comparados aos JSONs; zero diferenças; recursos parametrizados formatados sem exceções |
| Interface release | 273 pares distintos idioma/tela: 21 idiomas × 13 telas/menus |
| Seletor | OK injetado no seletor para cada idioma; tag persistida e Activity recriada; troca inicial também preservou página Biblioteca e favoritos |
| Navegação | Sequência de seis setas físicas por idioma (126 eventos); posição semântica correta nos layouts LTR/RTL |
| Automático | Contextos locais com idioma desconhecido, lista com script incompatível seguido de pt-PT e hi-IN; demais aliases/scripts/fallback cobertos nos testes puros; sem mudar idioma do SO |
| Proteção de tela | 21 Activities reais: fotos online observadas com título localizado e autor original, FLAG_SECURE mantido, saída com Voltar |
| Geometria de texto | 3.145 nós mostrados, com layout e interseção no viewport; zero ellipsis e zero altura interna excedida detectados |
| Dropdown de apps | Aberto em cada idioma, textos observados e limites horizontais da janela verificados; nenhuma ação de desinstalação executada |

As 273 telas release foram executadas em dois lotes sobre o mesmo APK: 210 telas principais e 63 menus adicionais. Relatórios brutos foram preservados separadamente; a consolidação rejeitou pares duplicados e confirmou recursos compilados idênticos entre lotes. Uma rodada debug anterior também executou as 210 telas principais.

Telas/menus: Home, Biblioteca, Atmosferas, Ajustes, Cinema, Aparência, opções da proteção, créditos, loja, idioma, ações dos apps, entradas e explicação da escolha de Home. Abertura dos painéis usa o harness dentro do aplicativo; não equivale a executar todas as ações externas pelo controle. Entradas no AVD verificam o estado vazio; escolha oficial de Home, instalação e desinstalação não foram acionadas.

## Matriz por idioma

Todos os itens abaixo passaram nos 260 valores compilados, 13 telas/menus, seletor, sequência DPAD e caption/saída da proteção no APK release. A revisão textual cruzada foi assistida por IA; revisão por falantes nativos permanece separada.

| Locale | Escrita | Runtime e estrutura | Revisão textual assistida |
|---|---|---|---|
| en-US | LTR | PASS | Revisada |
| en-GB | LTR | PASS | Revisada |
| en-CA | LTR | PASS | Revisada |
| pt-BR | LTR | PASS | Revisada |
| pt-PT | LTR | PASS | Revisada |
| es-ES | LTR | PASS | Revisada |
| fr-FR | LTR | PASS | Revisada |
| zh-CN | LTR | PASS | Revisada |
| hi-IN | LTR | PASS | Revisada |
| ar | RTL | PASS | Revisada |
| bn-BD | LTR | PASS | Revisada |
| ru-RU | LTR | PASS | Revisada |
| ur-PK | RTL | PASS | Revisada |
| ja-JP | LTR | PASS | Revisada |
| pnb-Arab-PK | RTL | PASS | Revisada |
| mr-IN | LTR | PASS | Revisada |
| de-DE | LTR | PASS | Revisada |
| it-IT | LTR | PASS | Revisada |
| ko-KR | LTR | PASS | Revisada |
| id-ID | LTR | PASS | Revisada |
| tr-TR | LTR | PASS | Revisada |

## Problemas encontrados e corrigidos

- Catálogo indonésio completo inicialmente caía em inglês em runtime. A comparação palavra por palavra dos recursos carregados revelou o problema; corrigido para `values-in-rID`, mantendo a tag pública id-ID.
- Datas pnb inicialmente usavam nomes ingleses. Alias CLDR pa-Arab-PK corrigiu a formatação Shahmukhi sem mudar a tag de UI.
- Palavra inglesa Atmospheres quebrava isolando a última letra. Fonte dos botões laterais ajusta entre 10 e 14sp, sem reconstruir o design.
- Revisão cruzada corrigiu concordância de tempo restante/estados, termos de paisagens, redação de OK prolongado, escolha de Home em punjabi, concordância em urdu, diferenças regionais inglesas e instruções dos guias.
- Uma sugestão sobre o nome marathi de nenúfares foi retirada após consulta terminológica; não foi tratada como erro comprovado.

## Reprodução

Na raiz:

```sh
python tools/build_locale_resources.py
python tools/verify_locales.py
python tools/verify_locales.py --self-check
python -m unittest discover -s tests -v
```

Em src/android, com JDK 17 e SDK configurados:

```sh
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleDebugAndroidTest assembleRelease lintRelease
```

Windows usa gradlew.bat. Use um dispositivo dedicado, confira seu serial e o AVD antes de instalar. Instale o APK de avaliação e o APK androidTest assinado pelo mesmo certificado. Não instale o pacote de teste na TV de uso diário sem planejar a interrupção:

```sh
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/release/app-release.apk
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w br.com.lume.launcher.test/br.com.lume.launcher.LocaleSmokeInstrumentation
adb -s DEVICE_SERIAL pull /sdcard/Android/data/br.com.lume.launcher/files/locale-smoke PATH_TO_EVIDENCE
```

Depois, na raiz: `python tools/audit_runtime_locales.py PATH_TO_EVIDENCE`. O runner padrão captura as 13 telas por idioma. `-e screens` e `-e locales` permitem lotes focados; o auditor completo exige a matriz integral de 273 pares. O runner restaura a preferência de idioma ao terminar. Capturas da proteção de tela não contornam FLAG_SECURE. Evidências de desenvolvimento não fazem parte do pacote público.

## Limites e segurança

Não foi repetido nesta versão o teste físico na KONKA/API30, 4K, Home, standby, HDMI/imagem/som, todas as plataformas de streaming, proteção Dream iniciada pelo SO ou uso prolongado. A base anterior possui [registro histórico](../validation.md). Esses resultados antigos não são aprovação automática da 0.4.0.

Comparar recursos carregados prova correspondência textual e resolução; revisão assistida não certifica todas as escolhas linguísticas como nativas. Interseção com viewport e métricas de layout não garantem visibilidade integral durante toda rolagem, shaping perfeito em cada firmware, contraste de todo papel de parede ou fluidez em hardware real. A cobertura JaCoCo citada mede o core, não toda a UI.

Sem mudanças de firmware, root, locale global, permissões, Home ou remoção de componentes do sistema nesta etapa. Preferências/IDs de conteúdo e URLs não foram traduzidos. Não foram acrescentadas dependências; chave privada e dados particulares permanecem fora do repositório. Manifesto mantém as permissões/exportações anteriores e o guard BIND_DREAM_SERVICE; pacote de teste fica separado do release.

Próxima validação recomendada: atualização supervisionada na KONKA Android TV 11, focos/streaming/Home/standby, idioma manual/Automático e proteção nativa; depois revisão por falantes dos idiomas escolhidos.
