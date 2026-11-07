# Arquitectura — SELDIA MUSIC

## Resumen

Arquitectura **MVVM** por capas, con **repositorios independientes por proveedor** y
una **frontera de dominio agnóstica de plataforma** preparada para iOS.

```
UI (Fragments/Activities + ViewModels)
        │  observa StateFlow / LiveData
        ▼
Repositorios (Library / Local / SoundCloud / YouTube)
        │
        ├── Room (persistencia local)
        ├── MediaStore (música local)
        └── Retrofit (SoundCloud / YouTube)
```

---

## Capas

### 1. Dominio (`data/model`)
- `Track`, `Playlist`, `Source` son **clases puras de Kotlin**, sin dependencias de Android.
- Esta es la capa que se reutilizará en la futura versión iOS (ver `IOS_PREPARATION.md`).

### 2. Datos (`data/`)
- **Room** (`data/db`): entidades `TrackEntity`, `PlaylistEntity`, `PlaylistTrackCrossRef`,
  `HistoryEntity` + DAOs. Persistencia de favoritos, playlists e historial.
- **MediaStore** (`data/local`): escaneo de audio local.
- **Retrofit** (`data/remote`): `SoundCloudApi` (API v2) y `YouTubeApi` (Data API v3).

### 3. Repositorios (`data/repository`)
Cada proveedor tiene su repositorio aislado:
- `LibraryRepository` (favoritos / playlists / historial)
- `LocalMusicRepository`
- `SoundCloudRepository`
- `YouTubeRepository`

### 4. Reproducción (`playback/`)
- `MusicPlayer`: singleton que envuelve `ExoPlayer` (Media3), gestiona la **cola de
  reproducción** y expone el estado como `StateFlow<PlayerState>`.
- `PlaybackService`: `MediaSessionService` (Media3). Aloja la `MediaSession`, la
  notificación de controles y el **servicio en primer plano** (segundo plano).

### 5. UI (`ui/`)
- `MainActivity`: navegación (Inicio / Buscar / Biblioteca) + mini reproductor (vertical)
  o panel de reproductor fijo (horizontal).
- `Fragment`s por pantalla + `ViewBinding` + `RecyclerView`.
- `PlayerViewModel`, `LibraryViewModel`, `SearchViewModel`, `HomeViewModel` (MVVM).

---

## Decisiones clave

### XML + ViewBinding en lugar de Jetpack Compose
El dispositivo objetivo (AC8227L, 1 GB RAM, 4 núcleos, API 27) se beneficia de una UI
**ligera**. Compose añade overhead de runtime y requiere un árbol de composición en memoria.
Se eligió **XML + ViewBinding + RecyclerView + Fragments**, que ofrece el mejor rendimiento
y estabilidad en hardware limitado. (Compose puede evaluarse para versiones futuras de
hardware más potente.)

### Media3 (ExoPlayer + MediaSessionService)
Reproducción con `androidx.media3` (1.4.1), reproducción en segundo plano con
`MediaSessionService`, y controles multimedia vía `MediaSession` + notificación `MediaStyle`.

### DI manual (sin Hilt)
`AppContainer` (service locator) evita el procesador de anotaciones de Hilt, reduciendo el
tamaño del APK y el tiempo de compilación. Suficiente para este alcance y más ligero en RAM.

### Repositorios por proveedor
Aísla SoundCloud y YouTube para poder añadir/quitar proveedores sin tocar la UI ni el
núcleo. La búsqueda devuelve un `Track` de dominio unificado (`Source`).

### Cola de reproducción
`MusicPlayer` mantiene una cola en memoria (lista de `Track` + índice). El `ExoPlayer`
reproduce un `MediaItem` por vez y la lógica de avance/repetir/aleatorio está en
`MusicPlayer`. Las URLs de SoundCloud se resuelven perezosamente antes de reproducir.

---

## Persistencia

- **Room** (`seldia_music.db`): tracks (favoritos), playlists, playlist_tracks, history.
- La clave de un `Track` es `"SOURCE|id"` (p. ej. `LOCAL|17`, `SOUNDCLOUD|123`), única
  entre proveedores.

## Publicidad

La app **no** incluye ningún SDK ni vista de publicidad. No hay AdMob, Google Mobile Ads,
banners, popups ni rastreadores publicitarios.
