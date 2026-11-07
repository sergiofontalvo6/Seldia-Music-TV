# Preparación arquitectónica para iOS — SELDIA MUSIC

La aplicación Android está estructurada para poder extraer una **versión iOS** sin
comprometer la versión Android. El principio rector es **separar el dominio y los datos
de la plataforma**.

## Lo que ya es multiplataforma

### 1. Modelos de dominio (`data/model`)
- `Track`, `Playlist`, `Source` son **clases Kotlin puras** (sin imports de Android).
- Pueden traducirse 1:1 a Swift `struct`/`enum`, o compartirse vía **Kotlin Multiplatform
  (KMP)** si se desea un único código fuente.

### 2. Repositorios por proveedor (`data/repository`)
- `SoundCloudRepository` y `YouTubeRepository` encapsulan toda la lógica de red y mapeo.
- No dependen de Android salvo por los DTO Retrofit/Gson (`data/remote`), que son
  serializables y triviales de portar a `Codable`/`URLSession`.

### 3. Casos de uso implícitos
- Favoritos, playlists, historial y cola son **conceptos de dominio** cuya lógica puede
  reimplementarse contra `Core Data`/`SwiftData` en iOS.

## Lo que es específico de Android (a reimplementar en iOS)

| Android | Equivalente iOS |
|---------|-----------------|
| `ExoPlayer` (Media3) | `AVPlayer` / `AVQueuePlayer` |
| `MediaSessionService` | `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` |
| Servicio en primer plano | Background Audio mode (`UIBackgroundModes: audio`) |
| Room | `Core Data` / `SwiftData` |
| MediaStore | `MPMediaQuery` |
| Retrofit/Gson | `URLSession` + `Codable` |
| XML/ViewBinding | `SwiftUI` |

## Ruta sugerida para iOS

1. Crear un **módulo compartido KMP** (`shared`) con `Track`, `Playlist`, `Source` y las
   interfaces de los repositorios (contratos).
2. Implementar `SoundCloudRepository`/`YouTubeRepository` en KMP (usando `khttp`/`Ktor`),
   reutilizables en ambas plataformas.
3. En iOS, implementar la capa de reproducción con `AVPlayer` y la UI con `SwiftUI`.
4. La persistencia local puede quedar por plataforma (`Room` vs `Core Data`), o unificarse
   con `SQLDelight`.

## Por qué no compromete Android

- El código Android actual **no se toca** para preparar iOS: la separación ya existe en
  `data/model` y `data/repository`. Extraer el dominio a KMP es un cambio **aditivo**.
- `AppContainer` (DI manual) facilita sustituir implementaciones por las de KMP sin
  reescribir la UI Android.
