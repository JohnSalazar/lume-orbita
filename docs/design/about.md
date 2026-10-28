# Sobre

Acesso: engrenagem > Ajustes > Sobre. O painel reutiliza o diálogo do launcher, com navegação pelo controle, rolagem limitada à área disponível e Voltar no cabeçalho.

- Nome do aplicativo lido de `ApplicationInfo.loadLabel`.
- Versão instalada e código de compilação lidos de `PackageManager.getPackageInfo`, sem versão fixa no texto da interface. Se o pacote não puder ser consultado, mostra informação indisponível.
- Identificação da licença MIT do código original e aviso de que fotos, software e marcas de terceiros conservam os próprios direitos.
- Botões abrem o repositório público e o texto da licença por HTTPS, somente após ação do usuário. Sem navegador disponível ou com abertura bloqueada, o painel mostra erro traduzido.
- Nenhuma preferência, Home, configuração do Android ou permissão é alterada pelo painel.

Implementação: `LauncherActivity.showAbout()`. Sete chaves `activity_about_*` no catálogo `tools/activity_strings.json` e nas 20 traduções adicionais; inglês dos Estados Unidos vem do catálogo principal. Recursos dos 21 idiomas gerados por `python tools/build_locale_resources.py`.

Validação desta alteração: geração concluída com 267 textos por idioma; `python tools/verify_locales.py` retornou `PASS: 21/21 catalogs, 21/21 guides, 0 errors`. Estrutura e placeholders verificados; isso não certifica revisão linguística nativa.

Validação física posterior: build/lint concluídos; Sobre renderizado nos21 idiomas na KONKA com versão0.4.1/build27, sem ellipsis ou candidato de altura excedida. Links externos não foram acionados nessa matriz. Resultados e limites: [validação KONKA](../qa/konka-041-validation.md).
