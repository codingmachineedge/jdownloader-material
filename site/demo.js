'use strict';

const root = document.documentElement;
const content = document.querySelector('#demo-content');
const search = document.querySelector('#demo-search');
const speedOutput = document.querySelector('#speed-output');
const globalStatus = document.querySelector('#global-status');
const throughput = document.querySelector('.throughput');
const themeButton = document.querySelector('#demo-theme-button');
const drawer = document.querySelector('#add-drawer');
const scrim = document.querySelector('#drawer-scrim');
const linksInput = document.querySelector('#links-input');
const linksError = document.querySelector('#links-error');
const toastNode = document.querySelector('#demo-toast');
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)');

let activeScreen = 'downloads';
let activeFilter = 'all';
let settingsSection = 'general';
let queueState = 'Downloading';
let toastTimer;
let lastDrawerFocus;

const downloads = [
  { name: 'Documentation bundle', detail: 'Reference files', size: '842 MB', host: 'github.com', state: 'Downloading', progress: 68, speed: '11.2 MB/s', eta: '03:02' },
  { name: 'Open movie archive', detail: 'Creative Commons media', size: '2.4 GB', host: 'blender.org', state: 'Downloading', progress: 41, speed: '7.2 MB/s', eta: '03:18' },
  { name: 'Reference package', detail: 'Queued direct file', size: '316 MB', host: 'example.org', state: 'Paused', progress: 24, speed: '—', eta: '—' },
  { name: 'Release checksums', detail: 'Completed file', size: '24 KB', host: 'github.com', state: 'Finished', progress: 100, speed: '—', eta: 'Done' }
];

const grabbed = [
  { name: 'course-assets.zip', detail: 'Direct link', availability: 'Online', host: 'cdn.example.net', size: '1.8 GB', priority: 'Normal' },
  { name: 'project-sources.tar.zst', detail: 'Direct link', availability: 'Checking', host: 'github.com', size: '620 MB', priority: 'High' },
  { name: 'reference-video.mp4', detail: 'Direct link', availability: 'Online', host: 'media.example.net', size: '2.1 GB', priority: 'Normal' },
  { name: 'mirror-copy.iso', detail: 'Direct link', availability: 'Offline', host: 'mirror.example.org', size: '4.7 GB', priority: 'Normal' }
];

const historyEntries = [
  { scope: 'downloads', title: 'Paused Reference package', detail: 'Queue state changed · event appended', time: 'Now', revision: 'd4a91b2' },
  { scope: 'linkgrabber', title: 'Added 2 links to Downloads', detail: 'LinkGrabber confirmation · event appended', time: '2 min', revision: '7cb03ad' },
  { scope: 'downloads', title: 'Started 2 downloads', detail: 'Queue state changed · event appended', time: '4 min', revision: 'f12e89c' },
  { scope: 'settings', title: 'Changed collision policy', detail: 'Settings updated · event appended', time: '18 min', revision: 'ac908e1' },
  { scope: 'downloads', title: 'Restored download queue', detail: 'Restore created a new history event', time: '1 hr', revision: '019edbf' }
];

const escapeText = (value) => String(value)
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;')
  .replaceAll("'", '&#039;');

function postParent(message) {
  if (window.parent === window) return;
  window.parent.postMessage(message, location.origin);
}

function setTheme(theme, persist = true, notify = true) {
  const next = theme === 'light' ? 'light' : 'dark';
  root.dataset.theme = next;
  themeButton.setAttribute('aria-pressed', String(next === 'light'));
  themeButton.setAttribute('aria-label', `Switch to ${next === 'light' ? 'dark' : 'light'} theme`);
  document.querySelector('meta[name="theme-color"]').content = next === 'light' ? '#f7f9fc' : '#111318';
  if (persist) localStorage.setItem('jd-material-site-theme', next);
  if (notify) postParent({ type: 'jd-demo-theme', theme: next });
}

function toast(message) {
  toastNode.textContent = message;
  toastNode.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastNode.classList.remove('show'), 2300);
}

function stateClass(value) {
  return String(value).toLowerCase().replaceAll(' ', '-');
}

