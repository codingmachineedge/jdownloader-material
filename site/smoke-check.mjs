import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const siteDirectory = dirname(fileURLToPath(import.meta.url));
const read = (name) => readFileSync(join(siteDirectory, name), 'utf8');
const index = read('index.html');
const styles = read('styles.css');
const demoCss = read('demo.css');
const demoJs = read('demo.js');

function requireCheck(condition, message) {
  if (!condition) throw new Error(message);
}

requireCheck(
  /<details class="header-menu">\s*<summary aria-label="Open main navigation">Menu<\/summary>/.test(index),
  'Compact navigation must use a named native details menu.'
);
requireCheck(
  /<span lang="yue">粵<\/span>/.test(index) && /<option lang="yue">香港粵語<\/option>/.test(demoJs),
  'Cantonese fragments must declare their language.'
);
requireCheck(
  /@media \(max-width: 1080px\)[\s\S]*?\.header-menu\s*\{\s*display:\s*block;/.test(styles),
  'Compact navigation must remain available below 1080 px.'
);
requireCheck(
  /\.console-state > span\s*\{\s*gap:\s*4px;\s*white-space:\s*nowrap;/.test(styles)
    && !/\.console-state > span\s*\{\s*font-size:\s*0;/.test(styles),
  'Mobile queue states must retain visible text.'
);
requireCheck(
  /\.data-table\s*\{\s*min-width:\s*340px;/.test(demoCss)
    && /\.data-table \.status-col\s*\{\s*width:\s*29% !important;/.test(demoCss),
  'Compact demo tables must reserve room for status labels.'
);
requireCheck(
  /event\.data\?\.type === 'jd-screen'\) changeScreen\(event\.data\.screen, true\);/.test(demoJs),
  'Parent-driven demo navigation must move focus to the new page heading.'
);

console.log('Static Pages responsive/a11y smoke check passed');
