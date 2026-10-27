# Idiomas por aplicativo

## Resolução
`core/LocaleRules` define 21 tags canônicas, allowlist manual, aliases e fallback en-US. Automático percorre a lista de preferências do aparelho. Regiões não cadastradas podem usar a variante padrão do mesmo idioma; scripts incompatíveis não são convertidos (zh-Hant não vira zh-Hans; Gurmukhi não vira Shahmukhi).

O locale canônico indonésio continua id-ID, mas os recursos usam values-in-rID, necessário para o matching Android observado no teste real. Datas/números Shahmukhi usam o alias de formatação pa-Arab-PK: o [CLDR](https://github.com/unicode-org/cldr/blob/main/common/main/pa_Arab.xml) registra seus dados sob pa-Arab. O idioma de UI e a preferência persistida continuam pnb-Arab-PK.

`AppLocales.wrap(base)` copia Configuration e cria contexto localizado, com en-US como segunda preferência. Preserva a origem do contexto para que voltar a Automático não herde a escolha manual anterior. `app_locale` fica nas preferências existentes; escolha inválida armazenada é tratada como Automático. Não usa Locale.setDefault, recursos globais mutáveis, configuração protegida ou alteração de idioma do Android.

Activities envolvem o contexto em attachBaseContext. Dream cria seu renderer com contexto localizado. A direção RTL é explícita para árabe, urdu e punjabi ocidental, inclusive em tabelas ICU que não reconheçam pnb. O seletor é próprio do Lume; não há sincronização com o seletor de idiomas por aplicativo do Android 13. locales_config.xml é inventário verificável, não registro dessa integração no manifesto.

## Apresentação e estado
260 recursos próprios por locale ficam em strings.xml; fontes revisáveis em tools/common_strings.json, activity_strings.json e translations/*.json. `build_locale_resources.py` verifica placeholders e gera XML com escape Android. Os guias ficam em docs/i18n/<tag>/README.md. O verificador independente checa XML, UTF-8, chaves, formatos e guias.

`LocalizedText` resolve nomes de atmosferas/arte própria e duração. Nomes de apps, autores e metadados das plataformas não recebem tradução inventada. Identificadores, URLs e chaves de preferências permanecem estáveis. Collator usa o locale efetivo; valores fornecidos por terceiros em frases formatadas recebem isolamento bidi.

Os APKs incluem todos os idiomas. Language splits também ficam desativados no Gradle para que uma eventual compilação de App Bundle não retire idiomas necessários ao seletor offline; publicação e entrega de AAB não foram validadas nesta etapa.

Troca manual salva a escolha e recria a Activity. Bundle preserva página, guia e tag de foco; restauração espera a View correspondente existir e interação real cancela restauração pendente. Workers/loaders antigos são encerrados pelo lifecycle existente. Favoritos e permissão de conteúdo não são regravados pela troca.

## Navegação
Layouts herdam a direção do contexto. START/END preservam posicionamento semântico. Somente handlers horizontais próprios usam `LocaleRules.navigationKey`; setas repassadas ao framework não são invertidas. Links de foco físicos são definidos por focusStart/focusEnd. Botões laterais ajustam a fonte entre 10 e 14sp para evitar quebrar palavras longas.

## Verificação e limites
Testes puros cobrem resolução, scripts, aliases, direções e reversibilidade das setas. O runner androidTest usa o seletor real para recriação, observa recursos compilados e telas nativas, envia eventos DPAD e registra textos/capturas. Ele é exclusivo do pacote de teste, sem receiver de controle no release. Casos sem dados de plataformas não equivalem a reproduções reais de streaming. FLAG_SECURE da proteção de tela é preservado; capturas não devem contorná-lo.

AVD de telefone com perfil 720p testa layout/lifecycle, não certifica integrações Android TV, Home/standby do fabricante, GPU ou fluidez. Revisão textual assistida não equivale à certificação por falantes nativos. Consultar o relatório de QA para os testes realmente executados.