function pageHeader(kicker, title, summary = '') {
  return `<header class="page-header"><div><p class="page-kicker">${escapeText(kicker)}</p><h1 tabindex="-1">${escapeText(title)}</h1></div>${summary ? `<div class="page-summary">${summary}</div>` : ''}</header>`;
}

function filterMatches(item, stateKey) {
  const query = search.value.trim().toLowerCase();
  const text = Object.values(item).join(' ').toLowerCase();
  const matchesText = !query || text.includes(query);
  const value = String(item[stateKey]).toLowerCase();
  return matchesText && (activeFilter === 'all' || value === activeFilter);
}

function downloadsScreen() {
  const rows = downloads.filter((item) => filterMatches(item, 'state'));
  const active = downloads.filter((item) => item.state === 'Downloading').length;
  const tableRows = rows.length ? rows.map((item) => `
    <tr data-state="${escapeText(item.state.toLowerCase())}">
      <td class="name-col"><div class="file-cell"><i class="file-marker"></i><span class="file-copy"><strong>${escapeText(item.name)}</strong><small>${escapeText(item.detail)}</small></span></div></td>
      <td class="size-col number-cell">${escapeText(item.size)}</td>
      <td class="host-col">${escapeText(item.host)}</td>
      <td class="status-col"><span class="state-label"><i class="status-dot ${stateClass(item.state)}"></i>${escapeText(item.state)}</span></td>
      <td class="progress-col"><div class="progress-cell"><div class="progress-track" role="progressbar" aria-label="${escapeText(item.name)}" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${item.progress}"><i style="--progress:${item.progress}%"></i></div><span>${item.progress}%</span></div></td>
      <td class="speed-col number-cell">${escapeText(item.speed)}</td>
      <td class="eta-col number-cell">${escapeText(item.eta)}</td>
    </tr>`).join('') : `<tr><td colspan="7"><div class="empty-state"><strong>No matching downloads</strong><span>Change the filter or search term.</span></div></td></tr>`;

  return `${pageHeader('Transfer queue', 'Downloads', `<span><i class="status-dot ${active ? 'live' : ''}"></i>${active} active</span><span>${downloads.length} files</span>`)}
    <section class="workspace-panel table-panel" aria-label="Sample download queue">
      <div class="table-toolbar">
        ${['all', 'downloading', 'paused', 'finished'].map((filter) => `<button class="filter-chip ${activeFilter === filter ? 'active' : ''}" type="button" data-filter="${filter}" aria-pressed="${activeFilter === filter}">${filter[0].toUpperCase() + filter.slice(1)}</button>`).join('')}
        <span class="toolbar-spacer"></span><button class="app-action" type="button" data-table-action="move">Move</button><button class="app-action" type="button" data-table-action="remove">Remove</button>
      </div>
      <div class="table-scroller"><table class="data-table"><thead><tr><th class="name-col" style="width:29%">Name</th><th class="size-col" style="width:10%">Size</th><th class="host-col" style="width:14%">Host</th><th class="status-col" style="width:14%">Status</th><th class="progress-col" style="width:17%">Progress</th><th class="speed-col" style="width:10%">Speed</th><th class="eta-col" style="width:8%">ETA</th></tr></thead><tbody>${tableRows}</tbody></table></div>
    </section>`;
}

