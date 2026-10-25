# Clean Launcher

![Clean Launcher en el radio](docs/screenshot.png)

Launcher Android limpio, ligero y a medida para un radio de auto chino **NWD (Allwinner T3, Android 7.1.1)**
instalado en un Chevrolet Epica. Reemplaza el launcher de fábrica con una interfaz limpia y rápida
que se comunica directamente con los servicios propietarios del radio.

## Esta versión (adaptada para Darlin)

Adaptación del proyecto original para un radio Android genérico (Rockchip RK3326, Android 14, pantalla 1280×720)
en un Hyundai, en República Dominicana. Cambios respecto al original:

- Saludo con el nombre **Darlin** y fotos propias del auto (`res/drawable-nodpi/optra*.png`).
- **Luces del auto automáticas por hora**: la imagen con faros encendidos se muestra de 7:00 p. m. a 6:30 a. m.
  (se cambia en `LIGHTS_ON_MIN` y `LIGHTS_OFF_MIN` de `MainActivity.java`).
- **Botones de Navegación, Bluetooth, Radio, Música y Ecualizador abren las apps nativas del radio.**
  Se toca el botón y, la primera vez, pide elegir la app; con **pulsación larga** se cambia después.
- Sin el aviso de *pico y placa* (era de Quito).
- Se dejan de usar las partes exclusivas de los radios NWD: radio FM y Bluetooth por Binder, overlay de volumen,
  modo pantalla apagada y saludo por voz. Los archivos siguen en el código, pero ya no se inician.

### Instalar en el radio sin computadora de desarrollo

1. En GitHub, pestaña **Actions** → **Compilar APK** → última ejecución → **CarLauncher-APK** (descarga un zip con el `.apk`).
2. Copiar `CarLauncher.apk` a una memoria USB y abrirlo desde el administrador de archivos del radio
   (puede pedir permitir instalar apps de fuentes desconocidas).
3. Para que el launcher use la hora y el clima: dar permiso de **ubicación** cuando lo pida y, en Ajustes del radio,
   activar el **acceso a notificaciones** para "Inicio" (así muestra la canción que suena).

**No desinstalar el launcher de fábrica**: así siempre se puede volver a él y se conservan los botones del volante
y la cámara de reversa.

## Funciones

- **Pantalla principal** con hora, clima (Open-Meteo, por GPS o IP) y reproducción actual.
- **Radio FM**: dial, presets y sintonía hablando por Binder con `com.nwd.radio.service`.
- **Bluetooth**: teléfono (marcador, contactos) y música vía `com.bt.bc03`.
- **Overlay de volumen** propio (reemplaza el FloatBar de fábrica).
- **Modo pantalla apagada** que sigue respondiendo a los botones del volante.
- **Control de aceite / odómetro** con recordatorios de mantenimiento.
- **GreeterService**: saludo por voz al encender (voz Salomé, es-CO), avisos de cinturón,
  clima y mantenimiento los lunes. Restaura la fuente de audio
  (radio o BT) al dar contacto.
- Cajón de aplicaciones.

## Compilar

No usa Gradle; `build.sh` llama directamente a `aapt2`, `javac`, `d8` y `apksigner` del Android SDK.

```sh
sh build.sh            # genera build/CarLauncher.apk
sh build.sh install    # además lo instala por ADB Wi-Fi
RADIO=<ip>:5555 sh build.sh install
```

Requisitos: Android SDK (build-tools 36, platform android-33), JDK de Android Studio y
`~/.android/debug.keystore`. Ajusta las rutas al inicio de `build.sh`.

## Integración con el radio NWD

| Qué | Cómo |
|---|---|
| Cambio de fuente de audio | broadcast `com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE` (radio=8, BT música=15, USB=2) |
| Radio | Binder crudo a `com.nwd.radio.service` (`RadioClient`) |
| Bluetooth | Binder crudo a `com.bt.bc03` (`BtClient`) |
| Voz sobre la radio | launcher añadido a `/nwdappconfig/app/NaviSound.ini` |
| No ser cerrado en segundo plano | añadido a `BackgroundManager.ini` y `OneClearConfig.xml` |

## Cambios hechos al radio (todos reversibles)

- Deshabilitado bloatware de fábrica (apps de prueba, DiDi, voz, anuncios CaradService, carstore, Aptoide).
- Deshabilitados componentes del launcher de fábrica: FloatBar, WeatherService, SmallPackageUpdater, AMap APSService.
  **No** se deshabilita `com.android.launcher` completo (aloja el aprendizaje de botones del volante y el apagado de pantalla).
- Animaciones a 0.5x.
- Archivos de configuración modificados: los originales están en [`backup/`](backup/).

Para revertir: copiar los `.bak` de `backup/` a `/nwdappconfig/app/` y volver a habilitar los paquetes con
`adb shell pm enable <paquete>`.
