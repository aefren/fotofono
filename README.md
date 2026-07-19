# Fotófono

*[Español](#español) · [English](#english)*

## Español

Aplicación Android accesible que convierte la luz ambiental en sonido, pensada para personas ciegas o con baja visión. La app mide la luminosidad a través de la cámara trasera y emite un pitido cuyo tono y volumen suben con la cantidad de luz detectada: más luz, tono más agudo y más fuerte.

El nombre viene del *photophone*, el aparato que Alexander Graham Bell patentó en 1880 para transmitir voz sobre un haz de luz. Esta app recorre el camino contrario: toma la luz y la convierte en sonido.

Todo el procesamiento ocurre en el dispositivo. La app no accede a internet ni guarda imágenes: los fotogramas de la cámara se analizan en memoria y se descartan de inmediato.

### Características

- **Realimentación sonora continua.** Pitido generado por síntesis, entre 260 Hz (oscuridad) y 2100 Hz (luz máxima), enrutado como audio de accesibilidad.
- **Lectura hablada del nivel.** El porcentaje de luz y una etiqueta ("muy baja", "baja", "media", "alta", "muy alta") se anuncian por TalkBack.
- **Sensibilidad ajustable** del 5 % al 200 %, para adaptar la respuesta a entornos muy oscuros o muy iluminados.
- **Intervalo del pitido configurable** entre 100 ms y 10 000 ms.
- **Bloqueo de exposición**, que evita que el ajuste automático de la cámara compense los cambios de luz y aplane las lecturas. Se desactiva solo si el dispositivo no lo admite.
- **Diseño accesible:** controles de 56 dp, texto grande, etiquetas TalkBack en todos los controles y anuncios al cambiar de estado. Los ajustes se conservan entre sesiones.

### Requisitos

- Android 6.0 (API 23) o superior. Hasta ahora la app solo se ha probado en Android 16; en versiones anteriores debería funcionar, pero no está verificado.
- Un dispositivo con cámara. Se prefiere la trasera; si no hay, se usa la primera disponible.
- Permiso de cámara, que la app solicita al iniciar la detección.

### Compilación

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

### Cómo funciona

La app abre una sesión de Camera2 a una resolución baja (máximo 640×480) y promedia el plano de luminancia (Y) de cada fotograma `YUV_420_888`, muestreando una rejilla de píxeles en lugar de la imagen completa. Ese valor medio se escala con la sensibilidad elegida y se convierte en frecuencia y volumen. Un hilo de audio independiente sintetiza cada pitido como una onda senoidal con rampas de entrada y salida y lo escribe en un `AudioTrack`, de modo que la cadencia del sonido no depende de la velocidad de captura de la cámara.

Está escrita en Java con las APIs del framework de Android, sin AndroidX, Compose ni CameraX, para mantener el APK pequeño y sin dependencias externas.

### Licencia

Distribuido bajo la licencia Apache 2.0. Consulta el archivo [LICENSE](LICENSE).

---

## English

Accessible Android app that turns ambient light into sound, designed for blind and low-vision users. The app measures brightness through the back camera and plays a beep whose pitch and volume rise with the amount of light detected: more light means a higher, louder tone.

The name comes from the *photophone*, the device Alexander Graham Bell patented in 1880 to transmit voice over a beam of light. This app walks the opposite path: it takes light and turns it into sound.

All processing happens on the device. The app never accesses the internet and never stores images: camera frames are analyzed in memory and discarded immediately.

> **Note:** the app's user interface is currently available in Spanish only.

### Features

- **Continuous audio feedback.** Synthesized beep between 260 Hz (darkness) and 2100 Hz (maximum light), routed as accessibility audio.
- **Spoken level readout.** The light percentage and a label ("very low", "low", "medium", "high", "very high" — in Spanish) are announced through TalkBack.
- **Adjustable sensitivity** from 5% to 200%, to adapt the response to very dark or very bright environments.
- **Configurable beep interval** between 100 ms and 10,000 ms.
- **Exposure lock**, which prevents the camera's auto-exposure from compensating for light changes and flattening the readings. It disables itself only if the device does not support it.
- **Accessible design:** 56 dp touch targets, large text, TalkBack labels on every control and announcements on state changes. Settings persist across sessions.

### Requirements

- Android 6.0 (API 23) or later. So far the app has only been tested on Android 16; it should work on earlier versions, but this is unverified.
- A device with a camera. The back camera is preferred; if there is none, the first available camera is used.
- Camera permission, which the app requests when detection starts.

### Building

You need a JDK 17–21 and the Android SDK with the API 36 platform and build-tools 36.0.0. Note: Gradle 8.13 does not support JDK 24, which fails with `Unsupported class file major version 68`. If you have Android Studio installed, its bundled JDK works:

```bash
export JAVA_HOME="/path/to/Android Studio/jbr"
```

Create a `local.properties` file in the project root pointing to your SDK:

```properties
sdk.dir=/path/to/your/Android/Sdk
```

Then, from the project root:

```bash
./gradlew lintDebug assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

If you build from WSL, use the helper script `skills/android-app-maintainer/scripts/build_local.sh`, which temporarily points `local.properties` at a Linux SDK and JDK and restores it on exit. The Windows build tools do not run reliably from WSL.

### How it works

The app opens a Camera2 session at a low resolution (at most 640×480) and averages the luminance (Y) plane of each `YUV_420_888` frame, sampling a grid of pixels instead of the full image. That average is scaled by the chosen sensitivity and converted into frequency and volume. An independent audio thread synthesizes each beep as a sine wave with fade-in/fade-out ramps and writes it to an `AudioTrack`, so the sound cadence does not depend on the camera's capture rate.

It is written in Java using only Android framework APIs — no AndroidX, Compose or CameraX — to keep the APK small and dependency-free.

### License

Distributed under the Apache 2.0 license. See the [LICENSE](LICENSE) file.
