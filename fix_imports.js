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

    // Check if not in root package
    const pkgMatch = content.match(/^package (.*)/m);
    if (!pkgMatch) return;
    const pkg = pkgMatch[1].trim();

    if (pkg !== 'com.example.android') {
        // Check for R. usage (e.g. R.layout., R.id., R.string., R.drawable., R.style., R.anim., R.color., @R.id)
        // Also handle cases like R.dimen.
        if (/\bR\.[a-z]+/.test(content)) {
            if (!content.includes('import com.example.android.R')) {
                // Insert import com.example.android.R after package declaration
                content = content.replace(/^package .*$/m, `$&` + '\n\nimport com.example.android.R');
                changed = true;
            }
        }
        
        if (/\bBuildConfig\./.test(content)) {
            if (!content.includes('import com.example.android.BuildConfig')) {
                // Insert import com.example.android.BuildConfig after package declaration
                content = content.replace(/^package .*$/m, `$&` + '\nimport com.example.android.BuildConfig');
                changed = true;
            }
        }
    }

    if (changed) {
        fs.writeFileSync(file, content);
    }
});

console.log('Fixed imports for R and BuildConfig');
