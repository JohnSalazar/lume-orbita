# Plano de testes — internacionalização em21 idiomas

Estado em 2026-10-27: **gates estruturais e smoke localizado no AVD API35 concluídos**, conforme matriz e evidências abaixo. Execução Android por Clara; conferência dos relatórios e testes dos auditores por Helena. O AVD é de telefone, não Android TV: estes resultados não certificam controle/OEM da KONKA, desempenho gráfico ou tradução nativa.

Escopo: idioma automático pelo sistema, fallback en-US e seleção manual nas configurações; interface e documentação nos21 idiomas definidos abaixo. Este plano não autoriza mudança de idioma global, permissões, Home, rede ou firmware de uma TV. Execuções devem identificar claramente emulador isolado versus aparelho real.

## Catálogo canônico

| Locale BCP47 | Idioma/variante | Escrita | Recursos | UI real | Revisão linguística |
|---|---|---|---|---|---|
| en-US | Inglês dos EUA; fallback | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| en-GB | Inglês britânico | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| en-CA | Inglês canadense | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| pt-BR | Português brasileiro | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| pt-PT | Português europeu | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| es-ES | Espanhol | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| fr-FR | Francês | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| zh-CN | Mandarim, escrita simplificada | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| hi-IN | Hindi | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| ar | Árabe padrão moderno | RTL | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| bn-BD | Bengali | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| ru-RU | Russo | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| ur-PK | Urdu | RTL | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| ja-JP | Japonês | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| pnb-Arab-PK | Punjabi ocidental, Shahmukhi | RTL | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| mr-IN | Marathi | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| de-DE | Alemão | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| it-IT | Italiano | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| ko-KR | Coreano | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| id-ID | Indonésio | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |
| tr-TR | Turco | LTR | PASS estrutural | PASS AVD:13 telas | Revisão IA; sem certificação nativa |

Não considerar variantes inglesas/portuguesas satisfeitas apenas porque textos coincidem. Verificar locale efetivamente resolvida e termos regionais. Para pnb-Arab-PK, validar AAPT, resolução Android, direção e formação de glifos; não converter pa-Guru-IN para Shahmukhi. Aliases como pa-Arab-PK precisam ser explicitamente aprovados/documentados e testados, sem equivalência presumida por idioma semelhante.

## Resolução e persistência

- Automático escolhe o primeiro locale suportado da LocaleList do sistema; se nenhum for compatível, usa en-US. Testar lista vazia, idioma desconhecido, variante suportada, múltiplas preferências e scripts incompatíveis. Exemplo: [não suportado,pt-PT] resulta pt-PT, não inglês prematuro.
- Canonicalização/matching não dependem do locale ativo para conversão de maiúsculas, especialmente em turco. Testar tags malformadas, aliases aceitos, script explícito e região; rejeitar/normalizar somente conforme contrato.
- Escolha manual prevalece sobre SO, persiste ao recriar/encerrar/reabrir app e não altera a configuração global do sistema. Voltar a Automático abandona o override manual e respeita a lista corrente do SO.
- Idioma salvo é identificador estável, não nome traduzido. Não traduzir tags de foco, pacotes, IDs de cena, chaves de preferência ou valores internos como on/off/auto.
- Troca de idioma preserva favoritos/ordem, guia/tag lembrada, aparência, Cinema/agendamento, proteção/relógio e seleção Home. Recriação não provoca abertura de app, pedido de permissão ou perda de foco para janela errada.
- Seletor deve permitir recuperar outro idioma mesmo sem entender o atual: nomes nativos legíveis,21 escolhas únicas e Automático acessível por D-pad. Não usar bandeiras como substituto de idiomas.

## Inventário obrigatório de texto

Catalogar cada string própria com chave, origem, contexto, placeholders, possíveis plurais e telas/estados. Extração inclui contentDescription, Toasts, estados vazios/erros, rótulos do manifesto/Dream, menus e metadados próprios dos assets. Não limitar busca a chamadas text()/button().

