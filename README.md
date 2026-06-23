# Manordomo — Android App

Aplicación Android del sistema **Manordomo** (SPRM-UTL). Permite controlar dispositivos del hogar inteligente: luces, sensores, cámaras, y más, a través de gestos y comandos desde el teléfono.

---

## 📦 Deploy en Render (Docker)

El proyecto usa una imagen Docker de dos fases:

1. **Fase Build** — JDK 17 + Android SDK compila el APK.
2. **Fase Serve** — Nginx sirve el APK y una interfaz web de descarga.

### Variables de entorno requeridas en Render

Configura estas variables en **Environment > Build Arguments** de tu servicio Docker en Render:

| Variable | Descripción | Ejemplo |
|---|---|---|
| `ORG_GRADLE_PROJECT_API_BASE_URL` | URL base del backend (SPRM API) | `https://mi-backend.onrender.com/` |
| `BUILD_VERSION` | Versión semántica del build (se incluye en el nombre del APK) | `1.2.0` |

> **Nota:** Las variables `ORG_GRADLE_PROJECT_*` son reconocidas automáticamente por Gradle como project properties, lo que permite que `build.gradle.kts` las lea sin configuración adicional.

### ¿Cómo se resuelve `BASE_URL` en Gradle?

El [`app/build.gradle.kts`](app/build.gradle.kts) busca la URL en este orden de prioridad:

```
1. Gradle project property  →  -PAPI_BASE_URL=...
2. Variable de entorno      →  ORG_GRADLE_PROJECT_API_BASE_URL
3. local.properties         →  API_BASE_URL=...
4. Valor por defecto        →  http://0.0.0.0:5295/
```

Para desarrollo local, crea o edita `local.properties` (no se sube al repo):

```properties
API_BASE_URL=http://192.168.1.100:5295/
```

---

## 🔖 Historial de versiones

Cada build en Render genera **dos archivos**:

| Archivo | Descripción |
|---|---|
| `app-latest.apk` | Siempre apunta al APK más reciente |
| `app-v{VERSION}_{YYYYMMDD}_{HHMMSS}.apk` | Copia versionada para el historial |

> ⚠️ **Render es efímero**: los archivos históricos dentro del contenedor se pierden con cada nuevo despliegue. Para historial persistente usa un **Disk** de Render (montado en `/usr/share/nginx/html/downloads`) o sube los APKs a un bucket S3/Cloudflare R2.

---

## 🏗️ Estructura del proyecto web

```
android/
├── Dockerfile          # Build en 2 fases: compilación + servidor nginx
├── .dockerignore       # Excluye .gradle, .idea, logs, etc. del contexto
├── index.html          # Interfaz web de descargas (nginx sirve esto)
├── generate-list.sh    # Genera downloads.json con la lista de APKs
├── app/
│   └── build.gradle.kts   # Configuración de build (lee variables de entorno)
└── ...
```

---

## 💻 Desarrollo local

### Requisitos

- Android Studio (o IntelliJ IDEA con plugin de Android)
- JDK 17+
- Android SDK (API 29 mínimo, API 36 objetivo)

### Setup

```bash
# 1. Clonar el repositorio
git clone https://github.com/SPRM-UTL/android.git

# 2. Crear local.properties con la URL del backend
echo "API_BASE_URL=http://10.0.2.2:5295/" >> local.properties

# 3. Compilar y ejecutar desde Android Studio
#    o desde línea de comandos:
./gradlew assembleDebug
```

> `10.0.2.2` es la IP del host desde el emulador de Android.

---

## 🔒 Debug vs Release

Actualmente el Dockerfile compila en modo **Debug** (`assembleDebug`), lo cual no requiere keystore. Para distribución en producción mediante Google Play o firma propia, se necesita configurar un keystore y usar `assembleRelease`.

---

## 🧰 Stack de tecnologías

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + XML Views |
| Arquitectura | MVVM |
| Red | Retrofit 2 + OkHttp |
| Base de datos local | Room |
| Cámara | CameraX |
| Detección de gestos | MediaPipe |
| QR | ZXing (journeyapps) |
| Build | Gradle 8 + KSP |
| CI/CD | Docker + Render |
