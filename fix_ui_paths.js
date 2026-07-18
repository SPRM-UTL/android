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

    // Remaining ui fixes
    const replacements = [
        ['com.example.android.ui.auth.InitialSetupActivity', 'com.example.android.feature.auth.InitialSetupActivity'],
        ['com.example.android.ui.HomeActivity', 'com.example.android.feature.home.HomeActivity'],
        ['com.example.android.ui.LugaresActivity', 'com.example.android.feature.home.LugaresActivity'],
        ['com.example.android.ui.adapters', 'com.example.android.core.ui.adapters'],
        // For PermisosActivity specifically:
        ['com.example.android.core.network.RetrofitClient', 'RetrofitClient'], // use just RetrofitClient and rely on import
        ['com.example.android.core.view.CustomDialog', 'CustomDialog'],
        // Clean up explicit imports that are wrong
        ['import com.example.android.core.view.DialogType\n', '']
    ];

    replacements.forEach(([oldStr, newStr]) => {
        const regex = new RegExp(oldStr.replace(/\./g, '\\.'), 'g');
        if (regex.test(content)) {
            content = content.replace(regex, newStr);
            changed = true;
        }
    });

    if (changed) {
        fs.writeFileSync(file, content);
    }
});

console.log('Fixed remaining ui paths');