function linkgrabberScreen() {
  const rows = grabbed.filter((item) => filterMatches(item, 'availability'));
  const online = grabbed.filter((item) => item.availability === 'Online').length;
  const tableRows = rows.length ? rows.map((item) => `
    <tr>
      <td class="name-col"><div class="file-cell"><i class="file-marker"></i><span class="file-copy"><strong>${escapeText(item.name)}</strong><small>${escapeText(item.detail)}</small></span></div></td>
      <td class="status-col"><span class="state-label"><i class="status-dot ${stateClass(item.availability)}"></i>${escapeText(item.availability)}</span></td>
      <td class="host-col">${escapeText(item.host)}</td>
      <td class="size-col number-cell">${escapeText(item.size)}</td>
      <td class="priority-col">${escapeText(item.priority)}</td>
    </tr>`).join('') : `<tr><td colspan="5"><div class="empty-state"><strong>No matching links</strong><span>Change the availability filter or search term.</span></div></td></tr>`;

  return `${pageHeader('Staged direct links', 'LinkGrabber', `<span><i class="status-dot online"></i>${online} online</span><span>${grabbed.length} links</span>`)}
    <section class="workspace-panel table-panel" aria-label="Sample LinkGrabber results">
      <div class="table-toolbar">
        ${['all', 'online', 'checking', 'offline'].map((filter) => `<button class="filter-chip ${activeFilter === filter ? 'active' : ''}" type="button" data-filter="${filter}" aria-pressed="${activeFilter === filter}">${filter[0].toUpperCase() + filter.slice(1)}</button>`).join('')}
        <span class="toolbar-spacer"></span><button class="app-action" type="button" data-table-action="paste">Paste</button><button class="app-action primary" type="button" data-table-action="add-all">Add all</button>
      </div>
      <div class="table-scroller"><table class="data-table"><thead><tr><th class="name-col" style="width:38%">Name</th><th class="status-col" style="width:16%">Availability</th><th class="host-col" style="width:21%">Host</th><th class="size-col" style="width:13%">Size</th><th class="priority-col" style="width:12%">Priority</th></tr></thead><tbody>${tableRows}</tbody></table></div>
    </section>`;
}

function historyScreen() {
  const entries = historyEntries.filter((item) => activeFilter === 'all' || item.scope === activeFilter);
  return `${pageHeader('Append-only local timeline', 'History', `<span>${historyEntries.length} recent events</span>`)}
    <section class="workspace-panel history-panel" aria-label="Sample history timeline">
      <div class="table-toolbar">
        ${['all', 'downloads', 'linkgrabber', 'settings'].map((filter) => `<button class="filter-chip ${activeFilter === filter ? 'active' : ''}" type="button" data-filter="${filter}" aria-pressed="${activeFilter === filter}">${filter === 'linkgrabber' ? 'LinkGrabber' : filter[0].toUpperCase() + filter.slice(1)}</button>`).join('')}
        <span class="toolbar-spacer"></span><button class="history-action" type="button" data-history-action="undo">Undo</button><button class="history-action" type="button" data-history-action="redo">Redo</button>
      </div>
      <ol class="history-list">${entries.map((item) => `<li class="history-item"><i class="history-node"></i><span class="history-copy"><strong>${escapeText(item.title)}</strong><small>${escapeText(item.detail)}</small></span><span class="history-meta"><b>${escapeText(item.time)}</b><span>${escapeText(item.revision)}</span></span></li>`).join('')}</ol>
    </section>`;
}

const settingsSections = ['general', 'downloads', 'linkgrabber', 'appearance', 'backup', 'about'];

function switchControl(label, checked = true) {
  return `<button class="switch ${checked ? 'on' : ''}" type="button" role="switch" aria-checked="${checked}" data-toggle><i></i><span class="sr-only">${escapeText(label)}</span></button>`;
}

