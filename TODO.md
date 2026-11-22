# TODO — Nabungah

Ide perbaikan, diurutkan dari yang paling dekat sampai jangka panjang.
Terakhir diperbarui: 23 November 2026 · versi 1.5.0 (`versionCode` 6).

**Cara pakai**

- `P0` = kerjakan berikutnya, `P1` = bulan ini, `P2` = nanti.
- `S` / `M` / `L` = perkiraan usaha (jam / hari / minggu).
- Centang item setelah selesai, jangan hapus barisnya.
- Baca dulu **§8**: beberapa ide populer bertabrakan dengan aturan produk di
  `GUIDE.md` §13 dan `README.md` §4, jadi butuh keputusan produk sebelum dikoding.

## Ringkasan prioritas

| Prioritas | Fokus | Item |
|---|---|---|
| P0 | Keandalan data & rasa aman | feedback aksi, rotasi form, error load, audit saldo |
| P1 | Efisiensi input harian | edit/hapus transaksi, backdate, reminder backup, i18n |
| P2 | Skala & distribusi | migrasi DB, UI test, keystore rilis, tablet |

---

## Selesai di 1.2

- [x] Review jadi **laporan bulanan live** (income, expense, net, saldo akhir bulan, jumlah
  transaksi, rata-rata, 3 pengeluaran terbesar) dari `buildMonthReport()`.
- [x] **Snapshot otomatis** saat bulan berganti; `manual` tidak pernah ditimpa, bisa dikembalikan
  ke otomatis. Database naik ke skema v2 (`source`), backup v2 (v1 masih bisa diimpor).
- [x] Error state + "Try again" saat database gagal dibuka.

## Selesai di 1.5.0

- [x] Tombol **"+ Tambah"** dan **"Transfer"** di header Dompet Home kini punya jarak 8dp.
- [x] **Skalabilitas**: `AppData` hanya menyimpan 200 transaksi terakhir; Aktivitas memakai
  paging + pencarian/filter SQL, Laporan query per bulan, dan total bulanan dihitung SQL.
- [x] Export backup memakai `JsonWriter` streaming; salinan JSON penuh tidak lagi dibangun di
  memori.
- [x] Versi naik ke **1.5.0** (`versionCode` 6).

## Antrean 1.6.0

- [ ] Jarak 8dp antara tombol **"Edit laporan manual"** dan **"Kembalikan ke otomatis"** di
  kartu "Simpan atau edit" halaman Laporan.
- [ ] Tombol **"Salin aktivitas"** di Laporan menyalin seluruh transaksi bulan terpilih
  (tanggal, tipe, nominal, dompet, catatan), bukan hanya per item.
- [ ] Tombol **"Salin"** dan **"Edit"** di sheet Detail Transaksi memakai tinggi yang sama.

## Selesai di 1.4.1

- [x] Pengaturan tampilan **Sistem / Terang / Gelap**, tersimpan lokal; warna semantik
  mengikuti pilihan aplikasi, bukan hanya tema sistem.
- [x] **Deskripsi target** opsional: skema v5, form tambah/edit, tampil di kartu Home dan
  Pengaturan, ikut backup v6 (backup lama v1–v5 tetap diterima).
- [x] Tombol Salin di Aktivitas dipindah ke sisi nominal, ikon "+" kartu Aksi Cepat dihapus,
  biaya global ikut backup.
- [x] Perbaikan konfirmasi impor: URI file tidak lagi terhapus sebelum `importBackup` dijalankan.

## Selesai di 1.4

- [x] Skema v4: `starting_balance`, CHECK constraint snapshot, migrasi v1→v4 + test.
- [x] Snackbar + Undo, rollover tengah malam/ON_RESUME, edit/backdate/detail/hapus massal,
  filter wallet, onboarding, backup preview + info, alat audit saldo, goal selesai otomatis.
- [x] Dark theme, adaptive/themed icon, navigation rail ≥ 600dp, Gradle `androidComponents`,
  Compose UI test + test impor negatif.

## 1. Alur kerja sistem

