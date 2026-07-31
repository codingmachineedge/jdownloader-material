'use strict';

document.documentElement.classList.add('js');

const root = document.documentElement;
const themeButton = document.querySelector('[data-theme-toggle]');
const themeColor = document.querySelector('meta[name="theme-color"]');
const demo = document.querySelector('#product-demo');
const demoSwitcher = document.querySelector('[data-demo-switcher]');

function applyTheme(theme, persist = true) {
  const next = theme === 'light' ? 'light' : 'dark';
  root.dataset.theme = next;
  themeButton?.setAttribute('aria-pressed', String(next === 'light'));
  themeButton?.setAttribute('aria-label', `Switch to ${next === 'light' ? 'dark' : 'light'} theme`);
  if (themeColor) themeColor.content = next === 'light' ? '#f7f9fc' : '#111318';
  if (persist) localStorage.setItem('jd-material-site-theme', next);
  demo?.contentWindow?.postMessage({ type: 'jd-theme', theme: next }, location.origin);
}

applyTheme(root.dataset.theme, false);

themeButton?.addEventListener('click', () => {
  applyTheme(root.dataset.theme === 'dark' ? 'light' : 'dark');
});

const header = document.querySelector('[data-header]');
const syncHeader = () => header?.classList.toggle('scrolled', window.scrollY > 8);
syncHeader();
window.addEventListener('scroll', syncHeader, { passive: true });

const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)');
const revealItems = document.querySelectorAll('.reveal');

if (reduceMotion.matches || !('IntersectionObserver' in window)) {
  revealItems.forEach((item) => item.classList.add('in-view'));
} else {
  const observer = new IntersectionObserver((entries, activeObserver) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('in-view');
      activeObserver.unobserve(entry.target);
    });
  }, { rootMargin: '0px 0px -7% 0px', threshold: .08 });
  revealItems.forEach((item) => observer.observe(item));
}

function sendDemo(message) {
  if (!demo?.contentWindow) return;
  demo.contentWindow.postMessage(message, location.origin);
}

demo?.addEventListener('load', () => {
  sendDemo({ type: 'jd-theme', theme: root.dataset.theme });
});

demoSwitcher?.addEventListener('click', (event) => {
  const screenButton = event.target.closest('[data-demo-screen]');
  const actionButton = event.target.closest('[data-demo-action]');

  if (screenButton) {
    demoSwitcher.querySelectorAll('[data-demo-screen]').forEach((button) => {
      const active = button === screenButton;
      button.classList.toggle('active', active);
      button.setAttribute('aria-pressed', String(active));
    });
    sendDemo({ type: 'jd-screen', screen: screenButton.dataset.demoScreen });
    demo?.focus({ preventScroll: true });
  }

  if (actionButton?.dataset.demoAction === 'add-links') {
    sendDemo({ type: 'jd-action', action: 'add-links' });
    demo?.focus({ preventScroll: true });
  }
});

window.addEventListener('message', (event) => {
  if (event.origin !== location.origin || event.source !== demo?.contentWindow) return;
  if (event.data?.type === 'jd-demo-theme') applyTheme(event.data.theme);
  if (event.data?.type === 'jd-active-screen') {
    demoSwitcher?.querySelectorAll('[data-demo-screen]').forEach((button) => {
      const active = button.dataset.demoScreen === event.data.screen;
      button.classList.toggle('active', active);
      button.setAttribute('aria-pressed', String(active));
    });
  }
});

