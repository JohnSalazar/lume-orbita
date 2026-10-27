# lume órbita — guida all’uso e alla collaborazione

QA: [0.4.0](../../qa/i18n-validation.md)

Launcher originale per **Android TV e Google TV 11 o successivi**, con Views native in Kotlin. Nessun root o firmware modificato. Design e funzioni restano invariati fra le lingue.

## Lingua
Apri l’ingranaggio in alto, poi **Lingua**. Automatico segue la prima lingua supportata della TV; se non trova una corrispondenza, usa l’inglese degli Stati Uniti. La scelta manuale resta salvata dopo il riavvio. Seleziona Automatico per tornare alla lingua di sistema. Cambiano soltanto Lume e il suo salvaschermo: impostazioni Android, altre app, titoli e descrizioni delle piattaforme non vengono tradotti.

21 impostazioni locali: en-US, en-GB, en-CA, pt-BR, pt-PT, es-ES, fr-FR, zh-CN, hi-IN, ar, bn-BD, ru-RU, ur-PK, ja-JP, pnb-Arab-PK, mr-IN, de-DE, it-IT, ko-KR, id-ID e tr-TR. Cinese semplificato; punjabi occidentale in Shahmukhi. Arabo, urdu e punjabi occidentale rispecchiano il layout e la navigazione orizzontale gestita dall’app.

## Telecomando e uso quotidiano
- Le frecce spostano il focus. OK apre un preferito; nella libreria aggiunge o rimuove un preferito. Tieni premuto OK per spostare, consultare le informazioni o aprire la conferma ufficiale di disinstallazione.
- Continua a guardare e Suggerimenti cambiano l’anteprima quando le intestazioni ricevono il focus. Sinistra/destra percorrono intestazioni e controlli vicini; giù o OK entra nella scheda memorizzata. Contenuti e immagini dipendono da ciò che condividono le app installate e dal consenso concesso.
- L’ordine dei preferiti viene mantenuto. Nomi e icone provengono dalle app installate. Installa app apre Google Play; non ci sono installazioni silenziose di APK.
- Rete apre le impostazioni Android. HDMI dipende dal produttore; Input sul telecomando resta un’alternativa.
- Cinema scurisce e riscalda il launcher e sospende gli effetti. Non modifica l’immagine della TV. La programmazione facoltativa 20:00–06:00 segue l’ora locale della TV.
- Atmosfere offre rotazione giornaliera e scelta manuale. Il salvaschermo imposta inattività, intervallo, oscuramento e orologio. Le foto online vengono decodificate solo nella RAM, senza cache fotografica dell’app su disco. Offline si alternano paesaggi procedurali. Frecce, OK o Indietro chiudono la vista. Android controlla il salvaschermo fuori dal launcher e lo standby della TV.

## Installare, aggiornare e ripristinare
Abilita il debug autorizzato nelle impostazioni ufficiali. Verifica il seriale esatto prima di ogni comando ADB; sostituisci DEVICE_SERIAL con il dispositivo confermato:

Prima e dopo `adb install -r`, annota e verifica il launcher predefinito e la destinazione del pulsante Home. Su alcuni dispositivi un aggiornamento può cambiare questa scelta: non presumere che venga mantenuta. Tieni disponibile un altro launcher funzionante e, se necessario, ripristina la scelta dal selettore di Android.

```sh
adb devices
adb -s DEVICE_SERIAL install -r app-release.apk
```

Gli aggiornamenti devono mantenere pacchetto e certificato di firma per conservare le preferenze. Mantieni privata la chiave. Una firma diversa non aggiorna l’installazione esistente. Non cancellare i dati per cambiare lingua.

Scegli Lume come Home solo dal selettore ufficiale, se il produttore lo permette. Prova Home da un’altra app e il ritorno dallo standby separatamente. Prima di disinstallare il launcher predefinito, installa e seleziona un altro launcher compatibile e verifica Home. Rimuovi poi Lume dalle impostazioni Android. Non servono root, modifiche al firmware o rimozione di componenti di sistema. Il comportamento dipende dal produttore.

## Compilazione, test e licenze
Servono JDK 17, Android SDK Platform 35 e Build Tools 36.0.0. Configura JAVA_HOME e ANDROID_HOME. Dalla radice del progetto, genera le risorse prima della verifica e della compilazione:

```sh
python tools/build_locale_resources.py
cd src/android
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

Su Windows usa gradlew.bat. APK: app/build/outputs/apk/release/app-release.apk. Dalla radice del progetto:

```sh
python tools/verify_locales.py
```

Le traduzioni si trovano nei cataloghi common/activity e in tools/translations. Rigenera le risorse dopo le modifiche, preservando ID e segnaposto. LocaleSmokeInstrumentation è un runner solo per i test: non cambia lingua globale o Home. Usa un dispositivo o emulatore dedicato. Un AVD per telefono non certifica integrazioni TV o telecomandi fisici.

Codice originale con licenza MIT, copyright 2026 Joao Salazar. Vedi LICENSE e THIRD_PARTY_NOTICES.md. Foto, marchi e contenuti di terzi mantengono i propri diritti; autori e URL delle licenze non cambiano. Gli account privati di streaming non vengono consultati né tradotti. Compatibilità, caratteri e prestazioni richiedono verifiche sul dispositivo.

Restano necessari la revisione da parte di madrelingua e il QA per ogni lingua. Risultati effettivi in docs/qa; questa guida non dichiara test completati. Installazione: docs/installation.md. Compilazione e firma: docs/build.md.
