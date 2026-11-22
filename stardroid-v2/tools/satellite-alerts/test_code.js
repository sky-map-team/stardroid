// Run from this directory: cat Code.gs test_code.js | node
// Code.gs is prepended, so its functions are in scope; Google's services are faked below.

let rows, store, sent, request, mailFails;

var AnalyticsData = {
  Properties: {
    runReport(body) {
      request = body;
      return rows.length ? { rows } : {};
    },
  },
};
var PropertiesService = {
  getScriptProperties: () => ({
    getProperty: key => (key in store ? store[key] : null),
    setProperty: (key, value) => { store[key] = value; },
  }),
};
var Session = { getEffectiveUser: () => ({ getEmail: () => 'owner@example.invalid' }) };
var MailApp = {
  sendEmail(to, subject, body) {
    if (mailFails) throw new Error('mail failed');
    sent.push({ to, subject, body });
  },
};

function expect(actual, expected) {
  const [a, e] = [JSON.stringify(actual), JSON.stringify(expected)];
  if (a !== e) throw new Error(`expected ${e}, got ${a}`);
}

const row = (date, event, code, count) => ({
  dimensionValues: [{ value: date }, { value: event }, { value: code }],
  metricValues: [{ value: String(count) }],
});
const failures = (date, count, code = '503') => row(date, FAILED, code, count);
const successes = (date, count) => row(date, OK, '(not set)', count);
const keys = budget =>
  findAlerts(fetchDays('3daysAgo'), budget, () => 0).map(alert => alert.key);

const tests = {
  queriesBothEvents() {
    fetchDays('3daysAgo');
    expect(request.dimensionFilter.filter.inListFilter.values, [FAILED, OK]);
  },
  atThresholdIsQuiet() {
    rows = [failures('20261112', 200)];
    expect(keys(null), []);
  },
  overThresholdAlerts() {
    rows = [failures('20261112', 150, '503'), failures('20261112', 51, '500')];
    expect(keys(null), ['20261112']);
  },
  sustainedIncidentAlertsEveryDay() {
    rows = [failures('20261108', 3610), failures('20261109', 5156), failures('20261110', 3607)];
    expect(keys(null), ['20261108', '20261109', '20261110']);
  },
  successesAreNotFailures() {
    rows = [successes('20261112', 5000)];
    expect(keys(null), []);
  },
  volumeOverBudgetAlerts() {
    rows = [successes('20261112', 50)];
    expect(keys(4999), ['volume:20261112']);
    expect(keys(5000), []);
  },
  failuresAndVolumeAlertSeparately() {
    rows = [failures('20261112', 300), successes('20261112', 50)];
    expect(keys(4000), ['20261112', 'volume:20261112']);
  },
  untrustedCodesFoldIntoOther() {
    rows = [failures('20261112', 9, '@maintainer'), failures('20261112', 1, '(not set)')];
    expect(describe(fetchDays('3daysAgo')), '20261112: 10 failures (other: 10)');
  },
  describesBothEvents() {
    rows = [failures('20261112', 7), successes('20261112', 3)];
    expect(describe(fetchDays('3daysAgo')),
      '20261112: 7 failures (503: 7), ~300 downloads (3 sampled)');
  },
  alertsOnceThenOnlyOnBigGrowth() {
    rows = [failures('20261112', 300)];
    checkSatelliteFailures();
    checkSatelliteFailures();
    expect(sent.length, 1);
    rows = [failures('20261112', 3000)];
    checkSatelliteFailures();
    expect(sent.length, 2);
  },
  failedEmailIsRetried() {
    rows = [failures('20261112', 300)];
    mailFails = true;
    try {
      checkSatelliteFailures();
    } catch (e) {
      expect(e.message, 'mail failed');
    }
    expect(store, {});
    mailFails = false;
    checkSatelliteFailures();
    expect(sent.length, 1);
  },
  testEmailWorksWithNoData() {
    sendTestEmail();
    expect(sent[0].body, 'No satellite events recorded.');
  },
};

const print = console.log;
console.log = () => {};
for (const name of Object.keys(tests)) {
  [rows, store, sent, request, mailFails] = [[], {}, [], null, false];
  try {
    tests[name]();
  } catch (e) {
    throw new Error(`${name}: ${e.message}`);
  }
}
print(`${Object.keys(tests).length} tests passed`);
