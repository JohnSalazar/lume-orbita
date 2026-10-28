# lume órbita — Türkçe kılavuz

QA: [0.4.0](../../qa/i18n-validation.md) · [0.4.1](../../qa/konka-041-validation.md) · [0.4.2](../../qa/clock-locale-042.md)

lume órbita, Android TV ve Google TV 11 veya üzeri için geliştirilmiş özgün bir başlatıcıdır. Kotlin ve yerel Views kullanır; root gerektirmez. Uygulama listesi, sıralanabilir favoriler, izlemeye devam etme ve öneriler, cam görünümü, Sinema modu, 30 arka plan ve ekran koruyucu sunar. Özelliklerin kullanılabilirliği üreticiye ve cihaza bağlıdır.

## Dil

Ayarlar içindeki Dil bölümünü açın. Otomatik, TV’nin dil listesindeki ilk desteklenen dili seçer; eşleşme yoksa İngilizce (ABD) kullanılır. Elle seçim saklanır ve yalnızca başlatıcıyı değiştirir; sistem dilini değiştirmez. Bölgesel seçeneklerle birlikte 21 dil desteklenir. Platformların sağladığı uygulama adları, yapım adları ve açıklamalar kaynak dilde kalabilir.

## Kumanda ve içerik

Sol/sağ tuşları menü, İzlemeye devam et, Öneriler ve Sinema arasında gezinir. Başlık odaktayken son kart ve ayrıntıları büyük önizleme olarak gösterilir; gerçek odak başlıkta kalır. Aşağı veya OK karta girer, Yukarı başlığa döner. Favorilerde OK uygulamayı açar, OK düğmesini basılı tutmak işlemleri gösterir. Uygulama listesinde OK favori ekler veya kaldırır. Taşıma sırasında oklarla konumu değiştirin, OK ile kaydedin veya Geri ile iptal edin.

İçerik ve ilerleme, platformların Android ile paylaştığı verilere bağlıdır. İsteğe bağlı READ_TV_LISTINGS izni TV üzerinden verilmelidir. İzin veya veri yoksa liste boş olabilir. Başlatıcı yayın hesaplarına erişmez. Katalogda yalnızca başlatılabilir uygulamalar yer alır; arka plan hizmetleri ve devre dışı bileşenler uygulama olarak aktarılmaz.

## Yükleme ve ana ekran

Önce TV sistemini, resmî hata ayıklama yöntemini ve mevcut ana ekran bileşenini kontrol edin. APK’nin SHA-256 değerini sürümde verilen değerle karşılaştırın. Örnekteki `SERIAL` ve `LUME.apk` ifadelerini kendi cihaz kimliğiniz ve dosya adınızla değiştirin:

`adb install -r` öncesinde ve sonrasında seçili varsayılan ana ekran uygulamasını ve Home düğmesinin açtığı ekranı kaydedip kontrol edin. Bazı cihazlarda güncelleme bu seçimi değiştirebilir; korunacağını varsaymayın. Çalışan başka bir başlatıcıyı hazır tutun ve gerekirse Android seçim ekranından eski seçiminizi geri yükleyin.

```sh
adb devices
adb -s SERIAL install -r LUME.apk
adb -s SERIAL shell am start -n br.com.lume.launcher/.LauncherActivity
```

Önce normal uygulama olarak deneyin. Ardından, mevcutsa başlatıcı Ayarları veya Android’in varsayılan uygulama ayarlarından Lume’u ana ekran seçin. Seçim arayüzü aygıt yazılımına bağlıdır. Değerlendirme sırasında başka bir uyumlu başlatıcıyı yüklü tutun; Home, Geri ve beklemeden dönüşü ayrı ayrı sınayın. Varsayılan ana ekran ile beklemeden dönüş davranışı aynı şey değildir.

Geri dönmek için Lume’u kaldırmadan önce çalışan başka bir ana ekran seçin. Tek ana ekranı veya sistem bileşenlerini kaldırmayın. Güncellemeler aynı paket ve imzayı gerektirir. Yerel derlemenin anahtarı farklı olabilir; imza hatasını aşmak için verileri silmeyin.

## Görünüm, Sinema ve ekran koruyucu

