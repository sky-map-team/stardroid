// Emails the maintainer about CelesTrak errors or download volume. Runs in Google Apps Script,
// not in CI: see README.md for setup, and redeploy by hand after changing this file.

const PROPERTY = 'properties/242522567';
const FAILED = 'satellite_fetch_failed';
const OK = 'satellite_fetch_ok';
const FAILURE_THRESHOLD = 200; // failures per day
const DAILY_BUDGET = null; // downloads per day agreed with CelesTrak (#1274); null = check off
// Must match SATELLITE_FETCH_OK_SAMPLE_ONE_IN in AnalyticsEvents.kt.
const SAMPLE_ONE_IN = 100;
const RENOTIFY_FACTOR = 10; // email again if a day grows this much after its alert

// The installed trigger refers to this name. Rechecks 3 days back, as events arrive late.
function checkSatelliteFailures() {
  const days = fetchDays('3daysAgo');
  const store = PropertiesService.getScriptProperties();
  const alerts = findAlerts(days, DAILY_BUDGET, key => Number(store.getProperty(key) || 0));
  console.log(describe(days));
  if (!alerts.length) return;

  email('ALERT: Sky Map satellite downloads',
    `${alerts.map(alert => alert.message).join('\n')}\n\n${describe(days)}`);
  // Only after sending, so that a failed email is retried on the next run.
  alerts.forEach(alert => store.setProperty(alert.key, String(alert.count)));
}

// Run by hand to test access and email delivery.
function sendTestEmail() {
  email('TEST: Sky Map satellite alert', describe(fetchDays('7daysAgo')));
}

// A fixed threshold: a trailing average absorbs an incident and then hides the next one.
function findAlerts(days, budget, lastAlerted) {
  const alerts = [];
  const check = (key, count, limit, message) => {
    if (count <= limit) return;
    const last = lastAlerted(key);
    if (!last || count >= last * RENOTIFY_FACTOR) alerts.push({ key, count, message });
  };
  for (const date of Object.keys(days).sort()) {
    const { failed, sampled } = days[date];
    check(date, failed, FAILURE_THRESHOLD,
      `${date}: ${failed} failures, over the limit of ${FAILURE_THRESHOLD}.`);
    if (budget !== null) {
      const estimate = sampled * SAMPLE_ONE_IN;
      check(`volume:${date}`, estimate, budget,
        `${date}: ~${estimate} downloads, over the budget of ${budget}. ` +
        'This is a lower bound, as installs without analytics are not counted.');
    }
  }
  return alerts;
}

function fetchDays(startDate) {
  const response = AnalyticsData.Properties.runReport({
    dateRanges: [{ startDate, endDate: 'today' }],
    dimensions: [{ name: 'date' }, { name: 'eventName' }, { name: 'customEvent:http_code' }],
    metrics: [{ name: 'eventCount' }],
    dimensionFilter: { filter: {
      fieldName: 'eventName',
      inListFilter: { values: [FAILED, OK] },
    } },
  }, PROPERTY);

  const days = {};
  for (const row of response.rows || []) {
    const [date, event, rawCode] = row.dimensionValues.map(v => v.value);
    const count = Number(row.metricValues[0].value);
    const day = days[date] || (days[date] = { failed: 0, sampled: 0, codes: {} });
    if (event === OK) {
      day.sampled += count;
    } else {
      // Clients supply http_code, so only real status codes may reach the email.
      const code = /^\d{3}$/.test(rawCode) ? rawCode : 'other';
      day.failed += count;
      day.codes[code] = (day.codes[code] || 0) + count;
    }
  }
  return days;
}

function describe(days) {
  return Object.keys(days).sort().map(date => {
    const { failed, sampled, codes } = days[date];
    const split = Object.keys(codes).map(code => `${code}: ${codes[code]}`).join(', ');
    return `${date}: ${failed} failures${split ? ` (${split})` : ''}` +
      (sampled ? `, ~${sampled * SAMPLE_ONE_IN} downloads (${sampled} sampled)` : '');
  }).join('\n') || 'No satellite events recorded.';
}

function email(subject, body) {
  MailApp.sendEmail(Session.getEffectiveUser().getEmail(), subject, body);
}
