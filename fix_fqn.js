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

    const mappings = [
        {
            regex: /(?<!core\.)com\.example\.android\.db/g,
            replacement: 'com.example.android.core.db'
        },
        {
            regex: /(?<!core\.)com\.example\.android\.network/g,
            replacement: 'com.example.android.core.network'
        },
        {
            regex: /(?<!core\.)com\.example\.android\.view/g,
            replacement: 'com.example.android.core.view'
        },
        {
            regex: /(?<!feature\.)com\.example\.android\.ai/g,
            replacement: 'com.example.android.feature.ai'
        },
        {
            regex: /com\.example\.android\.ui\.HomeActivity/g,
            replacement: 'com.example.android.feature.home.HomeActivity'
        },
        {
            regex: /com\.example\.android\.ui\.LugaresActivity/g,
            replacement: 'com.example.android.feature.home.LugaresActivity'
        },
        {
            regex: /com\.example\.android\.ui\.adapters/g,
            replacement: 'com.example.android.core.ui.adapters'
        },
        {
            regex: /(?<!feature\.)com\.example\.android\.device/g,
            replacement: 'com.example.android.feature.device'
        },
        // HandMetrics PUÑO fix
        {
            regex: /PUO/g,
            replacement: 'PUNO'
        },
        {
            regex: /PUO/g,
            replacement: 'PUNO'
        },
        {
            regex: /PU\?O/g,
            replacement: 'PUNO'
        },
        {
            regex: /PUÑO/g,
            replacement: 'PUNO'
        },
        // HandMetrics estático fix
        {
            regex: /esttico/g,
            replacement: 'estatico'
        }
    ];

    mappings.forEach(m => {
        if (m.regex.test(content)) {
            content = content.replace(m.regex, m.replacement);
            changed = true;
        }
    });

    if (changed) {
        fs.writeFileSync(file, content);
    }
});

console.log('Fixed fully qualified names safely.');
