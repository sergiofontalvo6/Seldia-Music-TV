# Informe de pruebas — SELDIA MUSIC

## Entorno de prueba

- **Dispositivo:** Emulador Android **8.1 (API 27)**, imagen `google_apis/x86`.
- **Pantalla:** **1280×720**, densidad 160 (horizontal).
- **Aceleración:** WHPX.
- **APK:** `app-debug.apk` (~15 MB debug), release ofuscado (~4 MB), `minSdk 27`, `targetSdk 34`.

> El emulador coincide con el hardware objetivo (Android 8.1, 1280×720 horizontal).
> Jank en emulador con renderizado por software no es representativo de hardware real.

---

## Resultados

| # | Prueba | Resultado | Evidencia |
|---|--------|-----------|-----------|
| 1 | Instalación en Android 8.1 | ✅ PASS | `adb install -r` → Success (debug y release compatibles) |
| 2 | Inicio y navegación | ✅ PASS | Arranque en frío → Favoritas con 12-13 pistas; filtros Listas/Artistas/Buscar visibles |
| 3 | Reproducción online/offline | ✅ PASS | Posición avanza (~500 ms/frame vía `positionFlow`), seekbars y tiempos sincronizados, sin recalcular portada/favoritos cada frame |
| 4 | Búsqueda YouTube | ✅ PASS | `ALL` devuelve vídeos no musicales (p. ej. "gatos" → contenido infantil); streams NewPipe reproducibles |
| 5 | Spotify por enlace | ✅ PASS | URL pública → playlist en Room (p. ej. Bonita: `98/98` pistas) |
| 6 | Spotify por archivo `.txt` | ✅ PASS | `list-spotify.txt` (8 enlaces) → progreso `Procesando enlace X de 8…`, listas reactivas en el lateral (Dancehall 10, Salsa Quilla 6, GRUPO FIRME 36, …) |
| 7 | Favoritos | ✅ PASS | Corazón → persistencia Room; "Canciones que te gustan" reactiva |
| 8 | Playlists | ✅ PASS | Crear/renombrar/eliminar, carátula aleatoria con imagen sin bloque rojo ni `<unknown>` |
| 9 | Historial | ✅ PASS | Registro automático + visible |
| 10 | Cola de reproducción | ✅ PASS | Próxima/anterior, shuffle/repeat |
| 11 | Controles multimedia | ✅ PASS | MediaSession activa, notificación `category=transport` |
| 12 | Segundo plano | ✅ PASS | Tras HOME, foreground continúa |
| 13 | Consumo de RAM | ✅ PASS | Idle fresco **~57 MB PSS** (Java 10,2 + Native 13,7 MB); tras navegar **~85 MB**; hilos 40-50 |
| 14 | Estabilidad | ✅ PASS | Sin `FATAL EXCEPTION` en logcat tras los cambios P0 |
| 15 | Filtros laterales | ✅ PASS | Fila `Listas \| Artistas \| Buscar` en orden; "Sin conexión" justo encima de Ajustes (solo icono), sin "Inicio" |
| 16 | Temas | ✅ PASS | Oscuro ↔ Claro ↔ Glass (iPhone, wallpaper vidrio) + cambio de acento (rojo→azul) global (FAB, progreso, YouTube chip) |

---

## Incidencias encontradas y corregidas

1. **Permiso `READ_EXTERNAL_STORAGE`** (API 27): se solicitaba en runtime antes de leer MediaStore. → Se añadió solicitud explícita.
2. **`LayoutManager` ausente** en `fragment_track_list.xml`. → Añadido `app:layoutManager`.
3. **`RemoteServiceException: startForegroundService() …`**: en Media3 1.4 la notificación solo se activa si la sesión se registra con `addSession()`. → Añadido en `PlaybackService`.
4. **Pantalla negra de vídeo**: `PlayerView` no vinculada al `ExoPlayer`. → Asignación siempre + `RESIZE_MODE_FIT` y re-vo de surface en modo vídeo.
5. **Charset NewPipe en API 27**: `URLEncoder.encode(String, Charset)` (Java 10+) no existe en Android 8. → Plugin `CharsetShimPlugin` (buildSrc) transforma bytecode a `"UTF-8"`.

---

## Limitaciones conocidas

- **YouTube en segundo plano**: reproducción de YouTube en primer plano según Términos (IFrame `YouTubePlayerActivity`); la ruta principal (ExoPlayer/NewPipe) es unificada con el resto de fuentes.
- **Spotify privado**: enlaces privados no se importan (requieren login; no implementado — solo público).
- **Rotación en teléfono**: la actividad se recrea; en el head unit (horizontal fijo) no aplica.
- **Artwork sin metadatos**: archivos WAV sin ID3 pueden mostrar icono en lugar de foto (sanitizado, sin `<unknown>`).
