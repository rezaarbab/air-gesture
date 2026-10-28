#!/usr/bin/env node
// Pre-push static scan for the Kotlin sources this machine cannot compile (the hand flavor).
//   node tools/scan.js app/src            -> must print 0 findings on a clean tree
// Proven both ways: a copy with one missing ')' or a `when:` parameter must name that line.
const fs = require('fs');
const path = require('path');

const KEYWORDS = new Set(['when', 'fun', 'object', 'in', 'is', 'as', 'val', 'var', 'typealias', 'package', 'import', 'class', 'interface', 'enum', 'sealed', 'throw', 'return', 'else', 'do', 'finally', 'try', 'for', 'while', 'super', 'this', 'true', 'false', 'null', 'break', 'continue', 'const', 'external', 'field', 'internal', 'lateinit', 'operator', 'reified', 'dynamic', 'companion', 'constructor', 'init', 'crossinline', 'noinline', 'vararg', 'where', 'by']);

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.kt') || e.name.endsWith('.kts')) out.push(p);
  }
  return out;
}

const findings = [];
for (const file of walk(process.argv[2] || 'app/src')) {
  const src = fs.readFileSync(file, 'utf8');
  const stack = [];
  const pairs = { ')': '(', ']': '[', '}': '{' };
  let line = 1;
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    const n = src[i + 1];
    if (c === '\n') { line++; i++; continue; }
    if (c === '/' && n === '/') { while (i < src.length && src[i] !== '\n') i++; continue; }
    if (c === '/' && n === '*') {
      i += 2;
      while (i < src.length && !(src[i] === '*' && src[i + 1] === '/')) { if (src[i] === '\n') line++; i++; }
      i += 2;
      continue;
    }
    if (src.startsWith('"""', i)) {
      i += 3;
      while (i < src.length && !src.startsWith('"""', i)) { if (src[i] === '\n') line++; i++; }
      i += 3;
      continue;
    }
    if (c === '"') {
      i++;
      while (i < src.length && src[i] !== '"') { if (src[i] === '\\') i++; if (src[i] === '\n') { break; } i++; }
      i++;
      continue;
    }
    if (c === "'") {
      // A character literal, or an apostrophe inside a comment already skipped above.
      const close = src.indexOf("'", i + 1);
      if (close > i && close - i <= 4 && src.indexOf('\n', i) > close) { i = close + 1; continue; }
      i++;
      continue;
    }
    if (c === '(' || c === '[' || c === '{') { stack.push({ c, line }); i++; continue; }
    if (c === ')' || c === ']' || c === '}') {
      const top = stack.pop();
      if (!top || top.c !== pairs[c]) findings.push(`${file}:${line}: closing '${c}' opens with ${top ? `'${top.c}' from line ${top.line}` : 'nothing'}`);
      i++;
      continue;
    }
    i++;
  }
  if (stack.length) findings.push(`${file}:?: unclosed ${stack.map(s => `'${s.c}'@${s.line}`).join(' ')}`);

  // Kotlin hard keywords used as an identifier: parameter, property or function name.
  const lines = src.split('\n');
  lines.forEach((t, idx) => {
    if (t.trimStart().startsWith('//') || t.trimStart().startsWith('*')) return;
    const patterns = [
      [/(^|[^\w.])(fun|val|var)\s+([A-Za-z_][\w]*)\s*[=:(]/g, 3],
      // `object : Type` in an argument list is an expression, never a parameter name.
      [/[(,]\s*(?!object\b)([A-Za-z_][\w]*)\s*:/g, 1],
      [/(^|[^\w.])(fun)\s+([A-Za-z_][\w]*)\s*\(/g, 3],
    ];
    for (const [re, group] of patterns) {
      let m;
      while ((m = re.exec(t)) !== null) {
        if (KEYWORDS.has(m[group])) findings.push(`${file}:${idx + 1}: keyword '${m[group]}' used as an identifier`);
      }
    }
  });
}

for (const f of findings) console.log(f);
console.log(`findings=${findings.length}`);
process.exit(findings.length ? 1 : 0);
