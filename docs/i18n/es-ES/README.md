# lume órbita — guía de uso y colaboración

QA: [0.4.0](../../qa/i18n-validation.md) · [0.4.1](../../qa/konka-041-validation.md) · [0.4.2](../../qa/clock-locale-042.md)

Launcher original para **Android TV y Google TV 11 o posterior**, con Views nativas en Kotlin. Sin root ni firmware modificado. El diseño y las funciones se mantienen al cambiar de idioma.

## Idioma
Abre el engranaje superior y **Idioma**. Automático utiliza el primer idioma compatible de la TV; si no hay coincidencia, usa inglés de Estados Unidos. La selección manual se conserva al reiniciar; vuelve a Automático para seguir el sistema. Solo cambian Lume y su salvapantallas: no se traducen los ajustes de Android, otras aplicaciones ni los títulos y descripciones de las plataformas.

Hay 21 locales: en-US, en-GB, en-CA, pt-BR, pt-PT, es-ES, fr-FR, zh-CN, hi-IN, ar, bn-BD, ru-RU, ur-PK, ja-JP, pnb-Arab-PK, mr-IN, de-DE, it-IT, ko-KR, id-ID y tr-TR. Chino simplificado y punyabí occidental en Shahmukhi. Árabe, urdu y punyabí occidental invierten la disposición y la navegación horizontal propia de la aplicación.

## Mando y uso diario
- Las flechas mueven el foco. OK abre un favorito; en la biblioteca, añade o elimina favoritos. Mantén OK pulsado para mover, ver información o abrir la confirmación oficial de desinstalación.
- Seguir viendo y Sugerencias cambian la vista previa al enfocar sus cabeceras. Izquierda/derecha recorren cabeceras y controles cercanos; abajo u OK entra en la tarjeta recordada. Los contenidos y las imágenes dependen de lo que compartan las aplicaciones instaladas y de tu autorización.
- Se conserva el orden de los favoritos. Nombres e iconos proceden de las aplicaciones instaladas. Instalar aplicaciones abre Google Play: no hay instalación silenciosa de APK.
- Red abre los ajustes de Android. HDMI depende del fabricante; Input en el mando sigue siendo una alternativa.
- Cine oscurece y da calidez al launcher y pausa los efectos. No modifica la imagen de la TV. El horario opcional de 20:00 a 06:00 usa la hora local de la TV.
- Atmósferas permite rotación diaria y selección manual. El salvapantallas ajusta la inactividad, el intervalo, el oscurecimiento y el reloj. Las fotos online solo se decodifican en RAM, sin caché de fotos en disco de la aplicación. Sin conexión alternan paisajes procedurales. Flechas, OK o Volver cierran la vista. Android controla el salvapantallas fuera del launcher y la espera de la TV.

## Instalar, actualizar y recuperar
Activa la depuración autorizada desde los ajustes oficiales. Confirma el serial exacto antes de cada comando ADB; sustituye DEVICE_SERIAL por el dispositivo verificado:

Antes y después de `adb install -r`, anota y comprueba el launcher predeterminado y qué abre el botón Home. Una actualización puede cambiar esta selección en algunos dispositivos; no des por hecho que se conserva. Mantén otro launcher funcional disponible y restablece tu elección con el selector de Android si hace falta.

```sh
adb devices
adb -s DEVICE_SERIAL install -r app-release.apk
```

Para conservar las preferencias, las actualizaciones deben mantener el paquete y el certificado de firma. Guarda la clave en privado. Una firma distinta no puede actualizar la instalación existente. No borres los datos para cambiar de idioma.

Elige Lume como inicio mediante el selector oficial solo si el fabricante lo admite. Prueba Home desde otra aplicación y la salida del modo de espera por separado. Antes de desinstalar el launcher predeterminado, instala y selecciona otro compatible y comprueba Home. Después elimina Lume desde los ajustes de Android. No hace falta root, cambiar el firmware ni eliminar componentes del sistema. El comportamiento depende del fabricante.

## Compilación, pruebas y licencias
Necesitas JDK 17, Android SDK Platform 35 y Build Tools 36.0.0. Configura JAVA_HOME y ANDROID_HOME. Desde la raíz del proyecto, genera los recursos antes de verificar y compilar:

```sh
python tools/build_locale_resources.py
cd src/android
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

En Windows, usa gradlew.bat. APK en app/build/outputs/apk/release/app-release.apk. Desde la raíz del proyecto:

```sh
python tools/verify_locales.py
```

Las traducciones están en los catálogos common/activity y tools/translations. Regenera los recursos tras cada cambio, preservando IDs y marcadores de formato. LocaleSmokeInstrumentation es un ejecutor exclusivo de pruebas; no cambia el idioma global ni Home. Usa un dispositivo o emulador dedicado. Un AVD de teléfono no certifica integraciones de TV ni el mando físico.

Código original bajo MIT, copyright 2026 Joao Salazar. Consulta LICENSE y THIRD_PARTY_NOTICES.md. Fotos, marcas y contenido ajeno conservan sus derechos; los autores y enlaces de licencia no cambian. No se accede a cuentas privadas de streaming ni se traducen. Compatibilidad, tipografías y rendimiento requieren pruebas por dispositivo.

Quedan pendientes la revisión lingüística nativa y el QA por idioma. Resultados reales en docs/qa: esta guía no afirma que las pruebas estén terminadas. Instalación: docs/installation.md. Compilación y firma: docs/build.md.

## Actualizaciones desde GitHub (0.4.3+)

En **Acerca de**, elige **Buscar actualizaciones**: GitHub solo se consulta al pulsar, sin comprobaciones periódicas. Si hay una versión nueva compatible, elige **Descargar actualización**. Tras verificar la descarga, elige **Instalar actualización**; Android te pedirá que confirmes la instalación. Si Android exige autorización, abre **Permitir esta fuente** y permite personalmente que Lume instale desde los ajustes de Android; vuelve para confirmar. Lume no concede este acceso automáticamente. La actualización con el mismo paquete y certificado de firma conserva los ajustes; no desinstales Lume para actualizar. Comprueba Home después. Si la TV cambia la selección, **Revisar pantalla de inicio** abre los ajustes oficiales para que la elijas manualmente.
