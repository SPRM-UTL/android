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
        ['import com.example.android.actions.', 'import com.example.android.core.actions.'],
        ['import com.example.android.ui.adapters.', 'import com.example.android.core.ui.adapters.'],
        ['import com.example.android.ui.auth.', 'import com.example.android.feature.auth.'],
        ['import com.example.android.ui.HomeActivity', 'import com.example.android.feature.home.HomeActivity'],
        ['import com.example.android.ui.LugaresActivity', 'import com.example.android.feature.home.LugaresActivity'],
        ['import com.example.android.ui.', 'import com.example.android.core.ui.'] // Catch-all for remaining ui if any? Wait, this might be risky.
    ];

    replacements.forEach(([oldStr, newStr]) => {
        if (oldStr === 'import com.example.android.ui.') {
            // Only replace if it doesn't match the others already replaced
            const regex = /import com\.example\.android\.ui\.(?!(adapters|auth|HomeActivity|LugaresActivity))/g;
            if (regex.test(content)) {
                content = content.replace(regex, 'import com.example.android.core.ui.');
                changed = true;
            }
        } else {
            const regex = new RegExp(oldStr.replace(/\./g, '\\.'), 'g');
            if (regex.test(content)) {
                content = content.replace(regex, newStr);
                changed = true;
            }
        }
    });

    if (changed) {
        fs.writeFileSync(file, content);
    }
});

console.log('Fixed old import paths');
