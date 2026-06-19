#!/bin/sh

# Generar un archivo JSON con la lista de APKs disponibles
echo "[" > /usr/share/nginx/html/downloads.json

first=true
for file in /usr/share/nginx/html/downloads/*.apk; do
    [ -e "$file" ] || continue
    basename=$(basename "$file")
    
    # Evitar duplicar el "latest" en la lista detallada si quieres
    if [ "$basename" = "app-latest.apk" ]; then
        continue
    fi

    if [ "$first" = true ]; then
        first=false
    else
        echo "," >> /usr/share/nginx/html/downloads.json
    fi
    
    echo "  \"$basename\"" >> /usr/share/nginx/html/downloads.json
done

echo "]" >> /usr/share/nginx/html/downloads.json
