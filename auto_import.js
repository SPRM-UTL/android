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
const classMap = {}; // { ClassName: "com.example.android.core.ui.ClassName" }

// 1. Build a map of all classes and their packages
ktFiles.forEach(file => {
    const content = fs.readFileSync(file, 'utf8');
    const pkgMatch = content.match(/^package (.*)/m);
    if (!pkgMatch) return;
    const pkg = pkgMatch[1].trim();
    
    // Naive way: extract class/object/interface names
    const classRegex = /(?:class|object|interface)\s+([A-Za-z0-9_]+)/g;
    let match;
    while ((match = classRegex.exec(content)) !== null) {
        const className = match[1];
        classMap[className] = `${pkg}.${className}`;
    }
    
    // Also include the filename without .kt as a class name, because sometimes there are top-level functions
    const fileName = path.basename(file, '.kt');
    if (!classMap[fileName]) {
        classMap[fileName] = `${pkg}.${fileName}`;
    }
});

// Avoid importing very common standard classes if they happen to share a name (unlikely here but just in case)
delete classMap['Context'];
delete classMap['Intent'];

console.log(`Found ${Object.keys(classMap).length} classes.`);

// 2. Auto-import missing classes
ktFiles.forEach(file => {
    let content = fs.readFileSync(file, 'utf8');
    let changed = false;

    const pkgMatch = content.match(/^package (.*)/m);
    if (!pkgMatch) return;
    const currentPkg = pkgMatch[1].trim();

    Object.keys(classMap).forEach(className => {
        const fqName = classMap[className];
        const classPkg = fqName.substring(0, fqName.lastIndexOf('.'));
        
        // Skip if same package
        if (classPkg === currentPkg) return;
        
        // Check if class is used in this file
        // Regex to find word boundaries, ensuring we don't match substrings (e.g. "Device" in "DeviceAdapter")
        const usageRegex = new RegExp(`\\b${className}\\b`);
        if (usageRegex.test(content)) {
            // Check if it's already imported
            const importRegex = new RegExp(`import ${fqName.replace(/\./g, '\\.')}`);
            if (!importRegex.test(content)) {
                // Add import
                content = content.replace(/^package .*$/m, `$&` + `\nimport ${fqName}`);
                changed = true;
                console.log(`Added import ${fqName} to ${path.basename(file)}`);
            }
        }
    });

    if (changed) {
        fs.writeFileSync(file, content);
    }
});

console.log('Auto-import complete.');
