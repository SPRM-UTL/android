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

    if (content.includes('import RetrofitClient')) {
        content = content.replace(/import RetrofitClient/g, 'import com.example.android.core.network.RetrofitClient');
        changed = true;
    }

    if (content.includes('import CustomDialog')) {
        content = content.replace(/import CustomDialog/g, 'import com.example.android.core.view.CustomDialog');
        changed = true;
    }

    if (changed) {
        fs.writeFileSync(file, content);
    }
});

console.log('Fixed broken imports caused by global replace.');
