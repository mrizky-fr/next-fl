<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:131517,100:0465C4&height=200&section=header&text=NextFL&fontColor=ffffff&fontSize=56&fontAlignY=36&desc=Edit%2C%20Remix%2C%20dan%20Visual%20DJ%20di%20Android&descSize=18&descAlignY=58" alt="NextFL — Edit, Remix, dan Visual DJ di Android" width="100%" />

<img src="https://readme-typing-svg.demolab.com?font=Fira+Code&size=18&duration=3000&pause=1000&color=0465C4&center=true&vCenter=true&width=640&lines=Edit+%26+remix+lagu+langsung+di+HP;Visual+DJ+yang+bergerak+ikut+beat;Export+WAV%2C+MP3+%26+video+MP4+siap+upload" alt="Edit & remix lagu langsung di HP" />

<a href="https://github.com/mrizky-fr/next-fl/raw/main/release/NextFL.apk"><img src="https://img.shields.io/badge/Download%20%26%20Install%20Now-APK%200.1.43-0465C4?style=for-the-badge" alt="Download & Install Now" height="42" /></a>

<sub>Android 10+ · ±3,4 MB · Gratis</sub>

</div>

## NextFL

Mempersembahkan **NextFL**, studio musik saku untuk Android. Susun lagu di playlist multi-track, rapikan tempo, racik suaranya di mixer, lalu ubah jadi konten: logo DJ-mu berdenyut di tengah layar, mengikuti setiap hentakan drum.

Semuanya berjalan **offline** di HP. Tidak perlu akun, tidak perlu server, tidak ada yang dikirim ke mana pun.

- **Gaya FL Studio**, tapi disederhanakan untuk layar sentuh
- **Mixer sungguhan**, setiap knob benar-benar mengubah suara
- **Visual ikut beat**, logo, gunung spektrum, dan komet berasap bergerak sesuai drum
- **Siap posting**, export WAV, MP3 128 sampai 320 kbps, atau video MP4 16:9, 9:16, dan 1:1

## Pengalaman Membuat

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>Playlist multi-track</h3>
      Import MP3, M4A, WAV, OGG, FLAC, atau Opus. Geser klip bebas ke kiri, kanan, dan pindah track seperti FL Studio, trim, potong di playhead, duplikat, urutkan track dengan tahan dan seret, snap ke bar atau beat, plus pinch zoom dan geser dengan fling.
    </td>
    <td width="50%" valign="top">
      <h3>Tempo yang menurut</h3>
      BPM terdeteksi otomatis. Klip bisa ikut BPM project, diatur speed-nya, dan nada tetap terkunci lewat time-stretch real-time. Nada juga bisa dinaikkan atau diturunkan sampai 12 semitone tanpa mengubah speed atau BPM, dan langsung terdengar saat digeser.
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>Mixer ala studio</h3>
      Fader, pan, mute dan solo, plus FX chain di setiap track dan master: Parametric EQ, Compressor, Stereoizer, Reverb, Graphic EQ 8 band, dan Leveller. Semua efek mulus tanpa klik saat diputar atau dinyalakan, lengkap dengan preset, reset, salin, dan tempel. Master memakai limiter lookahead supaya keras tapi tidak pecah.
    </td>
    <td width="50%" valign="top">
      <h3>NextPlayer</h3>
      Template Pulse Ring dengan logo DJ dan background pilihanmu. Gunung beat, komet berasap, dan partikel bereaksi pada drum. Jumlah komet dan partikel bisa diatur, ada cahaya glare ala Blender untuk komet, glitch, dan gunung beat, ada slider sinkron untuk HP mana pun, dan ikon 3D TikTok, YouTube, Spotify, serta Instagram dengan username bisa dipasang di bawah judul.
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>Mode Ngonten</h3>
      Satu track di atas, deck DJ di bawahnya: jog wheel untuk mengatur BPM, knob Filter, Echo, Flanger, dan Crush, plus pengaturan speed dan nada klip. Preview visual kotak di kanan.
    </td>
    <td width="50%" valign="top">
      <h3>Export sekali ketuk</h3>
      Mixdown WAV atau MP3 (pilih 128, 192, 256, atau 320 kbps, dengan tag gapless jadi tanpa jeda di awal) ke Music/NextFL, atau render video MP4 30 fps ke Movies/NextFL. Pilih rasio dulu, batalkan kapan saja.
    </td>
  </tr>
</table>

## Install Sekarang

<div align="center">

