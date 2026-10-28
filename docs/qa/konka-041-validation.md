# Validação KONKA — Lume0.4.1

Estado: **instalação e testes funcionais concluídos com ressalvas na KONKA, em27/10/2026**. Matriz física completa, playback online e procedural, restauração e Home conferidos. O auditor estrito continua reprovado por21 ellipsis intencionais em metadado externo; não há PASS global desse critério. Operação física/ADB/build por Clara; conferência de fontes, relatórios e testes Python por Helena. Plano e critérios: [konka-041-test-plan.md](konka-041-test-plan.md).

## Gates confirmados

| Gate | Evidência observada | Resultado |
|---|---|---|
| Build Android | Log privado android-041-build.log: BUILD SUCCESSFUL em3m19s | PASS |
| JVM | Relatórios JUnit:143 testes,0 falhas,0 erros,0 skips | PASS |
| Core JaCoCo |395/396 linhas (99,75%) e682/717 ramos (95,12%) | PASS core; não cobertura da UI/decoder |
| Lint release |0 erros,0 fatais,16 avisos | PASS sem erros; avisos não são zero |
| Recursos/documentos | Verificador estrutural:21/21 catálogos,21/21 guias,0 erros;267 chaves por locale | PASS estrutural |
| Instalação0.4.1 | Instalação com -r; ADB confirmou versão0.4.1/code27, mesmo certificado, Home e READ_TV_LISTINGS preservados | Confirmado antes e depois dos testes |
| Backup/snapshot | Snapshot privado de preferências contém15 entradas tipadas; sem divulgar valores | Disponível para recuperação |
| Rodada curta pt-BR | short2-run.txt:14 telas registradas, digests antes/depois idênticos,3 estacionamentos em Android Settings, RESULT_OK sem restoration_error | PASS curto; não extrapolar para21 locales |
| Auditor Python |9 testes executados por Helena após atualização de SCREENS,0 falhas/erros | PASS contratos sintéticos do auditor |
| Matriz nativa completa |294 pares únicos,21 locales×14 telas;5.607 valores compilados exatos contra os catálogos atuais | PASS integridade/textos compilados |
| Auditor estrito da UI |3.465 TextViews filtrados,21 ellipsis,0 candidatos verticais,0 diferenças de catálogo; saída1 na execução de Clara | FAIL critério zero ellipsis; reconciliação abaixo |
| Preferências dos lotes | Três journals:15 entradas tipadas antes/depois idênticas entre si e ao backup inicial, status restored/test_failed false | PASS nas três fronteiras de lote |
| Playback online | Duas fotos distintas observadas pelo renderer, captions presentes, troca em30.526ms,15 prefs restauradas exatamente | PASS funcional do harness; sem benchmark |

As preferências brutas, IDs de conteúdo, árvore/capturas pessoais e identificadores do aparelho permanecem em evidências privadas. Não são anexos deste relatório publicável. A contagem de15 entradas e a igualdade dos digests não divulgam o conteúdo desses valores.

## Limpeza da Home padrão

A primeira rodada curta falhou no limite de limpeza do harness: quatro passagens curtas não observaram destruição de todas as Activities. O finally posterior concluiu e restaurou as preferências. Isso não foi classificado como crash, vazamento de Activity ou bug de produto; a causa específica OEM não foi provada.

Após ampliar a espera limitada de destruição, a rodada short2 concluiu14 telas e restauração integral. O runner estaciona em Android Settings antes de terminar o Lume, evitando que o sistema reabra automaticamente a Home durante a recuperação. Confirma janela externa/pausa e exige liberação das Activities; não esvazia artificialmente o tracker para produzir PASS. A verificação final confirmou as mesmas15 preferências tipadas do backup.

## Revisão cruzada do auditor

