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

    // 1. Remove bad DialogType import
    if (content.includes('import com.example.android.core.view.DialogType')) {
        content = content.replace(/import com\.example\.android\.core\.view\.DialogType\r?\n/g, '');
        changed = true;
    }

    // 2. Remove duplicate Gesto import in GestureAdapter
    if (file.endsWith('GestureAdapter.kt')) {
        content = content.replace(/import com\.example\.android\.db\.Gesto\r?\n/g, '');
        changed = true;
    }

    // 3. Fix IconPickerAdapter in SecuenciaConfigActivity
    if (file.endsWith('SecuenciaConfigActivity.kt')) {
        if (!content.includes('import com.example.android.core.ui.adapters.IconPickerAdapter')) {
            content = content.replace(/^package .*$/m, `$&` + '\nimport com.example.android.core.ui.adapters.IconPickerAdapter');
            changed = true;
        }
    }

    // 4. Fix LugaresActivity in HomeFragment
    if (file.endsWith('HomeFragment.kt')) {
        if (!content.includes('import com.example.android.feature.home.LugaresActivity')) {
            content = content.replace(/^package .*$/m, `$&` + '\nimport com.example.android.feature.home.LugaresActivity');
            changed = true;
        }
    }

    // 5. Fix AddGestoAdapter in GestosFragment
    if (file.endsWith('GestosFragment.kt')) {
        if (!content.includes('import com.example.android.core.ui.adapters.AddGestoAdapter')) {
            content = content.replace(/^package .*$/m, `$&` + '\nimport com.example.android.core.ui.adapters.AddGestoAdapter');
            changed = true;
        }
    }

    if (changed) {
        fs.writeFileSync(file, content);
    }
});

console.log('Final specific fixes applied.');
