const fs = require('fs');
const path = require('path');

const srcDir = path.join(__dirname, 'app/src/main/java/com/example/android/ai');
const destDir = path.join(__dirname, 'app/src/main/java/com/example/android/feature/ai');

if (!fs.existsSync(destDir)) {
    fs.mkdirSync(destDir, { recursive: true });
}

function getFiles(dir, fileList = []) {
    if (!fs.existsSync(dir)) return fileList;
    const files = fs.readdirSync(dir);
    for (const file of files) {
        const filePath = path.join(dir, file);
        if (fs.statSync(filePath).isDirectory()) {
            getFiles(filePath, fileList);
        } else if (filePath.endsWith('.kt') || filePath.endsWith('.xml')) {
            fileList.push(filePath);
        }
    }
    return fileList;
}

const aiFiles = getFiles(srcDir);

aiFiles.forEach(file => {
    const relativePath = path.relative(srcDir, file);
    const destFile = path.join(destDir, relativePath);
    
    // Ensure dest directory exists
    const fileDestDir = path.dirname(destFile);
    if (!fs.existsSync(fileDestDir)) {
        fs.mkdirSync(fileDestDir, { recursive: true });
    }

    let content = fs.readFileSync(file, 'utf8');
    
    // Fix package name
    if (file.endsWith('.kt')) {
        content = content.replace(/^package com\.example\.android\.ai/m, 'package com.example.android.feature.ai');
    }

    fs.writeFileSync(destFile, content, 'utf8');
});

// Delete old directory
fs.rmSync(srcDir, { recursive: true, force: true });
console.log('Restored feature/ai files with correct UTF-8 encoding and package names.');
