const fs = require('fs');
const path = require('path');

const baseDir = path.join(__dirname, 'app/src/main/java/com/example/android');

const filesToUpdate = [
    { name: 'core/ui/adapters/AddDeviceAdapter.kt', pkg: 'com.example.android.core.ui.adapters' },
    { name: 'core/ui/adapters/BluetoothDeviceAdapter.kt', pkg: 'com.example.android.core.ui.adapters' },
    { name: 'core/ui/adapters/DeviceAdapter.kt', pkg: 'com.example.android.core.ui.adapters' },
    { name: 'core/ui/adapters/GestureAdapter.kt', pkg: 'com.example.android.core.ui.adapters' },
    { name: 'core/ui/adapters/HabitacionesEditAdapter.kt', pkg: 'com.example.android.core.ui.adapters' },
    { name: 'core/ui/adapters/IconPickerAdapter.kt', pkg: 'com.example.android.core.ui.adapters' },
    { name: 'core/ui/adapters/WifiDeviceAdapter.kt', pkg: 'com.example.android.core.ui.adapters' },
    { name: 'feature/home/LugaresActivity.kt', pkg: 'com.example.android.feature.home' },
    { name: 'feature/home/HomeTutorialHelper.kt', pkg: 'com.example.android.feature.home' }
];

filesToUpdate.forEach(f => {
    const fullPath = path.join(baseDir, f.name);
    if (fs.existsSync(fullPath)) {
        let content = fs.readFileSync(fullPath, 'utf8');
        content = content.replace(/^package .*$/m, `package ${f.pkg}`);
        fs.writeFileSync(fullPath, content);
        console.log(`Updated package for ${f.name}`);
    }
});