| Grupo real da base | Estados/textos a cobrir |
|---|---|
| Shell/Home | Sidebar, rede, relógio/data, cabeçalhos, favoritos/incluir, prévia e instruções de OK/segurar/mover |
| Conteúdo compartilhado | Conectar/atualizar, loading, indisponível/vazio, detalhes de temporada/episódio/progresso/tempo restante |
| Biblioteca/ações | Incluir/remover favorito, mover/salvar/cancelar, informações, instalar/loja e rótulo de desinstalação quando disponível |
| Atmosferas | Título/tema próprios das30 cenas, modo diário/manual e seleção; imagens não devem ser regravadas |
| Cinema | Ligado/desligado, ação atual, agenda20h–6h e feedback; números visuais não mudam regra horária interna |
| Configurações | Todas as opções, seletor de idioma, aparência, transparência/reflexos/animações/Leve e Voltar |
| Proteção de tela | Automático, intervalos, intensidade, relógio, prévia, créditos, caption procedural, descrição acessível e estados sem foto |
| Integrações | Avisos de Home/loja/rede/HDMI indisponíveis e lista vazia; não confundir texto próprio com tela Android externa |
| Acessibilidade | Descrições de botões sem texto, estado favorito, porcentagem assistida, instruções do controle e status da rede |

Exceções precisam de allowlist revisável: marcas/nomes de apps, títulos/sinopses fornecidos por plataformas, autores e referências legais originais, URLs e identificadores técnicos. Essas entradas não são traduções ausentes do app. Conteúdo privado de plataformas não deve ser enviado a serviço de tradução nem incluído nas fixtures.

## Gate automático de recursos e textos

1. Parsear XML/recursos finais:21 locales esperados, sem chave ausente/extra inesperada/duplicada ou texto vazio; base en-US completa para fallback. Verificar presença específica no catálogo de cada locale, pois resolução Android por fallback pode esconder arquivo incompleto.
2. Validar placeholders por índice e tipo, preservando reordenação legítima; testar %, apóstrofos, aspas, XML/HTML, quebras e símbolos. Formatar todos os recursos parametrizados com dados sintéticos, incluindo Unicode/RTL e valores longos; falha de formato bloqueia gate.
3. Plurais: exigir other, categorias adequadas ao idioma e testar0/1/2/3/5/11/21/101 conforme uso real. Não copiar obrigatoriamente o mesmo conjunto de categorias para todos os idiomas. Consultar regras ICU Android quando aplicável; teste técnico não prova redação gramatical.
4. Detectar chaves/marcadores visíveis, texto de rascunho e sobras de português em outros catálogos com allowlist para marcas/coincidências legítimas. Igualdade com inglês não é prova automática de falta de tradução, especialmente nas variantes inglesas.
5. Buscar strings próprias ainda hardcoded em Kotlin, manifesto e assets. Separar apresentação de dados/erros internos; comentários, mensagens de teste e IDs não entram na contagem de tradução. Rodar lint MissingTranslation, ExtraTranslation, HardcodedText e erros de formato pertinentes, com baseline de avisos justificado.
6. Emitir relatório por locale/chave: cobertura, parâmetros, plurais, exceções e resultado da formatação. Meta estrutural:100% das chaves próprias incluídas; cobertura de código relevante continua com gate mínimo90%, separada da cobertura de tradução.

“Cada palavra testada” exige revisar todas as strings no contexto, não apenas comparar número de chaves. Um spellchecker pode ajudar quando houver dicionário adequado, mas não certifica significado, regionalismo, concordância ou scripts sem separação por espaços. Registrar para cada locale revisão técnica, revisão textual independente e disponibilidade de revisão nativa; não chamar tradução de certificada ou sem erros por passar lint/testes.

### Verificador estático disponível

Ferramenta sem dependências Python externas: `tools/verify_locales.py` (Python3.10+). Uso a partir da raiz:

```sh
python tools/verify_locales.py --self-check
python tools/verify_locales.py
python tools/verify_locales.py --json
```

