# Relógio por idioma —0.4.2

Pedido: o formato da hora deve acompanhar o padrão do idioma selecionado no Lume. Os dois relógios usavam `HH:mm`, inclusive quando a interface mudava para inglês dos EUA.

Correção: Home e proteção de tela usam uma única função de formatação curta por locale. O idioma efetivo mantém a política existente de seleção automática/manual, fallback en-US e mapeamento de formatação Shahmukhi. Não há lista manual de países com12/24 horas. Idioma, horário, fuso e preferência12/24 global do Android não são alterados.

Exemplos esperados para17h05: pt-BR `17:05`, en-US `5:05 PM`, en-GB `17:05`. Meia-noite e meio-dia em en-US precisam distinguir `12:00 AM` e `12:00 PM`. Pontuação, espaços, nomes de período e algarismos seguem os dados de locale disponíveis no Android.

Escopo de regressão: testes JVM, build/cobertura/lint e teste focado na KONKA/API30. Comparar63 casos conhecidos (21 idiomas×3 horários) com um formatter nativo independente e verificar os relógios realmente renderizados em pt-BR/en-US/en-GB, incluindo a largura adicional de AM/PM. A proteção é uma instância de teste com relógio ligado em memória; não muda a preferência salva de exibição do relógio.

Preservação: novo backup das16 preferências atuais, incluindo a escolha manual feita pelo usuário depois da0.4.1. Não reutilizar o snapshot anterior de15 entradas para restaurar esta rodada. Usar a mesma assinatura, atualização com-r, journal tipado, verificação exata após teste e conferir Home padrão. Artefatos pessoais permanecem fora do repositório.

## Resultados executados

- Build release/teste concluído:148 testes JVM, sem falhas; cobertura core398/399 linhas e682/717 ramos; lint0 erros/16 avisos. Verificador de21 catálogos/guias e9 testes Python também passaram. Cinco testes JVM novos cobrem a regressão de formato, meia-noite/meio-dia, preservação do locale global, aliases/fallback/Shahmukhi e algarismos padrão.
- APK0.4.2/code28 instalado na KONKA/API30 com-r e certificado anterior preservado. SHA-256: `7f0cb95aa2ce905f36179c1393638eaddfa4d4e018f473c0253c39b0b3971114`.
- Instrumentação nativa: **63/63 casos** concordaram com `java.text.DateFormat.SHORT` do Android, incluindo a sequência exata de algarismos. Normalização separada de espaços/bidi não dispensou essa comparação dos algarismos.
- Relógios renderizados de Home e proteção conferidos em três idiomas: **pt-BR23:58 / en-US11:58 PM / en-GB23:58**, iguais entre os dois componentes. Seis medições sem ellipsis, quebra de linha ou corte de área visível. A comparação protegeu a possível virada de minuto usando horários antes/depois da leitura.
- Todas as16 preferências tipadas idênticas ao novo backup depois da atualização e depois dos testes. Escolha manual de idioma preservada; nenhum retorno forçado ao snapshot anterior de15 entradas.
- Home continuou no Lume; READ_TV_LISTINGS manteve o grant anterior; idioma Android pt-BR, fuso America/Sao_Paulo e time_12_24 null permaneceram iguais. Removido apenas o APK auxiliar de teste. KEYCODE_HOME abriu LauncherActivity do Lume; comandos automáticos encerrados.

Revisão de segurança: formatter somente de apresentação; snapshot restaurado por commit/digest; renderer encerrado antes de abrir Android Settings para limpar as Activities. Nenhuma nova permissão, dependência ou alteração protegida. Backup e evidências completos ficaram privados no computador.

Não foi repetida a matriz completa de294 telas da0.4.1: a mudança ficou limitada aos dois relógios. A formatação dos21 idiomas foi exercitada no Android; a geometria dos relógios foi medida em três idiomas, não21. Sem novo benchmark de FPS, teste de Power/standby, HDMI ou revisão linguística por falante nativo nesta rodada.

Próxima etapa: usuário alternar pt-BR/en-US pelo seletor normal e conferir o resultado com o controle físico. O formato segue o padrão do idioma escolhido no Lume; o ajuste global12/24 do Android não sobrescreve essa escolha local.
