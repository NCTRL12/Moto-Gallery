# Galería (Moto Gallery)

Una galería de fotos y vídeos para Android, pensada para móviles Motorola que
vienen sin app de galería propia. Lee lo que ya hay en el teléfono (MediaStore):
no copia, no sube nada a internet y no pide ningún permiso de red.

## Qué hace

- **Fotos**: todas las imágenes y vídeos del teléfono, en rejilla y agrupados por día.
- **Álbumes**: las carpetas reales del móvil (Cámara, WhatsApp, Descargas, capturas…).
- **Favoritos**: marca con ♥ lo que quieras tener a mano.
- **Visor a pantalla completa**: desliza para pasar, pellizca o toca dos veces para ampliar.
- **Vídeo**: se reproduce dentro de la app, con controles.
- **Selección múltiple**: mantén pulsada una foto para seleccionar varias y compartir o borrar de golpe.
- **Compartir, abrir con, usar como (fondo de pantalla), detalles y borrar**.
- **Tema claro/oscuro** automático y colores dinámicos en Android 12+.

Requisitos: Android 7.0 (API 24) o superior.

## Cómo conseguir el APK

El APK se compila solo en GitHub Actions cada vez que se sube un cambio.

1. Entra en la pestaña **Actions** del repositorio.
2. Abre la ejecución más reciente del workflow **Build APK**.
3. Abajo, en **Artifacts**, descarga `galeria-apk` (es un .zip).
4. Dentro tienes dos archivos:
   - `galeria.apk` → el que conviene instalar (versión release, más pequeña).
   - `galeria-debug.apk` → la versión de depuración, se puede instalar a la vez
     que la otra porque usa otro identificador.

## Cómo instalarlo en el Motorola

1. Pasa el `.apk` al teléfono (cable, Drive, Telegram contigo mismo, lo que uses).
2. Ábrelo desde el gestor de archivos.
3. Android pedirá permitir **"Instalar apps desconocidas"** para la app desde la
   que lo abres (Archivos, Chrome…). Actívalo y vuelve atrás.
4. Instala y abre **Galería**. Al arrancar pedirá permiso para ver fotos y vídeos.

> Si al instalar una versión nueva sale *"La aplicación no se ha instalado"*,
> desinstala primero la anterior. Pasa cuando el APK se firmó con una clave
> distinta (ver más abajo).

## Firma estable (opcional, para actualizar sin desinstalar)

Sin configuración, cada compilación se firma con la clave de depuración que genera
el runner, que cambia en cada ejecución. Para que las actualizaciones se instalen
encima de la versión anterior, crea un keystore propio una vez:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias galeria
base64 -w0 release.jks   # copia el resultado
```

Y añade en **Settings → Secrets and variables → Actions** del repositorio:

| Secret | Valor |
| --- | --- |
| `KEYSTORE_BASE64` | la salida del `base64` de arriba |
| `KEYSTORE_PASSWORD` | la contraseña del keystore |
| `KEY_ALIAS` | `galeria` |
| `KEY_PASSWORD` | la contraseña de la clave |

Guarda el `release.jks` en un sitio seguro: si se pierde, hay que desinstalar y
reinstalar la app para volver a actualizarla.

## Compilar en local

Hace falta el SDK de Android (Android Studio o las command line tools):

```bash
./gradlew assembleRelease
# APK en app/build/outputs/apk/release/app-release.apk
```

## Estructura

```
app/src/main/java/com/nctrl/motogallery/
├── GalleryApp.kt            Application + carga de miniaturas (Coil)
├── MainActivity.kt          Punto de entrada
├── data/                    MediaStore, modelo y favoritos
├── ui/                      ViewModel, navegación y borrado
│   ├── screens/             Rejilla, álbumes, visor, permisos, detalles
│   └── theme/               Colores Material 3
└── util/                    Permisos, compartir/borrar, formatos
```
