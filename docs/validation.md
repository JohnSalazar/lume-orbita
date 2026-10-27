# Validação da versão0.3.4

Registro histórico da base anterior. Para a versão multilíngue 0.4.0, consulte [a validação de idiomas](qa/i18n-validation.md).

Estado antes da primeira publicação:129 testes JVM aprovados, sem falhas/erros/skips. JaCoCo do core:349/350 linhas(99,71%),570/600 ramos(95%); lint release:0 erros/11 avisos preexistentes. O gate não é cobertura de toda a UI.

Em uma KONKA AndroidTV11/720p foram observadas navegação por controle/ADB, abertura de apps e Settings, seleção como Home, retorno de Home, favorito/ordem, cards de plataformas e proteção. A revisão034 verificou títulos focados com card ampliado sem foco real, cadeia horizontal e entrada explícita na prévia. Standby físico com mesma capa foi confirmado na revisão032; não foi repetido fisicamente na034. Outros modelos/4K, uso prolongado e todos os casos extremos não estão certificados.

APK oficial de avaliação0.3.4, versionCode25, minSdk30/target35. SHA256: `FD765C110CE8CC9F411235582D8CA8395D043813AE2183AE643F37609A4ED90C`. CertificadoSHA256: `c435b38a840b6381b56c2f71f6e914f7958218762139b47ecd9607793cd67cdb`.

Registros particulares de TV, capturas com histórico, arquivos XML de UI e backups de outros apps foram intencionalmente excluídos da publicação. Rodar novamente os gates de build acima é necessário para qualquer alteração futura.
