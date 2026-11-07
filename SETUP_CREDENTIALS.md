# SETUP_CREDENTIALS — SELDIA MUSIC

La aplicación funciona **sin credenciales** para la música local, favoritos, playlists,
historial, cola y reproducción en segundo plano. Las integraciones externas
(SoundCloud y YouTube) requieren credenciales **opcionales** para la búsqueda/streaming.

Cuando una credencial no está configurada, la app muestra un aviso amable
("SoundCloud no está configurado. Consulta SETUP_CREDENTIALS.md.") y **no se bloquea**.

> ⚠️ **Nunca escribas secretos reales en Git.** Las credenciales se inyectan en tiempo de
> compilación desde variables de entorno o desde `gradle.properties` local (ignorado).

---

## 1. SoundCloud — `client_id`

**Qué servicio lo necesita:** búsqueda de pistas y resolución de URLs de streaming
(API pública v2 de SoundCloud).

**¿Hay alternativa oficial sin credenciales?** No para la búsqueda programática.
La reproducción en sí usa la URL de streaming pública devuelta por la API.

**Dónde obtenerlo (enlace oficial):**
- Regístrate y crea una app en: https://developers.soundcloud.com/
- El `client_id` se obtiene al registrar una aplicación.

**Dónde colocarlo:**

Opción A — variable de entorno (recomendado):
```bash
# Windows (PowerShell)
$env:SELDIA_SOUNDCLOUD_CLIENT_ID = "TU_CLIENT_ID"
# Linux/macOS
export SELDIA_SOUNDCLOUD_CLIENT_ID="TU_CLIENT_ID"
```

Opción B — `gradle.properties` local (no versionar):
```
soundcloud.client.id=TU_CLIENT_ID
```

**Dónde se usa:** `NetworkProvider.soundCloudClientId` → `BuildConfig.SOUNDCLOUD_CLIENT_ID`
(incluido en `app/build.gradle` vía `buildConfigField`).

---

## 2. YouTube — API key (Data API v3)

**Qué servicio lo necesita:** búsqueda de vídeos (YouTube Data API v3).

**¿Hay alternativa oficial sin credenciales?** La **reproducción** de un vídeo usa el
reproductor oficial embebido (IFrame Player API) y **no requiere clave**. Solo la
**búsqueda** necesita la API key.

**Dónde obtenerla (enlace oficial):**
- Consola de Google Cloud: https://console.cloud.google.com/
- Crea un proyecto, habilita "YouTube Data API v3" y genera una API key.

**Dónde colocarla:**

Opción A — variable de entorno:
```bash
# Windows (PowerShell)
$env:SELDIA_YOUTUBE_API_KEY = "TU_API_KEY"
# Linux/macOS
export SELDIA_YOUTUBE_API_KEY="TU_API_KEY"
```

Opción B — `gradle.properties` local (no versionar):
```
youtube.api.key=TU_API_KEY
```

**Dónde se usa:** `NetworkProvider.youtubeApiKey` → `BuildConfig.YOUTUBE_API_KEY`.

---

## Notas de cumplimiento

- **YouTube**: la reproducción es **solo en primer plano** (los Términos de YouTube no
  permiten reproducción en segundo plano ni descarga). Por eso YouTube no entra en la
  cola de reproducción en segundo plano; se reproduce en su pantalla dedicada.
- **SoundCloud**: solo se reproducen pistas con streaming permitido (`policy != BLOCK/SNIP`).

## Verificación

Tras configurar las credenciales, recompila:
```bash
gradlew.bat assembleDebug
```
y la búsqueda de SoundCloud/YouTube devolverá resultados reales.
