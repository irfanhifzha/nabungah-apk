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
- [x] Actions dimodernkan: `checkout@v7`, `setup-java@v6`, `setup-gradle@v6`,
  `upload-artifact@v7`, `action-gh-release@v3` (semua runtime Node 24). Diuji lewat
  `workflow_dispatch` (run 44884200897, hijau 2m39s) tanpa membuat tag atau release baru;
  langkah Publish dilewati sesuai desain.
- [x] Tag `v1.3` sudah dipush. Workflow rilis hijau (3m12s): unit test, build, upload
  artifact, dan GitHub Release terbit di https://github.com/irfanhifzha/nabungah-apk/releases/tag/v1.3
  berisi `nabungah-release.apk` (1,3 MB) yang sudah diverifikasi bertanda tangan
  `CN=Nabungah` (bukan debug).
- [x] `keystore.properties` dipakai sebagai fallback signing lokal, jadi `assembleRelease` di
  mesin ini otomatis memakai keystore rilis tanpa env var. Gradle daemon tidak lagi jadi
  jebakan; build CI tetap memakai env dari secrets.
- [x] Semua tombol sekunder/hapus memakai outline lewat `OutlinedActionButton`; aksi hapus
  memakai border merah dan selebar kartu. Tombol isi hanya untuk aksi utama (simpan/ekspor).
- [x] "Hapus semua data" ditambahkan di Data & reset: menghapus enam tabel dalam satu transaksi,
  dengan ringkasan jumlah data dan konfirmasi wajib mengetik `HAPUS`.
- [x] Copy diperbarui jadi blok polos tanpa emoji (judul `*Nabungah — …*`). Laporan punya tombol
  Salin yang menyalin ringkasan bulan + satu baris ringkas per transaksi (`dd/MM judul — nominal · dompet`).
- [x] Halaman Pengaturan dirapikan: kartu ringkasan saldo/jumlah data, pemisah baris, tombol
  ekspor/impor yang jelas, kartu biaya, target dengan chevron, dan zona berbahaya terpisah.
- [x] Biaya menjadi setelan global tersimpan (SharedPreferences `fee_percent`, default 2,5%), bisa
  diubah dari Pengaturan atau kartu Biaya di Laporan. Tidak ada lagi input live di Laporan; snapshot
  lama tetap menyimpan persentase historisnya.
- [x] Transfer kini dicatat di tabel `transfer_log` (skema v3, backup v3) dan tampil di Aktivitas
  dengan filter Tipe → Transfer. Log ini tidak pernah masuk pemasukan/pengeluaran, Laporan, Home,
  atau snapshot; transaksi tetap satu-satunya sumber angka.
- [x] Markdown terkait (README, GUIDE, PLAN) diselaraskan dengan biaya tersimpan, transfer
  log, saldo, copy baru, dan petunjuk build Windows `cmd`.
- [x] Build lokal pasca-perubahan: 14 unit test lulus, `assembleDebug` dan `assembleRelease`
  sukses. APK rilis 1.308.162 byte, bertanda tangan `CN=Nabungah` (keystore rilis lokal).
  Belum ada commit, push, atau tag.
- [x] Kartu Dompet dipindah ke Home tepat di bawah Total Saldo; bentuknya kini kartu lebar
  (bukan persegi) dengan avatar, nama, persen porsi, nominal, dan progress bar.
- [x] Teks penjelasan ditambahkan di bagian Aksi cepat dan Target tabungan (Home dan
  Pengaturan): template transaksi, progres manual, dan target tidak memotong saldo dompet.
- [x] Aktivitas punya tampilan Kalender: grid Senin–Minggu, navigasi bulan, titik hijau/merah/teal
  untuk pemasukan/pengeluaran/transfer, tap tanggal untuk daftar aktivitas hari itu, plus filter tipe.
- [x] Laporan mendapat dashboard statistik: 6 tile (pemasukan, pengeluaran, selisih, rata-rata,
  jumlah transaksi, biaya), tren persen vs bulan sebelumnya, saldo akhir bulan, dan pengeluaran terbesar.
- [x] Batch 1.4: skema v4 (`starting_balance`, CHECK, migrasi v1→v4 + test), snackbar + Undo,
  form `rememberSaveable`, backdate + date picker, quick action fokus nominal, rollover bulan,
  detail/edit/hapus massal transaksi, goal selesai otomatis, audit konsistensi + perbaikan,
  backup preview + info ekspor, filter dompet + "Bulan ini", onboarding, haptic, dark theme,
  adaptive/themed icon, navigation rail ≥600dp, `androidComponents`, Compose UI test.
- [x] Build 1.4: 14 unit test lulus, compile androidTest sukses, `assembleDebug` dan
  `assembleRelease` sukses. APK rilis 1.533.314 byte, bertanda tangan `CN=Nabungah`.
  Belum ada commit/push/tag untuk batch ini.
- [x] Batch 1.4.1: tombol Salin di baris Aktivitas dipindah ke sisi nominal, ikon "+" di kartu
  Aksi Cepat dihapus, biaya global ikut diekspor/diimpor (backup v5), dan versi naik ke
  `versionCode` 5 / `1.4.1`; lanjutan batch menambah tema dan deskripsi target (schema v5,
  backup v6).
- [x] Pengaturan tema **Sistem / Terang / Gelap** tersimpan lokal; status bar dan warna
  semantik mengikuti pilihan.
- [x] **Deskripsi target** opsional di skema v5 (migrasi v1→v5), form tambah/edit, kartu Home dan
  Pengaturan, serta backup v6 (v1–v5 tetap dapat diimpor).
- [x] Perbaikan impor: `ConfirmDialog` memanggil `onDismiss` sebelum `onConfirm`; URI file kini
  diambil sebelum state dialog dibersihkan.
- [x] Build 1.4.1 final: 14 unit test lulus, compile androidTest sukses, `assembleDebug` dan
  `assembleRelease` sukses. APK rilis 1.549.702 byte, `versionCode` 5 / `1.4.1`, bertanda tangan
  `CN=Nabungah`.
- [x] Tag `v1.4.1` dihapus lalu dibuat ulang di commit final.