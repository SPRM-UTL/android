$icons = @(
    @{ Name="bot"; Out="ic_input_add.xml" },
    @{ Name="speaker"; Out="speaker.xml" },
    @{ Name="headphones"; Out="headphones.xml" },
    @{ Name="lightbulb"; Out="lightbulb.xml" },
    @{ Name="zap"; Out="ic_voltage.xml" },
    @{ Name="activity"; Out="ic_current.xml" },
    @{ Name="plug-zap"; Out="ic_power_2.xml" },
    @{ Name="calendar-days"; Out="ic_consumption_today.xml" },
    @{ Name="bar-chart-2"; Out="ic_consumption_total.xml" }
)

foreach ($icon in $icons) {
    $url = "https://unpkg.com/lucide-static@latest/icons/$($icon.Name).svg"
    $svgFile = "$($icon.Name).svg"
    $outFile = "app\src\main\res\drawable\$($icon.Out)"
    
    Write-Host "Downloading $url..."
    Invoke-WebRequest -Uri $url -OutFile $svgFile
    
    Write-Host "Converting $svgFile to $outFile..."
    npx -y svg2vectordrawable -i $svgFile -o $outFile
    
    Remove-Item $svgFile
}
Write-Host "Done!"