function settingsBody() {
  const sections = {
    general: `<h2>General</h2><p class="settings-lede">Default locations and workspace behavior.</p>
      <div class="setting-row"><div><strong>Default download folder</strong><small>Used unless a package overrides it.</small></div><input class="setting-control" value="C:\\Downloads" aria-label="Default download folder"></div>
      <div class="setting-row"><div><strong>Maximum simultaneous downloads</strong><small>Global direct-transfer limit.</small></div><select class="setting-control" aria-label="Maximum simultaneous downloads"><option>3</option><option>5</option><option>8</option></select></div>
      <div class="setting-row"><div><strong>Clipboard monitoring</strong><small>Inspect copied direct links.</small></div>${switchControl('Clipboard monitoring')}</div>`,
    downloads: `<h2>Downloads</h2><p class="settings-lede">Limits, retry, and file handling.</p>
      <div class="setting-row"><div><strong>Maximum downloads per host</strong><small>Keep one host from occupying every slot.</small></div><select class="setting-control" aria-label="Maximum downloads per host"><option>2</option><option>1</option><option>3</option></select></div>
      <div class="setting-row"><div><strong>Collision policy</strong><small>Choose what happens when a file exists.</small></div><select class="setting-control" aria-label="Collision policy"><option>Rename</option><option>Overwrite</option><option>Skip</option></select></div>
      <div class="setting-row"><div><strong>Automatic retry</strong><small>Retry bounded transient failures.</small></div>${switchControl('Automatic retry')}</div>`,
    linkgrabber: `<h2>LinkGrabber</h2><p class="settings-lede">Control how staged direct links enter the queue.</p>
      <div class="setting-row"><div><strong>Probe availability</strong><small>Check metadata before confirmation.</small></div>${switchControl('Probe availability')}</div>
      <div class="setting-row"><div><strong>Auto-confirm online links</strong><small>Add confirmed links to Downloads.</small></div>${switchControl('Auto-confirm online links', false)}</div>
      <div class="setting-row"><div><strong>Start after confirmation</strong><small>Begin confirmed transfers immediately.</small></div>${switchControl('Start after confirmation', false)}</div>`,
    appearance: `<h2>Appearance</h2><p class="settings-lede">Theme and interface language apply immediately.</p>
      <div class="setting-row"><div><strong>Theme</strong><small>Choose light or dark surfaces.</small></div><select class="setting-control" id="settings-theme" aria-label="Theme"><option value="dark" ${root.dataset.theme === 'dark' ? 'selected' : ''}>Dark</option><option value="light" ${root.dataset.theme === 'light' ? 'selected' : ''}>Light</option></select></div>
      <div class="setting-row"><div><strong>Language mode</strong><small>English, playful Hong Kong Cantonese, or both.</small></div><select class="setting-control" aria-label="Language mode"><option>English</option><option lang="yue">香港粵語</option><option>Bilingual</option></select></div>`,
    backup: `<h2>Backup</h2><p class="settings-lede">Encrypted settings backup stays a local file operation.</p>
      <div class="setting-row"><div><strong>Export encrypted settings</strong><small>Create an AES-256-GCM protected backup.</small></div><button class="app-action" type="button" data-settings-action="export">Export</button></div>
      <div class="setting-row"><div><strong>Import encrypted settings</strong><small>Validate and restore a settings backup.</small></div><button class="app-action" type="button" data-settings-action="import">Import</button></div>`,
    about: `<h2>About</h2><p class="settings-lede">JDownloader Material interactive browser preview.</p>
      <div class="setting-row"><div><strong>Project</strong><small>Independent direct HTTP(S) download workspace.</small></div><a class="app-action" href="https://github.com/Ding-Ding-Projects/jdownloader-material" target="_top">Repository</a></div>
      <div class="setting-row"><div><strong>Preview data</strong><small>No network transfers run in this browser demo.</small></div><span class="number-cell">Sample only</span></div>`
  };
  return sections[settingsSection];
}

function settingsScreen() {
  return `${pageHeader('Preferences', 'Settings', '<span>Sample settings</span>')}
    <section class="workspace-panel settings-panel" aria-label="Sample settings">
      <nav class="settings-nav" aria-label="Settings sections">${settingsSections.map((section) => `<button class="settings-tab ${settingsSection === section ? 'active' : ''}" type="button" data-settings-section="${section}" aria-current="${settingsSection === section ? 'page' : 'false'}">${section === 'linkgrabber' ? 'LinkGrabber' : section[0].toUpperCase() + section.slice(1)}</button>`).join('')}</nav>
      <div class="settings-body">${settingsBody()}</div>
    </section>`;
}

function render(focusTitle = false) {
  const screens = {
    downloads: downloadsScreen,
    linkgrabber: linkgrabberScreen,
    history: historyScreen,
    settings: settingsScreen
  };
  content.innerHTML = screens[activeScreen]();
  document.querySelectorAll('[data-screen]').forEach((button) => {
    const active = button.dataset.screen === activeScreen;
    button.classList.toggle('active', active);
    if (active) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
  });
  const searchable = activeScreen === 'downloads' || activeScreen === 'linkgrabber';
  search.disabled = !searchable;
  search.placeholder = searchable ? `Search ${activeScreen === 'downloads' ? 'downloads' : 'links'}` : 'Search unavailable here';
  if (focusTitle) content.querySelector('h1')?.focus({ preventScroll: true });
  postParent({ type: 'jd-active-screen', screen: activeScreen });
}

