const fs = require('fs');
const path = require('path');

const baseDir = path.join(__dirname, 'app/src/main/java');

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
    // Determine expected package from directory structure
    const relPath = path.relative(baseDir, file); // e.g. com\example\android\feature\ai\HandMetrics.kt
    const expectedPkg = path.dirname(relPath).split(path.sep).join('.');

    let content = fs.readFileSync(file, 'utf8');
    const pkgMatch = content.match(/^package (.*)$/m);
    
    if (pkgMatch) {
        const actualPkg = pkgMatch[1].trim();
        if (actualPkg !== expectedPkg) {
            content = content.replace(/^package .*$/m, `package ${expectedPkg}`);
            fs.writeFileSync(file, content);
            console.log(`Updated package of ${path.basename(file)} from ${actualPkg} to ${expectedPkg}`);
        }
    }
});

console.log('All packages verified/fixed.');
