# Beacon OTC Auto Overlay

Project Android Studio native: overlay mengambang yang membaca layar (via
MediaProjection) sekali per detik, menganalisa candle secara visual, dan
menampilkan rekomendasi BUY/SELL/SKIP berdasarkan logika di `OtcIntentEngine`.

**Ini bukan auto-entry / auto-click / bot trading.** Tidak ada Accessibility
Service yang menekan tombol otomatis. Semua eksekusi order tetap manual oleh
kamu di app broker.

## Yang baru ditambahkan di paket ini

1. **`BitmapAnalyzer.java`** — sebelumnya file ini dipanggil oleh
   `CaptureService` dan `OtcIntentEngine` tapi tidak ada di upload kamu, jadi
   project tidak akan compile tanpanya. Saya tulis implementasi heuristik
   berbasis warna piksel:
   - Meng-crop area chart (persen dari layar: `X1,X2,Y1,Y2` — default kira-kira
     tengah layar, **wajib kamu kalibrasi** sesuai posisi chart & overlay di HP
     kamu, sama seperti catatan di checklist lama).
   - Menentukan warna dominan candle terakhir (hijau/merah), rasio body,
     doji, dan arah wick (upper/lower/two_way) dari kepadatan warna per baris.
   - Estimasi pola MHI dari 3 kolom candle sebelum candle terakhir.
   - **Ini heuristik, bukan computer vision presisi.** Kalau chart broker kamu
     pakai warna candle yang beda (bukan hijau/merah standar) atau
     tema gelap/terang berbeda, sesuaikan fungsi `classify()`.

2. **Struktur Gradle lengkap** (`build.gradle`, `settings.gradle`,
   `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`) supaya
   project ini bisa langsung dibuka di Android Studio tanpa perlu bikin
   project baru dan copy-paste file satu-satu.

3. **Launcher icon adaptif** sederhana (candle hijau/merah) di
   `res/drawable` + `res/mipmap-anydpi-v26`.

4. **Dukungan multi-window / split-screen** ditambahkan di
   `AndroidManifest.xml` (`resizeableActivity`, `<layout>` default size).
   Ini menjawab permintaan "auto screenshot ketika multi window":
   - `CaptureService` sudah pakai `MediaProjection` yang merekam **seluruh
     layar fisik**, bukan hanya window app ini — jadi kalau kamu split-screen
     Beacon di atas/bawah IQ Option, auto-scan tiap 1 detik tetap membaca
     seluruh layar (termasuk chart IQ Option) tanpa perubahan tambahan.
   - Yang saya tambahkan: MainActivity sekarang secara eksplisit
     `resizeableActivity="true"` dengan ukuran default overlay window supaya
     nyaman ditaruh di mode split-screen/freeform, dan overlay servicenya
     sendiri (`OverlayService`) tetap jalan sebagai floating window terpisah
     terlepas dari mode multi-window MainActivity.
   - Catatan: kalau area chart IQ Option bergeser posisi saat masuk
     split-screen (karena layar jadi lebih pendek), sesuaikan `X1,X2,Y1,Y2`
     di `BitmapAnalyzer` untuk mode split-screen tersebut secara terpisah
     kalau perlu.

## Cara build

1. Buka folder `BeaconOTCAutoOverlay` di Android Studio (File > Open).
2. Kalau diminta "Gradle wrapper not found / regenerate", klik **OK/Sync**
   — Android Studio akan generate `gradlew`/`gradlew.bat`/wrapper jar
   otomatis (project ini disiapkan tanpa akses internet jadi wrapper jar-nya
   belum ada).
3. Tunggu Gradle Sync selesai (Android Studio akan download AGP 8.5.2 +
   Gradle 8.7 + SDK 34 kalau belum ada).
4. Build > Build Bundle(s)/APK(s) > Build APK(s).
5. Install APK debug ke HP (Settings > izinkan install dari sumber tak
   dikenal kalau perlu).

## Kalibrasi setelah install

1. Buka app → **IZINKAN / START OVERLAY** → aktifkan "Tampil di atas
   aplikasi lain".
2. **START AUTO CAPTURE** → izinkan rekam layar.
3. Buka IQ Option (full screen atau split-screen dengan Beacon).
4. Kalau signal selalu "SKIP"/dominant "none": area crop di
   `BitmapAnalyzer` (X1,X2,Y1,Y2) belum pas dengan posisi chart kamu.
   Ambil screenshot manual, ukur persentase posisi chart, lalu update
   konstanta itu dan rebuild.
5. Isi harga (`P:-` lalu -10/-5/+5/+10) dan crowd (N70/N90/T70/T90) manual
   sesuai yang kamu lihat di app broker — ini memang didesain manual
   (bukan OCR) seperti dijelaskan di `LOGIKA_OTC_INTENT.md`.

## Risiko

Binary/OTC options berisiko tinggi dan diatur ketat atau dilarang di
beberapa yurisdiksi — cek regulasi lokal kamu. Aplikasi ini murni alat
bantu baca visual + heuristik, bukan jaminan profit, dan tidak melakukan
order otomatis.