`tools/audit_runtime_locales.py` lista14 telas, incluindo Sobre. O gate exige simultaneamente quantidade exata, ausência de pares duplicados e **igualdade com o produto cartesiano de21 locales×14 telas**. A validação ficou mais restritiva que uma simples contagem total: uma tela extra não compensa uma tela ausente.

`tests/test_runtime_audit.py` usa nomes/quantidades de SCREENS e preserva os negativos: idioma incorreto com chaves completas; chave ausente/extra; caption/autor/contexto errados; FLAG_SECURE falso; corte/ellipsis; duplicação ou locale ausente; arquivo ausente/JSON malformado. Fixtures ficam em TemporaryDirectory e não simulam aprovação de UI Android.

Comando executado nesta revisão:

```sh
python -B -m unittest discover -s tests -p test_runtime_audit.py -v
```

Resultado: **9 testes passaram**. Não houve relaxamento dos asserts de texto, segurança ou geometria observado nesta revisão. Não se atribui nova porcentagem de cobertura a essa execução; as métricas de core Android na tabela vêm dos relatórios JaCoCo, separadamente.

O auditor CLI deve ser executado com Python normal, sem -O/PYTHONOPTIMIZE: a checagem de matriz usa assert. Um relatório parcial de7 locales não passa como conjunto completo. Os três lotes concluídos foram combinados em locales-complete; Helena conferiu independentemente294 pares exatos,267 chaves por locale e5.607 valores iguais aos catálogos. Os três logs registram RESULT_OK/98 telas cada, sem erro de restauração. Os journals confirmam restauração exata das15 entradas tipadas do backup inicial.

## Reconciliação dos21 ellipsis

O auditor estrito **não foi relaxado e não passou**: Clara observou saída1, compatível com audit.json e com a contagem independente de Helena. As21 ocorrências são uma por locale na Home; todas correspondem ao mesmo título de programa externo. Nenhuma diferença de string compilada foi encontrada. Não publicar esse título nem os metadados pessoais de origem.

Geometria observada em todas as ocorrências: largura284px, altura53px, maxLines2, layoutHeight40px e padding inferior5px. A fonte em LauncherActivity.kt define explicitamente o título do card com duas linhas, ellipsize END e40dp de altura. Portanto, esses casos são **truncamento deliberado de título longo de plataforma**, distinto de um botão/rótulo próprio traduzido cortado por falta de espaço. Não representam zero truncamentos e não foram apagados da métrica.

O corpo do card recebe título completo em contentDescription na fonte. O relatório de TextViews não comprova título completo visualmente nos detalhes nem valida leitura por screen reader; não fazer essa extrapolação. Ausência de erros de tradução detectados também não significa que todo texto externo aparecerá inteiro em qualquer tamanho.

Sobre foi conferido nos21 relatórios: versão0.4.1/build27 presentes, nenhuma ellipsis e nenhum candidato de altura excedida detectados. Nomes/URLs/licenças foram renderizados; não houve ensaio de abertura dos links externos nesta matriz.

## Matriz consolidada e pendências

| Prova | Resultado necessário | Estado |
|---|---|---|
| Matriz física completa |294 pares únicos,21 locales,5.607 valores compilados sem diferenças | Conferido; três lotes concluídos |
| Seletor e RTL |21 seleções/OK/recriações e cadeia de setas coerente com cada direção | Assertivas do runner concluídas;126 setas injetadas, não teste IR/CEC humano |
| Sobre | Versão/build reais; licença/terceiros e ações completas nos21 idiomas; sem corte de texto longo |21 telas conferidas;0 ellipsis/altura excedida detectadas; links externos não acionados |
| Métricas de texto | Nós mostrados/com layout/interseção com viewport;0 ellipsis/altura excedida; revisão contextual dos candidatos | FAIL zero ellipsis:21 em título externo;0 verticais/0 diferenças; reconciliação explícita acima |
| ScreenSaverActivity |21 captions/autor/contexto corretos, FLAG_SECURE e saída Back |21 JSONs e assertivas do runner conferidos; nenhuma captura de foto |
| Playback online | Duas fotos efetivamente publicadas/trocadas, sem captura protegida ou persistência da foto | Duas fotos distintas/30.526ms; captions e restauração conferidos; Dialog seguro afirmado pelo harness |
| Playback sem fontes | Três cenas procedurais consecutivas com tempos compatíveis; catálogo vazio somente da instância de teste | Três temas distintos; intervalos30.026/30.027ms; preferências restauradas |
| Fechamento | Todos os snapshots restaurados, mesmos tipos/chaves/valores, Home/Dream/grants preservados após retorno normal | ADB final:15prefs exatas, Home Lume, Dream/timeout/grant/idioma preservados; auxiliar removido; HOME abriu Lume |

