import { createHash } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const siteDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryDirectory = join(siteDirectory, '..');
const read = (name) => readFileSync(join(siteDirectory, name), 'utf8');
const index = read('index.html');
const styles = read('styles.css');
const materialCss = read('material-system.css');
const demoCss = read('demo.css');
const demoJs = read('demo.js');
const mainJs = read('main.js');
const materialJs = read('material-system.js');
const mark = read('assets/jdownloader-mark.svg');
const catalog = JSON.parse(read('assets/dimsum/catalog.json'));

let assertions = 0;
function requireCheck(condition, message) {
  assertions += 1;
  if (!condition) throw new Error(message);
}
function count(text, pattern) { return [...text.matchAll(pattern)].length; }

// Existing responsive, semantic and ownership guards.
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

// Eight browser-style site pages, each wired to exactly one panel.
const main = index.match(/<main id="main">([\s\S]*?)<\/main>/)?.[1] || '';
const tabIds = [...index.matchAll(/<button id="site-tab-([^"]+)"[^>]*role="tab"[^>]*aria-controls="([^"]+)"[^>]*data-site-tab="([^"]+)"/g)];
const panelIds = [...main.matchAll(/<section[^>]*class="[^"]*site-tab-panel[^"]*"[^>]*id="([^"]+)"[^>]*role="tabpanel"[^>]*aria-labelledby="site-tab-([^"]+)"/g)];
requireCheck(
  /class="site-tabstrip" role="tablist"/.test(index)
    && tabIds.length === 8
    && panelIds.length === 8
    && tabIds.every((match) => match[1] === match[2] && match[2] === match[3])
    && panelIds.every((match) => match[1] === match[2]),
  'Pages must expose eight exactly linked tab/tab-panel pairs.'
);
requireCheck(
  count(main, /class="[^"]*site-tab-panel[^"]*"/g) === panelIds.length
    && count(main, /class="[^"]*site-tab-panel[^"]*"[^>]*[\s\S]{0,180}?hidden/g) >= panelIds.length - 1
    && /\.site-tab-panel\[hidden\][\s\S]*display:\s*none\s*!important/.test(styles),
  'Top-level site pages must remain discrete hidden panels rather than one long scroll.'
);
requireCheck(
  /ArrowRight/.test(mainJs) && /ArrowLeft/.test(mainJs) && /event\.key === 'Home'/.test(mainJs)
    && /event\.key === 'End'/.test(mainJs) && /aria-selected/.test(mainJs)
    && /tab\.scrollIntoView/.test(mainJs),
  'The primary tab strip must implement roving keyboard navigation and overflow reveal.'
);
requireCheck(
  /draggable="true"[^>]*data-site-tab/.test(index) && /dataset\.pinned/.test(materialJs)
    && /dataset\.groupCollapsed/.test(materialJs) && /dragstart/.test(materialJs)
    && /moveTabByKeyboard/.test(materialJs) && /reorderGroup/.test(materialJs) && /event\.ctrlKey/.test(materialJs),
  'Tabs must support persisted pinning, groups, collapse, pointer reorder and keyboard reorder.'
);