- *[x] **P0 · S — Feedback setelah aksi.** Saat ini sheet langsung tertutup tanpa konfirmasi; hanya
  error yang tampil inline. Tambahkan satu `SnackbarHost` di `Dashboard` dengan pesan sukses
  ("Transaksi ditambahkan", "Backup tersimpan") dan aksi **Undo** untuk hapus transaksi tunggal.*
- [x] ***P0 · S — Draft form tahan rotasi.** Semua field di `Sheets.kt` memakai `remember`, jadi
  memutar HP menghapus isian. Ganti ke `rememberSaveable` (termasuk pilihan wallet/tipe).*
- [x] ***P1 · M — Edit transaksi.** Sekarang salah ketik = hapus + input ulang (dua mutasi saldo).
  Ini melanggar invariant #6 ("no editing") — butuh keputusan produk dulu, lalu update
  `GUIDE.md` §13, `README.md` §4, dan `RepoTest`.*
- [x] ***P1 · M — Tanggal transaksi (backdate).** `addTransaction` selalu memakai `now`, jadi
  transaksi yang lupa dicatat kemarin masuk ke bulan berjalan. Tambah date picker; simpan
  `occurred_at` pilihan dan turunkan `month` dari tanggal itu.*
- [x] ***P1 · S — Quick action → form terisi.** Tile sudah pre-fill judul/nominal/tipe; fokuskan
  field nominal dan tampilkan tombol simpan langsung supaya benar-benar satu ketukan.*
- [x] ***P1 · S — Rollover bulan.** `AppData.currentMonth` dibaca sekali saat load; aplikasi yang
  dibiarkan terbuka melewati tanggal 1 masih menampilkan bulan lama. Refresh saat `ON_RESUME`
  atau saat jam melewati tengah malam.*
- [x] **P1 · S — Snapshot kedaluwarsa.** Di `ReviewTab`, bandingkan angka tersimpan vs angka live
  dan tampilkan badge "perlu diperbarui" plus tombol update. *(1.2)*
- [ ] **P1 · M — Reminder backup.** Simpan waktu export terakhir, tampilkan banner di Home/Settings
  bila > 30 hari atau bila ada > 50 transaksi baru sejak backup terakhir.
- [x] ***P2 · S — Detail transaksi.** Row belum punya layar detail; tambahkan sheet read-only
  (judul, nominal, wallet, catatan, tanggal) dengan Copy dan Delete.*
- [x] ***P2 · M — Hapus massal di Activity.** Mode seleksi + hapus beberapa transaksi dalam satu
  DB transaction (balikkan efek tiap baris sekali jalan).*
- [x] ***P2 · S — Goal selesai otomatis.** Set `status = completed` saat `saved_amount >=
  target_amount`, dan tampilkan progress yang di-clamp 100%.*

## 2. Database & integritas data

- [x] **P0 · M — Kolom `starting_balance`.** Saldo wallet hanya menyimpan hasil akhir, jadi saldo
  awal tidak bisa direkonstruksi. Tambah kolom (schema v2) supaya ada alat audit:
  `starting_balance + Σ income − Σ expense = balance`.
- [x] **P0 · M — Kebijakan migrasi.** `NabungDb.onUpgrade` masih kosong. Tulis pola migrasi per
  versi (`if (oldVersion < 2) …`), naikkan `version`, dan tambah test upgrade dari fixture v1.
- [x] **P1 · S — Alat "Cek konsistensi data".** Setelah `starting_balance` ada, tombol di Settings
  untuk menghitung ulang semua saldo dan menampilkan selisih sebelum memperbaiki.
- [x] **P1 · M — `month` duplikat.** `trx.month` disalin dari `occurred_at`; kalau aturan tanggal
  berubah (backdate/timezone) keduanya bisa beda. Pilih satu sumber kebenaran atau tambahkan
  pengecekan konsistensi saat load.
- [x] **P1 · M — Reload selektif.** `MainViewModel.write` memanggil `repo.load()` penuh setelah
  setiap mutasi. Kini dua query agregat bulanan dihapus (dihitung dari baris yang sudah dimuat);
  reload penuh tetap dipertahankan agar saldo, bulan, dan snapshot selalu konsisten.
