# lume órbita — panduan bahasa Indonesia

QA: [0.4.0](../../qa/i18n-validation.md) · [0.4.1](../../qa/konka-041-validation.md) · [0.4.2](../../qa/clock-locale-042.md)

lume órbita adalah peluncur orisinal untuk Android TV dan Google TV 11 atau lebih baru. Dibuat dengan Kotlin dan Views native, tanpa root. Fiturnya meliputi daftar aplikasi, favorit yang dapat diurutkan, lanjut menonton dan rekomendasi, tampilan kaca, mode Sinema, 30 latar, serta pengaman layar. Ketersediaan fitur bergantung pada perangkat dan produsennya.

## Bahasa

Buka Bahasa di Setelan. Otomatis memilih bahasa pertama yang didukung dari daftar bahasa TV; jika tidak cocok, digunakan bahasa Inggris (Amerika Serikat). Pilihan manual disimpan dan hanya mengubah peluncur, bukan bahasa sistem. Tersedia 21 bahasa termasuk variasi wilayah. Nama aplikasi, judul, dan sinopsis dari platform dapat tetap memakai bahasa sumber.

## Remote dan konten

Kiri/kanan menavigasi menu, Lanjutkan menonton, Rekomendasi, dan Sinema. Saat judul mendapat fokus, kartu terakhir beserta keterangannya ditampilkan lebih besar, tetapi fokus tetap pada judul. Bawah atau OK masuk ke kartu; Atas kembali ke judul. OK pada favorit membuka aplikasi; tahan OK untuk tindakan. Pada daftar aplikasi, OK menambah atau menghapus favorit. Saat memindahkan, panah mengubah posisi, OK menyimpan, dan Kembali membatalkan.

Konten serta progres berasal dari data yang dibagikan platform kepada Android. Izin opsional READ_TV_LISTINGS harus disetujui di TV. Tanpa izin atau data, daftar dapat kosong. Peluncur tidak mengakses akun streaming. Hanya aplikasi yang dapat dijalankan yang ditampilkan, bukan layanan latar atau komponen yang dinonaktifkan.

## Instalasi dan layar utama

Periksa sistem TV, metode debugging resmi, dan komponen layar utama saat ini. Cocokkan SHA-256 APK dengan hash rilis. Ganti `SERIAL` dan `LUME.apk` pada contoh dengan pengenal perangkat serta nama file Anda:

Sebelum dan sesudah `adb install -r`, catat serta periksa peluncur default dan layar yang dibuka tombol Home. Pembaruan dapat mengubah pilihan ini pada perangkat tertentu; jangan menganggapnya pasti tetap sama. Siapkan peluncur lain yang berfungsi dan pulihkan pilihan melalui pemilih Android bila diperlukan.

```sh
adb devices
adb -s SERIAL install -r LUME.apk
adb -s SERIAL shell am start -n br.com.lume.launcher/.LauncherActivity
```

Uji sebagai aplikasi biasa dahulu. Setelah itu, pilih Lume sebagai layar utama melalui Setelan peluncur atau pengaturan aplikasi default Android jika tersedia. Menu bergantung pada firmware. Biarkan peluncur kompatibel lain terinstal selama evaluasi; uji Home, Kembali, dan pemulihan dari siaga secara terpisah. Layar utama default dan perilaku setelah siaga adalah hal berbeda.

Untuk memulihkan, pilih layar utama lain yang berfungsi sebelum menghapus Lume. Jangan hapus satu-satunya layar utama atau komponen sistem. Pembaruan memerlukan paket dan tanda tangan yang sama. Build lokal dapat memakai kunci berbeda; jangan menghapus data hanya untuk mengatasi kesalahan tanda tangan.

## Tampilan, Sinema, dan pengaman layar

Atur transparansi, pantulan, animasi, mode ringan, dan latar. Sinema hanya mengubah kecerahan, nuansa warna, dan efek peluncur; setelan gambar TV tidak berubah. Jadwal opsional berjalan 20.00–06.00 waktu setempat. Peralihan manual menonaktifkan jadwal.

Pengaman layar menampilkan pemandangan daring gratis atau seni prosedural saat luring. Foto hanya disimpan di RAM, tidak ditulis ke penyimpanan TV. Setelah proses berakhir, foto perlu dimuat lagi. Atur jeda mulai, interval gambar, peredupan, dan jam. Fitur di dalam peluncur berbeda dari pengaman layar yang dikelola Android pada aplikasi lain dan saat TV tidur. Panah, OK, atau Kembali mengakhiri tampilan. Kredit gambar mencantumkan pembuat, sumber, lisensi, dan perubahan tampilan.

Google Play, setelan jaringan, dan HDMI bergantung pada layanan sistem perangkat. Toko memeriksa kompatibilitas dan meminta persetujuan instalasi; peluncur tidak mengunduh atau menginstal APK sendiri.

## Build dan verifikasi

Diperlukan JDK 17, Android SDK Platform 35, dan Build Tools 36.0.0. Atur `JAVA_HOME`, `ANDROID_HOME`, atau `local.properties` pribadi yang tidak dilacak, lalu terima lisensi SDK. Gradle tersedia melalui wrapper:

Setelah mengubah katalog JSON, buat ulang sumber daya XML dari akar repositori sebelum menjalankan verifikasi dan build Gradle:

```sh
python tools/build_locale_resources.py
cd src/android
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

Di Windows PowerShell gunakan `.\gradlew.bat` sebagai pengganti `./gradlew`. APK tersedia di `src/android/app/build/outputs/apk/release/app-release.apk`. Dari akar repositori:

```sh
python tools/verify_locales.py
python tools/verify_locales.py --self-check
```

Pemeriksaan statis memvalidasi sumber daya dan format; self-check menguji alatnya sendiri. Ini bukan pengganti pengujian UI nyata 21 bahasa atau peninjauan penutur asli. Cakupan logika tidak menjamin UI bebas kesalahan di semua TV. Lihat [rencana uji i18n](../../qa/i18n-test-plan.md).

## Privasi dan lisensi

Tidak ada backend sendiri atau telemetri. Favorit, tampilan, dan bahasa disimpan sebagai preferensi privat aplikasi. Gambar daring menggunakan HTTPS. Batas cache RAM bukan total memori aplikasi. Jangan memublikasikan riwayat tontonan, rekaman debugging, atau kunci penandatanganan.

Kode orisinal berlisensi [MIT](../../../LICENSE). Foto, dependensi, alat, dan merek tetap memiliki hak masing-masing: [pemberitahuan pihak ketiga](../../../THIRD_PARTY_NOTICES.md). Terjemahan tidak menggantikan teks hukum asli.
