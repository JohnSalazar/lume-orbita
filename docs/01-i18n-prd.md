# Internacionalização — escopo e aceitação

## Objetivo
Preservar o design e os fluxos da versão 0.3.4; separar textos de apresentação da lógica. Android TV/Google TV 11+, sem root, alterações de firmware ou idioma global do aparelho.

Idiomas: en-US (padrão), en-GB, en-CA, pt-BR, pt-PT, es-ES, fr-FR, zh-CN (mandarim simplificado), hi-IN, ar (árabe padrão moderno), bn-BD, ru-RU, ur-PK, ja-JP, pnb-Arab-PK (punjabi ocidental Shahmukhi), mr-IN, de-DE, it-IT, ko-KR, id-ID e tr-TR.

Automático usa a primeira preferência compatível do aparelho; sem idioma compatível usa en-US. Configurações permite escolha manual persistente e retorno a Automático. Não converter silenciosamente chinês tradicional para simplificado ou Gurmukhi para Shahmukhi.

## Superfícies
Launcher, biblioteca, menus, estados vazios/erro, acessibilidade, rede, Cinema, atmosferas, proteção de tela e nomes de arte própria. Nomes de plataformas, autores, títulos e descrições fornecidos por terceiros permanecem como publicados. Preferências, IDs, URLs, permissões, assinatura e dados persistidos mantêm seu significado.

## Plano verificável
- [x] Inventário inicial e cópia de segurança Git/releases antes da exclusão remota.
- [x] Resolução de idioma e testes puros de aliases, fallback e RTL.
- [x] Recursos Android completos, placeholders compatíveis e seletor manual.
- [x] Contexto localizado nas Activities e Dream; estado/foco preservados ao trocar idioma.
- [x] Layout RTL e comandos físicos coerentes, sem inversão global de eventos.
- [x] Documentação de uso por idioma e referências de operação atualizadas.
- [x] Build, lint, testes unitários/cobertura, auditoria de recursos e testes reais de UI por idioma.
- [x] Revisão de segurança e relatório factual de limitações.

## Critérios de aceite
Todos os catálogos têm as mesmas chaves e placeholders; nenhum texto próprio depende de literal português. Alterar idioma não altera favoritos, permissões, Home ou conteúdo compartilhado. Texto pode crescer e manter legibilidade/navegação em 720p. Testes registram locale efetivo, tela observada, truncamentos, glifos e foco. Cobertura automatizada não equivale a certificação por tradutor nativo; registrar essa limitação.

## Publicação e reversão
Pedido de exclusão do repositório autorizado pelo usuário; tentativa bloqueada por ausência de delete_repo. Usuário realizou exclusão manual; consulta autenticada do repositório antigo retornou 404. Backup local fora do repositório contém mirror, bundle e release anterior. Não publicar trabalho parcial. Atualizações na TV exigem APK com a mesma assinatura; trocar idioma é reversível pelo seletor e não requer limpar dados. Manter alternativa de launcher antes de desinstalar o Lume padrão.

## Fontes oficiais
- https://developer.android.com/guide/topics/resources/localization
- https://developer.android.com/guide/topics/resources/multilingual-support
- https://developer.android.com/guide/topics/resources/app-languages

INTENT: código 0.3.4 apresenta textos próprios em português; pedido exige seleção automática/manual em 21 locales com fallback en-US; README descreve Android TV 11+ e preservação das funcionalidades existentes.

Aceite técnico: ver docs/qa/i18n-validation.md. Validação em API35/720p; revisão nativa e repetição física na KONKA/API30 permanecem limites declarados, sem aprovação universal.