// Persistent browser-style navigation for the landing site. Each button owns
// one discrete panel; hidden panels never form a single marketing scroll.
const siteTabs = [...document.querySelectorAll('[role="tab"][data-site-tab]')];
const sitePanels = new Map(siteTabs.map((tab) => [tab.dataset.siteTab, document.getElementById(tab.getAttribute('aria-controls'))]));
const siteTabStrip = document.querySelector('.site-tabstrip');
const siteTabSearch = document.querySelector('#site-tab-search');
const siteTabSearchStatus = document.querySelector('#site-tab-search-status');
const siteRegexButton = document.querySelector('[data-site-tab-regex]');
const siteRegexBuilder = document.querySelector('#site-tab-regex-builder');
const sitePattern = document.querySelector('#site-tab-pattern');
const siteSample = document.querySelector('#site-tab-sample');
const siteRegexValidation = document.querySelector('#site-tab-regex-validation');
const siteAppearanceButton = document.querySelector('[data-edit-site-tab]');
const siteAppearance = document.querySelector('#site-tab-appearance');
const siteAppearanceTarget = document.querySelector('#site-tab-appearance-target');
const siteAppearanceControls = [...document.querySelectorAll('[data-tab-style]')];
const siteTabLinks = [...document.querySelectorAll('[data-site-tab-link]')];
const SITE_ACTIVE_TAB_KEY = 'jd-material-site-active-tab';
const SITE_TAB_APPEARANCE_KEY = 'jd-material-site-tab-appearance-v1';
const reduceSiteMotion = matchMedia('(prefers-reduced-motion: reduce)');
let activeSiteTab = 'top';
let openSitePopover = null;
let openSitePopoverAnchor = null;
let appearanceSiteTab = 'top';

function safeStoredJson(key) {
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || '{}');
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

let siteTabAppearanceState = safeStoredJson(SITE_TAB_APPEARANCE_KEY);

function boundedNumber(value, minimum, maximum, fallback) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(minimum, Math.min(maximum, number)) : fallback;
}

function sanitizedTabStyle(raw = {}) {
  const color = (value, fallback) => /^#[0-9a-f]{6}$/i.test(String(value || '')) ? value : fallback;
  const fonts = new Set([
    'system-ui, sans-serif',
    "'Segoe UI Variable', 'Segoe UI', sans-serif",
    'Georgia, serif',
    "'Cascadia Mono', Consolas, monospace"
  ]);
  return {
    background: color(raw.background, '#d1e4e2'),
    color: color(raw.color, '#00382f'),
    fontFamily: fonts.has(raw.fontFamily) ? raw.fontFamily : 'system-ui, sans-serif',
    fontSize: boundedNumber(raw.fontSize, 12, 22, 14),
    fontWeight: [400, 600, 700, 800].includes(Number(raw.fontWeight)) ? Number(raw.fontWeight) : 600,
    borderRadius: boundedNumber(raw.borderRadius, 0, 24, 12)
  };
}

function applySiteTabAppearance(tab) {
  const raw = siteTabAppearanceState[tab.dataset.siteTab];
  if (!raw) {
    for (const property of ['--tab-bg', '--tab-color', '--tab-font', '--tab-size', '--tab-weight', '--tab-radius']) {
      tab.style.removeProperty(property);
    }
    return;
  }
  const style = sanitizedTabStyle(raw);
  tab.style.setProperty('--tab-bg', style.background);
  tab.style.setProperty('--tab-color', style.color);
  tab.style.setProperty('--tab-font', style.fontFamily);
  tab.style.setProperty('--tab-size', `${style.fontSize}px`);
  tab.style.setProperty('--tab-weight', String(style.fontWeight));
  tab.style.setProperty('--tab-radius', `${style.borderRadius}px`);
}

siteTabs.forEach(applySiteTabAppearance);

function visibleSiteTabs() {
  return siteTabs.filter((tab) => !tab.hidden);
}

