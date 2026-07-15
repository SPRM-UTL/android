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

    // 1. Remove the invalid ViewHolder import globally
    if (content.includes('import com.example.android.feature.device.ViewHolder')) {
        content = content.replace(/import com\.example\.android\.feature\.device\.ViewHolder\r?\n/g, '');
        changed = true;
    }

    // 2. Fix old db and network imports globally
    if (/import com\.example\.android\.db\./.test(content)) {
        content = content.replace(/import com\.example\.android\.db\./g, 'import com.example.android.core.db.');
        changed = true;
    }
    
    if (/import com\.example\.android\.network\./.test(content)) {
        content = content.replace(/import com\.example\.android\.network\./g, 'import com.example.android.core.network.');
        changed = true;
    }

    if (changed) {
        fs.writeFileSync(file, content);
    }
});

console.log('Final import cleanup done.');