<a href="https://github.com/mrizky-fr/next-fl/raw/main/release/NextFL.apk"><img src="https://img.shields.io/badge/Download%20%26%20Install%20Now-APK%200.1.43-0465C4?style=for-the-badge" alt="Download & Install Now" height="42" /></a>

</div>

1. Unduh APK lewat tombol di atas.
2. Buka file yang terunduh. Bila diminta, izinkan pemasangan dari sumber ini.
3. Pasang, buka **NextFL**, tekan **+ Audio**, dan mulai meracik.

## Dibangun Dengan

<p align="center">
  <img src="https://skillicons.dev/icons?i=kotlin" alt="Kotlin" title="Kotlin" width="48" height="48" />&nbsp;
  <img src="https://skillicons.dev/icons?i=androidstudio" alt="Android Studio" title="Android Studio" width="48" height="48" />&nbsp;
  <img src="https://skillicons.dev/icons?i=gradle" alt="Gradle" title="Gradle" width="48" height="48" />
</p>

<p align="center"><sub>Kotlin · Jetpack Compose · AudioTrack · MediaCodec · OpenGL ES · Font Inter</sub></p>

<details>
<summary><b>Untuk developer</b></summary>

<br>

Build dan uji dengan JDK bawaan Android Studio:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :core:test :app:assembleRelease
```

APK rilis (R8 dan resource shrinking aktif) dibuat di `app/build/outputs/apk/release/NextFL-release.apk`, lalu disalin menimpa [`release/NextFL.apk`](release/NextFL.apk) untuk tombol unduh. `versionCode` diambil dari jumlah commit (`git rev-list --count HEAD`) dan `versionName` menjadi `0.1.<jumlah commit>`, jadi setiap perubahan otomatis menaikkan versi. Build debug tetap tersedia lewat `:app:assembleDebug`.

Project terbagi dua modul:

| Modul | Isi |
|---|---|
| `core/` | Kotlin murni: model, edit, undo/redo, DSP (EQ, stereoizer, reverb FDN, limiter, WSOLA real-time, efek DJ, FFT), mix engine, deteksi BPM dan beat. Bisa dipakai ulang untuk versi desktop. |
| `app/` | Android: decode audio, playback `AudioTrack`, penyimpanan proyek, UI Compose, template visual, dan render video H.264 + AAC. |

Tidak ada API key. Kredensial tanda tangan rilis dibaca dari environment variable, atau dari `keystore.properties` bila variabelnya tidak diset. Salin [`keystore.properties.example`](keystore.properties.example) menjadi `keystore.properties`, lalu isi nilainya. Keystore dan file itu tidak di-commit. Tanpa keystore, build rilis tetap jalan tetapi APK-nya tidak bertanda tangan.

| Variable | Fungsi | Default |
|---|---|---|
| `NEXTFL_STORE_FILE` | Path keystore, relatif ke root project | - |
| `NEXTFL_STORE_PASSWORD` | Password keystore | - |
| `NEXTFL_KEY_ALIAS` | Alias kunci | - |
| `NEXTFL_KEY_PASSWORD` | Password kunci | - |

Lokasi SDK diatur Android Studio di `local.properties` (tidak di-commit).

</details>

## Catatan

APK di tombol unduh adalah **build rilis** bertanda tangan, bukan rilis Play Store. File ini selalu ditimpa versi terbaru; versinya naik otomatis setiap commit. Bila sebelumnya memasang build debug, hapus dulu sebelum memasang rilis. Proyek, audio, logo, dan background tersimpan di penyimpanan aplikasi; hasil export masuk ke folder Music dan Movies. Font Inter dipakai di bawah SIL Open Font License (lihat [`licenses/Inter-OFL.txt`](licenses/Inter-OFL.txt)). Export MP3 memakai [jump3r](https://git.iem.at/sciss/jump3r), port Java dari LAME, di bawah LGPL 2.1 (lihat [`licenses/jump3r-LGPL-2.1.txt`](licenses/jump3r-LGPL-2.1.txt)).

## Pembuat

Dibuat oleh **M Rizky** (Kyy).

[![GitHub](https://img.shields.io/badge/GitHub-mrizky--fr-131517?style=for-the-badge)](https://github.com/mrizky-fr)
[![Repo](https://img.shields.io/badge/Repo-next--fl-0465C4?style=for-the-badge)](https://github.com/mrizky-fr/next-fl)

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:0465C4,100:131517&height=120&section=footer" alt="" width="100%" />
