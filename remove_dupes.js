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
    const lines = content.split(/\r?\n/);
    const seenImports = new Set();
    const newLines = [];
    let changed = false;

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.startsWith('import ')) {
            if (seenImports.has(line)) {
                changed = true; // Duplicate found, ignore it
            } else {
                seenImports.add(line);
                newLines.push(line);
            }
        } else {
            newLines.push(line);
        }
    }

    if (changed) {
        fs.writeFileSync(file, newLines.join('\n'));
    }
});

console.log('Removed duplicate imports.');
