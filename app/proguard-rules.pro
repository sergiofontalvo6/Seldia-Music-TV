# SELDIA MUSIC - ProGuard / R8 rules
# Mantener modelos Gson/Retrofit (usados por reflexión)
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Modelos de dominio serializados
-keep class com.seldia.music.data.remote.** { *; }

# Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Room - base de datos, entidades y DAOs (generados vía anotaciones)
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleTypeAnnotations
-keep class com.seldia.music.data.db.AppDatabase { *; }
-keep class com.seldia.music.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# Media3 / ExoPlayer - MediaSession y ExoPlayer se resuelven vía manifiesto y API pública
-keep class androidx.media3.session.MediaSessionService { *; }
-keep class androidx.media3.session.MediaSession { *; }
-keep class androidx.media3.exoplayer.ExoPlayer { *; }
-keep interface androidx.media3.common.Player
-keep class androidx.media3.datasource.** { *; }
-dontwarn androidx.media3.**

# Coil (carga de imágenes) - registrar componentes y clases de imagen públicas
-keep class coil.** { *; }
-keep class coil_singleton.** { *; }
-dontwarn coil.**

# Rhino (NewPipeExtractor) - clases java.beans no presentes en Android
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.script.ScriptEngineFactory