O relatório online/playback.json e os snapshots antes/depois foram conferidos independentemente: dois índices diferentes com bitmap publicado, captions não vazios, rotation passed, restored true, ausência de erro/cleanup_error/restoration_error e igualdade exata ao backup de15 entradas. A diferença de elapsed_ms foi30.526ms. O harness usa30s temporários em memória; não alterou o intervalo salvo. FLAG_SECURE é uma assertiva da janela de teste; não houve screenshot de foto nem inspeção de arquivos de imagem no disco que permita prometer ausência universal de escrita por todo o sistema.

O relatório offline/playback.json foi conferido: três frames, temas consecutivos diferentes, intervalos30.026/30.027ms, rotation passed, restored true, sem erros de limpeza/restauração e mesmas15 preferências. Catálogo de teste vazio comprova funcionamento sem fontes disponíveis naquela instância; **não comprova desconexão real de Wi-Fi nem falha HTTP integral**. A rede global e o catálogo do APK de entrega permaneceram intactos.

## Entrega e recuperação

APK instalado:0.4.1/code27, SHA-256 `8d171da7959de7aa9fe67e32f0414930df08c917e00d5711a1086c4d23fd7752`. Certificado igual ao0.3.4 anterior. Nenhum root, firmware, launcher padrão, grant ou configuração protegida foi modificado. A atualização preservou a Home existente.

Backup privado no computador: APK anterior, snapshot tipado e XML original com hashes conferidos. A KONKA negou adb pull do diretório externo privado; o auxiliar exportou ZIP limitado por Binder, sem gravar imagens nem ZIP na TV. Exportações e conteúdo pessoal ficaram fora do repositório. Ver [procedimento operacional](../operations/device-validation.md).

Última verificação: READ_TV_LISTINGS concedida com USER_SET; Dream original Backdrop habilitado; timeout300000ms; idioma do Android pt-BR. O teste auxiliar foi desinstalado. Um evento Android KEYCODE_HOME abriu LauncherActivity do Lume. A navegação automática foi encerrada; não houve nova confirmação de Power/standby com controle físico nesta rodada. Journals pequenos de diagnóstico e recuperação permanecem no diretório privado do Lume; não contêm pixels das fotos online.

## Limites

Os testes são funcionais na KONKA/API30; nenhuma conclusão de aceleração de GPU, FPS ou fluidez é autorizada por eles. Também não certificam todos os OEMs,4K, fontes extremas, Power/standby, HDMI ou reprodução em terceiros nesta versão.

Mesmo ausência de ellipsis/altura excedida em uma tela não prova visibilidade integral: interseção parcial com viewport, shaping, contraste, sobreposição, texto rolável e linhas ocultas por maxLines ainda exigem avaliação contextual. Nesta rodada houve21 ellipsis reais, reconciliados acima. Revisão das traduções por IA não substitui revisão de falantes nativos. Não prometer ausência universal de erros linguísticos.

Próxima etapa: avaliação pelo usuário no controle físico, incluindo Sobre e preferência de idioma; revisão linguística por falantes nativos continua pendente. Manter a saída estrita1 e a reconciliação dos títulos externos no resultado final; não declarar PASS global ou zero ellipsis.