async function changeScreen(screen, focusTitle = false) {
  if (!['downloads', 'linkgrabber', 'history', 'settings'].includes(screen)) return;
  if (screen === activeScreen) {
    if (focusTitle) content.querySelector('h1')?.focus({ preventScroll: true });
    return;
  }
  if (!reduceMotion.matches && content.animate) {
    content.classList.add('changing');
    const exit = content.animate([{ opacity: 1, transform: 'translateY(0)' }, { opacity: 0, transform: 'translateY(-5px)' }], { duration: 120, easing: 'cubic-bezier(.2,0,0,1)', fill: 'forwards' });
    await exit.finished.catch(() => {});
  }
  activeScreen = screen;
  activeFilter = 'all';
  search.value = '';
  render(focusTitle);
  content.classList.remove('changing');
  if (!reduceMotion.matches && content.animate) content.animate([{ opacity: 0, transform: 'translateY(7px)' }, { opacity: 1, transform: 'none' }], { duration: 210, easing: 'cubic-bezier(.2,0,0,1)' });
}

function updateQueue(state) {
  downloads.forEach((item, index) => {
    if (item.state === 'Finished') return;
    item.state = state;
    item.speed = state === 'Downloading' ? (index === 0 ? '11.2 MB/s' : index === 1 ? '7.2 MB/s' : '1.1 MB/s') : '—';
    item.eta = state === 'Downloading' ? (index === 0 ? '03:02' : index === 1 ? '03:18' : '11:42') : '—';
  });
  queueState = state;
  const running = state === 'Downloading';
  speedOutput.value = running ? '19.5 MB/s' : '0 MB/s';
  throughput.classList.toggle('stopped', !running);
  globalStatus.value = running ? '19.5 MB/s · 3 active · 8.3 GB remaining' : `0 MB/s · 0 active · 8.3 GB remaining`;
  if (activeScreen !== 'downloads') {
    activeScreen = 'downloads';
    activeFilter = 'all';
  }
  render();
  content.querySelectorAll('tbody tr').forEach((row) => row.classList.add('row-update'));
  toast(state === 'Downloading' ? 'Downloads started' : state === 'Paused' ? 'Downloads paused' : 'Downloads stopped');
}

function openDrawer(trigger = document.activeElement) {
  lastDrawerFocus = trigger;
  drawer.inert = false;
  drawer.setAttribute('aria-hidden', 'false');
  drawer.classList.add('open');
  scrim.classList.add('open');
  scrim.setAttribute('aria-hidden', 'false');
  requestAnimationFrame(() => linksInput.focus());
}

function closeDrawer(restoreFocus = true) {
  drawer.classList.remove('open');
  scrim.classList.remove('open');
  drawer.setAttribute('aria-hidden', 'true');
  scrim.setAttribute('aria-hidden', 'true');
  drawer.inert = true;
  linksError.textContent = '';
  linksInput.removeAttribute('aria-invalid');
  if (restoreFocus) lastDrawerFocus?.focus?.();
}

document.querySelectorAll('[data-screen]').forEach((button) => {
  button.addEventListener('click', () => changeScreen(button.dataset.screen, true));
});

document.querySelector('#start-button').addEventListener('click', () => updateQueue('Downloading'));
document.querySelector('#pause-button').addEventListener('click', () => updateQueue('Paused'));
document.querySelector('#stop-button').addEventListener('click', () => updateQueue('Stopped'));
document.querySelector('#add-links-button').addEventListener('click', (event) => openDrawer(event.currentTarget));
document.querySelector('#close-drawer').addEventListener('click', () => closeDrawer());
document.querySelector('#cancel-drawer').addEventListener('click', () => closeDrawer());
scrim.addEventListener('click', () => closeDrawer());

themeButton.addEventListener('click', () => setTheme(root.dataset.theme === 'dark' ? 'light' : 'dark'));
search.addEventListener('input', () => render());

