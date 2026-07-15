$ktFiles = Get-ChildItem -Path 'D:\DESARROLLO\UTL\SPRM\android\app\src\main\java' -Filter '*.kt' -Recurse
foreach ($f in $ktFiles) {
    $content = [System.IO.File]::ReadAllText($f.FullName)
    $newContent = $content -replace 'com\.example\.android\.core\.db\.GuardarConfiguracionGestosDto', 'com.example.android.core.db.models.GuardarConfiguracionGestosDto'
    if ($content -cne $newContent) {
        [System.IO.File]::WriteAllText($f.FullName, $newContent)
    }
}
Write-Output "Fix complete."
