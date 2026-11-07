# Arquitectura — SELDIA MUSIC

## Resumen

Arquitectura **MVVM por capas**, con **inyector manual** (`AppContainer`) y una
**frontera de dominio** (`data/model`) agnóstica de plataforma preparada para una futura
evolución iOS. La UI actual (XML + ViewBinding) se organiza en **tres paneles en
horizontal** (lateral · contenido · "Sonando ahora") más barra inferior flotante,
optimizada para head units 1280×720 (API 27) y teléfonos.

```
UI (Activities / Fragments + ViewModels)
        │  StateFlow / LiveData
        ▼
Data (LibraryRepository · YouTubeRepository · OfflineMusicRepository)
        │
        ├── Room (persistencia local)
        ├── MediaStore (música local)
        └── NewPipe Extractor (YouTube search + streams) + Spotify parser público
```

---

## Capas

### 1. Dominio (`data/model`)
- `Track(id, title, artist, album, durationMs, artworkUrl, streamUrl, uri, source,
  videoId, …)` y `Playlist(id, name, createdAt, trackCount)` son **clases puras de
  Kotlin**, sin dependencias de Android. `Track.key` (`"${source}|$id"`, p. ej.
  `YOUTUBE|dQw4w9WgXcQ`) es la clave estable en Room y la identidad en listados.
  Esta es la capa reutilizable para una versión iOS (ver `IOS_PREPARATION.md`).

### 2. Datos (`data/`, `playback/`)
- **Room** (`data/db`): `AppDatabase` (`seldia_music.db`) con entidades
  `TrackEntity`, `PlaylistEntity`, `PlaylistTrackCrossRef`, `HistoryEntity` + DAOs.
  Persiste favoritos, playlists, historial y URI local descargada. `LibraryRepository`
  centraliza el comportamiento (crear/renombrar/eliminar playlists, toggle favorito,
  `importPlaylist()` transaccional, `markDownloaded()`).
- **MediaStore** (`data/offline/OfflineMusicRepository`): escaneo de audio local,
  fusiones MediaStore ↔ Room y listas "Sin conexión". Sanitiza `<unknown>`
  vía `Mappers.kt` (`sanitizeTitle/Artist/Album/ArtworkUrl`).
- **Extractor** (`data/extractor`): `YouTubeAudioExtractor` con **NewPipe Extractor**
  v0.26.5 — búsqueda `YoutubeSearchQueryHandlerFactory.ALL` (todos los vídeos, no
  solo música; fallbacks `VIDEOS` → `MUSIC_SONGS`) y resolución perezosa de streams
  de audio/vídeo. Sin API key por defecto (Data API v3 opcional vía
  `BuildConfig.YOUTUBE_API_KEY`, ausente = vacío → extractor directamente).
  **Complemento de compatibilidad:** `buildSrc` (`CharsetShimPlugin`) transforma
  bytecode `URLEncoder.encode(String, Charset)` / `URLDecoder.decode(String, Charset)`
  de NewPipe a las sobrecargas con `"UTF-8"` para API 27.
- **Spotify público** (`data/spotify`): `SpotifyPlaylistParser` (embed `__NEXT_DATA__`
  → meta/HTML, playlists **y álbumes**) + `SpotifyPlaylistImporter`
  (parseo → NewPipe por pista → guardado Room transaccional). Ver sección Spotify.
- **Red** (`data/network`, `data/remote`): `NetworkConnectivityManager` (online ⇄
  offline transparente) y `NetworkProvider` (OkHttp/Retrofit para YouTube).
- **Backup** (`data/backup`): `BackupManager` exporta/importa JSON vía SAF
  (`CreateDocument`/`OpenDocument`), sustitución completa en transacción.
- **Letras** (`data/repository/LyricsRepository`): letras sincronizadas/plano.

### 3. Reproducción (`playback/`)
- `MusicPlayer`: singleton que envuelve **ExoPlayer (Media3 1.4.1)**, gestiona la cola
  (`queue`, `currentIndex`, shuffle/repeat, audio/vídeo) y expone:
  - `state: StateFlow<PlayerState>` — estado **discreto** (pista, play/pause,
    buffering, shuffle/repeat, videoMode, duración, cola). Sin posición en cada push.
  - `positionFlow: StateFlow<Long>` — posición cada 500 ms **independiente**; la UI
    (seekbars, tiempos, karaoke de letras) solo escucha este flujo.
  - `fun close()` cancela `positionJob` y el scope.
- `PlaybackService`: `MediaSessionService` (Media3) con `MediaSession` y foreground
  de reproducción. `MediaDownloadManager` descarga **solo audio** (M4A/WEBM) a
  MediaStore y enlaza `uri` en Room — disponible offline en todas las listas.
- **YouTube en primer plano:** `ui/youtube/YouTubePlayerActivity` (IFrame API)
  coexiste como reproducción secundaria; la ruta principal es ExoPlayer + NewPipe.

