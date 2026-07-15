$BasePath = "D:\DESARROLLO\UTL\SPRM\android\app\src\main\java"

$DaoFiles = "CasaDao.kt", "DispositivoDao.kt", "GestoDao.kt", "GestoDetalleDao.kt", "HabitacionDao.kt", "UsuarioDao.kt"
$ModelFiles = "AparatoTipo.kt", "Casa.kt", "CatalogoGesto.kt", "Dispositivo.kt", "Gesto.kt", "GestoDetalle.kt", "GestoPaso.kt", "Habitacion.kt", "Usuario.kt"
$InitFiles = "AppDatabase.kt"

$ktFiles = Get-ChildItem -Path $BasePath -Filter "*.kt" -Recurse

foreach ($ktFile in $ktFiles) {
    # We want to replace in all files, including db files because AppDatabase.kt might have com.example.android.core.db.Usuario
    $content = [System.IO.File]::ReadAllText($ktFile.FullName)
    $originalContent = $content
    
    foreach ($model in $ModelFiles) {
        $className = $model -replace "\.kt", ""
        # We need to make sure we don't replace if it's already .models. or .dao. or .init.
        # But if it's "com.example.android.core.db.models.AparatoTipo", the regex "com\.example\.android\.core\.db\.$className\b" won't match, 
        # it only matches exactly "com.example.android.core.db.AparatoTipo". So it's safe.
        $content = $content -replace "com\.example\.android\.core\.db\.$className\b", "com.example.android.core.db.models.$className"
    }
    foreach ($dao in $DaoFiles) {
        $className = $dao -replace "\.kt", ""
        $content = $content -replace "com\.example\.android\.core\.db\.$className\b", "com.example.android.core.db.dao.$className"
    }
    foreach ($init in $InitFiles) {
        $className = $init -replace "\.kt", ""
        $content = $content -replace "com\.example\.android\.core\.db\.$className\b", "com.example.android.core.db.init.$className"
    }
    
    if ($content -cne $originalContent) {
        [System.IO.File]::WriteAllText($ktFile.FullName, $content)
    }
}
Write-Output "Fix complete."