- [x] **P2 · S — Backup: preview sebelum replace.** Tampilkan jumlah wallet/transaksi/snapshot dari
  file sebelum dialog konfirmasi, bukan hanya peringatan "semua data diganti".
- [ ] **P2 · M — Mode merge import.** Saat ini restore selalu replace. Mode gabung (id baru,
  deteksi duplikat) berguna saat pindah HP tanpa kehilangan data baru.
- [x] **P2 · S — CHECK constraint snapshot.** `month_snapshot` menerima angka negatif; tambahkan
  `CHECK` yang sesuai atau validasi di `Repo.upsertSnapshot`.
- [x] **P2 · S — Ambang transfer.** `amount <= 1000` ditolak tanpa penjelasan produk; pastikan
  aturan ini memang disengaja dan pesannya jelas ("minimal Rp 1.001").

## 3. Quality of life

- [x] **P1 · S — Onboarding singkat.** Layar kosong saat pertama install belum menjelaskan urutan
  "buat wallet → catat transaksi → buat goal". Cukup satu kartu di Home dengan CTA.
- [x] **P1 · S — Filter wallet di Activity.** Filter bulan/tipe sudah ada; tambahkan wallet dan
  tombol "Bulan ini" untuk kembali cepat.
- [x] **P1 · S — Input uang lebih aman.** Prefix `Rp`, batas 15 digit, dan IME "Selesai"
  langsung menyimpan sudah ada; uji font scale 1.3 di perangkat belum dijalankan.
- [ ] **P2 · S — Rentang tanggal.** Filter bulan saja kurang untuk "minggu ini"; tambah preset
  7/30 hari tanpa mengubah aturan bulanan.
- [x] **P2 · S — Salin ringkasan bulan.** Review sudah bisa copy per baris; tambah satu tombol
  "Copy ringkasan" (teks siap tempel ke chat).
- [x] **P2 · S — Haptic + konfirmasi berbeda.** Getar halus saat simpan/hapus, dan warna bahaya
  yang konsisten antara dialog dan tombol.

## 4. UI/UX

- [ ] **P1 · M — i18n.** Seluruh teks UI masih hardcoded bahasa Inggris di Kotlin; `strings.xml`
  hanya berisi `app_name`. Pindahkan ke resource + `values-in` (Indonesia), lalu bahasa mengikuti
  locale perangkat.
- [x] **P1 · S — Adaptive icon.** `@drawable/ic_launcher` masih vector tunggal tanpa adaptive icon
  (foreground/background) atau monochrome untuk themed icon Android 13+.
- [x] **P1 · M — Dark theme.** Sekarang light-only. Tambahkan `darkColorScheme` dengan pasangan
  warna hero/tint, lalu uji kontras teks `Muted` di atas `CanvasBg`.
- [x] **P1 · S — Audit aksesibilitas.** `contentDescription` untuk aksi/nav/kalender dan target
  sentuh utama ≥ 48dp sudah ditambahkan; uji TalkBack dan font scale 2.0 di perangkat fisik masih
  perlu dilakukan.
- [ ] **P2 · M — Tablet & landscape.** Navigation rail ≥ 600dp sudah ada; layout dua kolom untuk
  Home/Activity belum dikerjakan.
- [a ] **P2 · M — Grafik.** Ditunda: "charts" masih non-goal, butuh keputusan produk.
- [x] **P2 · S — Empty state lebih informatif.** Setiap tab kosong sebaiknya menampilkan satu aksi
  utama, bukan hanya teks.

## 5. Backup, privasi & keamanan

- [ ] **P0 · S — `allowBackup`.** Manifest memakai `android:allowBackup="true"` tanpa aturan, jadi
  Android Auto Backup bisa mengunggah database ke cloud Google. Untuk klaim "sepenuhnya offline",
  set `false` atau tambahkan `dataExtractionRules` + dokumentasikan pilihannya.
