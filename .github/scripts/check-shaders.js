// Compiles every AGSL shader embedded in the Kotlin sources (`const val *_SRC = """..."""`)
// with Skia's real runtime-effect compiler (CanvasKit), so a shader typo fails CI
// instead of showing up as a black background on the phone.
const fs = require('fs');
const path = require('path');
const CanvasKitInit = require('canvaskit-wasm');

function walk(dir, out = []) {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f);
    if (fs.statSync(p).isDirectory()) walk(p, out);
    else if (p.endsWith('.kt')) out.push(p);
  }
  return out;
}

CanvasKitInit({ locateFile: (f) => require.resolve('canvaskit-wasm/bin/' + f) }).then((CK) => {
  let count = 0;
  let failed = 0;
  for (const file of walk('app/src/main/java')) {
    const src = fs.readFileSync(file, 'utf8');
    const re = /const val (\w+_SRC)\s*=\s*"""([\s\S]*?)"""/g;
    let m;
    while ((m = re.exec(src))) {
      count++;
      const errors = [];
      const effect = CK.RuntimeEffect.Make(m[2], (e) => errors.push(e));
      if (!effect) {
        failed++;
        console.log(`::error file=${file}::${m[1]} does not compile:\n${errors.join('\n')}`);
      } else {
        const names = [];
        for (let i = 0; i < effect.getUniformCount(); i++) names.push(effect.getUniformName(i));
        console.log(`OK  ${m[1]}  (${path.basename(file)})  uniforms: ${names.join(', ')}`);
        effect.delete();
      }
    }
  }
  console.log(`${count} shader(s) checked, ${failed} failed`);
  process.exit(failed || count === 0 ? 1 : 0);
});
