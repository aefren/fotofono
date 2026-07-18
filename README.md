# Fotófono

Aplicación Android accesible que convierte la luz ambiental en sonido, pensada para personas ciegas o con baja visión. La app mide la luminosidad a través de la cámara trasera y emite un pitido cuyo tono y volumen suben con la cantidad de luz detectada: más luz, tono más agudo y más fuerte.

El nombre viene del *photophone*, el aparato que Alexander Graham Bell patentó en 1880 para transmitir voz sobre un haz de luz. Esta app recorre el camino contrario: toma la luz y la convierte en sonido.

Todo el procesamiento ocurre en el dispositivo. La app no accede a internet ni guarda imágenes: los fotogramas de la cámara se analizan en memoria y se descartan de inmediato.

## Características

- **Realimentación sonora continua.** Pitido generado por síntesis, entre 260 Hz (oscuridad) y 2100 Hz (luz máxima), enrutado como audio de accesibilidad.
- **Lectura hablada del nivel.** El porcentaje de luz y una etiqueta ("muy baja", "baja", "media", "alta", "muy alta") se anuncian por TalkBack.
- **Sensibilidad ajustable** del 5 % al 200 %, para adaptar la respuesta a entornos muy oscuros o muy iluminados.
- **Intervalo del pitido configurable** entre 100 ms y 10 000 ms.
- **Bloqueo de exposición**, que evita que el ajuste automático de la cámara compense los cambios de luz y aplane las lecturas. Se desactiva solo si el dispositivo no lo admite.
- **Diseño accesible:** controles de 56 dp, texto grande, etiquetas TalkBack en todos los controles y anuncios al cambiar de estado. Los ajustes se conservan entre sesiones.

## Requisitos

- Android 6.0 (API 23) o superior.
- Un dispositivo con cámara. Se prefiere la trasera; si no hay, se usa la primera disponible.
- Permiso de cámara, que la app solicita al iniciar la detección.

## Compilación

Necesitas un JDK 17–21 y el SDK de Android con la plataforma API 36 y build-tools 36.0.0. Ojo: Gradle 8.13 no admite JDK 24, que falla con `Unsupported class file major version 68`. Si tienes Android Studio instalado, su JDK incluido sirve:

```bash
export JAVA_HOME="/ruta/a/Android Studio/jbr"
```

Crea un archivo `local.properties` en la raíz apuntando a tu SDK:

```properties
sdk.dir=/ruta/a/tu/Android/Sdk
```

Después, desde la raíz del proyecto:

```bash
./gradlew lintDebug assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

Si compilas desde WSL, usa el script auxiliar `skills/android-app-maintainer/scripts/build_local.sh`, que apunta temporalmente `local.properties` a un SDK y un JDK de Linux y lo restaura al terminar. Las herramientas de compilación de Windows no se ejecutan de forma fiable desde WSL.

## Cómo funciona

La app abre una sesión de Camera2 a una resolución baja (máximo 640×480) y promedia el plano de luminancia (Y) de cada fotograma `YUV_420_888`, muestreando una rejilla de píxeles en lugar de la imagen completa. Ese valor medio se escala con la sensibilidad elegida y se convierte en frecuencia y volumen. Un hilo de audio independiente sintetiza cada pitido como una onda senoidal con rampas de entrada y salida y lo escribe en un `AudioTrack`, de modo que la cadencia del sonido no depende de la velocidad de captura de la cámara.

Está escrita en Java con las APIs del framework de Android, sin AndroidX, Compose ni CameraX, para mantener el APK pequeño y sin dependencias externas.

## Licencia

Distribuido bajo la licencia Apache 2.0. Consulta el archivo [LICENSE](LICENSE).
