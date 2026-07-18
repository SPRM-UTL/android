$NetPath = "D:\DESARROLLO\UTL\SPRM\android\app\src\main\java\com\example\android\core\network"
$ktFiles = Get-ChildItem -Path $NetPath -Filter "*.kt" -Recurse

foreach ($f in $ktFiles) {
    $content = [System.IO.File]::ReadAllText($f.FullName)
    $imports = "import com.example.android.core.network.api.*`r`nimport com.example.android.core.network.client.*`r`nimport com.example.android.core.network.bluetooth.*`r`nimport com.example.android.core.network.wifi.*`r`nimport com.example.android.core.network.stream.*"
    
    if ($content -notmatch "import com\.example\.android\.core\.network\.api\.\*") {
        # Using string replace on the first occurrence
        $lines = $content -split "`r`n"
        $newContent = @()
        foreach ($line in $lines) {
            $newContent += $line
            if ($line -match "^package ") {
                $newContent += ""
                $newContent += $imports
            }
        }
        [System.IO.File]::WriteAllText($f.FullName, ($newContent -join "`r`n"))
    }
}
