# Plano físico KONKA — Lume0.4.1

Estado: **planejamento; resultados físicos pendentes**. Helena revisou fontes e catálogos sem ADB, Gradle, instalação ou interação com a TV. Clara executará os gates e registrará evidências privadas. Os relatórios de0.4.0/AVD anteriores continuam históricos; não são prova física desta versão.

Alvo: KONKA com Android11/API30, interface1280×720 e memória limitada. Escopo autorizado: instalar a atualização, testar21 idiomas, navegação e proteção de tela, mantendo preferências e Home. Não alterar rede global, Dream, firmware, grants, apps de terceiros ou assinatura para facilitar testes.

## Matriz de execução

Exigir **21 locales ×14 telas =294 pares únicos**. Lista canônica: en-US,en-GB,en-CA,pt-BR,pt-PT,es-ES,fr-FR,zh-CN,hi-IN,ar,bn-BD,ru-RU,ur-PK,ja-JP,pnb-Arab-PK,mr-IN,de-DE,it-IT,ko-KR,id-ID,tr-TR.

| Tela do runner | Critério de leitura/layout; nenhuma ação externa automática |
|---|---|
| home | Sidebar, cabeçalhos, favoritos e prévia completos; foco real distinto do destaque visual |
| library | Apps reais, um foco por card e marcador passivo; nomes de terceiros não são textos próprios traduzíveis |
| gallery | Rótulos próprios e última linha acessíveis; não selecionar wallpaper durante coleta |
| showSettings | Todas as opções alcançáveis, incluindo Sobre no fim; Back acessível |
| showCinema | Ligado/desligado e agenda legíveis; não tocar em toggles nesta coleta |
| showAppearance | Labels, percentuais, toggles e Back; não ajustar valores durante leitura |
| showScreensaverSettings | Intervalos, dim, relógio, prévia e créditos; não sobrescrever preferências |
| showScreensaverCredits | Fonte/autor/licença legíveis e conteúdo rolável; fotos não recebem licença MIT por associação |
| showStore | Rótulos/links de loja; não instalar aplicativos |
| showLanguage |21 escolhas nativas únicas e Automático; seleção real por OK, recriação e locale efetivo corretos |
| appOptions | Dropdown ancorado íntegro; ações dependem do app; não confirmar desinstalação, mover ou favorito |
| showInputs | Lista/estado indisponível legíveis; não selecionar HDMI nesta rodada |
| showHomeChoice | Texto de escolha/restauração legível; não acionar mudança de Home |
| showAbout | Nome real, versão/build instalados, MIT/terceiros, dois links e Back legíveis |

Por idioma, verificar cadeia de cabeçalhos ida/volta com D-pad e resultado esperado. Em ar,ur-PK,pnb-Arab-PK, LEFT/RIGHT seguem a direção visual RTL. Abertura das14 telas por reflexão usa a UI real, mas não equivale a14 jornadas completas de usuário; separar as teclas efetivamente exercitadas das telas apenas renderizadas. Esperar catálogo/âncora válidos antes de abrir appOptions; um waitForIdleSync não prova que consulta assíncrona de apps terminou.

## Gate antes de tocar na TV

- Identificar APK por SHA256, versionName/versionCode, certificado compatível e minSdk30. Build/lint/JVM devem terminar sem falhas; avisos remanescentes precisam de classificação. Não usar resultado de build antigo ou mudar debuggable/permissões para instrumentar.
- Verificar assinatura/compatibilidade do APK de teste com o alvo release. Compilar a suite não comprova que a instrumentação será aceita pela KONKA.
- Validar uma rodada curta com journal/restauração confirmados antes das21 locales completas. Se falhar, recuperar o estado e interromper a matriz, sem acumular mudanças.
- Capturar baseline atual: mapa tipado de SharedPreferences, idioma original, favoritos/ordem, guia/capa lembrada, aparência/Cinema/proteção; resolver e holder HOME, Dream/timeout e estado de READ_TV_LISTINGS. Não reutilizar números históricos de favoritos.

## Preservação obrigatória

O LocaleSmokeInstrumentation registra snapshot completo antes de qualquer seleção/Activity. `output` aceita somente nome de diretório; o journal único contém prefs-before, prefs-after e digests, preservando tipos e chaves ausentes, inclusive app_locale. Não restaurar apenas uma seleção normalizada ou defaults.

Antes de fechar o launcher, estacionar em **Android Settings via ACTION_SETTINGS**, sem enviar teclas ou mudar configuração. Confirmar pacote externo no foreground e Activities do alvo pausadas; só então fechar as instâncias rastreadas e aguardar onDestroyed. Isso evita o relançamento automático do Lume quando ele é a Home padrão. Se Settings não resolver ou a janela não for confirmada, falhar fechado; nunca presumir que waitForIdleSync confirmou o processo externo.