content.addEventListener('click', (event) => {
  const filter = event.target.closest('[data-filter]');
  const tableAction = event.target.closest('[data-table-action]');
  const historyAction = event.target.closest('[data-history-action]');
  const sectionButton = event.target.closest('[data-settings-section]');
  const settingsAction = event.target.closest('[data-settings-action]');
  const toggle = event.target.closest('[data-toggle]');

  if (filter) {
    activeFilter = filter.dataset.filter;
    render();
    content.querySelector(`[data-filter="${activeFilter}"]`)?.focus();
  }
  if (tableAction) {
    const messages = { move: 'Choose a queue item to move', remove: 'Choose a queue item to remove', paste: 'Clipboard inspection requested', 'add-all': 'Online links added to Downloads' };
    toast(messages[tableAction.dataset.tableAction]);
  }
  if (historyAction) toast(`${historyAction.dataset.historyAction === 'undo' ? 'Undo' : 'Redo'} appended a new history event`);
  if (sectionButton) {
    settingsSection = sectionButton.dataset.settingsSection;
    render();
    content.querySelector(`[data-settings-section="${settingsSection}"]`)?.focus();
  }
  if (settingsAction) toast(`${settingsAction.dataset.settingsAction === 'export' ? 'Export' : 'Import'} chooser would open in the desktop app`);
  if (toggle) {
    const on = toggle.getAttribute('aria-checked') !== 'true';
    toggle.setAttribute('aria-checked', String(on));
    toggle.classList.toggle('on', on);
  }
});

content.addEventListener('change', (event) => {
  if (event.target.id === 'settings-theme') setTheme(event.target.value);
});

drawer.addEventListener('click', (event) => {
  const toggle = event.target.closest('[data-toggle]');
  if (!toggle) return;
  const on = toggle.getAttribute('aria-checked') !== 'true';
  toggle.setAttribute('aria-checked', String(on));
  toggle.classList.toggle('on', on);
});

document.querySelector('#add-links-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const submission = event.submitter?.value === 'start' ? 'start' : 'queue';
  const values = linksInput.value.split(/\r?\n/).map((value) => value.trim()).filter(Boolean);
  const urls = [];
  let valid = values.length > 0;

  for (const value of values) {
    try {
      const url = new URL(value);
      if (!['http:', 'https:'].includes(url.protocol)) valid = false;
      else urls.push(url);
    } catch {
      valid = false;
    }
  }

  if (!valid || urls.length !== values.length) {
    linksInput.setAttribute('aria-invalid', 'true');
    linksError.textContent = 'Enter at least one valid HTTP(S) link.';
    linksInput.focus();
    return;
  }

  urls.reverse().forEach((url) => {
    const encodedName = url.pathname.split('/').filter(Boolean).pop() || 'download';
    let pathname = encodedName;
    try { pathname = decodeURIComponent(encodedName); } catch { /* Keep the valid encoded URL name. */ }
    const detail = document.querySelector('#package-input').value || 'New downloads';
    if (submission === 'start') {
      downloads.unshift({ name: pathname, detail, size: '—', host: url.host, state: 'Downloading', progress: 0, speed: 'Starting…', eta: '—' });
    } else {
      grabbed.unshift({ name: pathname, detail, availability: 'Checking', host: url.host, size: '—', priority: 'Normal' });
    }
  });

  closeDrawer(false);
  await changeScreen(submission === 'start' ? 'downloads' : 'linkgrabber', true);
  toast(submission === 'start'
    ? `${urls.length} ${urls.length === 1 ? 'download' : 'downloads'} started`
    : `${urls.length} ${urls.length === 1 ? 'link' : 'links'} staged in LinkGrabber`);
});

document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && drawer.classList.contains('open')) {
    event.preventDefault();
    closeDrawer();
    return;
  }
  if (event.key !== 'Tab' || !drawer.classList.contains('open')) return;
  const focusable = [...drawer.querySelectorAll('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href]')];
  const first = focusable[0];
  const last = focusable.at(-1);
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
});

window.addEventListener('message', (event) => {
  if (event.origin !== location.origin) return;
  if (event.data?.type === 'jd-screen') changeScreen(event.data.screen, true);
  if (event.data?.type === 'jd-action' && event.data.action === 'add-links') openDrawer(document.querySelector('#add-links-button'));
  if (event.data?.type === 'jd-theme') setTheme(event.data.theme, false, false);
});

setTheme(root.dataset.theme, false, false);
render();
