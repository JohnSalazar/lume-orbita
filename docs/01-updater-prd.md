# Atualização manual pelo GitHub

## Objetivo

Em Ajustes > Sobre, consultar novas versões do repositório público JohnSalazar/lume-orbita e permitir atualização com confirmação do usuário e do Android, mantendo pacote, assinatura, configurações e a seleção existente de Home.

## Escopo e fluxo

1. “Verificar atualizações” consulta o GitHub somente por ação do usuário. A aplicação não faz verificações automáticas no boot nem instala silenciosamente.
2. Exibe ausência de versão nova, erro recuperável ou versão disponível. Considera a linha de avaliação/prereleases já usada pelo projeto; `/releases/latest` sozinho não atende esse canal.
3. “Baixar atualização” solicita o APK e o checksum da mesma release oficial. Progresso visível; Voltar cancela a operação. Nenhuma fotografia ou conteúdo das plataformas participa da consulta.
4. Confere limites de tamanho, SHA-256, nome do pacote, versão real do APK, SDK mínimo e certificados atuais iguais aos da instalação. Pacote incompatível ou não verificável é bloqueado.
5. Se necessário, usuário abre a autorização de fontes do Android e decide concedê-la. Retorna ao Lume e escolhe “Instalar atualização”. O instalador nativo confirma a ação.
6. A atualização padrão do Android conserva os dados do pacote. Não desinstalar o Lume, limpar dados, solicitar root ou redefinir Home. Se o OEM modificar a escolha de Home após substituir o pacote, informar e oferecer acesso à seleção oficial, sem alterá-la automaticamente.

## Implementação definida

- Versão0.4.3/code29; Android11/API30 ou posterior; Kotlin/Views existentes, sem nova biblioteca.
- Política pura `UpdateRules`, cliente síncrono `GitHubUpdater` fora da thread de UI e instalador Android com Activity privada/PackageInstaller.
- Interface reutiliza painéis e navegação do launcher;23 textos novos nos21 idiomas.
- APK temporário privado é necessário ao instalador e é descartado após staging/cancelamento/falha. A regra de fotos online somente em RAM permanece intacta.
- Novo acesso especial `REQUEST_INSTALL_PACKAGES` é declarado; nenhum grant é concedido por ADB ou por código. Não usar `INSTALL_PACKAGES` ou `UPDATE_PACKAGES_WITHOUT_USER_ACTION`.

## Aceite e evidência

- Testes de versões/URLs/checksum/limites/pacote/SDK/certificado; negativos para adulteração, downgrade e cancelamento.
- Testes do cliente com rede controlada, consulta real ao GitHub e interface sem versão nova na KONKA. Uma versão instalada de desenvolvimento superior à release pública deve informar ausência de atualização, sem oferecer downgrade.
- Recursos e estados traduzidos verificados, incluindo RTL e controle remoto; preservar mapa tipado das configurações, Home e acessos especiais antes/depois.
- Confirmação de instalação futura é uma prova distinta da consulta: não alegar autoatualização concluída apenas porque o botão aparece. Nunca publicar APK fictício para forçar o teste.
- Build, lint e cobertura das regras passam; cobertura core não representa o instalador inteiro. Documentar gates executados e pendentes.

## Limites

O GitHub pode ficar indisponível ou limitar consultas. TLS/checksum não substituem a assinatura do desenvolvedor. O Android/OEM pode exigir autorização adicional ou indisponibilizar o instalador. A seleção de Home deve ser conferida, sem prometer comportamento universal. Ver [contrato de segurança](design/update-security.md) e [plano QA](qa/updater-test-plan.md).

Fontes oficiais: [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller), [ação obrigatória do usuário](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)), [autorização de fontes](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()), [releases do GitHub](https://docs.github.com/en/rest/releases/releases#list-releases).
