# Arquitetura

- `src/android/app/src/main/java/br/com/lume/launcher`: Activity, componentes visuais, catálogo de apps, ações Android, conteúdo compartilhado, imagens em memória e proteção de tela.
- `core/`: regras puras testáveis sem dispositivo.
- `app/src/test`: testes JVM de regras, limites, cancelamento e transporte falso.
- `app/src/main/assets`: atmosferas próprias e metadados/créditos de fotografias remotas.

Kotlin+Views, sem WebView, NDK, backend ou serviço permanente de automação. Manifesto declara Home e Leanback; Android decide o papel Home. D-pad mantém foco real nos cabeçalhos, com prévia visual independente no card lembrado. Metadados de prévia não gravam a escolha do card; somente foco real o faz. Preferências privadas guardam guia e dois identificadores, não histórico de títulos/URIs.

Materiais estáticos e transformações nativas evitam blur pesado no Android11. Cache de imagens é limitado em RAM, preservado na pausa e liberado na destruição; conteúdo fora da tela pode ser descartado. Processos encerrados precisam recarregar as fotos. Proteção procedural é renderizada localmente; fotos remotas mantêm atribuição acessível nos Ajustes.
