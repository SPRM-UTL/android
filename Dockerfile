# --- Fase 1: Compilación del APK ---
FROM eclipse-temurin:17-jdk-jammy AS build

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN apt-get update && apt-get install -y wget unzip && rm -rf /var/lib/apt/lists/*

RUN mkdir -p $ANDROID_HOME/cmdline-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d $ANDROID_HOME/cmdline-tools \
    && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses

WORKDIR /app
COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew assembleRelease

# --- Fase 2: Servidor con Interfaz de Selección ---
FROM nginx:alpine

WORKDIR /usr/share/nginx/html

# Limpiar archivos por defecto de nginx
RUN rm -rf ./*

# Crear carpeta para las descargas de APKs
RUN mkdir -p downloads

# Copiar el APK recién compilado con un nombre único basado en la fecha del despliegue
# Nota: Si usas almacenamiento persistente en Render, se irán acumulando aquí.
RUN TIMESTAMP=$(date +%Y%m%d_%H%M%S) && \
    cp /app/app/build/outputs/apk/release/app-release.apk ./downloads/app-release_${TIMESTAMP}.apk && \
    cp /app/app/build/outputs/apk/release/app-release.apk ./downloads/app-latest.apk

# Copiar el HTML de la interfaz y el script que genera la lista de archivos
COPY index.html .
COPY generate-list.sh .

# Ejecutar el script para indexar los APKs disponibles antes de arrancar Nginx
RUN chmod +x generate-list.sh && ./generate-list.sh

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