`--self-check` testa o verificador com fixtures temporárias próprias, não as traduções/UI do produto. Validação normal retorna código1 em qualquer falha; recursos ausentes não são ignorados. `--json` escreve relatório completo em stdout; caminho de recursos/docs/config pode ser substituído por `--resources`, `--docs-root`, `--locale-config` ou `--root` para integração/testes.

Contrato atual: base en-US em `values`; qualifiers clássicos ou BCP47 aceitos para as demais variantes, ar em `values-ar` ou BCP47, pnb em `values-b+pnb+Arab+PK`. Uma pasta completa por locale; múltiplas representações simultâneas exigem revisão. Config esperada em `res/xml/locales_config.xml`, com21 tags canônicas. Documentos esperados: `docs/i18n/<BCP47>/README.md` nos21 idiomas, integrando os tópicos publicados.

São verificados UTF-8/XML, chaves de string/array/plurals, duplicatas, conteúdo visível não vazio, tamanho de arrays, quantity válida/other, referências simples de string sem ciclos, placeholders numerados com índice/conversão/multiplicidade compatíveis, conjunto do locale_config e guias UTF-8 não vazios. Recursos base `translatable=false` não são exigidos em todos os catálogos. Reordenação de placeholders é permitida. Categorias plurais podem variar; adequação gramatical/ICU permanece no gate Android, não é certificada pelo parser.

Evidência da ferramenta: **12 self-checks passaram**, usando diretórios temporários removidos automaticamente. Medição inicial com `trace` da biblioteca padrão, excluindo a classe de fixtures:238/252 linhas executáveis reportadas (94,44%); não mede ramos nem cobertura do app. A primeira validação real falhou com41 erros de arquivos ainda ausentes. Após completar e gerar os recursos, Helena repetiu a validação real: **PASS:21/21 catálogos,21/21 guias,0 erros**. Isso comprova o contrato estrutural; não certifica semântica ou UI.

Limites do utilitário: parser de tokens não executa Java Formatter nem substitui lint/Android real; existência do guia não comprova tradução, links ou comandos corretos. Nenhum idioma recebe aprovação linguística por esse script. Recursos/referências externos não resolvidos são erro explícito, não sucesso presumido.

## Instrumentação Android —21 locales reais

Suíte androidTest implementada, separada dos JVM, com runner Android e dados locais controlados. Os itens abaixo continuam como critérios de ampliação e não representam todos execução concluída. Preferir IDs/tags estáveis e consulta de estado, não seletores por texto português. ActivityScenario/runner equivalentes podem recriar a Activity e inspecionar Views reais; um TextView isolado ou catálogo renderizado em página de teste não substitui a tela real do launcher.

- Ambiente mínimo: AVD isolado API30/Android11 e API mais recente suportada disponível. Executar todas as21 locales em720p com perfil de TV quando disponível. AVD de telefone ajustado para720p pode servir para renderização, mas não deve ser chamado Android TV real nem prova de serviços/controle OEM.
- Aplicar locale somente no app ou contexto do teste; validar automático com configuração/LocaleList controlada do ambiente isolado. Não trocar idioma do SO da TV para facilitar teste.
- Navegar telas reais com D-pad/OK/Back. Para estados condicionais, usar fixtures sintéticas/test doubles limitados à suíte; sem flag de diagnóstico ou fixture no APK de entrega. Casos não alcançados devem ser listados, não marcados PASS por uma string existir no XML.
- Onde dados Android reais impedirem fixtures fiéis, separar prova do presenter/recurso de ensaio da integração. Não conceder READ_TV_LISTINGS, alterar defaultHome/Dream, abrir perfis de streaming ou confirmar desinstalação para validar rótulos.
- Medir bounds, linhas/ellipses e foco dos controles próprios: ações críticas completas/legíveis, sem sobreposição, glyph tofu ou janela cortada. Truncamento intencional de título de plataforma é distinto de ação própria ilegível.
- Em cada locale visitar: Home, duas guias/prévia/entrada/UP, biblioteca, ações/favorito/mover, galeria, Cinema, configurações, idioma, aparência, proteção e créditos, e estados vazios próprios disponíveis. Registrar variantes não ensaiadas.
- Inspecionar screensaver sem retirar FLAG_SECURE: fixtures procedurais/sem foto online e medidas/árvore de texto; não salvar fotos protegidas ou contornar proteção para obter screenshot.
- Capturas/árvores e relatório devem usar dados sintéticos, sem títulos privados da TV. Identificar locale, API, resolução, escala de fonte, rota e resultado real.

