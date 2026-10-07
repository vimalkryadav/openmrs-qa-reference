/* Local demonstration clock. Loaded before the OpenMRS frontend bundles.
 *
 * openmrs-ayu override of the gateway image's /opt/demo-clock/demo-clock.js
 * (bind-mounted by compose.yaml). Two clocks, on purpose:
 *
 * - Wall clock, FROZEN at OPENMRS_DEMO_TIME: `new Date()` and `Date()` with no
 *   arguments. Everything the page shows as "now" and every datetime a form sends
 *   comes from here, so it agrees with the backend (libfaketime, absolute and
 *   frozen) and with the rl_openmrs clone (frontend/lib/dateOverride.ts). An
 *   advancing wall clock would send datetimes ahead of the frozen server, which
 *   rejects them ("The encounter datetime should be before the current date.").
 *
 * - Timer clock, ADVANCING: `Date.now()` = OPENMRS_DEMO_TIME + real time elapsed
 *   since this script ran (performance.now). lodash/es-toolkit debounce and
 *   throttle, SWR's focus throttle and the session refresh compare successive
 *   `Date.now()` readings; with it frozen they never see time pass, so every
 *   debounced search (visit-note diagnosis 500 ms, forms list 300 ms, ...) never
 *   fires. Timers and performance.now stay real.
 */
(() => {
  'use strict';
  const RealDate = window.Date;
  const iso = window.__OPENMRS_DEMO_TIME__.replace(' ', 'T') + 'Z';
  const epoch = RealDate.parse(iso);
  if (!Number.isFinite(epoch)) throw new Error('Invalid OpenMRS demo date');
  const perf = window.performance;
  const loadedAt = perf.now();
  const now = () => epoch + Math.floor(perf.now() - loadedAt);
  const DemoDate = new Proxy(RealDate, {
    apply() { return new RealDate(epoch).toString(); },
    construct(target, args, newTarget) {
      return Reflect.construct(target, args.length ? args : [epoch], newTarget);
    },
    get(target, key, receiver) {
      return key === 'now' ? now : Reflect.get(target, key, receiver);
    },
  });
  window.Date = DemoDate;
  document.addEventListener('DOMContentLoaded', () => {
    const label = document.createElement('div');
    label.id = 'openmrs-demo-clock-label';
    label.textContent = 'Demo date: ' + iso.slice(0, 10) + ' · ' + iso.slice(11, 16) + ' UTC';
    label.title = 'Fixed demo clock. Records remain editable.';
    label.style.cssText = 'position:fixed;bottom:6px;left:6px;z-index:9999;background:#fff4ce;color:#332b00;border:1px solid #d5b54d;border-radius:3px;padding:3px 7px;font:12px sans-serif;pointer-events:none';
    document.body.appendChild(label);
  });
})();
