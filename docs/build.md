# Build e assinatura

Requisitos: JDK17, Android SDK/API35, BuildTools36.0.0; AGP8.9.2 e Kotlin2.1.20 fixados nos fontes. O wrapper fixa Gradle8.11.1 e seu SHA256.

Use `JAVA_HOME`/`ANDROID_HOME`, ou um `src/android/local.properties` particular com `sdk.dir` apropriado. Não versione esse arquivo. Aceite as licenças do SDK pelas ferramentas oficiais.

No diretório `src/android`, execute:

```sh
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

No Windows substitua `./gradlew` por `.\gradlew.bat`.

Para o APK auxiliar de testes nativos, execute `assembleReleaseAndroidTest`; saída `app/build/outputs/apk/androidTest/release/app-release-androidTest.apk`. `testBuildType = "release"` mantém os nomes internos Kotlin compatíveis com o APK de avaliação. Instalar uma instrumentação compilada contra debug no APK release pode causar `NoSuchMethodError` em acessos internal, sem representar falha do launcher normal. Os testes JVM continuam usando debug.

O gate JaCoCo exige pelo menos90% de linhas e ramos do pacote `core/**`. Relatórios em `app/build/reports/jacoco/rulesCoverage` e `app/build/reports/lint-results-release.*`. A cobertura do core não mede toda a Activity, renderização, rede real ou o controle físico.

## Assinatura

O build local usa a configuração de assinatura de desenvolvimento do Android, com `isDebuggable=false` no release. O APK publicado é uma versão de avaliação, assinada pela chave particular mantida pelo autor; não é publicação na Google Play nem assinatura de produção gerenciada pela loja.

Chaves, senhas e configuração local não estão no Git. Um clone pode gerar outra chave e outro certificado, portanto compilar não garante APK byte a byte idêntico nem permissão para atualizar o APK oficial existente. Para uma atualização compatível, o mantenedor precisa usar a mesma chave. Um fork deve manter sua própria identidade/assinatura; não apague dados ou desinstale a única Home só para contornar erro de certificado.

Downloads oficiais devem ser conferidos pelo SHA256 fornecido na release. A impressão digital do certificado é pública; a chave privada nunca deve ser publicada.
