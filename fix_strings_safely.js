const fs = require('fs');
const path = require('path');

const baseDir = path.join(__dirname, 'app/src/main/java/com/example/android');

function getFiles(dir, ext, fileList = []) {
    if (!fs.existsSync(dir)) return fileList;
    const files = fs.readdirSync(dir);
    for (const file of files) {
        const filePath = path.join(dir, file);
        if (fs.statSync(filePath).isDirectory()) {
            getFiles(filePath, ext, fileList);
        } else if (filePath.endsWith(ext)) {
            fileList.push(filePath);
        }
    }
    return fileList;
}

const ktFiles = getFiles(baseDir, '.kt');

ktFiles.forEach(file => {
    let content = fs.readFileSync(file, 'utf8');
    let changed = false;

    const replacements = [
        ['com.example.android.network.BluetoothController', 'com.example.android.core.network.BluetoothController'],
        ['com.example.android.ui.IconPickerAdapter', 'com.example.android.core.ui.adapters.IconPickerAdapter'],
        ['com.example.android.ui.LugaresActivity', 'com.example.android.feature.home.LugaresActivity'],
        ['com.example.android.ui.adapters.AddGestoAdapter', 'com.example.android.core.ui.adapters.AddGestoAdapter']
    ];

    replacements.forEach(([oldStr, newStr]) => {
        if (content.includes(oldStr)) {
            content = content.replace(new RegExp(oldStr.replace(/\./g, '\\.'), 'g'), newStr);
            changed = true;
        }
    });

    if (changed) {
        fs.writeFileSync(file, content);
    }
});

console.log('Fixed specific strings safely.');