## RTL, tipografia e controles

- ar,ur-PK,pnb-Arab-PK: verificar escrita/junção, mistura de números/pacotes/nomes LTR, porcentagens e pontuação. URLs/IDs permanecem dados íntegros; não inserir caracteres direcionais persistidos em identificadores.
- Política aceita: LEFT/RIGHT seguem posição visual em RTL. Testar cadeia menu/Continue/Sugestões/Cinema e volta, limites do trilho, favoritos e Mover; não manter handlers LTR cegos após espelhar layout. UP/DOWN e OK continuam ações equivalentes.
- Testar mudança LTR para RTL e retorno sem preferência/foco órfão. Popup/Voltar, SeekBar e engrenagem precisam continuar acessíveis; não confundir tecla de diminuir SeekBar com saída do diálogo.
- Ampliar validação de layout com pseudolocales en-XA/ar-XB apenas em debug, textos extensos e escala de fonte maior no ambiente isolado. Pseudolocale não substitui nenhum dos21 idiomas reais.
- Cobertura adicional1080p/4K e fontes maiores: priorizar RTL, alemão/francês, scripts índicos, CJK e pseudolocales. Se não houver execução completa por resolução, declarar subconjunto; não certificar todo OEM/4K por unidade matemática.

## Documentação

Inventariar tópicos publicáveis e exigir versões vinculadas nos21 locales: apresentação, build/assinatura, instalação/recuperação, recursos/limites, privacidade/créditos e release. Instruções e mensagens podem ser traduzidas; comandos, caminhos, APIs, tags, hashes e identificadores mantêm semântica exata.

Validar links relativos/âncoras e referência de idioma/canonical, arquivos ausentes, exemplos de shell e divergência de versões. Preservar LICENSE legal original e atribuições de terceiros; tradução explicativa não deve substituir texto legal por licença inventada. Plano/matriz técnica devem ter escopo de tradução explícito no inventário, sem anunciar “toda documentação traduzida” deixando páginas publicadas fora da conta.

## Resultado observado da rodada final

[Relatório consolidado da versão e comandos de reprodução](i18n-validation.md).

A pasta externa de evidências `i18n-release-complete` contém `report.json`, `audit.json`,21 catálogos compilados e21 relatórios de proteção de tela. Conferência independente:21 locales com13 telas/menus únicos cada; **5.460 valores compilados =260×21**, sem diferença em relação aos JSONs de origem. A rodada anterior tinha fallback inglês em id-ID; o qualifier legado `values-in-rID` foi corrigido e a comparação final desse idioma passou. Não usar o relatório anterior como aceite final.

