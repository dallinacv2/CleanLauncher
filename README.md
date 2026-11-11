# Clean Launcher

[![Build APK](https://github.com/jhontandazo008/CleanLauncher/actions/workflows/build.yml/badge.svg)](https://github.com/jhontandazo008/CleanLauncher/actions/workflows/build.yml)

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
  y modo pantalla apagada. Los archivos siguen en el código, pero ya no se inician.
- **Saludo por voz**: unos 2 minutos después de abrir el launcher saluda (según la hora), dice el clima,
  el aviso de aceite si toca, el cinturón y "maneja con cuidado". Solo vuelve a saludar si el radio estuvo
  apagado o dormido 6 horas o más (`DELAY_MS` y `PARKED_MS` en `GreeterService.java`). Baja el volumen de la
  música mientras habla.

### Instalar en el radio sin computadora de desarrollo

1. Descargar el APK directo (siempre la última versión):
   <https://github.com/dallinacv2/CleanLauncher/releases/download/latest/CarLauncher.apk>
2. Abrirlo desde el administrador de archivos del radio, o copiarlo a una memoria USB
   (puede pedir permitir instalar apps de fuentes desconocidas).
3. Para que el launcher use la hora y el clima: dar permiso de **ubicación** cuando lo pida y, en Ajustes del radio,
   activar el **acceso a notificaciones** para "Inicio" (así muestra la canción que suena).

### Actualizaciones dentro de la app

Cada vez que cambia el código, GitHub compila y publica el APK con un número de versión nuevo. La app lo revisa sola
(máximo cada 12 horas, al abrir el launcher) y, si hay uno nuevo, pregunta si quieres instalarlo. También se puede
buscar a mano con una **pulsación larga en el engranaje** del menú de abajo.

Todos los APK se firman con la misma llave, creada la primera vez y guardada en una versión **borrador** llamada
`llave-de-firma` (solo la ve el dueño del repo). **No la borres**: sin ella, Android no deja instalar una versión
encima de otra y habría que desinstalar la app una vez.

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
- **Ecualizador** con balance/fader y ajuste automático por fuente.
- **Dispositivos Bluetooth**: lista de emparejados y conexión.
- **Configuración inicial** para instalaciones compartidas: nombre, foto del carro, voz y permisos.
  En radios que no son NWD se ocultan las funciones que dependen de los servicios de fábrica.
- Cajón de aplicaciones.

## Compilar

No usa Gradle: [`build.sh`](build.sh) llama directamente a `aapt2`, `javac`, `d8`, `zipalign` y
`apksigner`. Funciona en Linux, macOS y Windows (Git Bash).

### Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| JDK | 17 o superior | Sirve el JBR que trae Android Studio |
| Android SDK Platform | `android-33` | Solo para compilar. El APK corre en Android 7.1+ (minSdk 25) |
| Android SDK Build-Tools | 36.0.0 (probado) | El script usa la más nueva instalada |
| Platform-Tools (`adb`) | cualquiera | Solo para `install` |

### Desde una instalación limpia

1. Instala un JDK 17+ y las [herramientas de línea de comandos del Android SDK](https://developer.android.com/studio#command-tools)
   (o Android Studio, que trae ambos).
2. Instala los paquetes del SDK:
   ```sh
   sdkmanager "platforms;android-33" "build-tools;36.0.0" "platform-tools"
   ```
3. Indica dónde está el SDK si no está en la ruta por defecto de Android Studio:
   ```sh
   export ANDROID_HOME=$HOME/Android/Sdk
   ```
4. Compila:
   ```sh
   sh build.sh                            # genera build/CleanLauncher.apk
   sh build.sh install                    # además lo instala con adb
   RADIO=192.168.1.15:5555 sh build.sh install   # radio por ADB Wi-Fi
   ```

Si falta algo, el script se detiene y dice qué instalar. Detecta el SDK con `ANDROID_HOME` o
`ANDROID_SDK_ROOT` (o las rutas por defecto de Android Studio) y el JDK con `JAVA_HOME`, `javac`
en el `PATH` o el JBR de Android Studio. Variables opcionales: `ANDROID_PLATFORM`, `BUILD_TOOLS`,
`KEYSTORE`, `KEYSTORE_PASS`, `KEY_ALIAS`.
`VARIANT=share sh build.sh` genera `CleanLauncher-compartir.apk`, una versión para regalar sin las fotos
del carro ni las voces grabadas: usa la foto del usuario y el texto a voz del equipo. Si no existe `~/.android/debug.keystore`, el script lo crea.

### Integración continua

Cada push a `main` y cada pull request se compila en GitHub Actions
([`.github/workflows/build.yml`](.github/workflows/build.yml)). El APK queda como artefacto
descargable en la pestaña **Actions** del repo. Está firmado con una clave de debug.

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
