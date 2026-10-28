# Plano QA — atualização pelo GitHub

Estado: **contratos e fontes de teste congelados; execução nativa pendente de repetição com variante compatível**. Helena não executou Gradle/ADB nem uma instalação futura. Este plano não transforma fixtures em release real ou autoriza publicar/instalar APK de versão inventada. Clara é a operadora de build, TV e GitHub.

## Variante da instrumentação

O app instalado na TV é release. O módulo configura `testBuildType = "release"`; a instrumentação deve ser compilada com `assembleReleaseAndroidTest`, a partir de `src/android`. Os testes JVM continuam usando `testDebugUnitTest`. A execução e a instalação do APK auxiliar ficam com Clara.

```powershell
.\gradlew.bat testDebugUnitTest verifyRulesCoverage assembleRelease assembleReleaseAndroidTest lintRelease
```

A primeira tentativa nativa, relatada por Clara, encerrou antes do bloco de snapshot com `NoSuchMethodError` em `UpdateInstaller.currentHome$app_debug`, pois o app instalado fornece a variante `$app_release`. Isso é incompatibilidade do artefato de teste com o alvo, não demonstra defeito do updater. `parseReleases` e outros acessos `internal` também precisam da mesma variante Kotlin. Não criar métodos públicos de produção nem resolver nomes mangled manualmente para contornar o problema. A nova execução deve comprovar restauração e estados; esta falha não concede PASS nativo. A confirmação externa de preferências/Home preservados cabe às leituras de Clara.

Escopo solicitado: em Sobre, consultar o repositório público e permitir instalação de versão nova confirmada pelo usuário, preservando configurações e Home. O primeiro ensaio real proposto é app0.4.3/code29 consultar a release pública0.4.2: resultado esperado sem atualização, sem solicitar acesso especial nem abrir o instalador. Se a release pública mudar antes da execução, registrar o estado real e reavaliar esse pressuposto.

## Contratos a congelar antes dos testes

Proposta atual dos especialistas, ainda sujeita ao contrato final:

- Cliente síncrono fora da UI, conexões/resolução DNS/relógio monotônico injetáveis, cancelamento por flag e sem executor particular por consulta. O controlador da Activity deve limitar concorrência e descartar callbacks de geração antiga.
- Metadados somente do repositório fixo; lista de releases, política explícita de prerelease e seleção determinística. Draft não publicável/response incompleto nunca vira autorização de instalação. Tag exibida não substitui versionCode real do APK.
- HTTPS inicial no caminho exato do repositório/tag/arquivo; redirects manuais, até3, somente hosts CDN aprovados, porta443, sem credenciais/fragmentos ou endereço privado. Sem token, cookie, Authorization ou outro header sensível repassado.
- Arquivo completo com tamanho exato/cap e SHA256 igual à entrada única do nome correto em SHA256SUMS; pacote exato, versionCode real maior que instalado, minSdk compatível e conjunto dos signers atuais idêntico ao instalado. Certificado histórico isolado não satisfaz igualdade dos signers atuais. Android revalida ao instalar.
- Download em arquivo privado próprio, com streaming; remover parciais em erro/cancelamento e definir o ciclo de vida do arquivo validado. Isso é distinto das fotos da proteção, que continuam somente em RAM. Nenhuma escrita deve atingir preferências, wallpapers, fontes ou arquivos alheios ao updater.
- Limites sugeridos por Davi: metadados1MiB/30 releases, checksums64KiB, APK32MiB, deadline total60s e read10s com connect limitado pelo tempo restante. Os testes usarão os valores finais de contrato, não números ajustados para a implementação passar.

O checksum hospedado junto do APK detecta corrupção, mas não é uma raiz independente de autenticidade. A comparação da assinatura com a instalação atual é obrigatória. Leitura de PackageManager com flags adequadas precisa de integração Android; um PackageInfo fake não prova verificação criptográfica do APK.

## Testes JVM/integração local sem rede

Reaproveitar o padrão já presente em MemoryImageLoaderTest e ProgramArtworkLoaderTest: HttpURLConnection fake, relógio injetado, streams limitados, exceções/latches controlados e JUnit4.13.2. Usar TemporaryFolder/diretório temporário exclusivo para os arquivos do updater. Nenhuma infraestrutura, pacote novo ou consulta real é necessária nesses testes.

| Área | Casos mínimos que devem falhar ou preservar invariantes |
|---|---|
| Consulta/seleção | Sem releases; atual/mais antiga; superior; tag malformada; duplicatas/ordem; prerelease conforme política; assets/checksum ausentes ou ambíguos; metadado truncado/oversize |
| HTTP |200 válido;401/403/404/429/5xx; MIME inválido; gzip/comprimento conforme contrato; stream vazio; read failure; Content-Length ausente/mentiroso; limite real de bytes e deadline |
| Origem | HTTP; host parecido/subdomínio não autorizado; userInfo/porta/fragmento; repo/tag/nome incorretos; redirect relativo/absoluto válido, fuga de allowlist, DNS privado em qualquer salto e loop além do limite |
| Checksum | SHA exato aceito; linha duplicada/nome diferente; caminho relativo/traversal; bytes corrompidos; tamanho declarado diferente; nenhum arquivo preparado retornado em falha |
| Compatibilidade | Mesmo package/code/signers atuais conforme contrato; rejeitar downgrade/mesmo code, package diferente, minSdk alto, signer errado, conjunto extra ou apenas histórico; APK inválido/truncado |
| Cancelamento | Antes de conectar, antes/depois de criar arquivo, durante read, após validar e antes de publicar resultado; cancelar não bloqueia UI com I/O; remover parcial e não emitir sucesso tardio |
| Concorrência/controlador | Toque duplo não duplica job/instalação; worker bloqueado não cria threads sem limite; timeout visual independe de callback; sair/voltar/recriar não publica estado antigo |
| Arquivos | Diretório próprio; tentativa com nome não confiável não escapa; erro/cancel remove só o próprio arquivo; último arquivo válido não é confundido com parcial de outra geração |
| Instalador/adapter | Apenas arquivo validado pode seguir; ausência de acesso especial não faz commit; cancelamento não instala; encaminhamento de user-action seguro; falha encerra/abandona sessão e limpa recursos |