Após teardown, restauração integral com commit/matches e digest igual ao inicial. Cleanup falho não pode impedir a tentativa independente de restaurar. Manter erro original e erro de restauração separados; não declarar PASS se qualquer um existir. O runner permanece estacionado em Settings; Clara retorna HOME somente após conferir os artefatos e repete comparação de prefs/role após estabilização. Nenhum journal ou metadado de conteúdo pessoal entra em pacote público.

| Comparação final | Resultado exigido | Estado |
|---|---|---|
| Snapshot tipado antes/depois | Mesmas chaves, tipos e valores; digests iguais; status restored sem restoration_error | Pendente físico |
| Idioma | Valor bruto/ausência original preservado e UI normal restaurada | Pendente físico |
| Home | Mesmo holder e resolvedor antes/depois | Pendente físico |
| Demais estados | Favoritos/ordem, guia/capa, aparência, Cinema, saver, Dream/timeout e grants preservados | Pendente físico |
| Reabertura | Lume normal estável, sem popup ou ação disparada por key-up antigo | Pendente físico |

A release não-debuggable com allowBackup=false não oferece backup de preferências por run-as/adb backup como garantia. O journal do contexto instrumentado é estado de recuperação de QA, distinto de backup do APK. Morte do processo pode impedir finally; manter snapshot recuperável e procedimento de recuperação antes da rodada longa. Não remover arquivos necessários à recuperação enquanto houver falha pendente.

## Sobre — revisão de fonte concluída, UI física pendente

`LauncherActivity.showAbout()` reutiliza diálogo/foco/Back existentes. O nome vem de ApplicationInfo e versão/build de PackageManager, sem versão fixa no texto. As duas URLs HTTPS são literais de repositório/licença e só abrem mediante clique; falha de handler gera mensagem localizada. A leitura da fonte não verifica disponibilidade HTTP desses destinos nem fluxo de navegador externo. Não há escrita de preferências nesse método.

As7 novas chaves activity_about_* estão nos20 catálogos traduzidos e base en-US: **267 chaves por idioma;5.607 valores compilados esperados no total**. Conferência de Helena: nenhuma chave vazia, %1$s/%2$d compatíveis e sem inversão semântica clara nas novas mensagens. Verificador executado nesta revisão: **PASS:21/21 catálogos,21/21 guias,0 erros**. Isso não é certificação por falantes nativos; licença/nomes/URLs preservam suas identidades.

## Proteção online e sem fontes

- Online: observar bitmap efetivamente publicado e duas fotos distintas com captions/autores do catálogo. Caption procedural ou placeholder não conta como foto carregada. Não capturar foto protegida nem gravar bitmap/encoded no disco da TV.
- Sem fontes: o harness usa contexto/AssetManager da instrumentação com screensaver.json vazio somente naquela instância, mantendo rede e catálogo do APK real intactos. Exigir três cenas procedurais distintas consecutivas, captions corretos e registros de elapsed_ms compatíveis com30s entre trocas; intervalo temporário em memória não altera a preferência salva.
- Dialog de teste deve manter FLAG_SECURE antes de apresentar o renderer. Essa janela valida renderer/timers; o ciclo de vida/saída da ScreenSaverActivity de produção é um ensaio separado no LocaleSmoke.
- Catálogo vazio não é queda real de Wi-Fi nem falha HTTP end-to-end. Transporte offline,429, timeout e callback tardio continuam cobertos por fakes/contratos próprios; não somar essas provas como desconexão física ensaiada.
- Restaurar prefs também após erro/timeout do playback, independentemente do sucesso do cleanup. Não desenhar conclusão sobre vazamento/GC instantâneo a partir de uma amostra de PSS.

## Métricas e limites do aceite

Auditar pares294,21 locales,5.607 valores exatos e21 captions/autor/contexto/FLAG_SECURE. Para TextViews mostrados, com layout e interseção com viewport, exigir zero ellipsis e zero altura de layout+padding acima do controle; revisar amostras visuais para cortes/overlap/shaping. Interseção com viewport não garante visibilidade integral nem substitui legibilidade a distância. Itens roláveis fora da amostra precisam ser diferenciados de cortes internos.

Não alegar benchmark de GPU, FPS, fluidez, suporte universal a OEM,4K ou fontes extremas. Cobertura do core/auditores não cobre toda a UI/decoder Android. Permanecem pendentes revisão linguística nativa e validações não incluídas nessa matriz. Testes de Power/standby, HDMI, volume e reprodução externa não recebem PASS novo por esta rodada.

Próxima etapa: Clara concluir build0.4.1, confirmar rodada curta de preservação, executar matriz e anexar resultados reais por gate. Este documento permanece planejamento até essas evidências serem conferidas.
