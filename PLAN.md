INI RENCANA UNTUK VERSI `1.3`.

## Selesai di 1.3

- [x] Quick action di Home dibuat jauh lebih menonjol: kartu besar 178×116, latar tint sesuai
  tipe (hijau/merah), ikon panah, nominal besar, dan ikon tambah.
- [x] Margin/tata letak dirapikan: gutter 16dp konsisten, jarak antar kartu 8–16dp, dan
  `SectionHeader` baru supaya judul bagian sejajar.
- [x] Badge "Offline" di Home dihapus — memang selalu offline, tidak perlu diumumkan.
- [x] Wallet, goal, transaksi, snapshot, dan tile Review memakai satu gaya kartu
  (`Modifier.appCard()`): putih, border tipis, sudut 16dp. Wallet kini kartu sendiri lengkap
  dengan persentase dari total saldo; transaksi punya ikon tipe di kiri.
- [x] **Seluruh teks aplikasi diterjemahkan ke bahasa Indonesia**: tab, Home, Aktivitas, Laporan,
  Pengaturan, semua sheet, pesan validasi, dan pesan error. Format uang/tanggal tetap `id-ID`.
  Teks ditaruh langsung di Compose (bukan `strings.xml`) karena aplikasi satu bahasa; ekstraksi
  ke resource dilakukan hanya jika nanti butuh bahasa kedua.
- [x] **Copy blok WhatsApp ikut diterjemahkan** ("Transaksi", "Nominal", "Tipe", "Dompet",
  "Tanggal", "Catatan"; snapshot memakai "Statistik Bulan", "Sumber", "Total Pemasukan",
  "Total Pengeluaran", "Total Saldo", "Biaya", "Diperbarui"). `CopyTextTest` dan `GUIDE.md`
  §4/§9 sudah disesuaikan; urutan field, emoji, dan `Rp` tetap sama.
- [x] Aktivitas & Pengaturan ditinjau untuk layar kecil: filter Bulan/Tipe kini satu baris
  (`singleLine`), label panjang tidak lagi membungkus dan meninggikan field. Padding daftar
  sudah seragam 16dp.
- [x] Animasi tekan halus lewat `Modifier.pressableCard()` (skala 0,97, 80 ms saat ditekan):
  dipakai di kartu quick action dan kartu target. Kartu dompet sengaja tidak diberi animasi
  tekan karena memang tidak punya aksi ketuk — animasi tanpa hasil akan menyesatkan.
- [x] Wallet card mendapat progress bar porsi saldo (dianimasikan) di samping teks persentase.
- [x] **Live preview fee**: Home menampilkan "Jika fee disisihkan" = total saldo − fee bulan
  berjalan, plus besar fee dan persennya. Laporan menampilkan baris "Saldo jika fee disisihkan"
  di kartu Biaya. Fee tetap informasi saja — dompet tidak pernah dikurangi, dan ini ditulis
  eksplisit di kedua tempat. Fee % memakai snapshot bulan ini bila ada, kalau tidak 2,5%.
- [x] **Workflow GitHub release** (`.github/workflows/release.yml`): trigger `push` tag `v*`
  (atau manual), jalankan unit test, build `assembleRelease`, upload artifact, lalu buat GitHub
  Release berisi `nabungah-release.apk` + release notes otomatis. Signing memakai secrets
  `NABUNGAH_KEYSTORE_BASE64`, `NABUNGAH_KEYSTORE_PASSWORD`, `NABUNGAH_KEY_ALIAS`,
  `NABUNGAH_KEY_PASSWORD`; tanpa secret, APK jatuh ke debug-sign.
- [x] Versi **1.3** (`versionCode` 3), unit test lulus, APK release dibangun.

## Berikutnya

- [x] Secrets keystore sudah diisi di repo (`NABUNGAH_KEYSTORE_BASE64`, `..._PASSWORD`,
  `..._ALIAS`, `..._KEY_PASSWORD`). Keystore asli ada di `nabungah-release.jks` +
  `keystore.properties` (keduanya di-gitignore) — simpan cadangannya, tanpa itu update APK
  tidak bisa ditandatangani dengan identitas yang sama.
- [x] Laporan kini punya kartu "Saldo saat ini": total saldo semua dompet plus rincian saldo
  per dompet, terpisah dari angka akhir bulan.
- [x] Kartu transaksi diberi padding kanan 14dp dan jarak 10dp sebelum nominal, plus
  `maxLines = 1`, jadi teks Rp tidak lagi menempel di tepi kartu.
- [x] Kartu dompet di Home diubah menjadi grid dua kolom berukuran persegi (`aspectRatio(1f)`):
  avatar + persen di atas, nama/nominal/progress bar di bawah.
- [ ] Putuskan apakah tiga perubahan pasca-tag di atas dirilis sebagai 1.3.1 (tag `v1.3` sudah
  terbit dan tidak menyertakan perubahan ini).
- [ ] ...
- [x] Tag `v1.3` sudah dipush. Workflow rilis hijau (3m12s): unit test, build, upload
  artifact, dan GitHub Release terbit di https://github.com/irfanhifzha/nabungah-apk/releases/tag/v1.3
  berisi `nabungah-release.apk` (1,3 MB) yang sudah diverifikasi bertanda tangan
  `CN=Nabungah` (bukan debug).
- [x] `keystore.properties` dipakai sebagai fallback signing lokal, jadi `assembleRelease` di
  mesin ini otomatis memakai keystore rilis tanpa env var. Gradle daemon tidak lagi jadi
  jebakan; build CI tetap memakai env dari secrets.
- [ ] ..