Os testes do adapter usam fake de sessão/installer; não criam nem confirmam uma sessão Android real. Se não houver seam no contrato final, separar lógica pura e marcar o fluxo PackageInstaller como integração nativa pendente. Não escrever teste que apenas espelhe cada linha do cliente.

Meta: pelo menos90% de linhas/ramos na lógica relevante incluída no gate, com relatório separado para cliente/arquivo/instalador. O gate atual só inclui core; não usar sua cobertura para anunciar cobertura de todos os novos componentes Android/HTTP. Build/lint e testes anteriores precisam continuar passando.

## Instrumentação focada: consulta sem atualização

Planejar AboutCheckNoUpdateInstrumentation com entrada adicional explícita no manifest androidTest, preservando o runner padrão. Só implementar após os nomes/estados/tags da UI e contratos finais estarem definidos.

1. Snapshot tipado integral antes de qualquer mutação, journal recuperável e comparação com backup atual; não restaurar snapshots de sessões antigas. Capturar Home/role e estado do acesso a instalação de fontes desconhecidas por leitura.
2. Abrir Sobre e acionar o botão real de consulta por OK. Registrar origem pública/versionCode instalado e resultado real. Não modificar versão do servidor ou publicar release de fixture para produzir sucesso.
3. Exigir estado verificando e resultado sem atualização quando a fonte pública continuar0.4.2 contra app0.4.3. Não baixar APK, criar sessão/commit, pedir permissão especial ou alterar configurações nesse caminho.
4. Testar Back/cancelamento com latência controlada em outro cenário de fixture, sem desligar rede da TV. Fixture de erro/timeout comprova UI correspondente, não indisponibilidade real do GitHub.
5. Estacionar em Android Settings, confirmar foreground e app pausado, encerrar Activities acompanhadas e esperar destruição antes da restauração. finally deve tentar restaurar mesmo se cleanup falhar; salvar erro original e erro de restauração.
6. Comparar mapa completo/tipos/ausências, digests e Home depois do retorno normal. Remover somente APK auxiliar/artefatos próprios conforme o procedimento de Clara. Nenhuma alteração de rede, SO locale, Dream ou grant é necessária.

Uma consulta real é suficiente para o contrato de rede básico. Repetir a API pública21 vezes pode consumir quota e introduzir falhas por rate limit. Para os21 idiomas, conferir recursos/strings compiladas e renderizar estados via fakes explícitos do cliente/controlador, distinguindo-os do ensaio real.

## Idiomas, foco e estados da UI

- Todos os novos textos em base+20 catálogos, sem vazios/fallback acidental, parâmetros de versão/tamanho/progresso corretos. Comparar compilados com JSONs da mesma build; não presumir contagem antiga de267 chaves após adicionar o updater.
- Revisar Sobre e seus estados: parado, verificando, sem atualização, disponível, baixando/progresso, validando, acesso especial necessário, confirmação Android, cancelado e erro. Estados inexistentes no contrato não devem gerar feature especulativa.
- D-pad/OK/Back: indicador de andamento não rouba foco; toque repetido não dispara duas consultas; Back cancela/fecha conforme contrato e devolve foco ao botão de origem; key-up de abertura não confirma instalação.
- RTL ar/ur/pnb: versão/hash/percentuais e nomes de arquivo preservam ordem legível; ações inteiras, sem sobreposição ou texto de erro cortado; strings longas europeias/índicas/CJK também precisam de amostras.
- Não abrir automaticamente links de release, configurações especiais ou instalador. A tela de confirmação pertence ao Android e pode variar por OEM; não tratá-la como View própria do Lume.

## Fluxo de versão futura: prova separada

Para exercer a validação, usar candidato/PackageInfo fake nos testes de contrato. Metadado fake de versão maior com APK real de code28 deve ser rejeitado; não é uma forma legítima de alcançar o instalador.

Um eventual ensaio nativo de confirmação/CANCEL exige APK local de fixture com **versionCode realmente maior, mesmo package e mesmo certificado**, identificado como fixture e nunca publicado. Abrir apenas a confirmação e cancelar não prova atualização instalada. Não instalar essa versão inventada nem confirmar commit sem nova autorização humana específica.

Se canRequestPackageInstalls for false, apenas ler/registrar esse estado: não conceder acesso por ADB, não tocar no toggle de fontes desconhecidas e não chamar isso de fluxo de instalação testado. A instalação real de uma futura release oficial e a persistência de dados após essa troca ficam pendentes até existir a versão/autorização correspondente.

## Entrega e riscos residuais

Registrar comandos/logs reais, SHA do APK, assinatura, testes/cobertura por componente, check público observado, fixtures usadas, cancelamentos, preferências/Home e estados não ensaiados. Não publicar journals, dados do TvProvider, identificadores da TV ou arquivos privados. Não afirmar updater pronto para qualquer OEM com apenas mocks/um caminho sem atualização.

Próxima etapa: Clara recompilará a instrumentação release e executará os gates e ensaios autorizados. Os 14 testes JVM do cliente e a instrumentação `UpdaterCheckInstrumentation` estão preparados; resultados e limitações precisam ser registrados após execução. Este documento não concede PASS ao recurso.
