# SELDIA MUSIC

Aplicación musical completa para Android, con diseño **Dark Glassmorphism / Spotify Pro**
(tres paneles en horizontal), pensada para **radios Android / head units (WJ/Wonjan AC8227L, Android 8.1, 1 GB RAM, 1280×720, horizontal)**.

**Sin publicidad propia.** Cero SDKs publicitarios, cero banners, cero rastreadores.

---

## Características

| Área | Estado |
|------|--------|
| Interfaz **desktop de tres paneles** (biblioteca · tabla de pistas · "Sonando ahora") + barra inferior flotante | ✅ |
| **Búsqueda directa en YouTube vía NewPipe Extractor** (MUSIC_SONGS), resultados listos para reproducir | ✅ |
| **Sistema híbrido Online ⇄ Offline transparente y automático** (`NetworkConnectivityManager`) | ✅ |
| **Sin descargas de vídeo en modo offline / auto-descarga** (solo flujo de audio M4A) | ✅ |
| Reproducción de música sin conexión (MediaStore + descargas enlazadas vía Room) | ✅ |
| **Listas online/offline sincronizadas con Room** (favoritos, playlists, historial y `uri` del audio descargado) | ✅ |
| Favoritos / Playlists / Historial | ✅ |
| Biblioteca offline agrupada por **Álbumes** y **Artistas** | ✅ |
| Tabla de pistas: # · Título · Álbum · Fecha · Duración | ✅ |
| Controles MediaSession + notificación, segundo plano (foreground service) | ✅ |
| Cola de reproducción, shuffle, repeat (off/all/one) | ✅ |
| Persistencia (Room + SharedPreferences) | ✅ |

---

## Requisitos de compilación

- JDK 17 (Temurin) — en este entorno: `JAVA_HOME=%USERPROFILE%\.jdks\jdk-17.0.21+9`
- Android SDK (platform 34, build-tools)
- Gradle 8.9 (incluido el wrapper)

> Nota: el `java` del sistema (Java 27) no es compatible con Gradle 8.9; compila con JDK 17.

## Compilar

```bash
# Windows
set JAVA_HOME=%USERPROFILE%\.jdks\jdk-17.0.21+9
gradlew.bat assembleDebug
# Linux/macOS
./gradlew assembleDebug
```

El APK se genera en: `app/build/outputs/apk/debug/app-debug.apk`

## Instalar en el dispositivo/emulador

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Mínimo y objetivo

- `minSdk 27` (Android 8.1)
- `targetSdk 34`
- Optimizado para 1 GB de RAM.

## Credenciales

La búsqueda YouTube funciona **sin credenciales** vía NewPipe Extractor.
Para habilitar la API de YouTube Data v3 (opcional), consulta **[SETUP_CREDENTIALS.md](SETUP_CREDENTIALS.md)**.

## Documentación

- [Arquitectura](docs/ARCHITECTURE.md)
- [Informe de pruebas](docs/TEST_REPORT.md)
- [Estado de funcionalidades](docs/FEATURES_STATUS.md)
- [Preparación para iOS](docs/IOS_PREPARATION.md)

---

## Estructura del proyecto

```
app/src/main/java/com/seldia/music/
├── SeldiaApp.kt                 # Application (+ init de OfflinePrefs)
├── di/AppContainer.kt           # DI manual (sin Hilt)
├── data/
│   ├── model/                   # Modelos de dominio
│   ├── db/                      # Room (entidades, DAOs, mappers; incluye markDownloaded())
│   ├── network/                 # NetworkConnectivityManager (online ⇄ offline transparente)
│   ├── offline/                 # OfflineMusicRepository (MediaStore, incluye descargas)
│   ├── extractor/               # YouTubeAudioExtractor (NewPipe: búsqueda + streams)
│   └── repository/              # LibraryRepository, YouTubeRepository, ...
├── playback/                    # MusicPlayer + PlaybackService + MediaDownloadManager (solo audio en offline/auto)
└── ui/                          # MainActivity (shell adaptativo desktop/móvil), Fragments, ViewModels
    ├── adapters/                # TrackTableAdapter (tabla #/Título/Álbum/Fecha/Duración)
    ├── library/                 # OfflineFragment, LikedSongsFragment, MediaGroupsFragment, GroupTracksFragment
    ├── TableListFragment.kt     # Base del encabezado de playlist (Reproducir/Mezclar/Añadir)
    └── player/                  # VisualizerView, LyricsBottomSheetFragment, ...
```