- [x] **P1 · S — Info backup di Settings.** Tampilkan waktu export terakhir, ukuran file, dan nama
  file default agar pengguna tahu apakah datanya sudah aman.
- [ ] **P2 · M — Backup terenkripsi.** File JSON saat ini plaintext; opsi password (AES-GCM dengan
  kunci dari PBKDF2) membuat backup aman disimpan di cloud pribadi.
- [ ] **P2 · M — Auto-backup mingguan.** SAF `takePersistableUriPermission` + WorkManager untuk
  menulis ke folder pilihan pengguna tanpa menambah permission storage.

## 6. Testing, performa & rilis

- [x] **P1 · M — UI test Compose.** Test pindah tab, tambah transaksi, dan filter dompet sudah
  ditulis serta lolos kompilasi; belum dijalankan karena tidak ada perangkat/emulator.
- [x] **P1 · S — Test import negatif.** JSON rusak, versi salah, id duplikat, tipe tidak dikenal —
  pastikan semuanya gagal tanpa mengubah data.
- [ ] **P1 · S — Ukur di perangkat fisik.** Target cold start < 1 detik belum terbukti (emulator
  SwiftShader 1,47 dtk). Ukur di HP low-end, lalu regenerasi baseline profile bila perlu.
- [x] **P2 · M — Rilis nyata.** Keystore sendiri, disiplin `versionCode`, CI (build + unit test +
  APK artifact), changelog, dan daftar rilis di README.
- [x] **P2 · S — API Gradle deprecated.** Penamaan APK memakai `applicationVariants` /
  `BaseVariantOutputImpl` yang akan hilang di AGP 9; migrasikan ke `androidComponents` +
  `VariantOutput` sebelum upgrade.
- [x] **P2 · S — Error state saat load gagal.** `reload()` tidak menangani exception; tambahkan
  state error + tombol coba lagi supaya database rusak tidak menghasilkan layar kosong. *(1.2)*

## 7. Jangka panjang (butuh desain produk)

- [a ] **P2 · M — Kategori/tag.** Ditunda: butuh desain produk + perubahan skema/backup.
- [ a] **P2 · L — Widget quick add.** Ditunda: fitur Android terpisah, butuh desain produk.
- [ ] **P2 · M — Pengingat harian.** Notifikasi lokal opsional (butuh izin `POST_NOTIFICATIONS`
  di Android 13+), bukan layanan push.
- [ ] **P2 · L — Lampiran struk.** Foto bukti per transaksi; menambah ukuran backup dan kebutuhan
  kompresi/penghapusan.
- [ ] **P2 · M — Distribusi.** Sideload APK vs Play Store (kebijakan data, signing, ukuran).

## 8. Jangan dikerjakan tanpa keputusan produk

Item berikut sering diminta, tetapi **melanggar aturan yang sudah disepakati** di `GUIDE.md` §13
dan `README.md` §4/§8. Kalau memang ingin diubah, revisi dokumen itu dulu, lalu kerjakan:

- Akun, login, sinkronisasi cloud, atau network call apa pun (produk ini offline, single-user).
- Edit wallet atau rename (hanya goal yang boleh diedit).
- Riwayat transfer (transfer sengaja tidak membuat record).
- Budget, transaksi berulang, grafik, dan export CSV — semuanya di daftar non-goal.
- Deduksi otomatis goal dari saldo wallet (goal sengaja terpisah).
- Telemetri/analytics/crash reporting online.

---

## Ide yang mungkin tidak terpikirkan

- **Mode "uang jajan"**: saldo harian dari wallet tertentu tanpa mengubah model data.
- **Rekonsiliasi bulanan**: cocokkan saldo aplikasi dengan saldo bank, catat selisih sebagai
  penyesuaian (bukan transaksi palsu).
- **Template catatan**: quick action sudah mendekati ini; pisahkan "template" dari "transaksi
  terakhir" supaya tidak tercampur.
- **Ekspor teks untuk pajak/arsip**: satu halaman ringkasan per tahun, cukup dari snapshot.
- **Mode gelap otomatis** mengikuti jadwal, bukan hanya switch manual.