function activateSiteTab(id, { focusTab = false, focusPanel = false, updateHash = true } = {}) {
  const tab = siteTabs.find((candidate) => candidate.dataset.siteTab === id);
  const panel = sitePanels.get(id);
  if (!tab || !panel) return false;
  activeSiteTab = id;
  for (const candidate of siteTabs) {
    const selected = candidate === tab;
    candidate.setAttribute('aria-selected', String(selected));
    candidate.tabIndex = selected ? 0 : -1;
  }
  for (const [panelId, candidate] of sitePanels) {
    if (candidate) candidate.hidden = panelId !== id;
  }
  siteTabLinks.forEach((link) => {
    if (link.dataset.siteTabLink === id) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  document.querySelectorAll('.site-tab-overflow[open], .header-menu[open]').forEach((details) => details.removeAttribute('open'));
  closeSitePopover(false);
  localStorage.setItem(SITE_ACTIVE_TAB_KEY, id);
  if (updateHash) history.replaceState(null, '', `#${id}`);
  tab.scrollIntoView({ behavior: reduceSiteMotion.matches ? 'auto' : 'smooth', block: 'nearest', inline: 'center' });
  panel.querySelectorAll('.reveal').forEach((item) => item.classList.add('in-view'));
  if (focusTab) tab.focus({ preventScroll: true });
  if (focusPanel) panel.focus({ preventScroll: true });
  applySiteTabFilter();
  return true;
}

siteTabs.forEach((tab) => {
  tab.addEventListener('click', () => activateSiteTab(tab.dataset.siteTab));
  tab.addEventListener('contextmenu', (event) => {
    event.preventDefault();
    openAppearanceForSiteTab(tab, event.currentTarget);
  });
});

siteTabStrip?.addEventListener('keydown', (event) => {
  const tabs = visibleSiteTabs();
  const current = tabs.indexOf(document.activeElement);
  let next = -1;
  if (event.key === 'ArrowRight') next = current < 0 ? 0 : (current + 1) % tabs.length;
  if (event.key === 'ArrowLeft') next = current < 0 ? tabs.length - 1 : (current - 1 + tabs.length) % tabs.length;
  if (event.key === 'Home') next = 0;
  if (event.key === 'End') next = tabs.length - 1;
  if (next >= 0 && tabs[next]) {
    event.preventDefault();
    activateSiteTab(tabs[next].dataset.siteTab, { focusTab: true });
  }
});

siteTabLinks.forEach((link) => link.addEventListener('click', (event) => {
  event.preventDefault();
  activateSiteTab(link.dataset.siteTabLink, { focusPanel: true });
}));

document.querySelector('.site-brand')?.addEventListener('click', (event) => {
  event.preventDefault();
  activateSiteTab('top', { focusPanel: true });
});

function selectedRegexFlags() {
  return [...document.querySelectorAll('[data-site-regex-flag]:checked')].map((field) => field.value).join('');
}

function siteSearchMode() {
  return document.querySelector('input[name="site-tab-search-mode"]:checked')?.value || 'plain';
}

function unsafeBrowserPattern(pattern) {
  // Tab labels are tiny, but reject the common nested-quantifier shape before
  // the browser's backtracking engine sees it.
  return /(\([^)]*[+*][^)]*\))[+*{]/.test(pattern) || /(\.\*){3,}/.test(pattern);
}

function siteTabPredicate() {
  const expression = siteTabSearch?.value || '';
  if (!expression) return { valid: true, predicate: () => true, detail: 'empty query' };
  if (siteSearchMode() === 'plain') {
    const insensitive = selectedRegexFlags().includes('i');
    const needle = insensitive ? expression.toLocaleLowerCase() : expression;
    return {
      valid: true,
      predicate: (label) => (insensitive ? label.toLocaleLowerCase() : label).includes(needle),
      detail: 'plain text'
    };
  }
  if (unsafeBrowserPattern(expression)) {
    return { valid: false, detail: 'Nested or repeated wild-card quantifiers are blocked for responsive local evaluation.' };
  }
  try {
    const regex = new RegExp(expression, selectedRegexFlags());
    return { valid: true, predicate: (label) => regex.test(label), detail: `ECMAScript /${selectedRegexFlags()}/` };
  } catch (error) {
    return { valid: false, detail: error instanceof Error ? error.message : 'Invalid regular expression' };
  }
}

function applySiteTabFilter() {
  if (!siteTabSearch) return;
  const evaluation = siteTabPredicate();
  let matches = 0;
  for (const tab of siteTabs) {
    const match = evaluation.valid && evaluation.predicate(tab.textContent.trim());
    if (match) matches += 1;
    tab.hidden = evaluation.valid ? (!match && tab.dataset.siteTab !== activeSiteTab) : false;
  }
  const sample = (siteSample?.value || '').slice(0, 256);
  const sampleResult = evaluation.valid && sample
    ? ` · sample ${evaluation.predicate(sample) ? 'matches' : 'does not match'}` : '';
  const message = evaluation.valid
    ? `${siteSearchMode() === 'plain' ? 'Plain text' : evaluation.detail} · ${matches} matching tab${matches === 1 ? '' : 's'}${sampleResult}`
    : `Invalid pattern · ${evaluation.detail}`;
  if (siteTabSearchStatus) siteTabSearchStatus.value = message;
  if (siteRegexValidation) {
    siteRegexValidation.value = message;
    siteRegexValidation.dataset.invalid = String(!evaluation.valid);
  }
  siteTabSearch.setAttribute('aria-invalid', String(!evaluation.valid));
}

function synchronizeSitePattern(source) {
  const value = source?.value?.slice(0, 256) || '';
  if (siteTabSearch && siteTabSearch !== source) siteTabSearch.value = value;
  if (sitePattern && sitePattern !== source) sitePattern.value = value;
  applySiteTabFilter();
}

siteTabSearch?.addEventListener('input', () => synchronizeSitePattern(siteTabSearch));
sitePattern?.addEventListener('input', () => synchronizeSitePattern(sitePattern));
siteSample?.addEventListener('input', applySiteTabFilter);
document.querySelectorAll('input[name="site-tab-search-mode"], [data-site-regex-flag]').forEach((field) => {
  field.addEventListener('change', applySiteTabFilter);
});

document.querySelectorAll('[data-regex-guide]').forEach((button) => button.addEventListener('click', () => {
  if (!sitePattern) return;
  const guide = button.dataset.regexGuide;
  const selected = sitePattern.value.slice(sitePattern.selectionStart, sitePattern.selectionEnd);
  let insertion = guide;
  if (guide === 'literal') insertion = (selected || 'literal').replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  if (guide === '^$') insertion = `^${selected}$`;
  if (guide === '()') insertion = `(${selected})`;
  const start = sitePattern.selectionStart;
  sitePattern.setRangeText(insertion, start, sitePattern.selectionEnd, 'end');
  document.querySelector('input[name="site-tab-search-mode"][value="regex"]')?.click();
  synchronizeSitePattern(sitePattern);
  sitePattern.focus();
}));

document.querySelector('[data-copy-site-pattern]')?.addEventListener('click', async () => {
  const flags = siteSearchMode() === 'regex' ? selectedRegexFlags() : '';
  const value = flags ? `(?${flags})${sitePattern?.value || ''}` : sitePattern?.value || '';
  try {
    await navigator.clipboard.writeText(value);
    if (siteRegexValidation) siteRegexValidation.value = 'Pattern copied to the clipboard.';
  } catch {
    if (siteRegexValidation) siteRegexValidation.value = 'Clipboard access was unavailable; the pattern remains selected.';
    sitePattern?.select();
  }
});

function positionSitePopover(popover, anchor) {
  if (!popover || popover.hidden || !anchor) return;
  const anchorBox = anchor.getBoundingClientRect();
  const width = Math.min(popover.offsetWidth || 520, innerWidth - 16);
  const height = Math.min(popover.offsetHeight || 500, innerHeight - 16);
  const left = Math.max(8, Math.min(anchorBox.left, innerWidth - width - 8));
  const below = anchorBox.bottom + 8;
  const top = below + height <= innerHeight - 8 ? below : Math.max(8, anchorBox.top - height - 8);
  popover.style.left = `${left}px`;
  popover.style.right = 'auto';
  popover.style.top = `${top}px`;
}

function showSitePopover(popover, anchor) {
  closeSitePopover(false);
  openSitePopover = popover;
  openSitePopoverAnchor = anchor;
  popover.hidden = false;
  if (popover === siteRegexBuilder) siteRegexButton?.setAttribute('aria-expanded', 'true');
  if (popover === siteAppearance) siteAppearanceButton?.setAttribute('aria-expanded', 'true');
  requestAnimationFrame(() => {
    positionSitePopover(popover, anchor);
    popover.querySelector('input, select, textarea, button')?.focus({ preventScroll: true });
  });
}

function closeSitePopover(returnFocus = true) {
  if (!openSitePopover) return;
  const anchor = openSitePopoverAnchor;
  openSitePopover.hidden = true;
  siteRegexButton?.setAttribute('aria-expanded', 'false');
  siteAppearanceButton?.setAttribute('aria-expanded', 'false');
  openSitePopover = null;
  openSitePopoverAnchor = null;
  if (returnFocus) anchor?.focus({ preventScroll: true });
}

siteRegexButton?.addEventListener('click', () => {
  if (openSitePopover === siteRegexBuilder) closeSitePopover();
  else {
    if (sitePattern && siteTabSearch) sitePattern.value = siteTabSearch.value;
    showSitePopover(siteRegexBuilder, siteRegexButton);
    applySiteTabFilter();
  }
});

function loadAppearanceControls(id) {
  const style = sanitizedTabStyle(siteTabAppearanceState[id]);
  for (const control of siteAppearanceControls) {
    const property = control.dataset.tabStyle;
    control.value = String(style[property]);
  }
}

function openAppearanceForSiteTab(tab, anchor = tab) {
  appearanceSiteTab = tab.dataset.siteTab;
  if (siteAppearanceTarget) siteAppearanceTarget.textContent = tab.textContent.trim();
  loadAppearanceControls(appearanceSiteTab);
  showSitePopover(siteAppearance, anchor);
}

siteAppearanceButton?.addEventListener('click', () => {
  const tab = siteTabs.find((candidate) => candidate.dataset.siteTab === activeSiteTab);
  if (tab) openAppearanceForSiteTab(tab, siteAppearanceButton);
});

siteAppearanceControls.forEach((control) => control.addEventListener('input', () => {
  const current = sanitizedTabStyle(siteTabAppearanceState[appearanceSiteTab]);
  const property = control.dataset.tabStyle;
  current[property] = ['fontSize', 'fontWeight', 'borderRadius'].includes(property) ? Number(control.value) : control.value;
  siteTabAppearanceState[appearanceSiteTab] = sanitizedTabStyle(current);
  localStorage.setItem(SITE_TAB_APPEARANCE_KEY, JSON.stringify(siteTabAppearanceState));
  const tab = siteTabs.find((candidate) => candidate.dataset.siteTab === appearanceSiteTab);
  if (tab) applySiteTabAppearance(tab);
}));

document.querySelector('[data-reset-site-tab]')?.addEventListener('click', () => {
  delete siteTabAppearanceState[appearanceSiteTab];
  localStorage.setItem(SITE_TAB_APPEARANCE_KEY, JSON.stringify(siteTabAppearanceState));
  const tab = siteTabs.find((candidate) => candidate.dataset.siteTab === appearanceSiteTab);
  if (tab) applySiteTabAppearance(tab);
  loadAppearanceControls(appearanceSiteTab);
});

document.querySelectorAll('[data-close-site-popover]').forEach((button) => {
  button.addEventListener('click', () => closeSitePopover());
});

window.addEventListener('resize', () => positionSitePopover(openSitePopover, openSitePopoverAnchor));
window.addEventListener('scroll', () => positionSitePopover(openSitePopover, openSitePopoverAnchor), { passive: true });
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && openSitePopover) {
    event.preventDefault();
    closeSitePopover();
  }
  if (event.key.toLowerCase() === 'a' && event.ctrlKey && event.shiftKey) {
    event.preventDefault();
    const tab = siteTabs.find((candidate) => candidate.dataset.siteTab === activeSiteTab);
    if (tab) openAppearanceForSiteTab(tab);
  }
});

window.addEventListener('hashchange', () => {
  const id = location.hash.slice(1);
  if (sitePanels.has(id)) activateSiteTab(id, { updateHash: false, focusPanel: true });
});

const requestedSiteTab = location.hash.slice(1);
const savedSiteTab = localStorage.getItem(SITE_ACTIVE_TAB_KEY);
activateSiteTab(sitePanels.has(requestedSiteTab) ? requestedSiteTab
  : sitePanels.has(savedSiteTab) ? savedSiteTab : 'top', { updateHash: false });
synchronizeSitePattern(siteTabSearch);
