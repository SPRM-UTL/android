$BasePath = "D:\DESARROLLO\UTL\SPRM\android\app\src\main\java"
$NetPath = "$BasePath\com\example\android\core\network"

$ApiFiles = "CasaApiService.kt", "DeviceApiService.kt", "GestureApiService.kt", "HabitacionApiService.kt", "ApiHandler.kt"
$ClientFiles = "RetrofitClient.kt", "SocketClient.kt"
$BluetoothFiles = "BluetoothController.kt", "BluetoothScanManager.kt"
$WifiFiles = "WifiScanManager.kt", "DeviceConnectionManager.kt"
$StreamFiles = "MjpegStreamReader.kt"

New-Item -Path "$NetPath\api" -ItemType Directory -Force | Out-Null
New-Item -Path "$NetPath\client" -ItemType Directory -Force | Out-Null
New-Item -Path "$NetPath\bluetooth" -ItemType Directory -Force | Out-Null
New-Item -Path "$NetPath\wifi" -ItemType Directory -Force | Out-Null
New-Item -Path "$NetPath\stream" -ItemType Directory -Force | Out-Null

$FilesMap = @{
    "api" = $ApiFiles
    "client" = $ClientFiles
    "bluetooth" = $BluetoothFiles
    "wifi" = $WifiFiles
    "stream" = $StreamFiles
}

# The classes inside the files that we need to replace
$ClassesMap = @{
    "api" = @("CasaApiService", "DeviceApiService", "GestureApiService", "HabitacionApiService", "ApiHandler", "WsStatusResponse", "WsStatusAllResponse", "AparatoEstadoResponse", "AparatoMensajeResponse", "ToggleAparatoResponse", "AparatoConsumoResponse", "AparatoConsumoActualResponse", "AparatoConsumoPuntoResponse", "AparatoConsumoResumenResponse", "EstadoLocalRequest")
    "client" = @("RetrofitClient", "SocketClient", "ApiResponse", "LoginRequest", "LoginResponse", "UserData", "RegisterRequest", "RegisterResponse", "RegisterData", "UsuarioDataResponse", "UserProfileData", "UpdateUserRequest", "ProfileImageUploadResponse", "ProfileImageUploadData", "ConfiguracionRedRequest", "ConfiguracionRedResponse", "UsuarioVozConfigDto", "AuthApiService")
    "bluetooth" = @("BluetoothController", "BluetoothScanManager", "ResultadoDispositivoBt")
    "wifi" = @("WifiScanManager", "DeviceConnectionManager")
    "stream" = @("MjpegStreamReader")
}

foreach ($folder in $FilesMap.Keys) {
    $files = $FilesMap[$folder]
    foreach ($file in $files) {
        $src = "$NetPath\$file"
        if (Test-Path $src) {
            Move-Item -Path $src -Destination "$NetPath\$folder\$file" -Force
            $content = [System.IO.File]::ReadAllText("$NetPath\$folder\$file")
            $content = $content -replace "(?m)^package com\.example\.android\.core\.network\s*$", "package com.example.android.core.network.$folder"
            [System.IO.File]::WriteAllText("$NetPath\$folder\$file", $content)
        }
    }
}

$ktFiles = Get-ChildItem -Path $BasePath -Filter "*.kt" -Recurse

foreach ($ktFile in $ktFiles) {
    $content = [System.IO.File]::ReadAllText($ktFile.FullName)
    $originalContent = $content
    
    foreach ($folder in $ClassesMap.Keys) {
        $classes = $ClassesMap[$folder]
        foreach ($className in $classes) {
            $content = $content -replace "com\.example\.android\.core\.network\.$className\b", "com.example.android.core.network.$folder.$className"
        }
    }
    
    if ($content -cne $originalContent) {
        [System.IO.File]::WriteAllText($ktFile.FullName, $content)
    }
}
Write-Output "Refactoring network complete."