// Four independent tab discovery searches plus per-settings/editor searches.
for (const id of ['group-tabs', 'group-names', 'master-tabs', 'close-containing', 'close-not-containing']) {
  requireCheck(new RegExp(`data-search-id="${id}"`).test(index), `Missing independent ${id} search surface.`);
}
requireCheck(
  /id="site-tab-search"[^>]*maxlength="256"/.test(index)
    && /id="site-tab-regex-builder"[^>]*role="dialog"[^>]*aria-modal="false"/.test(index)
    && /Browser ECMAScript regex/.test(index)
    && /id="site-tab-sample"[^>]*maxlength="2048"/.test(index)
    && /unsafeBrowserPattern/.test(mainJs),
  'Current-strip search must keep its own bounded adjacent full builder.'
);
requireCheck(
  count(index, /data-search-surface/g) >= 10
    && /class SafeSearchController/.test(materialJs)
    && /searchControllers = new Map/.test(materialJs)
    && /data-builder-guide="literal"/.test(index)
    && /data-builder-guide="\[A-Za-z\]"/.test(index)
    && /data-builder-guide="\^\$"/.test(index)
    && /data-builder-guide="\(\)"/.test(index)
    && /data-builder-guide="\(\?:a\|b\)"/.test(index)
    && /data-builder-guide="\{1,3\}"/.test(index),
  'Every secondary search must own cloned independent state and a complete guided builder.'
);
requireCheck(
  /LIMITS = Object\.freeze\(\{ pattern: 256, sample: 2048, matches: 100/.test(materialJs)
    && /performance\.now\(\) - started < 25/.test(materialJs)
    && /match\[0\] === ''\) regex\.lastIndex \+= 1/.test(materialJs)
    && /Potentially explosive nested or repeated quantifiers/.test(materialJs)
    && /capture groups/.test(index),
  'Regex evaluation must be bounded, reject risky shapes and advance zero-width matches safely.'
);
requireCheck(
  /data-preview-bulk-close="containing"/.test(index)
    && /data-preview-bulk-close="not-containing"/.test(index)
    && count(index, /data-bulk-scope=/g) === 2
    && /!controller\?\.state\.pattern\.trim\(\)/.test(materialJs)
    && /includePinned/.test(materialJs)
    && /pendingBulkClose/.test(materialJs)
    && /selected group .*all groups/.test(materialJs)
    && /tabId === 'settings'/.test(materialJs)
    && /showModal\(\)/.test(materialJs),
  'Containing and inverse bulk-close actions need non-empty validation, pinned exclusion and a review dialog.'
);
requireCheck(
  /id="settings-tab-general"[^>]*tabindex="0"/.test(index)
    && count(index, /id="settings-tab-(?:appearance|navigation)"[^>]*tabindex="-1"/g) === 2
    && count(index, /class="settings-panel" role="tabpanel" aria-labelledby="settings-tab-/g) === 3,
  'Settings tabs must initialize one roving stop and label every panel.'
);

// Persisted language and tone, professional-by-default, with first-run disclosure.
requireCheck(
  count(index, /data-language-picker/g) >= 2
    && /\['en', 'yue', 'both'\]/.test(materialJs)
    && /html\.dataset\.language/.test(materialJs)
    && /localized-secondary/.test(materialCss),
  'English, Cantonese and compact bilingual modes must share one persisted setting.'
);
requireCheck(
  /funnyEn: 1, funnyYue: 1/.test(materialJs)
    && /data-funny-en[^>]*aria-label="English funny level"/.test(index)
    && /data-funny-yue[^>]*aria-label="Cantonese funny level"/.test(index)
    && /clamp\(raw\.funnyEn, 1, 5, 1\)/.test(materialJs)
    && /clamp\(raw\.funnyYue, 1, 5, 1\)/.test(materialJs)
    && /including errors and warnings/.test(index),
  'Both funny sliders must be independent, 1–5, professional by default and honestly disclosed.'
);
requireCheck(
  /const en = \{[\s\S]*?error: \[/.test(materialJs)
    && /const yue = \{[\s\S]*?error: \[/.test(materialJs)
    && /facts \? `\$\{voiced\} \$\{facts\}`/.test(materialJs),
  'Tone must cover errors in both languages while appending unchanged factual detail.'
);
requireCheck(
  /id="first-run-disclosure"[^>]*role="status"/.test(index)
    && /STORAGE\.firstRun/.test(materialJs)
    && /Language and tone disclosure acknowledged/.test(materialJs),
  'First run must disclose all-message funny styling and keep Settings reset available.'
);

// One-percent local dim-sum delight and genuine copied catalog assets.
requireCheck(catalog.schemaVersion === 1 && catalog.assets.length === 4, 'The site dim-sum catalog must contain four schema-v1 entries.');
for (const asset of catalog.assets) {
  const sitePath = join(siteDirectory, 'assets', 'dimsum', asset.resource);
  const releasePath = join(repositoryDirectory, 'release-assets', 'dimsum', asset.resource);
  requireCheck(existsSync(sitePath), `Missing site dim-sum image ${asset.resource}.`);
  const bytes = readFileSync(sitePath);
  requireCheck(bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), `${asset.resource} is not a PNG.`);
  requireCheck(bytes.readUInt32BE(16) >= 1024 && bytes.readUInt32BE(20) >= 1024, `${asset.resource} is below 1024x1024.`);
  requireCheck(
    createHash('sha256').update(bytes).digest('hex') === createHash('sha256').update(readFileSync(releasePath)).digest('hex'),
    `${asset.resource} differs from the bundled release catalog image.`
  );
  requireCheck(Boolean(asset.englishName && asset.cantoneseName && asset.altTextEnglish && asset.altTextCantonese), `${asset.resource} needs bilingual names and alt text.`);
}
requireCheck(
  /unbiasedIndex\(100\) !== 0/.test(materialJs)
    && /range - \(range % maximum\)/.test(materialJs)
    && /sessionStorage\.setItem\(STORAGE\.dimSumSession, 'drawn'\)/.test(materialJs)
    && /localStorage\.getItem\(STORAGE\.firstRun\) === 'complete'/.test(materialJs)
    && /!preferences\.quietMode/.test(materialJs)
    && /!document\.body\.hasAttribute\('data-error-active'\)/.test(materialJs)
    && /!location\.search.*includes\('update'\)/.test(materialJs)
    && /setTimeout\(\(\) => \{ card\.hidden = true; \}, 9000\)/.test(materialJs),
  'Dim sum must use an unbiased exact 1%, one fire, eligibility gates, opt-out and auto-dismiss.'
);

// Non-blocking corner notifications and searchable persisted history.
requireCheck(
  /id="toast-region"[^>]*aria-live="polite"/.test(index)
    && /right:\s*18px;[\s\S]*bottom:\s*18px/.test(materialCss)
    && /item\.severity === 'success' \? 5000 : item\.severity === 'info' \? 6500 : 0/.test(materialJs)
    && /STORAGE\.notifications/.test(materialJs)
    && /LIMITS\.history/.test(materialJs)
    && /data-search-id="notifications"/.test(index),
  'Corner notifications must auto-dismiss only info/success and retain bounded searchable history.'
);

// M3 runtime appearance, self-editable pickers and portable profiles.
requireCheck(
  /--m3-seed:/.test(materialCss) && /--m3-density:/.test(materialCss)
    && /data-density-picker/.test(index) && /data-accent-picker/.test(index)
    && /data-theme-picker/.test(index) && /applyAppearanceProfile/.test(materialJs),
  'M3 theme, density, accent and profile tokens must apply live.'
);
for (const property of ['fontFamily', 'fontSize', 'fontWeight', 'fontVariationSettings', 'fontStyle', 'underline', 'strike', 'overline', 'smallCaps', 'superscript', 'subscript', 'letterSpacing', 'wordSpacing', 'lineHeight', 'baselineOffset', 'direction', 'textAlign', 'glow']) {
  requireCheck(new RegExp(`data-style-property="${property}"`).test(index), `Appearance editor is missing ${property}.`);
}
requireCheck(
  /data-appearance-target="chrome\.appearance-editor"/.test(index)
    && /data-appearance-target="chrome\.font-picker"/.test(index)
    && /data-appearance-target="chrome\.color-picker"/.test(index)
    && /document\.body\.querySelectorAll|\$\$\('\*', document\.body\)/.test(materialJs)
    && /Shift/.test(index) && /event\.key === 'F10' && event\.shiftKey/.test(materialJs)
    && /contextmenu/.test(materialJs),
  'Every major element and the editor/pickers themselves need context and keyboard appearance paths.'
);
for (const space of ['Named', 'HEX', 'HEX8', 'RGB / RGBA', 'HSL / HSLA', 'HSV / HSB', 'HWB', 'CIELAB', 'CIELCH', 'OKLab', 'OKLCH', 'CMYK']) {
  requireCheck(materialJs.includes(`['${space}'`) || materialJs.includes(`['${space}',`), `Color translator is missing ${space}.`);
}
requireCheck(
  /class="spectrum-field"/.test(index)
    && /data-color-entry/.test(index)
    && /parseColor/.test(materialJs)
    && /clipped to gamut/.test(materialJs)
    && /Contrast .*WCAG/.test(materialJs)
    && /data-export-profile/.test(index)
    && /data-import-profile/.test(index)
    && /data-reset-appearance-property/.test(index)
    && /data-reset-appearance-target/.test(index)
    && /data-reset-all-appearance/.test(index)
    && /LIMITS\.importBytes/.test(materialJs),
  'The infinite color picker needs continuous input, bidirectional translation, clipping/contrast and bounded profile I/O.'
);
requireCheck(
  count(materialJs, /parts\.channels\.length > 3 \? alphaValue\(parts\.channels\[3\]\) : parts\.alpha/g) === 2,
  'Legacy comma-form RGBA and HSLA must preserve their fourth alpha channel.'
);
requireCheck(
  /event\.stopImmediatePropagation\(\);[\s\S]{0,120}openAppearanceEditor/.test(materialJs)
    && /\}, true\);/.test(materialJs),
  'The Ctrl+Shift+A capture path must suppress the legacy tab appearance editor.'
);
requireCheck(
  /@media \(prefers-reduced-motion: reduce\)/.test(materialCss)
    && /prefers-reduced-motion: reduce/.test(mainJs)
    && /min-width:\s*44px|min-height:\s*44px/.test(materialCss),
  'New surfaces must preserve reduced-motion behavior and adequate hit targets.'
);

// In-site documentation: one substantive article per feature, not redirects.
const articles = [...index.matchAll(/<article id="article-([^"]+)"[\s\S]*?<\/article>/g)];
requireCheck(articles.length === 11, 'The in-site guide must contain eleven categorized feature articles.');
for (const article of articles) {
  const body = article[0];
  for (const heading of ['Behavior', 'Configuration', 'Failure modes', 'Security', 'Verification', 'Related:']) {
    requireCheck(body.includes(heading), `Article ${article[1]} is missing ${heading}.`);
  }
}
requireCheck(
  /data-search-id="guide"/.test(index) && /href="#article-/.test(index),
  'Feature articles need in-site navigation and local search.'
);

// Static privacy, local assets, canonical ownership and Windows-only scope.
requireCheck(
  !/(?:src|href)=["']https?:\/\/(?:cdn|fonts|unpkg|jsdelivr)/i.test(index)
    && !/\b(?:fetch|XMLHttpRequest|sendBeacon)\s*\(/.test(materialJs)
    && !/<script[^>]+(?:analytics|googletagmanager|segment\.com)/i.test(index)
    && !/(?:googletagmanager|segment\.com)\//i.test(mainJs + materialJs),
  'The site must not use CDNs, analytics or outbound runtime requests.'
);
requireCheck(
  /Windows x64 · Java 25 included/.test(index)
    && !/Linux x64|macOS arm64|macOS Intel|Foundation in progress|Workflow in progress|<td>Planned<\/td>|<td>Mixed<\/td>/.test(index),
  'Pages must describe the implemented Windows scope without stale platform claims.'
);
requireCheck(
  /<title id="title">JDownloader Material download mark<\/title>/.test(mark)
    && /mint download arrow descends into a deep-teal M-shaped tray/.test(mark)
    && !/<rect\b/.test(mark)
    && count(mark, /<path\b/g) >= 2,
  'The site mark must be an accessible transparent vector matching the app logo.'
);
requireCheck(
  /https:\/\/ding-ding-projects\.github\.io\/jdownloader-material\//.test(index)
    && !/codingmachineedge/.test(index + demoJs + materialJs),
  'Canonical ownership and project links must use Ding-Ding-Projects.'
);

console.log(`Static Pages M3/localization/tabs/search/appearance/docs/a11y smoke passed ${assertions} assertions; ${articles.length} feature articles; ${catalog.assets.length} verified dim-sum images`);
