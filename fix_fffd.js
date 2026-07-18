const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, 'app/src/main/java/com/example/android/feature/ai/HandMetrics.kt');
let content = fs.readFileSync(file, 'utf8');

// The replacement character is \uFFFD.
content = content.replace(/PU\uFFFDO/g, 'PUNO');
content = content.replace(/est\uFFFDtico/g, 'estatico');

fs.writeFileSync(file, content);
console.log('Fixed U+FFFD characters in HandMetrics.kt');
