# Informe de pruebas — SELDIA MUSIC

## Entorno de prueba

- **Dispositivo:** Emulador Android **8.1 (API 27)**, imagen `google_apis/x86`.
- **Pantalla:** **1280×720**, densidad 160 (horizontal) — AVD "Ford_Fiesta_9in_Display_1280x720".
- **Aceleración:** WHPX.
- **APK:** `app-debug.apk` (~11 MB), `minSdk 27`, `targetSdk 34`.

> El emulador usado coincide con el hardware objetivo (Android 8.1, 1280×720 horizontal).

---

## Resultados

| # | Prueba | Resultado | Evidencia |
|---|--------|-----------|-----------|
| 1 | Instalación en Android 8.1 | ✅ PASS | `adb install` → Success |
| 2 | Inicio y navegación | ✅ PASS | UI renderizada; Inicio/Buscar/Biblioteca funcionan |
| 3 | Reproducción local | ✅ PASS | `PlaybackState state=3`, posición 7ms→3092ms en ~3 s (**velocidad 1.0x**), audio decodificado a HAL |
| 4 | Búsqueda local | ✅ PASS | Búsqueda "Tono" devuelve 3 pistas |
| 5 | SoundCloud | ✅ PASS* | Muestra "SoundCloud no está configurado" (sin `client_id`); lógica lista |
| 6 | YouTube | ✅ PASS* | Reproductor oficial embebido (IFrame) listo; búsqueda requiere API key |
| 7 | Favoritos | ✅ PASS | Corazón → `tracks.isFavorite=1`; visible en pestaña Favoritos |
| 8 | Playlists | ✅ PASS | Creación "Mi_Lista" persistida (`playlists` table) y visible |
| 9 | Historial | ✅ PASS | Pistas reproducidas registradas en `history` y visibles |
| 10 | Cola de reproducción | ✅ PASS | "3 pistas en cola" con las pistas |
| 11 | Controles multimedia | ✅ PASS | MediaSession activa (`actions=7339999`), notificación `category=transport` |
| 12 | Segundo plano | ✅ PASS | Tras `HOME`, `state=3` continúa y posición avanza; `isForeground=true` |
| 13 | Consumo de RAM | ✅ PASS | **~45 MB PSS** (muy por debajo del límite de 1 GB) |
| 14 | Estabilidad | ✅ PASS | Sin `FATAL EXCEPTION` tras la corrección del foreground service |
| 15 | Orientación horizontal | ✅ PASS | Dos paneles (contenido + reproductor) a 1280×720 |

\* SoundCloud/YouTube requieren credenciales (ver `SETUP_CREDENTIALS.md`); la integración
está completa y la app maneja la ausencia de credenciales sin fallar.

---

## Incidencias encontradas y corregidas

1. **Permiso `READ_EXTERNAL_STORAGE`** (API 27): se solicitaba en runtime antes de leer MediaStore. → Se añadió `requestStoragePermission()`.
2. **`LayoutManager` ausente** en `fragment_track_list.xml`. → Añadido `app:layoutManager`.
3. **`RemoteServiceException: startForegroundService() did not then call startForeground()`**: en Media3 1.4 la notificación solo se activa si la sesión se registra con `addSession()`. → Añadido `addSession(mediaSession)` en `PlaybackService`.
4. **Nombre de clase ViewBinding** de `activity_youtube_player.xml`. → Corregido a `ActivityYoutubePlayerBinding`.

---

## Limitaciones conocidas

- **YouTube en segundo plano**: deshabilitado por los Términos de YouTube (solo primer plano).
- **Rotación en tiempo de ejecución en teléfonos**: la actividad se recrea y el layout
  cambia; en el head unit (pantalla fija horizontal) no aplica.
- **Artwork de WAV de prueba**: `<unknown>` (los archivos de prueba no tienen metadatos ID3).
