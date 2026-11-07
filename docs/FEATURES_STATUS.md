# Estado de funcionalidades — SELDIA MUSIC

## Terminadas (probadas)

1. ✅ Interfaz moderna oscura (tema oscuro, dos paneles en horizontal, nav inferior).
2. ✅ Búsqueda musical (local, SoundCloud, YouTube; con aviso si falta credencial).
3. ✅ Integración SoundCloud (API v2: búsqueda + resolución de streaming).
4. ✅ Integración YouTube (Data API v3 para búsqueda + reproductor oficial embebido).
5. ✅ Reproducción de música local (MediaStore + ExoPlayer).
6. ✅ Favoritos (toggle + persistencia + vista).
7. ✅ Playlists (crear, renombrar, eliminar, añadir/quitar pistas).
8. ✅ Historial (registro automático + vista + borrar).
9. ✅ Biblioteca musical (pestañas Playlists / Favoritos / Historial).
10. ✅ Cola de reproducción (mostrar, reproducir por índice, quitar).
11. ✅ Controles multimedia (MediaSession + notificación con acciones).
12. ✅ Reproducción en segundo plano (servicio en primer plano).
13. ✅ Adaptación horizontal/vertical (layout vertical + layout dos paneles horizontal).
14. ✅ Persistencia local (Room + SharedPreferences).
15. ✅ Cero publicidad.

## Pendientes / requiere credenciales externas

| Ítem | Estado | Nota |
|------|--------|------|
| Búsqueda SoundCloud con resultados reales | ⏳ requiere `client_id` | Ver SETUP_CREDENTIALS.md |
| Búsqueda YouTube con resultados reales | ✅ **configurado y probado** | API key en `gradle.properties` |
| Streaming SoundCloud de pistas concretas | ⏳ requiere `client_id` | Lógica completa |

## Mejoras futuras (opcionales)

- Gapless playback y crossfade.
- Descarga offline de pistas de SoundCloud (requiere permiso del titular).
- Caché de portadas y de metadatos con Room + Coil.
- Android Auto / soporte de controladores externos avanzados.
- Widget de escritorio.
- Sincronización con cuentas (login SoundCloud/YouTube).