### 4. UI (`ui/`)
- `MainActivity`: shell adaptativo (desktop 3 paneles ≥840 dp / teléfono con nav
  inferior). Filtros laterales `Listas | Artistas | Buscar` (sin "Inicio" ni "Álbumes"
  sueltos); "Sin conexión" justo encima de Ajustes (solo icono). Auto-selecciona
  la primera lista (Favoritas) en el centro al iniciar.
- **Todos los adaptadores son `ListAdapter` + `DiffUtil`** (`TrackTableAdapter`,
  `SidebarPlaylistAdapter`, `PlaylistAdapter`, `HomeCardAdapter`) con actualizaciones
  parciales (`notifyItemChanged` por clave) — sin `notifyDataSetChanged()` global.
  Miniaturas Coil recortadas con `size(128, 128)`.
- `SettingsFragment`: auto-descarga, Spotify (URL y `.txt` por lote), backup/restauración,
  temas Oscuro/Claro/Glass y acentos. `PlayerViewModel` comparte `player.state` y
  `player.positionFlow` con toda la UI.
- `TableListFragment`: base de encabezados de playlist/tabla; `PlaylistDetailFragment`,
  `QueueFragment`, `LyricsBottomSheetFragment`, `SearchFragment` (+ miniaturas libres).

---

## Decisiones clave

### XML + ViewBinding (no Compose)
El dispositivo objetivo (AC8227L, 1 GB RAM, 4 núcleos, API 27) se beneficia de UI
**ligera**. Compose añade overhead y árbol de composición en memoria. Se eligió
**XML + ViewBinding + RecyclerView + Fragments**, equilibrando rendimiento y
velocidad de iteración. (Compose puede evaluarse para hardware más potente.)

### Media3 (ExoPlayer + MediaSessionService)
Reproducción con `androidx.media3:1.4.1`. El `PlayerView` del panel derecho está
siempre vinculado (`player = exoPlayer`), evitando pantalla negra en vídeo;
en fullscreen se recompoe vía decorView y los controles se ocultan a 5 s.

### DI manual (sin Hilt)
`AppContainer` (service locator) evita el procesador de Hilt, reduce tamaño de APK
y tiempo de compilación. Suficiente para este alcance y más ligero en RAM.

### Búsqueda YouTube sin credenciales
NewPipe Extractor como fuente única de búsqueda/streaming. La API `YOUTUBE_API_KEY`
existe pero es opcional y no bloquea la app si está vacía (fallback a NewPipe).

### Importación Spotify sin OAuth
Enlace público o archivo `.txt` (un `playlist|album` por línea) → `__NEXT_DATA__`/
meta → mapeo NewPipe por pista → Room. **No requiere login ni Client ID de Spotify.**
OAuth 2.0/PKCE fue retirado de la UI (código y endpoints no presentes).

### Temas: Oscuro, Claro y Glass (iPhone)
`ThemePrefs` (persistencia SharedPreferences) + overlays de acento
(`?attr/accentPrimary`) y attrs `glassBackground/Sidebar/Panel/Stroke` para
Glass: wallpaper oscuro translúcido + paneles de cristal esmerilado. Usuario elige
en Ajustes → Personalización: Oscuro/Claro/Glass (iPhone) **y** 1 de 8 acentos.
Glass conserva acentos vía variantes combinadas `Glass.Accent*`.

### Rendimiento (P0)
- Adaptadores `ListAdapter` + `DiffUtil`, parches puntuales por clave.
- Coil `size(128, 128)` en miniaturas de lista/filas (evita decode 480×360).
- Scopes cancelados en `onDetachedFromRecyclerView` (`PlaylistAdapter`,
  `SidebarPlaylistAdapter`); `MusicPlayer.close()`.
- Buque desacoplado: `state` (discreto) vs `positionFlow` (500 ms).
- Release: `minifyEnabled true` + `shrinkResources true` (R8 para Media3, Room y Coil).

### Cola de reproducción
`MusicPlayer` mantiene cola en memoria (`List<Track>` + índice). ExoPlayer reproduce
un `MediaItem` por vez; URLs se resuelven perezosamente (NewPipe) antes de reproducir.
La pausa/continuación es por pista; si `uri` local existe en Room se reproduce el archivo.

---

## Persistencia

- **Room** (`seldia_music.db`): tracks (con `uri` descargada), playlists,
  playlist_tracks, history. La clave es `Track.key` (`LOCAL|id`, `YOUTUBE|videoId`).
- **SharedPreferences**: `OfflinePrefs` (auto-descarga), `ThemePrefs` (modo y acento).
- **Backup** (`BackupManager`): JSON con playlists, favoritos, historial y preferencias.

## Publicidad

La app **no** incluye ningún SDK ni vista de publicidad. No hay AdMob, Google Mobile Ads,
banners, popups ni rastreadores publicitarios.
