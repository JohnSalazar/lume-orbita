# Atualizações manuais pelo GitHub

## Estado e escopo

Recurso implementado na linha de desenvolvimento **0.4.3/code29**, para Android TV e Google TV 11/API30 ou posterior. Esta documentação descreve o fluxo e as barreiras de segurança; não certifica a instalação de uma release futura nem publica a0.4.3. Uma instalação0.4.3 não deve oferecer a release pública0.4.2 como atualização ou downgrade.

Fontes: [PRD](../01-updater-prd.md), [contrato de segurança](../design/update-security.md) e [plano de testes](../qa/updater-test-plan.md). Os resultados efetivamente executados precisam ser registrados separadamente pela operação. Testes de regras e adapters não equivalem à confirmação de uma instalação pelo Android.

## Uso na TV

1. Pela engrenagem, abra **Sobre > Verificar atualizações**. A consulta ocorre somente após esse clique; não há verificação periódica, no boot, ou instalação silenciosa.
2. O Lume consulta apenas as releases do repositório público fixo `JohnSalazar/lume-orbita`. Mostra ausência de versão nova, erro recuperável ou candidato compatível. Versões de avaliação são identificadas como tais. O histórico consultado é limitado, não uma varredura completa de todas as releases.
3. Se uma versão nova estiver disponível, escolha **Baixar atualização**. A consulta não inicia automaticamente o download. O APK e o arquivo de checksums pertencem à mesma release esperada. O progresso aparece no painel; Voltar cancela a operação do Lume.
4. Depois do download e da validação, escolha **Instalar atualização**. Esse é um novo clique do usuário, separado de baixar. Somente o APK validado segue para o instalador.
5. Se o Android exigir autorização para instalar dessa origem, abra **Permitir esta origem** e decida pessoalmente nos ajustes do Android. O aplicativo não concede o acesso por código, ADB ou root. Retorne ao Lume para confirmar a instalação; abrir a tela de autorização não instala nada.
6. O fluxo usa **PackageInstaller** e a confirmação oficial do Android. O usuário pode recusar ou cancelar. A disponibilidade e a aparência desse diálogo dependem do Android e do fabricante; não há garantia de instalação silenciosa ou universal.
7. Após uma atualização, confira o Lume e o botão Home. Atualizar o mesmo pacote com assinatura compatível preserva os dados pelo mecanismo padrão do Android. Se o fabricante alterar a Home, o Lume oferece **Revisar tela inicial**, com acesso à escolha oficial para o usuário decidir manualmente. O aplicativo não redefine Home automaticamente.

Não desinstale o Lume nem limpe os dados para atualizar. Mantenha um launcher alternativo funcional disponível antes de depender de um aplicativo como Home. A autorização para instalar fontes e a escolha de Home são decisões diferentes; uma não deve alterar a outra.

## Limites de rede, disco e concorrência

| Limite | Implementação prevista no fluxo |
|---|---|
| Releases | Até30 registros na listagem; drafts e metadados inválidos não viram autorização de instalação |
| JSON | Até1MiB |
| Checksums | Até64KiB |
| APK | Até32MiB, com contagem dos bytes realmente lidos e verificação do tamanho completo |
| Redirects | Até3, por HTTPS e somente nas origens/CDNs aprovadas |
| Tempo | Deadline de60s por transferência e watchdog de60s da UI para cada operação de consulta ou download/validação |
| Concorrência | Um worker e fila limitada; respostas de gerações antigas são descartadas |

Os60s não são uma promessa de concluir a instalação: o tempo gasto pelo usuário na autorização e no diálogo do PackageInstaller é separado. O watchdog cancela logicamente a operação e devolve um estado recuperável mesmo se uma resolução DNS demorar; a thread pode continuar ocupada até a chamada de plataforma retornar. O limite de concorrência evita abrir workers sem controle para contornar esse bloqueio. Não faça consultas repetidas em sequência para tentar ultrapassar limites do GitHub.

O APK precisa de um temporário em cache interno privado para a instalação, diferentemente das fotos online da proteção de tela, que continuam em RAM. Não usar Downloads, caminho escolhido pela release, permissão genérica de armazenamento ou URI pública. Falha, cancelamento e término do staging limpam apenas os temporários próprios; o cliente também identifica resíduos antigos com seu padrão privado. A sessão do Android ocupa espaço adicional. Não apagar preferências, papéis de parede, arquivos de terceiros ou sessões desconhecidas para liberar espaço.

## Validação e privacidade

O download usa TLS normal e URLs fixas do projeto, sem redirecionamento permissivo. O fluxo confere os bytes e o SHA-256 do asset, a versão anunciada contra a versão real do APK, pacote exato `br.com.lume.launcher`, `versionCode` estritamente maior, SDK mínimo compatível e o conjunto exato dos certificados dos signatários atuais. `PackageManager` examina o APK e `PackageInstaller` verifica o pacote ao instalar. Arquivos inválidos, ambíguos, adulterados ou assinados de forma incompatível devem ser bloqueados.

O checksum do mesmo repositório detecta corrupção, mas não é autenticação independente; a conferência da assinatura local permanece obrigatória. O APK não é descompactado para executar classes ou layouts no processo atual. Os mesmos bytes validados são reconferidos antes do staging.

Não há credenciais GitHub, cookies, telemetria própria ou envio de configurações, favoritos, histórico e contas de streaming. O GitHub e sua CDN recebem naturalmente o IP e a requisição de rede; isso não equivale a anonimato. Não registrar consultas assinadas de CDN, dados pessoais ou respostas brutas em logs públicos.

## Tentativas, callbacks tardios e recuperação

O instalador mantém uma tentativa própria identificada por owner, sessão e token. `InstallerAttemptRules` correlaciona esses valores com o estado ativo. Um callback tardio ou uma Activity antiga não pode confirmar, limpar ou abandonar uma tentativa posterior. A Activity de instalação é privada; apenas o retorno correlacionado à sessão ativa pode avançar a confirmação do Android. A decisão de Home é registrada separadamente para ser conferida após uma versão nova realmente instalada.

Cancelar ou falhar não autoriza desinstalar, limpar dados, conceder acesso especial ou mudar Home para tentar novamente. A limpeza abandona somente uma sessão cuja propriedade foi confirmada. Se o processo for encerrado entre criar a sessão e registrar seu identificador, o código não deve adivinhar nem abandonar outra sessão; uma nova tentativa pode ser bloqueada até que a situação seja resolvida. Reabra o Lume e verifique o estado oficial no Android antes de insistir.

Antes de uma validação física, registre a versão, Home, acesso a fontes e um backup recente de todas as preferências. Compare os mesmos tipos, valores e ausências de chaves depois do teste; não restaure um snapshot antigo por conveniência. Guarde os backups e as evidências em local privado. O [procedimento de validação do aparelho](device-validation.md) explica o journal tipado e a cópia segura.

Uma versão antiga de APK não garante downgrade com preservação de dados. Se a Home mudar por ação do fabricante, use a seleção oficial manual e mantenha uma alternativa funcional. O sucesso no caminho “nenhuma versão nova” comprova a consulta e a UI desse caso, não uma instalação completa. Não publicar release fictícia, conceder permissões por shell ou confirmar a instalação de um APK inválido para produzir um resultado de teste.
