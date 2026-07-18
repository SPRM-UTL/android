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

    // Remove legacy old db/network imports that conflict (because auto_import added the correct ones)
    const legacyImportsToRemove = [
        'import com.example.android.db.AppDatabase',
        'import com.example.android.db.Casa',
        'import com.example.android.db.Habitacion',
        'import com.example.android.network.ApiHandler',
        'import com.example.android.network.RetrofitClient',
        'import com.example.android.ui.DeviceAdapter',
        'import com.example.android.ui.AddDeviceAdapter',
        'import com.example.android.ui.BluetoothDeviceAdapter',
        'import com.example.android.ui.GestureAdapter',
        'import com.example.android.ui.HabitacionesEditAdapter',
        'import com.example.android.ui.IconPickerAdapter',
        'import com.example.android.ui.WifiDeviceAdapter',
        'import com.example.android.ui.adapters.AddGestoAdapter',
        'import com.example.android.ui.HomeTutorialHelper',
        'import com.example.android.ui.LugaresActivity',
        'import com.example.android.view.DialogType'
    ];

    legacyImportsToRemove.forEach(imp => {
        if (content.includes(imp)) {
            content = content.replace(new RegExp(imp.replace(/\./g, '\\.') + '(\\r?\\n)?', 'g'), '');
            changed = true;
        }
    });

    // Make sure we have the correct new imports for these specific missing classes in case auto_import missed them
    // Or because removing the old import leaves it unresolved
    const replacements = [
        { old: 'com.example.android.ui.DeviceAdapter', new: 'com.example.android.core.ui.adapters.DeviceAdapter' },
        { old: 'com.example.android.ui.AddDeviceAdapter', new: 'com.example.android.core.ui.adapters.AddDeviceAdapter' },
        { old: 'com.example.android.ui.BluetoothDeviceAdapter', new: 'com.example.android.core.ui.adapters.BluetoothDeviceAdapter' },
        { old: 'com.example.android.ui.GestureAdapter', new: 'com.example.android.core.ui.adapters.GestureAdapter' },
        { old: 'com.example.android.ui.HabitacionesEditAdapter', new: 'com.example.android.core.ui.adapters.HabitacionesEditAdapter' },
        { old: 'com.example.android.ui.IconPickerAdapter', new: 'com.example.android.core.ui.adapters.IconPickerAdapter' },
        { old: 'com.example.android.ui.WifiDeviceAdapter', new: 'com.example.android.core.ui.adapters.WifiDeviceAdapter' },
        { old: 'com.example.android.ui.adapters.AddGestoAdapter', new: 'com.example.android.core.ui.adapters.AddGestoAdapter' },
        { old: 'com.example.android.ui.HomeTutorialHelper', new: 'com.example.android.feature.home.HomeTutorialHelper' },
        { old: 'com.example.android.ui.LugaresActivity', new: 'com.example.android.feature.home.LugaresActivity' },
        { old: 'com.example.android.view.DialogType', new: 'com.example.android.core.view.DialogType' }
    ];

    replacements.forEach(rep => {
        const usageRegex = new RegExp(`\\b${rep.new.split('.').pop()}\\b`);
        if (usageRegex.test(content) && !content.includes(`import ${rep.new}`)) {
            content = content.replace(/^package .*$/m, `$&` + `\nimport ${rep.new}`);
            changed = true;
        }
    });
    
    // Quick fix for the "import com.example.android.feature.network_config.DeviceAdapter" added erroneously by auto_import
    if (content.includes('import com.example.android.feature.network_config.DeviceAdapter')) {
        content = content.replace(/import com\.example\.android\.feature\.network_config\.DeviceAdapter\r?\n/g, '');
        changed = true;
    }

    if (changed) {
        fs.writeFileSync(file, content);
    }
});

console.log('Cleaned up legacy imports and added correct ones');
