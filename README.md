# NextFL

Aplikasi Android untuk mengedit dan me-remix lagu secara sederhana (gaya DAW seperti FL Studio), dilengkapi template visual yang bergerak mengikuti beat untuk konten DJ.

## Features

- [x] Import audio (MP3, M4A/AAC, WAV, OGG, FLAC, Opus) — otomatis di-decode ke PCM 44.1 kHz dan dideteksi BPM-nya
- [x] Playlist multi-track — geser klip, trim tepi, potong di playhead, duplikat, hapus, snap (bar/beat/½/¼), pinch zoom
- [x] Edit klip — gain, fade in/out, normalize
- [x] Tempo — BPM project, tap tempo, klip ikut BPM project, speed manual, kunci nada (time-stretch WSOLA)
- [x] Mixer — volume fader, pan, mute/solo, meter level per track + master (limiter)
- [x] Efek per track — EQ 5 band (low cut, low shelf, mid, high shelf, high cut) dengan kurva respons, reverb (mix, decay, pre-delay, tone)
- [x] Template visual "Pulse Ring" — logo DJ + background bisa diganti, ring spektrum, partikel, kilatan garis, goyang saat beat; rasio 16:9, 9:16, 1:1
- [x] Undo/redo, autosave proyek
- [x] Export mixdown WAV ke `Music/NextFL` + bagikan
- [ ] Export video MP4 dari template visual
- [ ] Versi desktop (Compose Multiplatform)

## Tech Stack

| Layer | Technology |
|---|---|
| Bahasa | Kotlin 2.4 |
| UI | Jetpack Compose (Material 3 minimal, komponen custom), font Inter |
| Audio engine | Mixer sendiri di `:core` (Kotlin murni) + `AudioTrack` float |
| Decode audio | `MediaExtractor` + `MediaCodec`, resampler windowed-sinc |
| DSP | Biquad EQ, Freeverb, limiter, WSOLA time-stretch, FFT, deteksi BPM & beat |
| Penyimpanan | JSON (kotlinx.serialization) + PCM 16-bit di storage aplikasi (memory-mapped) |
| Build | Gradle 9.8, AGP 9.4, compileSdk 37, minSdk 29 |

## Struktur Project

```
core/   Logika murni Kotlin/JVM (model, edit, undo, DSP, engine) — bisa dipakai ulang untuk desktop
app/    Aplikasi Android (audio I/O, penyimpanan, UI Compose, template visual)
```

## Getting Started (Development)

### Prerequisites

- Android Studio (sudah termasuk JDK) atau JDK 21+
- Android SDK platform 37

### Setup

```bash
# 1. Buka folder ini di Android Studio, atau lewat terminal:
./gradlew :core:test            # unit test engine & DSP
./gradlew :app:assembleDebug    # build APK debug

# 2. Install ke HP/emulator yang terhubung
./gradlew :app:installDebug
```

APK debug ada di `app/build/outputs/apk/debug/NextFL-debug.apk`.

### Environment Variables

Tidak ada. Aplikasi berjalan sepenuhnya offline dan tidak memakai secret/API key. Lokasi SDK diatur otomatis oleh Android Studio di `local.properties` (tidak di-commit).

## Production Build

```bash
./gradlew :app:assembleRelease   # R8 + resource shrinking aktif
```

Untuk rilis ke Play Store, tambahkan konfigurasi signing (keystore) di luar repo.

## License

Kode: privat. Font Inter: SIL Open Font License (lihat `licenses/Inter-OFL.txt`).
