# Validação do atualizador —0.4.3

## Entrega

Versão0.4.3/code29 instalada na KONKA Android11/API30, com o certificado existente e atualização com-r. Sobre ganhou verificação manual de releases do GitHub. Uma atualização disponível exige download/validação, clique para instalar e confirmação do Android. A autorização especial de fontes é decisão do usuário nas configurações oficiais; nenhum grant foi aplicado por ADB.

O código não desinstala o pacote, apaga configurações, força Home ou solicita root. Home e permissão de conteúdo existentes foram preservados. A release pública permanece0.4.2; nenhuma versão fictícia ou0.4.3 foi publicada para forçar um ensaio de autoatualização.

## Gates executados

| Gate | Observação real |
|---|---|
| JVM |172 testes aprovados, sem falhas: inclui14 contratos de transporte/download e4 regressões de identidade de tentativa |
| JaCoCo core |441/442 linhas e817/859 ramos; gate90% passou |
| Regras de update |40/40 linhas,113/120 ramos; tentativas de instalação3/3 linhas,22/22 ramos |
| Build e lint | Release e instrumentação compilados;0 erros de lint/18 avisos |
| Recursos |21 catálogos/guias,290 textos por locale; verificador sem erros |
| Consulta real na TV | Botão selecionado e OK injetado; API pública do GitHub retornou ausência de versão mais nova para0.4.3, pois a release pública é0.4.2 |
| Contratos Android |8 casos: versões antigas/atuais, seleção da avaliação mais nova não draft, JSON inválido, assets ausentes/duplicados/externos, quantidade excessiva e rejeição de APK real instalado apesar de metadata futura |
| Controlador | Worker máximo1/fila1, rejeição/purge, watchdog lógico e callback tardio ignorado, usando latch e transporte falso sem rede extra |
| Interface nova |21 idiomas×14 estados =294 painéis;483 textos novos compilados iguais aos catálogos;1.050 instâncias de texto sem problemas geométricos registrados; Back fechou nos21 idiomas |
| Preservação |16 preferências com mesmos tipos/valores e digest antes/depois; namespace updater e sessões próprias inalterados; acesso de instalação continuou falso; Home continuou Lume |

O SHA-256 do APK instalado é `ada4b84a3b9d850ad6c5588c59900b14ead590a8e8f7d31937bae2813067b463`. APK privado guardado para entrega; test APK removido após o ensaio e KEYCODE_HOME reabriu LauncherActivity. A proteção nativa Backdrop e timeout300000ms permaneceram iguais. Backup anterior e evidências completas ficaram privados no computador.

Os dois avisos de lint adicionais são commit síncrono intencional do registro de tentativa e orientação landscape da Activity instaladora de TV. Commit é necessário antes de entregar a sessão ao instalador; não foi trocado por gravação assíncrona só para silenciar o aviso. Os demais16 avisos já existiam.

## Revisão de segurança e correções

- Origem e redirects HTTPS restritos ao repositório/CDNs previstos; payloads e tempo limitados; sem credenciais ou URLs assinadas em logs. Checksum não é autenticação independente do autor.
- APK com pacote diferente, SDK incompatível, código antigo ou certificados atuais diferentes é rejeitado. PackageManager verifica o arquivo e PackageInstaller verifica novamente ao instalar.
- Revisão encontrou e corrigiu colisão de callbacks entre tentativas: lease exclusiva, owner UUID, sessão/token/URI correlacionados e atualização/limpeza condicionais à identidade da tentativa.
- Revisão encontrou e corrigiu fila sem limite: updater usa worker global separado, fila1, purge/rejeição e watchdog independente do DNS. O DNS ainda pode ocupar esse único worker após cancelamento lógico, sem criar fila ilimitada ou bloquear a UI.
- Uma interrupção exatamente entre criar a sessão Android e persistir seu ID pode deixar uma sessão própria órfã. Novas tentativas falham de modo conservador até limpeza/expiração da sessão; não há abandono aleatório de sessões desconhecidas. Não confundir com expiração de24h dos APKs temporários.

## Falha do auxiliar corrigida

A primeira execução do novo runner encontrou `NoSuchMethodError` por usar nomes internal da variante debug contra release. O processo de teste terminou antes de alterar preferências; uma verificação independente confirmou o backup exato. Ajustado `testBuildType = "release"`, compilado `assembleReleaseAndroidTest` e repetido o ensaio completo, com resultado aprovado. Não foi adicionada API pública de teste ao produto para contornar a divergência.

## Limites e próxima etapa

Não houve instalação/commit pelo PackageInstaller de uma release futura nem concessão de fontes desconhecidas. Os estados de instalação no ensaio visual são rótulos, não instalação real. Os testes de download/identidade usam transporte e identidades controlados; o contrato Android usa um APK instalado real para provar rejeição de versão/metadados, não um APK novo de assinatura errada instalado no sistema.

O watchdog foi disparado logicamente no teste, sem afirmar espera real de60segundos. A cobertura percentual publicada é do core, não de toda a Activity, rede ou PackageInstaller. A matriz de294 painéis desta rodada é do atualizador, distinta das294 telas gerais da0.4.1. Não foi repetido benchmark de FPS, Power/standby, HDMI ou revisão linguística por nativos.

Próxima prova de instalação: uma release legítima mais nova, mesmo pacote/certificado, escolhida pelo usuário; acompanhar a autorização de fontes e a confirmação nativa, preservando backup e conferindo configurações/Home depois. Não oferecer downgrade nem publicar APK fictício para produzir PASS.
