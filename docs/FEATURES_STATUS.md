# Estado de funcionalidades — SELDIA MUSIC

## Terminadas (probadas)

1. ✅ **Interfaz desktop de tres paneles** (lateral · contenido · "Sonando ahora") + barra inferior flotante y layout de teléfono (nav inferior). Sin "Inicio" dedicado en desktop.
2. ✅ **Panel lateral:** fila horizontal `Listas | Artistas | Buscar` (en ese orden), auto-selección de Favoritas al iniciar, playlists verticales con carátula dinámica (sin bloque rojo / `<unknown>`), "Sin conexión" abajo, Ajustes solo icono.
3. ✅ **Búsqueda YouTube vía NewPipe** (`ALL`, no solo música): resultados listos para reproducir, sin credencial requerida.
4. ✅ **Reproducción Media3 unificada**: Audio/Vídeo en el panel derecho, `PlayerView` vinculada (sin pantalla negra), fullscreen con barra clonada y controles autohididos (5 s).
5. ✅ **Reproducción local** (MediaStore + ExoPlayer) y online (NewPipe → ExoPlayer).
6. ✅ **Favoritos** (toggle + persistencia + vista "Canciones que te gustan").
7. ✅ **Playlists** (crear, renombrar, eliminar, añadir/quitar pistas; carátula aleatoria o primera canción).
8. ✅ **Historial** (registro automático + vista).
9. ✅ **Biblioteca offline agrupada por Artistas** (filtro lateral) y "Sin conexión" (descargas). Los Álbumes como destino suelto fueron retirados del lateral.
10. ✅ **Cola de reproducción** (mostrar, reproducir por índice, quitar) + **MediaSession/notificación** + fondo (foreground service).
11. ✅ **Letras sincronizadas** (karaoke) en Bottom Sheet.
12. ✅ **Descargas de audio** (M4A, MediaStore) sincronizadas en todas las vistas (flecha en acento).
13. ✅ **Importación Spotify sin login**: enlace público (`playlist`/`album`) **o** archivo `.txt` por lote (SAF `OpenDocument`, línea por línea, progreso `Procesando enlace X de N…`, mapeo NewPipe, guardado Room en "Mis Listas"). Sin OAuth.
14. ✅ **Copia de seguridad y restauración JSON** (SAF) de DB + preferencias.
15. ✅ **Temas**: Oscuro / Claro / **Glass (iPhone)** + 8 acentos dinámicos globales.
16. ✅ **Rendimiento P0**: `ListAdapter`+`DiffUtil` (sin `notifyDataSetChanged` global), Coil `size(128)`, scopes cancelables, `positionFlow` separado del estado discreto, R8 release (`minifyEnabled` + `shrinkResources`, ~4 MB vs ~15 MB debug).
17. ✅ **Cero publicidad.** Sin AdMob, banners ni rastreadores.

## Sin credenciales (operan sin clave)

| Integración | Estado | Nota |
|---|---|---|
| YouTube (búsqueda + streams) | ✅ NewPipe sin clave | API Data v3 opcional vía `SELDIA_YOUTUBE_API_KEY`; si falta, extractor directo |
| Spotify (import público) | ✅ Sin OAuth | URL o `.txt` (playlists + álbumes) |
| SoundCloud | ❌ Retirado / no presente | Sin `SoundCloudRepositoryDetailed` ni `client_id` |

## Mejoras futuras (opcionales)

- Gapless playback y crossfade.
- Android Auto / soporte de controladores externos avanzados.
- Widget de escritorio.
- Equivalente iOS vía KMP (ver `IOS_PREPARATION.md`).
