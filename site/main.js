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
