# MEBUC — Generador de PIN + Buscador (Android / iPhone)

## Cómo funcionan las actualizaciones
Cada vez que subes un cambio a `www/` en GitHub:

**Android (APK)**
1. GitHub compila una APK nueva y la publica en **Releases** (versión 1, 2, 3…).
2. Cuando el usuario abre MEBUC, la app ve que hay una versión nueva y **se bloquea** con
   "Nueva versión disponible". No se puede usar hasta actualizar.
3. Toca **Instalar actualización** → se descarga con barra de progreso → Android la instala encima.
4. La app se cierra, se vuelve a abrir con la versión nueva y borra lo de la versión anterior
   (caché, archivos descargados y datos temporales). Solo conserva los cuadrantes que se hayan
   editado en el Buscador.

El **mensaje del commit** con el que subes el cambio aparece en la app como "novedades".

**iPhone (versión web)**: GitHub Pages publica el cambio y al abrir la app aparece
"Hay una nueva versión disponible → Actualizar ahora". Sube el número de `VERSION` en `www/sw.js`.

## Configuración inicial (una sola vez)
1. Crea un repositorio **público** en GitHub (las apps necesitan poder consultar las Releases sin clave).
2. Sube TODO el contenido de esta carpeta (incluidas las carpetas `.github` y `android`).
   NO subas la llave de firma (`mebuc-firma.jks` ni el .txt de la llave).
3. En el repositorio: **Settings → Secrets and variables → Actions → New repository secret**
   y crea los 2 secretos que están en `LLAVE-DE-FIRMA-NO-SUBIR-A-GITHUB.txt`:
   - `MEBUC_KEYSTORE_PASSWORD`
   - `MEBUC_KEYSTORE_BASE64`
4. **Settings → Pages → Source: GitHub Actions** (para la versión de iPhone).
5. Ve a **Actions → "Publicar actualización Android (APK)" → Run workflow**.
6. Cuando termine, entra a **Releases**, descarga `MEBUC.apk` e instálala en cada celular
   (esta primera instalación es manual). Desde ahí, todo lo demás es automático.

## Guarda la llave de firma
Todas las APK deben firmarse con la misma llave. Si la pierdes, las actualizaciones fallarán y
cada usuario tendría que desinstalar e instalar de nuevo. Guarda `mebuc-firma.jks` y el .txt en
un lugar seguro (Drive personal, USB).

## Lo que Android sí exige (no se puede saltar)
- **La primera vez** que se actualiza, Android pide permitir a MEBUC "instalar apps de esta fuente"
  y confirmar con "Actualizar". La app guía al usuario.
- En Android 12 o superior, desde la segunda actualización normalmente ya no pregunta.
- En algunos celulares (Android 10+), el sistema no deja que la app se reabra sola: en ese caso
  aparece una notificación **"MEBUC actualizada — Toca para abrir"**.

## Cambiar nombre o ícono
- Nombre: `appName` en `capacitor.config.json` y `app_name` en `android/app/src/main/res/values/strings.xml`.
- Ícono: reemplaza `assets/icon-only.png` (1024×1024) y ejecuta
  `npx capacitor-assets generate --android` (o pídele ayuda a Claude). También `www/icon-192.png` y `www/icon-512.png`.
- NO cambies `appId` (`co.mebuc.pin`): se crearía otra app distinta.

## Pestaña IA Policía
- **Android:** abre `https://app.ia.policia.gov.co/login` dentro de la app, en modo escritorio, con cámara
  y micrófono (igual que la app IA Policía original). Arriba está "‹ MEBUC" para volver; el botón atrás
  del celular primero retrocede dentro de la página. La sesión se conserva entre aperturas.
  Código: `android/app/src/main/java/co/mebuc/pin/IaPoliciaActivity.java`
  (si la página se ve cortada o pequeña, cambia `setInitialScale(45)` a 40 o 50).
- **iPhone / web:** abre la página en Safari (el modo escritorio forzado solo existe en la APK).
