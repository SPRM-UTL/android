# --- Fase 1: Compilación del APK ---
FROM eclipse-temurin:17-jdk-jammy AS build

# Configurar variables de entorno para el SDK de Android
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Instalar dependencias necesarias
RUN apt-get update && apt-get install -y wget unzip && rm -rf /var/lib/apt/lists/*

# Descargar e instalar Android Command Line Tools
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d $ANDROID_HOME/cmdline-tools \
    && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

# Aceptar licencias de Android SDK
RUN yes | sdkmanager --licenses

# Establecer el directorio de trabajo
WORKDIR /app

# Copiar los archivos del proyecto
COPY . .

# Dar permisos de ejecución al gradle wrapper y compilar el APK en modo Release
RUN chmod +x ./gradlew
RUN ./gradlew assembleRelease


# --- Fase 2: Servidor con Interfaz de Selección ---
FROM nginx:alpine

WORKDIR /usr/share/nginx/html

# Limpiar archivos por defecto de nginx
RUN rm -rf ./*

# Crear carpeta para las descargas de APKs
RUN mkdir -p downloads

# 1. Copiar todo el contenido de la carpeta de outputs de release
COPY --from=build /app/app/build/outputs/apk/release/ .

# 2. Detectar dinámicamente el nombre del APK, renombrarlo a 'app-latest.apk' 
#    y clonarlo con la marca de tiempo para el historial
RUN apk_file=$(ls *.apk | head -n 1) && \
    cp "$apk_file" ./downloads/app-latest.apk && \
    TIMESTAMP=$(date +%Y%m%d_%H%M%S) && \
    cp ./downloads/app-latest.apk ./downloads/app-release_${TIMESTAMP}.apk && \
    rm *.apk

# Copiar el HTML de la interfaz y el script que genera la lista de archivos
COPY index.html .
COPY generate-list.sh .

# Ejecutar el script para indexar los APKs disponibles antes de arrancar Nginx
RUN chmod +x generate-list.sh && ./generate-list.sh

# Exponer el puerto de Nginx
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