Saydamlığı, yansımaları, animasyonları, hafif modu ve arka planı ayarlayabilirsiniz. Sinema yalnızca başlatıcının parlaklığını, renk tonunu ve efektlerini değiştirir; TV görüntü ayarlarına dokunmaz. İsteğe bağlı zamanlama yerel saatle 20:00–06:00 arasıdır. Elle açıp kapatmak zamanlamayı kapatır.

Ekran koruyucu ücretsiz çevrimiçi manzaralar veya çevrimdışı yordamsal sanat gösterir. Fotoğraflar yalnızca RAM’de tutulur, TV depolamasına yazılmaz. İşlem sona erdiğinde yeniden yüklenmeleri gerekir. Başlama süresi, görsel aralığı, karartma ve saat ayarlanabilir. Başlatıcı içindeki özellik, diğer uygulamalar ve TV uyku durumunda Android’in yönettiği ekran koruyucudan ayrıdır. Oklar, OK veya Geri gösterimi bitirir. Görsel kaynakları, eser sahiplerini, orijinalleri, lisansları ve gösterim değişikliklerini içerir.

Google Play, ağ ayarları ve HDMI cihazın sistem özelliklerine bağlıdır. Uyumluluğu ve yükleme onayını mağaza yönetir; başlatıcı kendi başına APK indirmez veya yüklemez.

## Derleme ve doğrulama

JDK 17, Android SDK Platform 35 ve Build Tools 36.0.0 gereklidir. Kendi `JAVA_HOME`, `ANDROID_HOME` değerlerinizi veya izlenmeyen `local.properties` dosyanızı ayarlayın; SDK lisanslarını kabul edin. Gradle, wrapper ile sağlanır:

JSON çeviri kataloglarını değiştirdikten sonra depo kökünden XML kaynaklarını yeniden oluşturun; ardından doğrulama ve Gradle derlemesini çalıştırın:

```sh
python tools/build_locale_resources.py
cd src/android
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

Windows PowerShell’de `./gradlew` yerine `.\gradlew.bat` kullanın. APK yolu `src/android/app/build/outputs/apk/release/app-release.apk` olur. Depo kökünde şu kontrolleri çalıştırabilirsiniz:

```sh
python tools/verify_locales.py
python tools/verify_locales.py --self-check
```

Statik kontrol kaynakları ve biçimleri doğrular; self-check aracın kendisini sınar. Bunlar 21 dilde gerçek arayüz testi veya ana dili konuşanların incelemesi yerine geçmez. Mantık kapsamı, her TV arayüzünde sıfır hata garantisi değildir. [i18n test planına](../../qa/i18n-test-plan.md) bakın.

## Gizlilik ve lisans

Özel bir sunucu veya telemetri yoktur. Favoriler, görünüm ve dil uygulamanın özel tercihlerinde tutulur. Çevrimiçi görseller HTTPS kullanır. RAM önbellek sınırı toplam uygulama belleği değildir. İzleme geçmişini, hata ayıklama kayıtlarını veya imzalama anahtarlarını yayımlamayın.

Özgün kod [MIT](../../../LICENSE) lisanslıdır. Fotoğraflar, bağımlılıklar, araçlar ve markalar kendi haklarını korur: [üçüncü taraf bildirimleri](../../../THIRD_PARTY_NOTICES.md). Çeviri, özgün hukuki metnin yerini almaz.

## GitHub üzerinden güncellemeler (0.4.3+)

**Hakkında** bölümünde **Güncellemeleri denetle** seçeneğini seçin: GitHub yalnızca tıkladığınızda denetlenir, düzenli otomatik denetim yapılmaz. Daha yeni ve uyumlu bir sürüm varsa **Güncellemeyi indir** seçeneğini seçin. İndirme doğrulandıktan sonra **Güncellemeyi yükle** seçeneğini seçin; ardından Android yükleme onayı ister. Android izin isterse **Bu kaynağa izin ver** seçeneğini açıp Android ayarlarında Lume uygulamasına kendiniz izin verin ve onaylamak için geri dönün. Lume bu izni otomatik olarak vermez. Aynı paket ve imzalama sertifikasıyla güncelleme yapmak ayarları korur; güncellemek için Lume uygulamasını kaldırmayın. Ardından Home seçimini kontrol edin. TV bu seçimi değiştirirse **Ana ekran seçimini gözden geçir** üzerinden resmi ayarları açıp elle seçim yapabilirsiniz.
