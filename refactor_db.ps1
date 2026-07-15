$BasePath = "D:\DESARROLLO\UTL\SPRM\android\app\src\main\java"
$DbPath = "$BasePath\com\example\android\core\db"

$DaoFiles = "CasaDao.kt", "DispositivoDao.kt", "GestoDao.kt", "GestoDetalleDao.kt", "HabitacionDao.kt", "UsuarioDao.kt"
$ModelFiles = "AparatoTipo.kt", "Casa.kt", "CatalogoGesto.kt", "Dispositivo.kt", "Gesto.kt", "GestoDetalle.kt", "GestoPaso.kt", "Habitacion.kt", "Usuario.kt"
$InitFiles = "AppDatabase.kt"

New-Item -Path "$DbPath\dao" -ItemType Directory -Force | Out-Null
New-Item -Path "$DbPath\models" -ItemType Directory -Force | Out-Null
New-Item -Path "$DbPath\init" -ItemType Directory -Force | Out-Null

foreach ($file in $DaoFiles) {
    $src = "$DbPath\$file"
    if (Test-Path $src) {
        Move-Item -Path $src -Destination "$DbPath\dao\$file" -Force
        $content = [System.IO.File]::ReadAllText("$DbPath\dao\$file")
        $content = $content -replace "package com\.example\.android\.core\.db", "package com.example.android.core.db.dao`r`n`r`nimport com.example.android.core.db.models.*"
        [System.IO.File]::WriteAllText("$DbPath\dao\$file", $content)
    }
}

foreach ($file in $ModelFiles) {
    $src = "$DbPath\$file"
    if (Test-Path $src) {
        Move-Item -Path $src -Destination "$DbPath\models\$file" -Force
        $content = [System.IO.File]::ReadAllText("$DbPath\models\$file")
        $content = $content -replace "package com\.example\.android\.core\.db", "package com.example.android.core.db.models"
        [System.IO.File]::WriteAllText("$DbPath\models\$file", $content)
    }
}

foreach ($file in $InitFiles) {
    $src = "$DbPath\$file"
    if (Test-Path $src) {
        Move-Item -Path $src -Destination "$DbPath\init\$file" -Force
        $content = [System.IO.File]::ReadAllText("$DbPath\init\$file")
        $content = $content -replace "package com\.example\.android\.core\.db", "package com.example.android.core.db.init`r`n`r`nimport com.example.android.core.db.models.*`r`nimport com.example.android.core.db.dao.*"
        [System.IO.File]::WriteAllText("$DbPath\init\$file", $content)
    }
}

$ktFiles = Get-ChildItem -Path $BasePath -Filter "*.kt" -Recurse

foreach ($ktFile in $ktFiles) {
    $content = [System.IO.File]::ReadAllText($ktFile.FullName)
    $originalContent = $content
    
    foreach ($model in $ModelFiles) {
        $className = $model -replace "\.kt", ""
        $content = $content -replace "import com\.example\.android\.core\.db\.$className\b", "import com.example.android.core.db.models.$className"
    }
    foreach ($dao in $DaoFiles) {
        $className = $dao -replace "\.kt", ""
        $content = $content -replace "import com\.example\.android\.core\.db\.$className\b", "import com.example.android.core.db.dao.$className"
    }
    foreach ($init in $InitFiles) {
        $className = $init -replace "\.kt", ""
        $content = $content -replace "import com\.example\.android\.core\.db\.$className\b", "import com.example.android.core.db.init.$className"
    }
    
    if ($content -cne $originalContent) {
        [System.IO.File]::WriteAllText($ktFile.FullName, $content)
    }
}
Write-Output "Refactoring complete."
