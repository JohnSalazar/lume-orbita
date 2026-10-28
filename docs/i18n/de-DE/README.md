# lume órbita — Anleitung für Nutzung und Mitwirkung

QA: [0.4.0](../../qa/i18n-validation.md) · [0.4.1](../../qa/konka-041-validation.md) · [0.4.2](../../qa/clock-locale-042.md)

Eigenständiger Launcher für **Android TV und Google TV ab Version 11**, mit nativen Kotlin-Views. Ohne Root oder veränderte Firmware. Design und Funktionen bleiben beim Sprachwechsel erhalten.

## Sprache
Öffne das Zahnrad oben und **Sprache**. Automatisch nutzt die erste unterstützte TV-Sprache, andernfalls Englisch (USA). Die manuelle Auswahl bleibt nach einem Neustart erhalten. Mit Automatisch wechselst du zurück zur Systemsprache. Nur Lume und sein Bildschirmschoner ändern sich: Android-Einstellungen, andere Apps sowie Titel und Beschreibungen der Plattformen werden nicht übersetzt.

21 Sprachregionen: en-US, en-GB, en-CA, pt-BR, pt-PT, es-ES, fr-FR, zh-CN, hi-IN, ar, bn-BD, ru-RU, ur-PK, ja-JP, pnb-Arab-PK, mr-IN, de-DE, it-IT, ko-KR, id-ID und tr-TR. Vereinfachtes Chinesisch; Westpanjabi in Shahmukhi-Schrift. Arabisch, Urdu und Westpanjabi spiegeln das Layout und die vom Launcher gesteuerte horizontale Navigation.

## Fernbedienung und Alltag
- Pfeiltasten verschieben den Fokus. OK öffnet einen Favoriten; in der App-Bibliothek fügt OK Favoriten hinzu oder entfernt sie. Halte OK für Verschieben, App-Informationen oder die offizielle Android-Deinstallationsbestätigung gedrückt.
- Weiterschauen und Vorschläge wechseln die Vorschau beim Fokus auf ihre Überschrift. Mit Links/Rechts wechselst du zwischen Überschriften und benachbarten Bedienelementen; mit Unten oder OK gelangst du zur gemerkten Karte. Inhalte und Bilder hängen von den Freigaben installierter Apps und deiner Berechtigung ab.
- Die Reihenfolge der Favoriten bleibt erhalten. Namen und Symbole stammen aus den installierten Apps. Apps installieren öffnet Google Play; APKs werden nicht unbemerkt installiert.
- Netzwerk öffnet die Android-Einstellungen. HDMI hängt vom Hersteller ab; Input auf der Fernbedienung bleibt eine Alternative.
- Der Kinomodus dunkelt den Launcher ab, wärmt den Hintergrund und pausiert Effekte. Er verändert keine TV-Bildeinstellungen. Der optionale Zeitplan von 20 bis 6 Uhr verwendet die Ortszeit des TVs.
- Atmosphären bieten täglichen Wechsel oder manuelle Auswahl. Beim Bildschirmschoner lassen sich Inaktivität, Bildintervall, Abdunklung und Uhr einstellen. Online-Fotos werden nur im RAM dekodiert, ohne App-Fotocache auf dem Datenträger. Offline wechseln prozedurale Landschaften. Pfeile, OK oder Zurück beenden die Ansicht. Android steuert den Bildschirmschoner außerhalb des Launchers und den Standby.

## Installieren, aktualisieren und wiederherstellen
Aktiviere autorisiertes Debugging über die offiziellen Geräteeinstellungen. Prüfe vor jedem ADB-Befehl die genaue Seriennummer und ersetze DEVICE_SERIAL durch das bestätigte Gerät:

Notiere und prüfe vor und nach `adb install -r` den gewählten Standard-Launcher und das Ziel der Home-Taste. Ein Update kann diese Auswahl auf manchen Geräten ändern; gehe nicht davon aus, dass sie erhalten bleibt. Halte einen anderen funktionierenden Launcher bereit und stelle deine Auswahl bei Bedarf über die Android-Auswahl wieder her.

```sh
adb devices
adb -s DEVICE_SERIAL install -r app-release.apk
```

Updates müssen Paketname und Signaturzertifikat beibehalten, um Einstellungen zu erhalten. Halte den Signaturschlüssel geheim. Ein anderes Zertifikat kann die bestehende Installation nicht aktualisieren. Lösche keine App-Daten für einen Sprachwechsel.

Wähle Lume nur über die offizielle Android-Startbildschirmauswahl und nur, wenn der Hersteller dies unterstützt. Teste Home aus einer anderen App und den Standby getrennt. Installiere und wähle vor dem Entfernen des Standard-Launchers einen anderen kompatiblen Launcher und prüfe Home. Entferne Lume danach über die Android-App-Einstellungen. Root, Firmwareänderungen und das Entfernen von Systemkomponenten sind nicht nötig. Das Verhalten ist herstellerabhängig.

## Build, Tests und Lizenzen
Erforderlich: JDK 17, Android SDK Platform 35 und Build Tools 36.0.0. Setze JAVA_HOME und ANDROID_HOME. Erzeuge im Projektstamm die Ressourcen vor der Prüfung und dem Build neu:

```sh
python tools/build_locale_resources.py
cd src/android
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

Unter Windows gradlew.bat verwenden. APK: app/build/outputs/apk/release/app-release.apk. Im Projektstamm:

```sh
python tools/verify_locales.py
```

Übersetzungen liegen in den common/activity-Katalogen und tools/translations. Nach Änderungen Ressourcen neu erzeugen; IDs und Formatplatzhalter erhalten. LocaleSmokeInstrumentation ist ausschließlich ein Test-Runner und ändert weder globale Sprache noch Home. Ein separates Testgerät oder einen Emulator verwenden. Ein Telefon-AVD zertifiziert keine TV-Integration oder echte Fernbedienung.

Originalcode unter MIT, Copyright 2026 Joao Salazar. Siehe LICENSE und THIRD_PARTY_NOTICES.md. Fotos, Marken und Drittinhalte behalten ihre Rechte; Urheber und Lizenzlinks bleiben unverändert. Private Streamingkonten werden weder ausgelesen noch übersetzt. Kompatibilität, Schriften und Leistung müssen gerätespezifisch geprüft werden.

Muttersprachliche Prüfung und QA je Sprache stehen weiter aus. Tatsächliche Ergebnisse unter docs/qa; diese Anleitung behauptet keine abgeschlossenen Tests. Installation: docs/installation.md. Build und Signierung: docs/build.md.
