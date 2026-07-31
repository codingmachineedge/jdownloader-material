'use strict';

(() => {
  const STORAGE = Object.freeze({
    preferences: 'jd-material-site-preferences-v3',
    notifications: 'jd-material-site-notifications-v2',
    tabs: 'jd-material-site-tabs-v3',
    appearance: 'jd-material-site-appearance-v3',
    firstRun: 'jd-material-site-first-run-v2',
    dimSumSession: 'jd-material-site-dimsum-drawn-v2'
  });
  const LIMITS = Object.freeze({ pattern: 256, sample: 2048, matches: 100, history: 100, importBytes: 262144 });
  const html = document.documentElement;
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const $ = (selector, scope = document) => scope.querySelector(selector);
  const $$ = (selector, scope = document) => [...scope.querySelectorAll(selector)];

  function readObject(key, fallback = {}) {
    try {
      const value = JSON.parse(localStorage.getItem(key) || 'null');
      return value && typeof value === 'object' && !Array.isArray(value) ? value : structuredClone(fallback);
    } catch {
      return structuredClone(fallback);
    }
  }

  function writeObject(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch {
      return false;
    }
  }

  function clamp(value, minimum, maximum, fallback = minimum) {
    const number = Number(value);
    return Number.isFinite(number) ? Math.max(minimum, Math.min(maximum, number)) : fallback;
  }

  function safeText(value, maximum = 4000) {
    return String(value ?? '').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, '').slice(0, maximum);
  }

  function downloadLocalFile(name, type, text) {
    const blob = new Blob([text], { type });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = name;
    anchor.hidden = true;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  const COPY = Object.freeze({
    'settings.open': { en: 'Settings', yue: '設定' },
    'tab.overview': { en: 'Overview', yue: '總覽' },
    'tab.workflow': { en: 'Workflow', yue: '流程' },
    'tab.interface': { en: 'Interface demo', yue: '介面示範' },
    'tab.architecture': { en: 'Architecture', yue: '架構' },
    'tab.features': { en: 'Features', yue: '功能' },
    'tab.guide': { en: 'Feature guide', yue: '功能指南' },
    'tab.install': { en: 'Install', yue: '安裝' },
    'tab.settings': { en: 'Settings', yue: '設定' },
    'guide.eyebrow': { en: 'In-site documentation', yue: '網站內置文件' },
    'guide.title': { en: 'Every shipped desktop feature, explained here.', yue: '每個已推出嘅桌面功能，呢度逐樣講清楚。' },
    'guide.intro': { en: 'Search the articles locally. Each contract names behavior, configuration, failure modes, security, verification, and related reading.', yue: '文件搜尋只喺本機做；每篇都交代行為、設定、失敗情況、安全、驗證同相關文章。' },
    'search.guide': { en: 'Search feature articles', yue: '搜尋功能文章' },
    'settings.eyebrow': { en: 'Local preferences', yue: '本機偏好設定' },
    'settings.title': { en: 'Settings that apply live and stay on this device.', yue: '即時生效、淨係留喺呢部機嘅設定。' },
    'settings.disclosure': { en: 'Funny levels style every message, including errors and warnings; facts and choices never change. Reset either language at any time.', yue: '搞笑程度會調校所有訊息，包括錯誤同警告；事實同選項絕對唔會變。兩種語言隨時可以重設。' }
  });

  const defaultPreferences = Object.freeze({
    language: 'en', funnyEn: 1, funnyYue: 1, density: 'comfortable', accent: '#73d7c2',
    dimSumEnabled: true, quietMode: false
  });

  function normalizePreferences(raw) {
    return {
      language: ['en', 'yue', 'both'].includes(raw.language) ? raw.language : defaultPreferences.language,
      funnyEn: clamp(raw.funnyEn, 1, 5, 1),
      funnyYue: clamp(raw.funnyYue, 1, 5, 1),
      density: ['compact', 'comfortable', 'spacious'].includes(raw.density) ? raw.density : 'comfortable',
      accent: /^#[0-9a-f]{6}$/i.test(raw.accent || '') ? raw.accent : defaultPreferences.accent,
      dimSumEnabled: raw.dimSumEnabled !== false,
      quietMode: raw.quietMode === true
    };
  }

  let preferences = normalizePreferences({ ...defaultPreferences, ...readObject(STORAGE.preferences) });

  function setLocalizedText(element, key) {
    const copy = COPY[key];
    if (!copy) return;
    element.replaceChildren();
    if (preferences.language === 'yue') {
      element.lang = 'yue';
      element.textContent = copy.yue;
      return;
    }
    element.lang = 'en';
    element.append(document.createTextNode(copy.en));
    if (preferences.language === 'both') {
      const secondary = document.createElement('span');
      secondary.className = 'localized-secondary';
      secondary.lang = 'yue';
      secondary.textContent = copy.yue;
      element.append(secondary);
    }
  }

  function tonedLine(kind, facts = '') {
    const en = {
      saved: [
        'Saved.', 'Saved cleanly.', 'Saved; the paperwork behaved.',
        'Saved; even the pixels filed their forms.', 'Saved; the settings cupboard is now suspiciously organized.'
      ],
      sample: [
        'Sample notification.', 'Sample notification delivered.', 'Sample notification delivered; no drama detected.',
        'Sample notification delivered; the toast has excellent posture.', 'Sample notification delivered; tiny rectangle, enormous administrative confidence.'
      ],
      error: [
        'The action failed.', 'The action failed; details follow.', 'The action failed; the code tripped over a very visible chair.',
        'The action failed; the gremlin has been named and the facts follow.', 'The action failed; the code attempted interpretive dance, and the exact recovery is below.'
      ]
    };
    const yue = {
      saved: ['已儲存。', '穩陣儲好。', '儲好喇，啲設定今次好合作。', '儲好喇，啲像素排隊交表。', '儲好晒，設定櫃企得仲直過茶樓點心紙。'],
      sample: ['示範通知。', '示範通知已送到。', '示範通知到咗，暫時冇戲劇效果。', '示範通知到咗，呢塊 toast 企得幾有自信。', '示範通知到咗：細細塊長方形，行政氣場大過成個蒸籠。'],
      error: ['操作失敗。', '操作失敗，詳情如下。', '操作失敗，段 code 撞正張好光猛嘅櫈。', '操作失敗，隻曳曳已經點名，事實喺下面。', '操作失敗，段 code 突然跳現代舞；實際問題同補救方法一樣照列。']
    };
    const enText = (en[kind] || en.saved)[preferences.funnyEn - 1];
    const yueText = (yue[kind] || yue.saved)[preferences.funnyYue - 1];
    const voiced = preferences.language === 'en' ? enText : preferences.language === 'yue' ? yueText : `${enText} · ${yueText}`;
    return facts ? `${voiced} ${facts}` : voiced;
  }

  function applyPreferences({ persist = true } = {}) {
    preferences = normalizePreferences(preferences);
    html.dataset.language = preferences.language;
    html.lang = preferences.language === 'yue' ? 'yue' : 'en';
    html.dataset.density = preferences.density;
    html.style.setProperty('--m3-seed', preferences.accent);
    $$('[data-copy-key]').forEach((element) => setLocalizedText(element, element.dataset.copyKey));
    $$('[data-language-picker]').forEach((control) => { control.value = preferences.language; });
    $$('[data-funny-en]').forEach((control) => { control.value = String(preferences.funnyEn); });
    $$('[data-funny-yue]').forEach((control) => { control.value = String(preferences.funnyYue); });
    $$('[data-funny-en-output]').forEach((output) => { output.value = String(preferences.funnyEn); output.textContent = String(preferences.funnyEn); });
    $$('[data-funny-yue-output]').forEach((output) => { output.value = String(preferences.funnyYue); output.textContent = String(preferences.funnyYue); });
    $$('[data-density-picker]').forEach((control) => { control.value = preferences.density; });
    $$('[data-accent-picker]').forEach((control) => { control.value = preferences.accent; });
    $$('[data-dimsum-enabled]').forEach((control) => { control.checked = preferences.dimSumEnabled; });
    $$('[data-quiet-mode]').forEach((control) => { control.checked = preferences.quietMode; });
    $$('[data-theme-picker]').forEach((control) => { control.value = html.dataset.theme === 'light' ? 'light' : 'dark'; });
    if (persist) writeObject(STORAGE.preferences, preferences);
    window.dispatchEvent(new CustomEvent('jd-site-preferences', { detail: structuredClone(preferences) }));
  }

  $$('[data-language-picker]').forEach((control) => control.addEventListener('change', () => {
    preferences.language = control.value;
    applyPreferences();
    announce('success', tonedLine('saved'), 'Language mode changed without reloading the page.');
  }));
  $$('[data-funny-en]').forEach((control) => control.addEventListener('input', () => { preferences.funnyEn = Number(control.value); applyPreferences(); }));
  $$('[data-funny-yue]').forEach((control) => control.addEventListener('input', () => { preferences.funnyYue = Number(control.value); applyPreferences(); }));
  $$('[data-density-picker]').forEach((control) => control.addEventListener('change', () => { preferences.density = control.value; applyPreferences(); }));
  $$('[data-accent-picker]').forEach((control) => control.addEventListener('input', () => { preferences.accent = control.value; applyPreferences(); }));
  $$('[data-dimsum-enabled]').forEach((control) => control.addEventListener('change', () => { preferences.dimSumEnabled = control.checked; applyPreferences(); }));
  $$('[data-quiet-mode]').forEach((control) => control.addEventListener('change', () => { preferences.quietMode = control.checked; applyPreferences(); }));
  $$('[data-theme-picker]').forEach((control) => control.addEventListener('change', () => { if (typeof applyTheme === 'function') applyTheme(control.value); applyPreferences(); }));
  $('[data-reset-voice]')?.addEventListener('click', () => {
    preferences.language = defaultPreferences.language;
    preferences.funnyEn = defaultPreferences.funnyEn;
    preferences.funnyYue = defaultPreferences.funnyYue;
    applyPreferences();
    announce('success', tonedLine('saved'), 'Language and both funny levels were reset.');
  });

  // ---------------------------------------------------------------- Notifications
  let notifications = (() => {
    const value = readObject(STORAGE.notifications, { items: [] });
    return Array.isArray(value.items) ? value.items.slice(0, LIMITS.history) : [];
  })();
  const toastRegion = $('#toast-region');
  const notificationCenter = $('#notification-center');
  let notificationReturnFocus = null;

  function persistNotifications() {
    if (!writeObject(STORAGE.notifications, { items: notifications.slice(0, LIMITS.history) })) {
      // The live toast still works if storage is unavailable; avoid recursive persistence attempts.
      console.warn('JDownloader Material notification history could not persist.');
    }
  }

  function notificationMatches(item, controller) {
    return !controller || controller.matches(`${item.title}\n${item.body}\n${item.severity}`).match;
  }

  function renderNotificationHistory(controller = searchControllers.get('notifications')) {
    const host = $('[data-notification-history]');
    if (!host) return;
    host.replaceChildren();
    const visible = notifications.filter((item) => notificationMatches(item, controller));
    for (const item of visible) {
      const article = document.createElement('article');
      article.dataset.severity = item.severity;
      const title = document.createElement('strong'); title.textContent = item.title;
      const body = document.createElement('p'); body.textContent = item.body;
      const time = document.createElement('time'); time.dateTime = item.createdAt; time.textContent = new Date(item.createdAt).toLocaleString();
      article.append(title, body, time);
      host.append(article);
    }
    if (!visible.length) {
      const empty = document.createElement('p'); empty.textContent = 'No notifications match the current local filter.'; host.append(empty);
    }
    const count = $('[data-notification-count]');
    if (count) { count.textContent = String(notifications.length); count.hidden = notifications.length === 0; }
  }

  function dismissToast(toast) { toast?.remove(); }

  function announce(severity, title, body, options = {}) {
    const item = {
      id: crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      severity: ['info', 'success', 'warning', 'error'].includes(severity) ? severity : 'info',
      title: safeText(title, 180), body: safeText(body, 1200), createdAt: new Date().toISOString()
    };
    notifications.unshift(item);
    notifications = notifications.slice(0, LIMITS.history);
    persistNotifications();
    renderNotificationHistory();
    if (!toastRegion) return item;
    const toast = document.createElement('article');
    toast.className = 'm3-toast'; toast.dataset.severity = item.severity; toast.tabIndex = 0;
    toast.setAttribute('role', item.severity === 'error' || item.severity === 'warning' ? 'alert' : 'status');
    const copy = document.createElement('div');
    const heading = document.createElement('strong'); heading.textContent = item.title;
    const text = document.createElement('p'); text.textContent = item.body;
    copy.append(heading, text);
    if (typeof options.action === 'function' && options.actionLabel) {
      const action = document.createElement('button'); action.type = 'button'; action.textContent = safeText(options.actionLabel, 40);
      action.addEventListener('click', () => { options.action(); dismissToast(toast); }); copy.append(action);
    }
    const dismiss = document.createElement('button'); dismiss.type = 'button'; dismiss.setAttribute('aria-label', 'Dismiss notification'); dismiss.textContent = '×'; dismiss.addEventListener('click', () => dismissToast(toast));
    toast.append(document.createElement('span'), copy, dismiss);
    toastRegion.append(toast);
    const timeout = item.severity === 'success' ? 5000 : item.severity === 'info' ? 6500 : 0;
    if (timeout) setTimeout(() => dismissToast(toast), timeout);
    return item;
  }

  $('[data-test-notification]')?.addEventListener('click', () => announce('success', tonedLine('sample'), 'Settings remain local and reviewable in notification history.'));
  $$('[data-open-notifications]').forEach((button) => button.addEventListener('click', () => {
    notificationReturnFocus = button; notificationCenter.hidden = false; renderNotificationHistory();
    $('[data-close-notifications]', notificationCenter)?.focus({ preventScroll: true });
  }));
  $$('[data-close-notifications]').forEach((button) => button.addEventListener('click', () => { notificationCenter.hidden = true; notificationReturnFocus?.focus({ preventScroll: true }); }));
  $('[data-clear-notifications]')?.addEventListener('click', () => { notifications = []; persistNotifications(); renderNotificationHistory(); announce('info', tonedLine('saved'), 'Notification history was cleared before this confirmation was added.'); });

  // --------------------------------------------------------------- Safe search
  function unsafeBrowserPattern(pattern) {
    return pattern.length > LIMITS.pattern
      || /(\([^)]*[+*][^)]*\))[+*{]/.test(pattern)
      || /(\.\*){3,}/.test(pattern)
      || /(?:\+|\*|\{\d+(?:,\d*)?\})\s*(?:\+|\*|\{)/.test(pattern);
  }

  class SafeSearchController {
    constructor(root) {
      this.root = root;
      this.id = safeText(root.dataset.searchId || crypto.randomUUID(), 80).replace(/[^a-z0-9_-]/gi, '-');
      this.input = $('input[type="search"]', root);
      this.button = $('[data-search-builder-button]', root);
      this.status = $('[data-search-status]', root);
      this.state = { mode: 'plain', pattern: '', flags: ['i', 'u'], sample: '' };
      this.builder = this.createBuilder();
      this.input?.addEventListener('input', () => { this.state.pattern = this.input.value.slice(0, LIMITS.pattern); this.syncBuilder(); this.evaluateAndDispatch(); });
      this.button?.addEventListener('click', () => this.toggleBuilder());
      this.evaluateAndDispatch();
    }

    createBuilder() {
      const template = $('#safe-search-builder-template');
      const builder = template.content.firstElementChild.cloneNode(true);
      builder.dataset.builderFor = this.id;
      const titleId = `builder-title-${this.id}`;
      $('[data-builder-title]', builder).id = titleId;
      builder.setAttribute('aria-labelledby', titleId);
      $$('[data-builder-mode]', builder).forEach((radio) => { radio.name = `search-mode-${this.id}`; radio.addEventListener('change', () => { if (radio.checked) this.state.mode = radio.value; this.evaluateAndDispatch(); }); });
      $$('[data-builder-flag]', builder).forEach((flag) => flag.addEventListener('change', () => { this.state.flags = $$('[data-builder-flag]:checked', builder).map((item) => item.value); this.evaluateAndDispatch(); }));
      const pattern = $('[data-builder-pattern]', builder);
      pattern.addEventListener('input', () => { this.state.pattern = pattern.value.slice(0, LIMITS.pattern); if (this.input) this.input.value = this.state.pattern; this.evaluateAndDispatch(); });
      const sample = $('[data-builder-sample]', builder);
      sample.addEventListener('input', () => { this.state.sample = sample.value.slice(0, LIMITS.sample); this.evaluateAndDispatch(); });
      $$('[data-builder-guide]', builder).forEach((button) => button.addEventListener('click', () => this.insertGuide(button.dataset.builderGuide)));
      $$('[data-builder-close]', builder).forEach((button) => button.addEventListener('click', () => this.closeBuilder()));
      $('[data-builder-copy]', builder).addEventListener('click', () => this.copyPattern());
      $('[data-builder-export]', builder).addEventListener('click', () => this.exportSpec());
      document.body.append(builder);
      return builder;
    }

    compile() {
      const pattern = this.state.pattern.slice(0, LIMITS.pattern);
      if (!pattern) return { valid: true, empty: true, predicate: () => true, detail: 'Empty query' };
      if (this.state.mode === 'plain') {
        const insensitive = this.state.flags.includes('i');
        const needle = insensitive ? pattern.toLocaleLowerCase() : pattern;
        return { valid: true, predicate: (value) => (insensitive ? value.toLocaleLowerCase() : value).includes(needle), detail: 'Plain text' };
      }
      if (unsafeBrowserPattern(pattern)) return { valid: false, detail: 'Potentially explosive nested or repeated quantifiers are blocked.' };
      try {
        const flags = [...new Set(this.state.flags.filter((flag) => 'imsu'.includes(flag)))].join('');
        const regex = new RegExp(pattern, flags);
        return { valid: true, predicate: (value) => { regex.lastIndex = 0; return regex.test(value); }, detail: `ECMAScript /${flags}/` };
      } catch (error) {
        return { valid: false, detail: error instanceof Error ? error.message : 'Invalid regular expression' };
      }
    }

    matches(value) {
      const compiled = this.compile();
      return { ...compiled, match: compiled.valid && compiled.predicate(safeText(value, LIMITS.sample)) };
    }

    evaluateAndDispatch() {
      const compiled = this.compile();
      this.root.dataset.invalid = String(!compiled.valid);
      this.input?.setAttribute('aria-invalid', String(!compiled.valid));
      if (this.status) this.status.value = compiled.valid ? `${compiled.detail} · local bounded evaluation` : `Invalid pattern · ${compiled.detail}`;
      this.renderSample(compiled);
      this.root.dispatchEvent(new CustomEvent('safe-search-change', { bubbles: true, detail: { controller: this, compiled } }));
      const scope = this.root.dataset.searchScope;
      if (scope?.startsWith('#') || scope?.startsWith('[') || scope?.startsWith('.')) {
        $$(scope).forEach((item) => { item.dataset.searchHidden = String(!(compiled.valid && compiled.predicate(item.textContent || ''))); });
      }
    }

    renderSample(compiled) {
      const validation = $('[data-builder-validation]', this.builder);
      const list = $('[data-builder-matches]', this.builder);
      if (!validation || !list) return;
      validation.dataset.invalid = String(!compiled.valid);
      validation.value = compiled.valid ? compiled.detail : `Invalid · ${compiled.detail}`;
      list.replaceChildren();
      const sample = this.state.sample.slice(0, LIMITS.sample);
      if (!compiled.valid || !sample || !this.state.pattern) return;
      if (this.state.mode === 'plain') {
        const item = document.createElement('li'); item.textContent = compiled.predicate(sample) ? 'Sample contains the plain-text query.' : 'No sample match.'; list.append(item); return;
      }
      const flags = [...new Set([...this.state.flags.filter((flag) => 'imsu'.includes(flag)), 'g'])].join('');
      const started = performance.now();
      try {
        const regex = new RegExp(this.state.pattern, flags);
        let match; let count = 0;
        while ((match = regex.exec(sample)) && count < LIMITS.matches && performance.now() - started < 25) {
          const item = document.createElement('li');
          const captures = match.slice(1).map((capture, index) => `group ${index + 1}=${capture ?? 'unset'}`).join(', ');
          item.textContent = `match ${count + 1} @ ${match.index}: ${match[0] || 'zero-width'}${captures ? ` · ${captures}` : ''}`;
          list.append(item); count += 1;
          if (match[0] === '') regex.lastIndex += 1;
        }
        if (!count) { const item = document.createElement('li'); item.textContent = 'No sample matches.'; list.append(item); }
      } catch { /* compile already reports the error */ }
    }

    syncBuilder() {
      const pattern = $('[data-builder-pattern]', this.builder); if (pattern && pattern !== document.activeElement) pattern.value = this.state.pattern;
      const sample = $('[data-builder-sample]', this.builder); if (sample && sample !== document.activeElement) sample.value = this.state.sample;
    }

    insertGuide(guide) {
      const pattern = $('[data-builder-pattern]', this.builder);
      const selected = pattern.value.slice(pattern.selectionStart, pattern.selectionEnd);
      let insertion = guide;
      if (guide === 'literal') insertion = (selected || 'literal').replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      if (guide === '^$') insertion = `^${selected}$`;
      if (guide === '()') insertion = `(${selected})`;
      pattern.setRangeText(insertion, pattern.selectionStart, pattern.selectionEnd, 'end');
      this.state.mode = 'regex';
      $$('[data-builder-mode]', this.builder).forEach((radio) => { radio.checked = radio.value === 'regex'; });
      this.state.pattern = pattern.value.slice(0, LIMITS.pattern); if (this.input) this.input.value = this.state.pattern;
      this.evaluateAndDispatch(); pattern.focus();
    }

    positionBuilder() {
      if (this.builder.hidden || !this.button) return;
      const anchor = this.button.getBoundingClientRect();
      const width = Math.min(this.builder.offsetWidth || 560, innerWidth - 24);
      const height = Math.min(this.builder.offsetHeight || 600, innerHeight - 24);
      const left = Math.max(12, Math.min(anchor.right - width, innerWidth - width - 12));
      const top = anchor.bottom + 8 + height <= innerHeight - 12 ? anchor.bottom + 8 : Math.max(12, anchor.top - height - 8);
      Object.assign(this.builder.style, { left: `${left}px`, top: `${top}px` });
    }

    toggleBuilder() { if (this.builder.hidden) this.openBuilder(); else this.closeBuilder(); }
    openBuilder() { closeAllSearchBuilders(this); this.syncBuilder(); this.builder.hidden = false; this.button?.setAttribute('aria-expanded', 'true'); requestAnimationFrame(() => { this.positionBuilder(); $('[data-builder-pattern]', this.builder)?.focus({ preventScroll: true }); }); }
    closeBuilder(returnFocus = true) { this.builder.hidden = true; this.button?.setAttribute('aria-expanded', 'false'); if (returnFocus) this.button?.focus({ preventScroll: true }); }

    async copyPattern() {
      const payload = this.state.mode === 'regex' ? `/${this.state.pattern}/${this.state.flags.join('')}` : this.state.pattern;
      try { await navigator.clipboard.writeText(payload); announce('success', tonedLine('saved'), `Pattern copied from ${this.id}.`); }
      catch { $('[data-builder-pattern]', this.builder)?.select(); announce('warning', 'Clipboard unavailable.', 'The pattern remains selected for manual copy.'); }
    }

    exportSpec() {
      downloadLocalFile(`jdownloader-material-${this.id}-search.json`, 'application/json', JSON.stringify({ schemaVersion: 1, dialect: 'Browser ECMAScript', ...this.state }, null, 2));
    }
  }

  const searchControllers = new Map();
  $$('[data-search-surface]').forEach((root) => {
    const controller = new SafeSearchController(root);
    searchControllers.set(controller.id, controller);
  });

  function closeAllSearchBuilders(except) { for (const controller of searchControllers.values()) if (controller !== except) controller.closeBuilder(false); }
  window.addEventListener('resize', () => { for (const controller of searchControllers.values()) controller.positionBuilder(); });
  window.addEventListener('scroll', () => { for (const controller of searchControllers.values()) controller.positionBuilder(); }, { passive: true });
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape') closeAllSearchBuilders(); });
  document.addEventListener('safe-search-change', (event) => {
    const id = event.detail.controller.id;
    if (id === 'notifications') renderNotificationHistory(event.detail.controller);
  });

  applyPreferences({ persist: false });

  // ---------------------------------------------------------- Settings pages
  const settingsTabs = $$('[data-settings-tab]');
  settingsTabs.forEach((tab) => tab.addEventListener('click', () => {
    const id = tab.dataset.settingsTab;
    settingsTabs.forEach((candidate) => {
      const selected = candidate === tab;
      candidate.setAttribute('aria-selected', String(selected));
      candidate.tabIndex = selected ? 0 : -1;
    });
    $$('[data-settings-panel]').forEach((panel) => { panel.hidden = panel.dataset.settingsPanel !== id; });
    tab.focus({ preventScroll: true });
  }));
  $('.settings-tabs')?.addEventListener('keydown', (event) => {
    const current = settingsTabs.indexOf(document.activeElement);
    let next = current;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = (current + 1) % settingsTabs.length;
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = (current - 1 + settingsTabs.length) % settingsTabs.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = settingsTabs.length - 1;
    else return;
    event.preventDefault(); settingsTabs[next]?.click();
  });

  // ------------------------------------------------------- Complete tab model
  const tabElements = $$('[role="tab"][data-site-tab]');
  const tabStrip = $('.site-tabstrip');
  const defaultGroups = Object.freeze([
    { id: 'start', name: 'Start', color: '#73d7c2', order: 0, collapsed: false, pinned: false },
    { id: 'workspace', name: 'Workspace', color: '#a9c7ff', order: 1, collapsed: false, pinned: false },
    { id: 'learn', name: 'Learn', color: '#e8b8ff', order: 2, collapsed: false, pinned: false },
    { id: 'system', name: 'System', color: '#f0c36a', order: 3, collapsed: false, pinned: false }
  ]);

  function baseTabLabel(tab) {
    const keyed = COPY[tab.dataset.copyKey];
    return keyed?.en || tab.textContent.trim().split('\n')[0];
  }

  function normalizeTabState(raw) {
    const ids = tabElements.map((tab) => tab.dataset.siteTab);
    const incomingOrder = Array.isArray(raw.order) ? raw.order.filter((id) => ids.includes(id)) : [];
    const order = [...incomingOrder, ...ids.filter((id) => !incomingOrder.includes(id))];
    const incomingGroups = Array.isArray(raw.groups) ? raw.groups : [];
    const groups = incomingGroups.map((group, index) => ({
      id: safeText(group.id, 40).replace(/[^a-z0-9_-]/gi, '') || `group-${index + 1}`,
      name: safeText(group.name, 40) || `Group ${index + 1}`,
      color: /^#[0-9a-f]{6}$/i.test(group.color || '') ? group.color : '#73d7c2',
      order: index,
      collapsed: group.collapsed === true,
      pinned: group.pinned === true
    })).filter((group, index, all) => all.findIndex((item) => item.id === group.id) === index);
    for (const fallback of defaultGroups) if (!groups.some((group) => group.id === fallback.id)) groups.push({ ...fallback });
    groups.forEach((group, index) => { group.order = index; });
    const membership = {};
    for (const tab of tabElements) {
      const candidate = raw.membership?.[tab.dataset.siteTab] || tab.dataset.tabGroup || 'start';
      membership[tab.dataset.siteTab] = groups.some((group) => group.id === candidate) ? candidate : 'start';
    }
    return {
      order,
      pinned: Array.isArray(raw.pinned) ? raw.pinned.filter((id) => ids.includes(id)) : ['top'],
      closed: Array.isArray(raw.closed) ? raw.closed.filter((id) => ids.includes(id) && id !== 'settings') : [],
      groups,
      membership
    };
  }

  let tabState = normalizeTabState(readObject(STORAGE.tabs));
  let draggedTabId = null;
  let contextTarget = null;
  const groupBar = document.createElement('div');
  groupBar.className = 'tab-group-bar';
  groupBar.setAttribute('aria-label', 'Tab groups');
  tabStrip?.after(groupBar);

  function groupById(id) { return tabState.groups.find((group) => group.id === id); }
  function tabById(id) { return tabElements.find((tab) => tab.dataset.siteTab === id); }
  function isTabPinned(id) {
    const group = groupById(tabState.membership[id]);
    return tabState.pinned.includes(id) || group?.pinned === true;
  }

  function saveTabState() { tabState = normalizeTabState(tabState); writeObject(STORAGE.tabs, tabState); }

  function renderGroupBar() {
    groupBar.replaceChildren();
    for (const group of [...tabState.groups].sort((a, b) => a.order - b.order)) {
      const chip = document.createElement('button');
      chip.type = 'button'; chip.className = 'tab-group-chip'; chip.dataset.groupId = group.id;
      chip.dataset.appearanceTarget = `tab-group.${group.id}`;
      chip.setAttribute('aria-expanded', String(!group.collapsed));
      chip.style.borderColor = group.color;
      const count = tabState.order.filter((id) => tabState.membership[id] === group.id && !tabState.closed.includes(id)).length;
      chip.textContent = `${group.pinned ? '● ' : ''}${group.name} · ${count}`;
      chip.addEventListener('click', () => { group.collapsed = !group.collapsed; saveTabState(); applyTabState(); announce('info', tonedLine('saved'), `${group.name} is ${group.collapsed ? 'collapsed' : 'expanded'}; the preference remains stored.`); });
      chip.addEventListener('contextmenu', (event) => { event.preventDefault(); if (event.shiftKey) openAppearanceEditor(chip, chip); else showContextMenu(chip, event.clientX, event.clientY); });
      groupBar.append(chip);
    }
  }

  function applyTabState({ preserveSearchFilter = false, revealSearchMatches = false } = {}) {
    const active = tabElements.find((tab) => tab.getAttribute('aria-selected') === 'true')?.dataset.siteTab;
    tabState.order.forEach((id, index) => {
      const tab = tabById(id); if (!tab) return;
      const filteredOut = preserveSearchFilter && tab.hidden;
      const group = groupById(tabState.membership[id]);
      const pinned = isTabPinned(id);
      tab.style.order = String((pinned ? -10000 : (group?.order ?? 0) * 1000) + index);
      tab.dataset.pinned = String(pinned);
      tab.dataset.groupId = group?.id || 'start';
      tab.setAttribute('aria-label', `${baseTabLabel(tab)}; ${pinned ? 'pinned; ' : ''}group ${group?.name || 'Start'}`);
      const closed = tabState.closed.includes(id);
      tab.dataset.closed = String(closed);
      tab.hidden = closed || filteredOut;
      const collapsed = group?.collapsed && id !== active && !(revealSearchMatches && !filteredOut && !closed);
      tab.dataset.groupCollapsed = String(Boolean(collapsed));
      const panel = document.getElementById(tab.getAttribute('aria-controls'));
      if (panel && closed) panel.hidden = true;
    });
    renderGroupBar(); renderGroupEditor(); renderTabSearchResults(lastModelSearch);
  }

  const originalActivateSiteTab = typeof activateSiteTab === 'function' ? activateSiteTab : null;
  if (originalActivateSiteTab) {
    activateSiteTab = function enhancedActivateSiteTab(id, options = {}) {
      if (tabState.closed.includes(id)) {
        tabState.closed = tabState.closed.filter((item) => item !== id);
        saveTabState();
      }
      const result = originalActivateSiteTab(id, options);
      const hasStripQuery = Boolean($('#site-tab-search')?.value);
      applyTabState({ preserveSearchFilter: hasStripQuery, revealSearchMatches: hasStripQuery });
      return result;
    };
  }

  function moveTabByKeyboard(tab, direction) {
    const id = tab.dataset.siteTab;
    const order = tabState.order;
    const index = order.indexOf(id);
    const next = clamp(index + direction, 0, order.length - 1, index);
    if (next === index) return;
    order.splice(index, 1); order.splice(next, 0, id); saveTabState(); applyTabState(); tab.focus();
    announce('success', tonedLine('saved'), `${baseTabLabel(tab)} moved to position ${next + 1}.`);
  }

  tabStrip?.addEventListener('keydown', (event) => {
    const tab = event.target.closest('[data-site-tab]'); if (!tab) return;
    if (event.ctrlKey && event.shiftKey && (event.key === 'ArrowLeft' || event.key === 'ArrowRight')) {
      event.preventDefault(); event.stopImmediatePropagation(); moveTabByKeyboard(tab, event.key === 'ArrowLeft' ? -1 : 1); return;
    }
    if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() === 'p') {
      event.preventDefault(); event.stopImmediatePropagation(); togglePinned(tab.dataset.siteTab); return;
    }
    if (!event.ctrlKey && ['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
      const ordered = tabState.order.map(tabById).filter((candidate) => candidate && !candidate.hidden && candidate.dataset.groupCollapsed !== 'true');
      const current = ordered.indexOf(tab);
      let next = current;
      if (event.key === 'ArrowRight') next = (current + 1) % ordered.length;
      if (event.key === 'ArrowLeft') next = (current - 1 + ordered.length) % ordered.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = ordered.length - 1;
      if (ordered[next]) { event.preventDefault(); event.stopImmediatePropagation(); activateSiteTab(ordered[next].dataset.siteTab, { focusTab: true }); }
    }
  }, true);

  tabElements.forEach((tab) => {
    tab.addEventListener('dragstart', (event) => { draggedTabId = tab.dataset.siteTab; tab.classList.add('tab-dragging'); event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', draggedTabId); });
    tab.addEventListener('dragend', () => { draggedTabId = null; tab.classList.remove('tab-dragging'); tabElements.forEach((item) => item.classList.remove('tab-drop-target')); });
    tab.addEventListener('dragover', (event) => { if (!draggedTabId || draggedTabId === tab.dataset.siteTab) return; event.preventDefault(); tab.classList.add('tab-drop-target'); });
    tab.addEventListener('dragleave', () => tab.classList.remove('tab-drop-target'));
    tab.addEventListener('drop', (event) => {
      event.preventDefault(); const source = draggedTabId || event.dataTransfer.getData('text/plain'); const target = tab.dataset.siteTab;
      if (!source || source === target) return;
      const order = tabState.order; order.splice(order.indexOf(source), 1); order.splice(order.indexOf(target), 0, source);
      tabState.membership[source] = tabState.membership[target]; saveTabState(); applyTabState();
      announce('success', tonedLine('saved'), `${baseTabLabel(tabById(source))} moved beside ${baseTabLabel(tab)}.`);
    });
  });

  function togglePinned(id) {
    if (!tabById(id)) return;
    tabState.pinned = tabState.pinned.includes(id) ? tabState.pinned.filter((item) => item !== id) : [...tabState.pinned, id];
    saveTabState(); applyTabState();
    announce('success', tonedLine('saved'), `${baseTabLabel(tabById(id))} is now ${isTabPinned(id) ? 'pinned in the protected region' : 'unpinned'}.`);
  }

  function renderGroupEditor() {
    const host = $('[data-group-list]'); const selector = $('[data-group-scope]');
    if (!host || !selector) return;
    const previous = selector.value;
    host.replaceChildren(); selector.replaceChildren();
    for (const group of [...tabState.groups].sort((a, b) => a.order - b.order)) {
      const option = document.createElement('option'); option.value = group.id; option.textContent = group.name; selector.append(option);
      const row = document.createElement('div'); row.className = 'group-row'; row.dataset.appearanceTarget = `group-row.${group.id}`;
      const collapse = document.createElement('button'); collapse.type = 'button'; collapse.textContent = group.collapsed ? '▸' : '▾'; collapse.setAttribute('aria-label', `${group.collapsed ? 'Expand' : 'Collapse'} ${group.name}`); collapse.addEventListener('click', () => { group.collapsed = !group.collapsed; saveTabState(); applyTabState(); });
      const name = document.createElement('input'); name.type = 'text'; name.maxLength = 40; name.value = group.name; name.setAttribute('aria-label', `Name for ${group.name}`); name.addEventListener('change', () => { group.name = safeText(name.value, 40) || group.name; saveTabState(); applyTabState(); });
      const color = document.createElement('input'); color.type = 'color'; color.value = group.color; color.setAttribute('aria-label', `Color for ${group.name}`); color.addEventListener('input', () => { group.color = color.value; saveTabState(); applyTabState(); });
      const up = document.createElement('button'); up.type = 'button'; up.textContent = '↑'; up.setAttribute('aria-label', `Move ${group.name} earlier`); up.disabled = group.order === 0; up.addEventListener('click', () => reorderGroup(group.id, -1));
      const down = document.createElement('button'); down.type = 'button'; down.textContent = '↓'; down.setAttribute('aria-label', `Move ${group.name} later`); down.disabled = group.order === tabState.groups.length - 1; down.addEventListener('click', () => reorderGroup(group.id, 1));
      const pin = document.createElement('button'); pin.type = 'button'; pin.textContent = group.pinned ? 'Unpin group' : 'Pin group'; pin.addEventListener('click', () => { group.pinned = !group.pinned; saveTabState(); applyTabState(); });
      const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = 'Remove'; remove.disabled = group.id === 'start'; remove.addEventListener('click', () => removeGroup(group.id));
      row.append(collapse, name, color, up, down, pin, remove); host.append(row);
    }
    selector.value = tabState.groups.some((group) => group.id === previous) ? previous : tabState.groups[0]?.id || '';
  }

  function reorderGroup(id, direction) {
    const index = tabState.groups.findIndex((group) => group.id === id);
    const next = clamp(index + direction, 0, tabState.groups.length - 1, index);
    if (next === index) return;
    const [group] = tabState.groups.splice(index, 1); tabState.groups.splice(next, 0, group);
    saveTabState(); applyTabState(); announce('success', tonedLine('saved'), `${group.name} moved to group position ${next + 1}.`);
  }

  function removeGroup(id) {
    if (id === 'start') return;
    for (const tabId of tabState.order) if (tabState.membership[tabId] === id) tabState.membership[tabId] = 'start';
    tabState.groups = tabState.groups.filter((group) => group.id !== id); saveTabState(); applyTabState();
    announce('success', tonedLine('saved'), 'The empty group was removed; its tabs moved to Start without being closed.');
  }

  $('[data-add-group]')?.addEventListener('click', () => {
    const input = $('[data-new-group-name]'); const name = safeText(input.value, 40).trim(); if (!name) { input.focus(); return; }
    let id = name.toLocaleLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'group';
    let suffix = 2; const base = id; while (tabState.groups.some((group) => group.id === id)) id = `${base}-${suffix++}`;
    tabState.groups.push({ id, name, color: '#73d7c2', order: tabState.groups.length, collapsed: false, pinned: false });
    input.value = ''; saveTabState(); applyTabState(); announce('success', tonedLine('saved'), `Group ${name} was created.`);
  });

  $('[data-restore-tabs]')?.addEventListener('click', () => { tabState.closed = []; saveTabState(); applyTabState(); announce('success', tonedLine('saved'), 'Every locally closed site tab was restored.'); });

  $('#site-tab-search')?.addEventListener('input', () => {
    const hasStripQuery = Boolean($('#site-tab-search')?.value);
    applyTabState({ preserveSearchFilter: hasStripQuery, revealSearchMatches: hasStripQuery });
  });

  let lastModelSearch = 'group-tabs';
  function renderTabSearchResults(sourceId = lastModelSearch) {
    lastModelSearch = sourceId;
    const host = $('[data-tab-search-results]'); if (!host) return;
    host.replaceChildren();
    const controller = searchControllers.get(sourceId);
    if (sourceId === 'group-names') {
      for (const group of tabState.groups.filter((item) => !controller || controller.matches(item.name).match)) {
        const row = document.createElement('li');
        const label = document.createElement('div'); label.innerHTML = `<strong></strong><div class="tab-result-meta"></div>`; $('strong', label).textContent = group.name; $('.tab-result-meta', label).textContent = `${tabState.order.filter((id) => tabState.membership[id] === group.id).length} tabs · ${group.collapsed ? 'collapsed' : 'expanded'} · ${group.pinned ? 'pinned' : 'ordinary'}`;
        const action = document.createElement('button'); action.type = 'button'; action.textContent = group.collapsed ? 'Reveal' : 'Collapse'; action.addEventListener('click', () => { group.collapsed = !group.collapsed; saveTabState(); applyTabState(); });
        row.append(label, action); host.append(row);
      }
      return;
    }
    const selectedGroup = $('[data-group-scope]')?.value;
    const candidates = tabState.order.filter((id) => sourceId !== 'group-tabs' || tabState.membership[id] === selectedGroup);
    for (const id of candidates) {
      const tab = tabById(id); const labelText = baseTabLabel(tab); if (controller && !controller.matches(labelText).match) continue;
      const group = groupById(tabState.membership[id]);
      const row = document.createElement('li');
      const label = document.createElement('div'); const strong = document.createElement('strong'); strong.textContent = labelText; const meta = document.createElement('div'); meta.className = 'tab-result-meta'; meta.textContent = `Main window · Primary strip · ${group?.name || 'Start'} · ${isTabPinned(id) ? 'pinned' : 'ordinary'}${tabState.closed.includes(id) ? ' · closed' : ''}`; label.append(strong, meta);
      const actions = document.createElement('div'); actions.className = 'inline-actions';
      const open = document.createElement('button'); open.type = 'button'; open.textContent = 'Open'; open.addEventListener('click', () => activateSiteTab(id, { focusPanel: true }));
      const pin = document.createElement('button'); pin.type = 'button'; pin.textContent = isTabPinned(id) ? 'Unpin' : 'Pin'; pin.addEventListener('click', () => togglePinned(id));
      const move = document.createElement('select'); move.setAttribute('aria-label', `Move ${labelText} to group`); for (const candidate of tabState.groups) { const option = document.createElement('option'); option.value = candidate.id; option.textContent = candidate.name; move.append(option); } move.value = tabState.membership[id]; move.addEventListener('change', () => { tabState.membership[id] = move.value; saveTabState(); applyTabState(); });
      actions.append(open, pin, move); row.append(label, actions); host.append(row);
    }
  }

  document.addEventListener('safe-search-change', (event) => {
    if (['group-tabs', 'group-names', 'master-tabs'].includes(event.detail.controller.id)) renderTabSearchResults(event.detail.controller.id);
  });
  $('[data-group-scope]')?.addEventListener('change', () => renderTabSearchResults('group-tabs'));

  const contextMenu = $('#m3-context-menu');
  function showContextMenu(target, x, y) {
    contextTarget = target; contextMenu.hidden = false;
    const width = contextMenu.offsetWidth || 220; const height = contextMenu.offsetHeight || 140;
    contextMenu.style.left = `${Math.max(8, Math.min(x, innerWidth - width - 8))}px`;
    contextMenu.style.top = `${Math.max(8, Math.min(y, innerHeight - height - 8))}px`;
    $('[data-context-pin]', contextMenu).hidden = !target.matches('[data-site-tab]');
    $('[data-context-move]', contextMenu).hidden = !target.matches('[data-site-tab]');
    $('button:not([hidden])', contextMenu)?.focus({ preventScroll: true });
  }

  document.addEventListener('contextmenu', (event) => {
    const target = event.target.closest('[data-site-tab], [data-appearance-target]');
    if (!target) return;
    event.preventDefault(); event.stopImmediatePropagation();
    if (event.shiftKey) openAppearanceEditor(target, target);
    else showContextMenu(target, event.clientX, event.clientY);
  }, true);
  document.addEventListener('click', (event) => { if (!contextMenu.hidden && !event.target.closest('#m3-context-menu')) contextMenu.hidden = true; }, true);
  $('[data-context-edit]')?.addEventListener('click', () => { contextMenu.hidden = true; if (contextTarget) openAppearanceEditor(contextTarget, contextTarget); });
  $('[data-context-pin]')?.addEventListener('click', () => { contextMenu.hidden = true; if (contextTarget?.dataset.siteTab) togglePinned(contextTarget.dataset.siteTab); });
  $('[data-context-move]')?.addEventListener('click', () => { contextMenu.hidden = true; activateSiteTab('settings', { focusPanel: true }); settingsTabs.find((item) => item.dataset.settingsTab === 'navigation')?.click(); $('[data-group-scope]')?.focus(); });

  // --------------------------------------------------------- Guarded bulk close
  const bulkDialog = $('#bulk-close-dialog');
  let pendingBulkClose = [];
  $$('[data-preview-bulk-close]').forEach((button) => button.addEventListener('click', () => {
    const kind = button.dataset.previewBulkClose;
    const id = kind === 'containing' ? 'close-containing' : 'close-not-containing';
    const controller = searchControllers.get(id);
    const compiled = controller?.compile();
    if (!controller?.state.pattern.trim() || !compiled?.valid) {
      announce('warning', 'Bulk close did not run.', 'Enter a non-empty valid plain-text or regex query before previewing tabs.');
      controller?.input?.focus(); return;
    }
    const includePinned = $(`[data-include-pinned="${kind}"]`)?.checked === true;
    const scope = $(`[data-bulk-scope="${kind}"]`)?.value || 'all';
    const selectedGroup = $('[data-group-scope]')?.value || tabState.groups[0]?.id;
    pendingBulkClose = tabState.order.filter((tabId) => {
      if (tabId === 'settings') return false;
      if (scope === 'current' && tabState.membership[tabId] !== selectedGroup) return false;
      if (tabState.closed.includes(tabId) || (!includePinned && isTabPinned(tabId))) return false;
      const match = controller.matches(baseTabLabel(tabById(tabId))).match;
      return kind === 'containing' ? match : !match;
    });
    const mode = controller.state.mode === 'plain' ? 'plain text' : `ECMAScript /${controller.state.flags.join('')}/`;
    const scopeLabel = scope === 'current' ? `selected group ${groupById(selectedGroup)?.name || selectedGroup}` : 'all groups';
    $('[data-bulk-close-summary]').textContent = `${pendingBulkClose.length} tab(s) in ${scopeLabel} match the ${kind === 'containing' ? 'containing' : 'not-containing'} ${mode} predicate. ${includePinned ? 'Pinned tabs are explicitly included.' : 'Pinned tabs remain protected.'}`;
    const list = $('[data-bulk-close-preview]'); list.replaceChildren();
    pendingBulkClose.forEach((tabId) => { const item = document.createElement('li'); item.textContent = `${baseTabLabel(tabById(tabId))} · ${groupById(tabState.membership[tabId])?.name} · ${isTabPinned(tabId) ? 'pinned' : 'ordinary'}`; list.append(item); });
    $('[data-confirm-bulk-close]').disabled = pendingBulkClose.length === 0;
    bulkDialog.showModal();
  }));
  $('[data-confirm-bulk-close]')?.addEventListener('click', (event) => {
    if (!pendingBulkClose.length) { event.preventDefault(); return; }
    const active = tabElements.find((tab) => tab.getAttribute('aria-selected') === 'true')?.dataset.siteTab;
    tabState.closed = [...new Set([...tabState.closed, ...pendingBulkClose])];
    saveTabState(); applyTabState();
    if (active && pendingBulkClose.includes(active)) {
      const fallback = tabState.order.find((id) => !tabState.closed.includes(id)); if (fallback) activateSiteTab(fallback, { focusTab: true });
    }
    announce('success', tonedLine('saved'), `${pendingBulkClose.length} reviewed tab(s) were closed; Restore closed tabs remains available.`);
    pendingBulkClose = [];
  });

  // ---------------------------------------------------------- Dim-sum delight
  const dimSumCatalog = Object.freeze([
    { id: 'har-gow', en: 'Shrimp dumpling', yue: '蝦餃', file: 'assets/dimsum/har-gow.png', alt: 'Shrimp dumpling in a bamboo steamer · 竹籠內嘅蝦餃' },
    { id: 'siu-mai', en: 'Siu mai', yue: '燒賣', file: 'assets/dimsum/siu-mai.png', alt: 'Yellow-wrapped siu mai in a bamboo steamer · 竹籠內嘅燒賣' },
    { id: 'char-siu-bao', en: 'Char siu bao', yue: '叉燒包', file: 'assets/dimsum/char-siu-bao.png', alt: 'Steamed char siu bao in a bamboo steamer · 竹籠內嘅叉燒包' },
    { id: 'egg-tart', en: 'Egg tart', yue: '蛋撻', file: 'assets/dimsum/egg-tart.png', alt: 'Golden egg tart on a dim-sum plate · 金黃蛋撻' }
  ]);

  function unbiasedIndex(maximum) {
    const range = 0x100000000;
    const limit = range - (range % maximum);
    const value = new Uint32Array(1);
    do crypto.getRandomValues(value); while (value[0] >= limit);
    return value[0] % maximum;
  }

  function dimSumEligible() {
    return preferences.dimSumEnabled
      && !preferences.quietMode
      && localStorage.getItem(STORAGE.firstRun) === 'complete'
      && sessionStorage.getItem(STORAGE.dimSumSession) !== 'drawn'
      && sessionStorage.getItem('jd-material-site-mid-task') !== 'true'
      && !document.body.hasAttribute('data-error-active')
      && !location.search.toLocaleLowerCase().includes('update');
  }

  function runDimSumDraw() {
    if (!dimSumEligible()) return;
    sessionStorage.setItem(STORAGE.dimSumSession, 'drawn');
    if (unbiasedIndex(100) !== 0) return;
    const dish = dimSumCatalog[unbiasedIndex(dimSumCatalog.length)];
    const card = $('#dim-sum-surprise');
    $('[data-dimsum-image]', card).src = dish.file;
    $('[data-dimsum-image]', card).alt = dish.alt;
    $('[data-dimsum-name]', card).textContent = `${dish.en} · ${dish.yue}`;
    $('[data-dimsum-copy]', card).textContent = preferences.language === 'yue'
      ? ['一份本機小驚喜，冇網絡追蹤。', '點心到枱，CDN 今日可以抖暑。', '一抽即中，個 browser 差啲想敲鑼。'][Math.min(2, Math.floor((preferences.funnyYue - 1) / 2))]
      : ['A small local delight with no network tracking.', 'A local treat arrived; the CDN can keep napping.', 'One draw, one steamer basket; the browser nearly rang a tiny gong.'][Math.min(2, Math.floor((preferences.funnyEn - 1) / 2))];
    card.hidden = false;
    setTimeout(() => { card.hidden = true; }, 9000);
  }

  $('[data-dismiss-dimsum]')?.addEventListener('click', () => { $('#dim-sum-surprise').hidden = true; });
  const firstRunCard = $('#first-run-disclosure');
  if (localStorage.getItem(STORAGE.firstRun) !== 'complete') {
    firstRunCard.hidden = false;
    sessionStorage.setItem(STORAGE.dimSumSession, 'drawn');
  } else {
    (window.requestIdleCallback || ((callback) => setTimeout(callback, 250)))(runDimSumDraw);
  }
  $('[data-dismiss-first-run]')?.addEventListener('click', () => {
    localStorage.setItem(STORAGE.firstRun, 'complete'); firstRunCard.hidden = true;
    announce('info', tonedLine('saved'), 'Language and tone disclosure acknowledged; both remain adjustable in Settings.');
  });

  applyTabState();
  renderNotificationHistory();

  // ------------------------------------------------- Per-element appearance
  const appearanceEditor = $('#m3-appearance-editor');
  const appearanceControls = $$('[data-style-property]', appearanceEditor);
  const builtInPresets = Object.freeze({
    'material-teal': { name: 'Material Teal', global: { accent: '#73d7c2', theme: 'dark', density: 'comfortable', fontScale: 1, fontWeight: 400 } },
    'high-contrast': { name: 'High Contrast', global: { accent: '#ffff00', theme: 'dark', density: 'spacious', fontScale: 1.08, fontWeight: 600 } },
    'quiet-paper': { name: 'Quiet Paper', global: { accent: '#536d62', theme: 'light', density: 'comfortable', fontScale: 1, fontWeight: 400 } }
  });

  function normalizeAppearance(raw) {
    const profile = { schemaVersion: 1, global: {}, targets: {}, userPresets: {} };
    if (!raw || typeof raw !== 'object') return profile;
    profile.global = raw.global && typeof raw.global === 'object' ? raw.global : {};
    if (raw.targets && typeof raw.targets === 'object') {
      for (const [target, states] of Object.entries(raw.targets).slice(0, 1000)) {
        if (!/^[a-z0-9_.:-]{1,120}$/i.test(target) || !states || typeof states !== 'object') continue;
        profile.targets[target] = {};
        for (const state of ['normal', 'hover', 'focus', 'active', 'disabled']) {
          if (states[state] && typeof states[state] === 'object') profile.targets[target][state] = { ...states[state] };
        }
      }
    }
    if (raw.userPresets && typeof raw.userPresets === 'object') profile.userPresets = raw.userPresets;
    return profile;
  }

  let appearanceProfile = normalizeAppearance(readObject(STORAGE.appearance));
  let appearanceTargetElement = document.body;
  let appearanceTargetId = 'page.body';
  let appearanceState = 'normal';
  let appearanceReturnFocus = null;
  const resetPropertyPicker = $('[data-reset-property]', appearanceEditor);
  [...new Set(appearanceControls.map((control) => control.dataset.styleProperty === 'fontSizeRange' ? 'fontSize' : control.dataset.styleProperty))].sort().forEach((property) => {
    const option = document.createElement('option'); option.value = property; option.textContent = property.replace(/([A-Z])/g, ' $1').replace(/^./, (letter) => letter.toUpperCase()); resetPropertyPicker?.append(option);
  });

  function targetIdFor(element, index = 0) {
    if (element.dataset.appearanceTarget) return element.dataset.appearanceTarget;
    if (element.id) return `element.${element.id.replace(/[^a-z0-9_-]/gi, '-')}`;
    if (element.dataset.siteTab) return `tab.${element.dataset.siteTab}`;
    const tag = element.tagName.toLocaleLowerCase();
    const label = safeText(element.getAttribute('aria-label') || element.textContent, 32).toLocaleLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    return `element.${tag}.${label || index}`;
  }

  const appearanceCandidates = [document.body, ...$$('*', document.body).filter((element) => !element.matches('script, style, template, option, source'))];
  const usedTargetIds = new Set();
  appearanceCandidates.forEach((element, index) => {
    let id = targetIdFor(element, index); let suffix = 2; const base = id;
    while (usedTargetIds.has(id) && element.dataset.appearanceTarget !== id) id = `${base}.${suffix++}`;
    usedTargetIds.add(id); element.dataset.appearanceTarget = id;
    if (!element.hasAttribute('title') && element.matches('section, article, nav, table, iframe, img')) element.title = 'Right-click for Edit appearance…';
  });

  function sanitizeStyle(property, value) {
    const numeric = {
      fontSize: [8, 96, 16], fontWeight: [100, 900, 400], letterSpacing: [-3, 20, 0], wordSpacing: [-3, 40, 0],
      lineHeight: [.8, 3, 1.5], baselineOffset: [-20, 20, 0], borderWidth: [0, 12, 0], borderRadius: [0, 64, 12],
      padding: [0, 64, 0], outlineWidth: [0, 12, 0], glow: [0, 40, 0], shadowX: [-30, 30, 0],
      shadowY: [-30, 30, 4], shadowBlur: [0, 60, 12]
    };
    if (numeric[property]) return clamp(value, ...numeric[property]);
    if (['overline', 'smallCaps', 'superscript', 'subscript'].includes(property)) return value === true;
    if (['color', 'backgroundColor', 'borderColor', 'decorationColor'].includes(property)) return /^(?:#[0-9a-f]{6,8}|rgba?\([0-9., %/]+\))$/i.test(String(value)) ? String(value) : '';
    if (property === 'fontFamily') return safeText(value, 100).replace(/[;{}]/g, '') || 'system-ui';
    if (property === 'fontVariationSettings') return /^\s*(?:'[A-Za-z0-9]{4}'\s+-?\d+(?:\.\d+)?\s*,?\s*)*$/.test(String(value)) ? String(value) : '';
    const allowed = {
      fontStyle: ['normal', 'italic', 'oblique'], textTransform: ['none', 'uppercase', 'lowercase', 'capitalize'],
      underline: ['none', 'solid', 'double', 'dotted', 'wavy'], strike: ['none', 'single', 'double'],
      direction: ['inherit', 'ltr', 'rtl'], textAlign: ['start', 'center', 'end', 'justify']
    };
    if (allowed[property]) return allowed[property].includes(value) ? value : allowed[property][0];
    return safeText(value, 120);
  }

  function styleDeclarations(style) {
    const value = (property) => sanitizeStyle(property, style[property]);
    const declarations = [];
    if (style.fontFamily) declarations.push(`font-family:${JSON.stringify(value('fontFamily'))}, "Microsoft JhengHei UI", sans-serif`);
    if (style.fontSize != null) declarations.push(`font-size:${value('fontSize')}px`);
    if (style.fontWeight != null) declarations.push(`font-weight:${value('fontWeight')}`);
    if (style.fontStyle) declarations.push(`font-style:${value('fontStyle')}`);
    if (style.fontVariationSettings) declarations.push(`font-variation-settings:${value('fontVariationSettings')}`);
    if (style.textTransform) declarations.push(`text-transform:${value('textTransform')}`);
    if (style.smallCaps) declarations.push('font-variant-caps:small-caps');
    if (style.letterSpacing != null) declarations.push(`letter-spacing:${value('letterSpacing')}px`);
    if (style.wordSpacing != null) declarations.push(`word-spacing:${value('wordSpacing')}px`);
    if (style.lineHeight != null) declarations.push(`line-height:${value('lineHeight')}`);
    if (style.baselineOffset != null) declarations.push(`vertical-align:${style.superscript ? 'super' : style.subscript ? 'sub' : `${value('baselineOffset')}px`}`);
    if (style.direction) declarations.push(`direction:${value('direction')}`);
    if (style.textAlign) declarations.push(`text-align:${value('textAlign')}`);
    if (style.color) declarations.push(`color:${value('color')}`);
    if (style.backgroundColor) declarations.push(`background-color:${value('backgroundColor')}`);
    if (style.borderColor) declarations.push(`border-color:${value('borderColor')}`);
    if (style.borderWidth != null) declarations.push(`border-width:${value('borderWidth')}px;border-style:solid`);
    if (style.borderRadius != null) declarations.push(`border-radius:${value('borderRadius')}px`);
    if (style.padding != null) declarations.push(`padding:${value('padding')}px`);
    if (style.outlineWidth != null) declarations.push(`outline:${value('outlineWidth')}px solid ${value('borderColor') || 'currentColor'}`);
    const decorationLines = [];
    if (style.underline && style.underline !== 'none') decorationLines.push('underline');
    if (style.strike && style.strike !== 'none') decorationLines.push('line-through');
    if (style.overline) decorationLines.push('overline');
    if (decorationLines.length) {
      declarations.push(`text-decoration-line:${decorationLines.join(' ')}`);
      declarations.push(`text-decoration-style:${style.strike === 'double' || style.underline === 'double' ? 'double' : style.underline === 'none' ? 'solid' : value('underline')}`);
      if (style.decorationColor) declarations.push(`text-decoration-color:${value('decorationColor')}`);
    }
    const shadows = [];
    if (style.shadowBlur || style.shadowX || style.shadowY) shadows.push(`${value('shadowX') || 0}px ${value('shadowY') || 0}px ${value('shadowBlur') || 0}px rgb(0 0 0 / .45)`);
    if (style.glow) shadows.push(`0 0 ${value('glow')}px ${value('color') || 'currentColor'}`);
    if (shadows.length) declarations.push(`text-shadow:${shadows.join(',')}`);
    return declarations.filter(Boolean).join(';');
  }

  function applyAppearanceProfile() {
    let styleElement = $('#runtime-appearance-overrides');
    if (!styleElement) { styleElement = document.createElement('style'); styleElement.id = 'runtime-appearance-overrides'; document.head.append(styleElement); }
    const rules = [];
    for (const [target, states] of Object.entries(appearanceProfile.targets)) {
      for (const [state, style] of Object.entries(states)) {
        const declarations = styleDeclarations(style); if (!declarations) continue;
        const pseudo = state === 'normal' ? '' : state === 'disabled' ? ':disabled' : `:${state}`;
        rules.push(`[data-appearance-target="${target}"]${pseudo}{${declarations}}`);
      }
    }
    styleElement.textContent = rules.join('\n');
    const global = appearanceProfile.global || {};
    if (/^#[0-9a-f]{6}$/i.test(global.accent || '')) { preferences.accent = global.accent; html.style.setProperty('--m3-seed', global.accent); }
    html.style.setProperty('--m3-font-scale', String(clamp(global.fontScale, .75, 1.75, 1)));
    html.style.setProperty('--m3-font-weight', String(clamp(global.fontWeight, 100, 900, 400)));
    if (['compact', 'comfortable', 'spacious'].includes(global.density)) { preferences.density = global.density; html.dataset.density = global.density; }
    if (['light', 'dark'].includes(global.theme) && typeof applyTheme === 'function') applyTheme(global.theme);
    writeObject(STORAGE.appearance, appearanceProfile);
  }

  function currentTargetStyle() {
    appearanceProfile.targets[appearanceTargetId] ||= {};
    appearanceProfile.targets[appearanceTargetId][appearanceState] ||= {};
    return appearanceProfile.targets[appearanceTargetId][appearanceState];
  }

  function loadAppearanceControls() {
    const style = currentTargetStyle();
    for (const control of appearanceControls) {
      const property = control.dataset.styleProperty;
      const value = property === 'fontSizeRange' ? style.fontSize : style[property];
      if (control.type === 'checkbox') control.checked = value === true;
      else if (value != null && value !== '') control.value = String(value);
    }
    const sample = $('.font-live-sample', appearanceEditor);
    if (sample) sample.setAttribute('style', styleDeclarations(style));
  }

  function positionAppearanceEditor() {
    if (appearanceEditor.hidden || !appearanceReturnFocus) return;
    const anchor = appearanceReturnFocus.getBoundingClientRect();
    const width = Math.min(appearanceEditor.offsetWidth || 780, innerWidth - 24);
    const leftCandidate = anchor.right + 10;
    const left = leftCandidate + width <= innerWidth - 12 ? leftCandidate : Math.max(12, anchor.left - width - 10);
    const top = Math.max(12, Math.min(anchor.top, innerHeight - Math.min(appearanceEditor.offsetHeight || 700, innerHeight - 24) - 12));
    Object.assign(appearanceEditor.style, { left: `${left}px`, right: 'auto', top: `${top}px`, bottom: 'auto' });
  }

  function openAppearanceEditor(element = document.body, anchor = element) {
    appearanceTargetElement = element;
    appearanceTargetId = element.dataset.appearanceTarget || targetIdFor(element);
    element.dataset.appearanceTarget = appearanceTargetId;
    appearanceReturnFocus = anchor;
    $('[data-appearance-target-label]', appearanceEditor).textContent = `${appearanceTargetId} · ${safeText(element.getAttribute('aria-label') || element.textContent || element.tagName, 80)}`;
    appearanceState = $('[data-appearance-state]', appearanceEditor)?.value || 'normal';
    loadAppearanceControls(); appearanceEditor.hidden = false;
    requestAnimationFrame(() => { positionAppearanceEditor(); $('#appearance-editor-search')?.focus({ preventScroll: true }); });
  }

  function closeAppearanceEditor() { appearanceEditor.hidden = true; appearanceReturnFocus?.focus?.({ preventScroll: true }); }

  appearanceControls.forEach((control) => control.addEventListener('input', () => {
    const property = control.dataset.styleProperty;
    const style = currentTargetStyle();
    const realProperty = property === 'fontSizeRange' ? 'fontSize' : property;
    let value = control.type === 'checkbox' ? control.checked : control.value;
    if (['fontSize', 'fontSizeRange', 'fontWeight', 'letterSpacing', 'wordSpacing', 'lineHeight', 'baselineOffset', 'borderWidth', 'borderRadius', 'padding', 'outlineWidth', 'glow', 'shadowX', 'shadowY', 'shadowBlur'].includes(property)) value = Number(value);
    style[realProperty] = sanitizeStyle(realProperty, value);
    if (realProperty === 'superscript' && value) { style.subscript = false; $('[data-style-property="subscript"]', appearanceEditor).checked = false; }
    if (realProperty === 'subscript' && value) { style.superscript = false; $('[data-style-property="superscript"]', appearanceEditor).checked = false; }
    if (realProperty === 'fontSize') appearanceControls.filter((item) => ['fontSize', 'fontSizeRange'].includes(item.dataset.styleProperty) && item !== control).forEach((item) => { item.value = String(style.fontSize); });
    applyAppearanceProfile(); loadAppearanceControls();
  }));

  $('[data-appearance-state]')?.addEventListener('change', (event) => { appearanceState = event.target.value; loadAppearanceControls(); });
  $$('[data-close-appearance]').forEach((button) => button.addEventListener('click', closeAppearanceEditor));
  $('[data-reset-appearance-target]')?.addEventListener('click', () => { delete appearanceProfile.targets[appearanceTargetId]; applyAppearanceProfile(); loadAppearanceControls(); announce('success', tonedLine('saved'), `${appearanceTargetId} returned to inherited Material defaults.`); });
  $('[data-reset-appearance-property]')?.addEventListener('click', () => {
    const property = resetPropertyPicker?.value; if (!property) return;
    delete currentTargetStyle()[property]; applyAppearanceProfile(); loadAppearanceControls();
    announce('success', tonedLine('saved'), `${property} reset for ${appearanceTargetId} in the ${appearanceState} state.`);
  });
  $('[data-save-appearance-preset]')?.addEventListener('click', () => {
    const name = safeText(prompt('Name this local appearance preset:', ''), 60).trim(); if (!name) return;
    appearanceProfile.userPresets[name] = { target: appearanceTargetId, states: structuredClone(appearanceProfile.targets[appearanceTargetId] || {}) };
    applyAppearanceProfile(); announce('success', tonedLine('saved'), `Preset ${name} was saved locally.`);
  });
  $('[data-open-global-appearance]')?.addEventListener('click', (event) => openAppearanceEditor(document.body, event.currentTarget));
  $('[data-edit-site-tab]')?.addEventListener('click', (event) => {
    const tab = tabElements.find((candidate) => candidate.getAttribute('aria-selected') === 'true');
    if (tab) { event.preventDefault(); event.stopImmediatePropagation(); openAppearanceEditor(tab, event.currentTarget); }
  }, true);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'F10' && event.shiftKey) {
      const target = document.activeElement?.closest?.('[data-appearance-target]'); if (target) { event.preventDefault(); openAppearanceEditor(target, target); }
    }
    if (event.key.toLocaleLowerCase() === 'a' && event.ctrlKey && event.shiftKey) {
      event.preventDefault(); event.stopImmediatePropagation();
      openAppearanceEditor(document.activeElement?.closest?.('[data-appearance-target]') || document.body, document.activeElement);
    }
    if (event.key === 'Escape' && !appearanceEditor.hidden) closeAppearanceEditor();
  }, true);
  window.addEventListener('resize', positionAppearanceEditor);
  window.addEventListener('scroll', positionAppearanceEditor, { passive: true });

  $('[data-profile-preset]')?.addEventListener('change', (event) => {
    appearanceProfile.global = structuredClone(builtInPresets[event.target.value]?.global || {});
    applyAppearanceProfile(); applyPreferences(); announce('success', tonedLine('saved'), `${builtInPresets[event.target.value]?.name || 'Preset'} applied live.`);
  });
  $('[data-export-profile]')?.addEventListener('click', () => downloadLocalFile('jdownloader-material-site-appearance.json', 'application/json', JSON.stringify(appearanceProfile, null, 2)));
  $('[data-import-profile]')?.addEventListener('change', async (event) => {
    const file = event.target.files?.[0]; event.target.value = '';
    if (!file) return;
    if (file.size > LIMITS.importBytes) { announce('error', tonedLine('error'), `The appearance file is ${file.size} bytes; the local limit is ${LIMITS.importBytes} bytes.`); return; }
    try {
      const parsed = JSON.parse(await file.text());
      if (parsed.schemaVersion !== 1 || !parsed.targets || typeof parsed.targets !== 'object') throw new Error('Schema version 1 with a targets object is required.');
      appearanceProfile = normalizeAppearance(parsed); applyAppearanceProfile(); applyPreferences();
      announce('success', tonedLine('saved'), `Imported ${Object.keys(appearanceProfile.targets).length} appearance target(s).`);
    } catch (error) { announce('error', tonedLine('error'), safeText(error instanceof Error ? error.message : 'The file is invalid.')); }
  });
  $('[data-reset-all-appearance]')?.addEventListener('click', () => $('#reset-appearance-dialog').showModal());
  $('[data-confirm-reset-appearance]')?.addEventListener('click', () => {
    appearanceProfile = normalizeAppearance({}); applyAppearanceProfile();
    preferences = normalizePreferences(defaultPreferences); applyPreferences();
    announce('success', tonedLine('saved'), 'Global and per-element appearance overrides were reset; built-in presets remain.');
  });

  applyAppearanceProfile();

  // ------------------------------------------- Infinite color translation
  const NAMED_COLORS = Object.freeze({
    black: '#000000', white: '#ffffff', red: '#ff0000', green: '#008000', blue: '#0000ff',
    yellow: '#ffff00', cyan: '#00ffff', aqua: '#00ffff', magenta: '#ff00ff', fuchsia: '#ff00ff',
    gray: '#808080', grey: '#808080', silver: '#c0c0c0', maroon: '#800000', olive: '#808000',
    lime: '#00ff00', teal: '#008080', navy: '#000080', purple: '#800080', orange: '#ffa500',
    transparent: '#00000000'
  });
  const colorEntry = $('[data-color-entry]');
  const colorNative = $('[data-color-native]');
  const colorHue = $('[data-color-hue]');
  const colorAlpha = $('[data-color-alpha]');
  const colorField = $('[data-color-field]');
  let colorState = parseColor('#73d7c2');

  function clipped(value, minimum, maximum) { return Math.max(minimum, Math.min(maximum, value)); }
  function percentValue(value, scale = 1) {
    const text = String(value).trim();
    return text.endsWith('%') ? Number(text.slice(0, -1)) / 100 * scale : Number(text);
  }
  function alphaValue(value) { return value == null || value === '' ? 1 : clipped(percentValue(value, 1), 0, 1); }
  function hueValue(value) { const number = Number(String(value).replace(/deg$/i, '')); return ((number % 360) + 360) % 360; }
  function colorObject(r, g, b, a = 1, source = 'sRGB', wasClipped = false) {
    const values = [r, g, b, a];
    const finite = values.every(Number.isFinite);
    if (!finite) return null;
    const clippedNow = wasClipped || r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255 || a < 0 || a > 1;
    return { r: clipped(r, 0, 255), g: clipped(g, 0, 255), b: clipped(b, 0, 255), a: clipped(a, 0, 1), source, clipped: clippedNow };
  }

  function hslToRgb(h, s, l, a = 1, source = 'HSL') {
    h = hueValue(h) / 360; const rawS = s; const rawL = l; s = clipped(s, 0, 1); l = clipped(l, 0, 1);
    if (s === 0) return colorObject(l * 255, l * 255, l * 255, a, source, rawS !== s || rawL !== l);
    const q = l < .5 ? l * (1 + s) : l + s - l * s; const p = 2 * l - q;
    const channel = (t) => { if (t < 0) t += 1; if (t > 1) t -= 1; if (t < 1 / 6) return p + (q - p) * 6 * t; if (t < .5) return q; if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6; return p; };
    return colorObject(channel(h + 1 / 3) * 255, channel(h) * 255, channel(h - 1 / 3) * 255, a, source, rawS !== s || rawL !== l);
  }

  function hsvToRgb(h, s, v, a = 1, source = 'HSV') {
    h = hueValue(h); const rawS = s; const rawV = v; s = clipped(s, 0, 1); v = clipped(v, 0, 1);
    const c = v * s; const x = c * (1 - Math.abs((h / 60) % 2 - 1)); const m = v - c;
    const sectors = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
    return colorObject((sectors[0] + m) * 255, (sectors[1] + m) * 255, (sectors[2] + m) * 255, a, source, rawS !== s || rawV !== v);
  }

  function hwbToRgb(h, w, b, a = 1) {
    const rawW = w; const rawB = b; w = clipped(w, 0, 1); b = clipped(b, 0, 1);
    if (w + b >= 1) { const gray = w / (w + b); return colorObject(gray * 255, gray * 255, gray * 255, a, 'HWB', rawW !== w || rawB !== b || rawW + rawB > 1); }
    const base = hsvToRgb(h, 1, 1, a, 'HWB'); const factor = 1 - w - b;
    return colorObject((base.r / 255 * factor + w) * 255, (base.g / 255 * factor + w) * 255, (base.b / 255 * factor + w) * 255, a, 'HWB', rawW !== w || rawB !== b);
  }

  function linearToSrgb(value) { return value <= .0031308 ? 12.92 * value : 1.055 * Math.pow(value, 1 / 2.4) - .055; }
  function srgbToLinear(value) { return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4); }

  function labToRgb(l, a, b, alpha = 1, source = 'CIELAB') {
    const fy = (l + 16) / 116; const fx = a / 500 + fy; const fz = fy - b / 200;
    const inverse = (t) => t ** 3 > .008856 ? t ** 3 : (116 * t - 16) / 903.3;
    const x = 95.047 * inverse(fx) / 100; const y = 100 * inverse(fy) / 100; const z = 108.883 * inverse(fz) / 100;
    const red = linearToSrgb(x * 3.2406 + y * -1.5372 + z * -.4986) * 255;
    const green = linearToSrgb(x * -.9689 + y * 1.8758 + z * .0415) * 255;
    const blue = linearToSrgb(x * .0557 + y * -.204 + z * 1.057) * 255;
    return colorObject(red, green, blue, alpha, source, l < 0 || l > 100);
  }

  function oklabToRgb(l, a, b, alpha = 1, source = 'OKLab') {
    const ll = l + .3963377774 * a + .2158037573 * b;
    const mm = l - .1055613458 * a - .0638541728 * b;
    const ss = l - .0894841775 * a - 1.291485548 * b;
    const l3 = ll ** 3; const m3 = mm ** 3; const s3 = ss ** 3;
    const red = linearToSrgb(4.0767416621 * l3 - 3.3077115913 * m3 + .2309699292 * s3) * 255;
    const green = linearToSrgb(-1.2684380046 * l3 + 2.6097574011 * m3 - .3413193965 * s3) * 255;
    const blue = linearToSrgb(-.0041960863 * l3 - .7034186147 * m3 + 1.707614701 * s3) * 255;
    return colorObject(red, green, blue, alpha, source, l < 0 || l > 1);
  }

  function functionalParts(body) {
    const split = body.replace(/,/g, ' ').trim().split(/\s*\/\s*/, 2);
    return { channels: split[0].trim().split(/\s+/).filter(Boolean), alpha: alphaValue(split[1]) };
  }

  function parseColor(input) {
    const text = safeText(input, 160).trim().toLocaleLowerCase();
    if (NAMED_COLORS[text]) { const parsed = parseColor(NAMED_COLORS[text]); return parsed ? { ...parsed, source: `Named (${text})` } : null; }
    let match = text.match(/^#([0-9a-f]{3,8})$/i);
    if (match) {
      let hex = match[1]; if ([3, 4].includes(hex.length)) hex = [...hex].map((item) => item + item).join('');
      if (![6, 8].includes(hex.length)) return null;
      return colorObject(parseInt(hex.slice(0, 2), 16), parseInt(hex.slice(2, 4), 16), parseInt(hex.slice(4, 6), 16), hex.length === 8 ? parseInt(hex.slice(6, 8), 16) / 255 : 1, hex.length === 8 ? 'HEX8' : 'HEX');
    }
    match = text.match(/^rgba?\((.*)\)$/);
    if (match) {
      const parts = functionalParts(match[1]); if (parts.channels.length < 3) return null;
      const values = parts.channels.slice(0, 3).map((item) => percentValue(item, 255));
      const alpha = parts.channels.length > 3 ? alphaValue(parts.channels[3]) : parts.alpha;
      return colorObject(values[0], values[1], values[2], alpha, 'RGB');
    }
    match = text.match(/^hsla?\((.*)\)$/);
    if (match) { const parts = functionalParts(match[1]); if (parts.channels.length < 3) return null; const alpha = parts.channels.length > 3 ? alphaValue(parts.channels[3]) : parts.alpha; return hslToRgb(parts.channels[0], percentValue(parts.channels[1]), percentValue(parts.channels[2]), alpha); }
    match = text.match(/^(?:hsv|hsb)\((.*)\)$/);
    if (match) { const parts = functionalParts(match[1]); if (parts.channels.length < 3) return null; return hsvToRgb(parts.channels[0], percentValue(parts.channels[1]), percentValue(parts.channels[2]), parts.alpha, 'HSV/HSB'); }
    match = text.match(/^hwb\((.*)\)$/);
    if (match) { const parts = functionalParts(match[1]); if (parts.channels.length < 3) return null; return hwbToRgb(parts.channels[0], percentValue(parts.channels[1]), percentValue(parts.channels[2]), parts.alpha); }
    match = text.match(/^lab\((.*)\)$/);
    if (match) { const parts = functionalParts(match[1]); if (parts.channels.length < 3) return null; return labToRgb(percentValue(parts.channels[0], 100), Number(parts.channels[1]), Number(parts.channels[2]), parts.alpha); }
    match = text.match(/^lch\((.*)\)$/);
    if (match) { const parts = functionalParts(match[1]); if (parts.channels.length < 3) return null; const l = percentValue(parts.channels[0], 100); const c = Number(parts.channels[1]); const h = hueValue(parts.channels[2]) * Math.PI / 180; return labToRgb(l, c * Math.cos(h), c * Math.sin(h), parts.alpha, 'CIELCH'); }
    match = text.match(/^oklab\((.*)\)$/);
    if (match) { const parts = functionalParts(match[1]); if (parts.channels.length < 3) return null; return oklabToRgb(percentValue(parts.channels[0]), Number(parts.channels[1]), Number(parts.channels[2]), parts.alpha); }
    match = text.match(/^oklch\((.*)\)$/);
    if (match) { const parts = functionalParts(match[1]); if (parts.channels.length < 3) return null; const l = percentValue(parts.channels[0]); const c = Number(parts.channels[1]); const h = hueValue(parts.channels[2]) * Math.PI / 180; return oklabToRgb(l, c * Math.cos(h), c * Math.sin(h), parts.alpha, 'OKLCH'); }
    match = text.match(/^cmyk\((.*)\)$/);
    if (match) {
      const parts = functionalParts(match[1]); if (parts.channels.length < 4) return null;
      const raw = parts.channels.slice(0, 4).map((item) => percentValue(item)); const [c, m, y, k] = raw.map((item) => clipped(item, 0, 1));
      return colorObject(255 * (1 - c) * (1 - k), 255 * (1 - m) * (1 - k), 255 * (1 - y) * (1 - k), parts.alpha, 'CMYK', raw.some((item, index) => item !== [c, m, y, k][index]));
    }
    return null;
  }

  function rgbToHsl(color) {
    const r = color.r / 255; const g = color.g / 255; const b = color.b / 255; const maximum = Math.max(r, g, b); const minimum = Math.min(r, g, b); const delta = maximum - minimum;
    let h = 0; if (delta) h = maximum === r ? 60 * (((g - b) / delta) % 6) : maximum === g ? 60 * ((b - r) / delta + 2) : 60 * ((r - g) / delta + 4); if (h < 0) h += 360;
    const l = (maximum + minimum) / 2; const s = delta ? delta / (1 - Math.abs(2 * l - 1)) : 0; return { h, s, l };
  }
  function rgbToHsv(color) {
    const r = color.r / 255; const g = color.g / 255; const b = color.b / 255; const maximum = Math.max(r, g, b); const minimum = Math.min(r, g, b); const delta = maximum - minimum;
    let h = 0; if (delta) h = maximum === r ? 60 * (((g - b) / delta) % 6) : maximum === g ? 60 * ((b - r) / delta + 2) : 60 * ((r - g) / delta + 4); if (h < 0) h += 360;
    return { h, s: maximum ? delta / maximum : 0, v: maximum };
  }
  function rgbToLab(color) {
    const r = srgbToLinear(color.r / 255); const g = srgbToLinear(color.g / 255); const b = srgbToLinear(color.b / 255);
    const x = (r * .4124 + g * .3576 + b * .1805) / .95047; const y = r * .2126 + g * .7152 + b * .0722; const z = (r * .0193 + g * .1192 + b * .9505) / 1.08883;
    const f = (value) => value > .008856 ? Math.cbrt(value) : 7.787 * value + 16 / 116;
    const fx = f(x); const fy = f(y); const fz = f(z); return { l: 116 * fy - 16, a: 500 * (fx - fy), b: 200 * (fy - fz) };
  }
  function rgbToOklab(color) {
    const r = srgbToLinear(color.r / 255); const g = srgbToLinear(color.g / 255); const b = srgbToLinear(color.b / 255);
    const l = Math.cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b); const m = Math.cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b); const s = Math.cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b);
    return { l: .2104542553 * l + .793617785 * m - .0040720468 * s, a: 1.9779984951 * l - 2.428592205 * m + .4505937099 * s, b: .0259040371 * l + .7827717662 * m - .808675766 * s };
  }
  function hexByte(value) { return Math.round(clipped(value, 0, 255)).toString(16).padStart(2, '0').toUpperCase(); }
  function fixed(value, places = 3) { return Number(value.toFixed(places)).toString(); }
  function colorTranslations(color) {
    const hsl = rgbToHsl(color); const hsv = rgbToHsv(color); const lab = rgbToLab(color); const oklab = rgbToOklab(color);
    const lchC = Math.hypot(lab.a, lab.b); const lchH = (Math.atan2(lab.b, lab.a) * 180 / Math.PI + 360) % 360;
    const okC = Math.hypot(oklab.a, oklab.b); const okH = (Math.atan2(oklab.b, oklab.a) * 180 / Math.PI + 360) % 360;
    const r = Math.round(color.r); const g = Math.round(color.g); const b = Math.round(color.b); const alpha = fixed(color.a, 3);
    const k = 1 - Math.max(r, g, b) / 255; const c = k === 1 ? 0 : (1 - r / 255 - k) / (1 - k); const m = k === 1 ? 0 : (1 - g / 255 - k) / (1 - k); const y = k === 1 ? 0 : (1 - b / 255 - k) / (1 - k);
    const named = Object.entries(NAMED_COLORS).find(([, hex]) => hex.length === 7 && hex.toUpperCase() === `#${hexByte(r)}${hexByte(g)}${hexByte(b)}`)?.[0] || 'not defined';
    return [
      ['Named', named], ['HEX', `#${hexByte(r)}${hexByte(g)}${hexByte(b)}`], ['HEX8', `#${hexByte(r)}${hexByte(g)}${hexByte(b)}${hexByte(color.a * 255)}`],
      ['RGB / RGBA', `rgba(${r} ${g} ${b} / ${alpha})`], ['HSL / HSLA', `hsl(${fixed(hsl.h, 2)} ${fixed(hsl.s * 100, 2)}% ${fixed(hsl.l * 100, 2)}% / ${alpha})`],
      ['HSV / HSB', `hsv(${fixed(hsv.h, 2)} ${fixed(hsv.s * 100, 2)}% ${fixed(hsv.v * 100, 2)}% / ${alpha})`],
      ['HWB', `hwb(${fixed(hsv.h, 2)} ${fixed(Math.min(r, g, b) / 255 * 100, 2)}% ${fixed((1 - Math.max(r, g, b) / 255) * 100, 2)}% / ${alpha})`],
      ['CIELAB', `lab(${fixed(lab.l, 2)}% ${fixed(lab.a, 3)} ${fixed(lab.b, 3)} / ${alpha})`], ['CIELCH', `lch(${fixed(lab.l, 2)}% ${fixed(lchC, 3)} ${fixed(lchH, 2)} / ${alpha})`],
      ['OKLab', `oklab(${fixed(oklab.l, 4)} ${fixed(oklab.a, 4)} ${fixed(oklab.b, 4)} / ${alpha})`], ['OKLCH', `oklch(${fixed(oklab.l, 4)} ${fixed(okC, 4)} ${fixed(okH, 2)} / ${alpha})`],
      ['CMYK', `cmyk(${fixed(c * 100, 2)}% ${fixed(m * 100, 2)}% ${fixed(y * 100, 2)}% ${fixed(k * 100, 2)}% / ${alpha})`]
    ];
  }

  function relativeLuminance(color) { return .2126 * srgbToLinear(color.r / 255) + .7152 * srgbToLinear(color.g / 255) + .0722 * srgbToLinear(color.b / 255); }
  function contrastRatio(first, second) { const one = relativeLuminance(first); const two = relativeLuminance(second); return (Math.max(one, two) + .05) / (Math.min(one, two) + .05); }

  function setColorState(color, { updateEntry = true } = {}) {
    if (!color) return;
    colorState = color;
    const translations = colorTranslations(color);
    const hex = translations.find(([name]) => name === 'HEX')[1];
    if (updateEntry && colorEntry) colorEntry.value = color.a < 1 ? translations.find(([name]) => name === 'HEX8')[1] : hex;
    if (colorNative) colorNative.value = hex;
    const hsv = rgbToHsv(color);
    if (colorHue) colorHue.value = String(Math.round(hsv.h));
    if (colorAlpha) colorAlpha.value = String(Math.round(color.a * 100));
    colorField?.style.setProperty('--picker-hue', fixed(hsv.h, 2)); colorField?.style.setProperty('--picker-sat', fixed(hsv.s * 100, 2)); colorField?.style.setProperty('--picker-val', fixed(hsv.v * 100, 2));
    colorField?.setAttribute('aria-valuetext', `Hue ${Math.round(hsv.h)} degrees, saturation ${Math.round(hsv.s * 100)}%, value ${Math.round(hsv.v * 100)}%`);
    $('[data-color-preview]')?.style.setProperty('--active-color', translations.find(([name]) => name === 'RGB / RGBA')[1]);
    const validation = $('[data-color-validation]'); validation.value = `${color.source} · sRGB ${color.clipped ? 'clipped to gamut; review before saving' : 'in gamut'} · alpha ${Math.round(color.a * 100)}%`; validation.dataset.invalid = String(color.clipped);
    const background = parseColor(getComputedStyle(appearanceTargetElement || document.body).backgroundColor) || parseColor(html.dataset.theme === 'light' ? '#ffffff' : '#111318');
    const ratio = contrastRatio(color, background);
    $('[data-color-contrast]').value = `Contrast ${fixed(ratio, 2)}:1 against the selected element background · ${ratio >= 7 ? 'AAA' : ratio >= 4.5 ? 'AA normal text' : ratio >= 3 ? 'AA large text only' : 'below WCAG text thresholds'}.`;
    const body = $('[data-color-translations]'); body.replaceChildren();
    for (const [space, value] of translations) {
      const row = document.createElement('tr'); row.dataset.colorSpaceRow = space;
      const heading = document.createElement('th'); heading.scope = 'row'; heading.textContent = space;
      const cell = document.createElement('td'); cell.textContent = value;
      const actionCell = document.createElement('td'); const copy = document.createElement('button'); copy.type = 'button'; copy.textContent = 'Copy'; copy.setAttribute('aria-label', `Copy ${space}`); copy.addEventListener('click', async () => { try { await navigator.clipboard.writeText(value); announce('success', tonedLine('saved'), `${space} value copied.`); } catch { announce('warning', 'Clipboard unavailable.', `${space}: ${value}`); } }); actionCell.append(copy);
      row.append(heading, cell, actionCell); body.append(row);
    }
    searchControllers.get('color-picker')?.evaluateAndDispatch();
  }

  function applyColorEntry() {
    const parsed = parseColor(colorEntry?.value); if (!parsed) { const validation = $('[data-color-validation]'); validation.value = 'Invalid color. Accepted: named, HEX/HEX8, RGB/A, HSL/A, HSV/HSB, HWB, Lab/LCH, OKLab/OKLCH and CMYK.'; validation.dataset.invalid = 'true'; colorEntry?.setAttribute('aria-invalid', 'true'); return; }
    colorEntry?.setAttribute('aria-invalid', 'false'); setColorState(parsed, { updateEntry: false });
  }
  $('[data-apply-color]')?.addEventListener('click', () => {
    applyColorEntry(); if (colorEntry?.getAttribute('aria-invalid') === 'true') return;
    currentTargetStyle().color = colorTranslations(colorState).find(([name]) => name === 'RGB / RGBA')[1];
    applyAppearanceProfile(); loadAppearanceControls(); announce('success', tonedLine('saved'), `Color applied to ${appearanceTargetId} in the ${appearanceState} state.`);
  });
  colorEntry?.addEventListener('change', applyColorEntry);
  colorNative?.addEventListener('input', () => setColorState(parseColor(colorNative.value)));
  colorHue?.addEventListener('input', () => { const hsv = rgbToHsv(colorState); setColorState(hsvToRgb(Number(colorHue.value), hsv.s, hsv.v, colorState.a, 'HSV field')); });
  colorAlpha?.addEventListener('input', () => setColorState({ ...colorState, a: Number(colorAlpha.value) / 100, source: `${colorState.source} + alpha` }));
  function updateSpectrum(event) {
    const box = colorField.getBoundingClientRect(); const saturation = clipped((event.clientX - box.left) / box.width, 0, 1); const value = clipped(1 - (event.clientY - box.top) / box.height, 0, 1);
    setColorState(hsvToRgb(Number(colorHue.value), saturation, value, colorState.a, 'HSV spectrum'));
  }
  colorField?.addEventListener('pointerdown', (event) => { colorField.setPointerCapture(event.pointerId); updateSpectrum(event); });
  colorField?.addEventListener('pointermove', (event) => { if (colorField.hasPointerCapture(event.pointerId)) updateSpectrum(event); });
  colorField?.addEventListener('keydown', (event) => {
    if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return; event.preventDefault(); const hsv = rgbToHsv(colorState);
    if (event.key === 'ArrowLeft') hsv.s -= .01; if (event.key === 'ArrowRight') hsv.s += .01; if (event.key === 'ArrowUp') hsv.v += .01; if (event.key === 'ArrowDown') hsv.v -= .01;
    setColorState(hsvToRgb(hsv.h, clipped(hsv.s, 0, 1), clipped(hsv.v, 0, 1), colorState.a, 'HSV keyboard'));
  });
  setColorState(colorState);

  // Browsers perform native fragment scrolling before the tab script hides the
  // other panels. Reset only an initial site-tab fragment so sticky chrome never
  // covers the selected panel heading at narrow widths.
  if (location.hash && tabElements.some((tab) => `#${tab.dataset.siteTab}` === location.hash)) {
    requestAnimationFrame(() => scrollTo({ top: 0, behavior: 'auto' }));
  }

  // Keep anchored utilities collision-aware and return focus on dismissal.
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !contextMenu.hidden) contextMenu.hidden = true;
  });

  window.JDMaterialSite = Object.freeze({
    announce,
    searchControllers,
    get preferences() { return structuredClone(preferences); },
    get tabState() { return structuredClone(tabState); },
    get appearanceProfile() { return structuredClone(appearanceProfile); },
    parseColor,
    runDimSumDraw
  });
})();