- **273 telas/menus reais no APK release**: Home, biblioteca, galeria, configurações, Cinema, aparência, proteção de tela, créditos, loja, idioma, ações dos apps, entradas (estado vazio) e explicação de Home. Dois lotes do mesmo APK (210+63) foram consolidados sem pares repetidos. As telas são abertas por métodos reais via reflexão; isso não equivale a273 jornadas completas pelo controle.
- **21 seleções de idioma pelo seletor/OK**, recriação e locale efetivo/direção verificados pelo runner. A cadeia de cabeçalhos recebeu6 setas por locale, **126 no total**, com LEFT/RIGHT físicos invertidos em RTL. O runner também exercita Automático/fallback em contextos locais, sem mudar idioma global do sistema.
- Auditor final: **3.145 TextViews shown, com layout e interseção com viewport;0 ellipsis;0 casos de layoutHeight+padding maior que altura**. Isso elimina as falhas detectáveis por essas métricas no estado amostrado. Interseção parcial com viewport não garante visibilidade integral; contraste, sobreposição, glyphs, shaping, maxLines/linhas ausentes e conteúdo rolável fora da amostra exigem revisão visual/contextual. Não há promessa de todas as palavras visíveis em todos os estados.
- **21 ScreenSaverActivity**: legenda corresponde a título localizado procedural ou foto+autor do catálogo, descrição no idioma correto, FLAG_SECURE presente e Back encerra a Activity. As legendas/flags foram auditadas por JSON; não foram feitas capturas de fotos protegidas.
- Gate Android informado e relatórios locais conferidos: **140 testes JVM,0 falhas/erros/skips; core392/393 linhas e680/715 ramos; lint0 erros/16 avisos**. Cobertura é do core instrumentado, não das Views, traduções ou de toda a aplicação.
- Ambiente: **AVD de telefone API35**, com renderização em720p; não TV real, não ensaio físico de standby/Home/HDMI, nem benchmark de GPU/FPS. API30, escala de fonte ampliada,1080p/4K e todos os estados vazios/erros/perfis externos não recebem PASS por esta rodada.

### Regressão do auditor de runtime

`tools/audit_runtime_locales.py` compara valores compilados, caption/contexto/FLAG_SECURE e métricas visíveis. `tests/test_runtime_audit.py` usa somente uma raiz sintética em TemporaryDirectory, sem Android, rede ou arquivos de evidência reais. A saída `audit.json` dos testes fica apenas nesse temporário e é removida ao final.

```sh
python -B -m unittest discover -s tests -p test_runtime_audit.py -v
```

Resultado observado: **9 testes passaram**. Negativos cobrem idioma errado apesar de chaves completas; chave ausente/extra; caption de outro idioma ou autor incorreto; flag segura falsa; descrição de contexto errada; corte vertical/ellipsis; telas duplicadas/locale ausente; evidência ausente/JSON malformado. Positivos distinguem isolates bidi válidos e nós ocultos/sem layout/fora do viewport, que não são tratados como cortes visíveis. Fixtures não comprovam UI do produto.

Cobertura de linhas do auditor medida com `trace` da biblioteca padrão: **37/39 linhas executáveis (94,87%)**, excluindo o marcador sintético de linha0 gerado pelo compilador; as2 linhas do entrypoint CLI não foram exercitadas. Sem medição de ramos e sem extrapolação para o app. Os testes chamam a função pública de auditoria e observam retorno/arquivo; não executam ADB ou Gradle.

## Evidência e aceite

Cada locale precisa de resultado separado: catálogo/chaves, formato/plurais, telas/estados visitados, truncamentos/glyphs/foco/RTL, documentos e revisão textual. Anexar comandos/logs reais de JVM, build/lint e instrumentação, contagem de falhas/skips e artefato final identificado. Não agregar21 locales num PASS único se algum foi ignorado.

Falhas concretas de texto ausente, crash, parâmetro errado, sentido invertido ou navegação inacessível bloqueiam entrega. Ausência de revisão nativa permanece ressalva linguística, não pode ser transformada em promessa de zero bugs. Compatibilidade da KONKA/controle real fica separada da execução no AVD; validação física adicional exige escopo operacional próprio.

Fontes oficiais consultadas: [localização e recursos Android](https://developer.android.com/guide/topics/resources/localization), [pseudolocales](https://developer.android.com/guide/topics/resources/pseudolocales) e [preferências de idioma por app](https://developer.android.com/guide/topics/resources/app-languages). Android13 acrescenta seleção por app no sistema; Android11 continua exigindo que a solução manual seja validada no alvo mínimo, sem presumir disponibilidade dessa tela do SO.

Próxima etapa: revisão linguística por falantes nativos e ampliação dirigida em Android TV/API30, fontes maiores, resoluções adicionais e estados condicionais não percorridos. Não é necessário alterar idioma global, Home, Dream ou permissões da TV para validar rótulos. Nenhum ADB ou build foi executado por Helena nesta revisão.
