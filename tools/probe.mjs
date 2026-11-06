#!/usr/bin/env node
/**
 * Phase 0 protocol probe for ZTE U30 Pro (MU5358) stock goform API.
 *
 * SAFETY: read-only. The only writes sent are LOGIN and LOGOUT. No config
 * command is ever issued. Never logs the password.
 *
 * Usage (run with the Mac joined to the device WiFi):
 *   U30_PASSWORD='your-admin-password' node tools/probe.mjs
 *   U30_PASSWORD='...' node tools/probe.mjs --gateway 192.168.0.1 --port 80
 *   U30_PASSWORD='...' node tools/probe.mjs --out docs/probe-u30pro.json
 *
 * Env:
 *   U30_PASSWORD  admin password (required)
 *   U30_GATEWAY   default 192.168.0.1
 */

import { createHash } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

// ---------------------------------------------------------------------------
// args
// ---------------------------------------------------------------------------

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];
    if (!token.startsWith('--')) continue;
    const key = token.slice(2);
    const next = argv[i + 1];
    if (next && !next.startsWith('--')) {
      out[key] = next;
      i++;
    } else {
      out[key] = true;
    }
  }
  return out;
}

const args = parseArgs(process.argv.slice(2));
const password = args.password ?? process.env.U30_PASSWORD;
const gateway = args.gateway ?? process.env.U30_GATEWAY ?? '192.168.0.1';
const explicitPort = args.port ? Number(args.port) : undefined;
const outPath = resolve(args.out ?? 'docs/probe-u30pro.json');
const timeoutMs = Number(args.timeout ?? 8000);

if (!password) {
  console.error('missing password: set U30_PASSWORD env var (recommended) or pass --password');
  process.exit(2);
}

// ---------------------------------------------------------------------------
// field candidates (verified on U30 Air / F50 families; U30 Pro unknown)
// ---------------------------------------------------------------------------

const FIELD_GROUPS = {
  signal: [
    'network_type', 'ppp_status', 'network_provider', 'network_provider_fullname',
    'network_signalbar', 'signalbar', 'rssi',
    'Z5g_rsrp', 'Z5g_snr', 'Z5g_SINR', 'Z5g_rsrq', 'Z5g_rssi',
    'network_lte_rsrp', 'lte_rsrp', 'lte_rsrq', 'lte_rssi', 'lte_pci',
    'network_sinr', 'network_snr', 'Lte_snr', 'lte_snr', 'network_ecio',
    'nr_rsrp', 'nr_rsrq', 'nr_rssi', 'Nr_snr',
    'network_Z_PCI', 'network_Z5g_PCI', 'network_ZCELLINFO_band',
    'network_Z5g_dlEarfcn', 'network_Z5g_CELL_ID', 'network_cell_id',
    'nr5g_action_band', 'network_Z5g_CELLINFO_band', 'wan_active_band',
    'wan_lte_ca', 'cell_id', 'Lte_pci', 'Nr_pci', 'Nr_fcn',
    'wan_active_channel', 'nr5g_action_channel', 'network_rssi',
  ],
  traffic: [
    'flux_realtime_rx_thrpt', 'flux_realtime_tx_thrpt',
    'flux_realtime_rx_bytes', 'flux_realtime_tx_bytes', 'flux_realtime_time',
    'flux_monthly_rx_bytes', 'flux_monthly_tx_bytes', 'flux_monthly_time',
    'realtime_rx_thrpt', 'realtime_tx_thrpt',
    'realtime_rx_bytes', 'realtime_tx_bytes', 'realtime_time',
    'monthly_rx_bytes', 'monthly_tx_bytes', 'monthly_time',
    'data_volume_limit_switch', 'data_volume_limit_size', 'data_volume_alert_percent',
  ],
  device: [
    'imei', 'imsi', 'iccid', 'msisdn', 'sim_msisdn', 'sim_imsi', 'sim_iccid',
    'sim_slot', 'dual_sim_support', 'mac_address', 'lan_ipaddr', 'lan_netmask',
    'wan_ipaddr', 'ipv6_wan_ipaddr', 'LocalDomain',
    'wa_inner_version', 'cr_version', 'wa_version', 'web_version',
    'hardware_version', 'model_name', 'electron_id',
    'battery_value', 'battery_vol_percent', 'battery_charging',
    'sms_unread_num', 'wifi_access_sta_num', 'wifi_onoff_state', 'wifi_5g_enable',
    'pin_status', 'usb_port_switch', 'loginfo', 'new_version_state',
  ],
  temperature: [
    'temperature', 'cpu_temp', 'internal_temperature', 'ic_temp',
    'cpu_utility', 'mem_utility',
  ],
  wifi: [
    'wifi_chip1_ssid1_ssid', 'wifi_chip1_ssid1_authmode', 'wifi_chip1_ssid1_encryption',
    'queryAccessPointInfo',
  ],
};

const STATION_FIELDS = ['station_list', 'lan_station_list', 'station_num', 'hostNameList'];
const ACL_FIELDS = ['queryDeviceAccessControlList'];

// ---------------------------------------------------------------------------
// http plumbing
// ---------------------------------------------------------------------------

let cookie = '';
let baseUrl = '';

function url(path, params) {
  const qs = new URLSearchParams(params);
  return `${baseUrl}${path}?${qs.toString()}`;
}

async function httpGet(path, params) {
  const res = await fetch(url(path, params), {
    method: 'GET',
    headers: requestHeaders(),
    signal: AbortSignal.timeout(timeoutMs),
  });
  captureCookie(res);
  return res;
}

async function httpPost(path, form) {
  const res = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: { ...requestHeaders(), 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(form).toString(),
    signal: AbortSignal.timeout(timeoutMs),
  });
  captureCookie(res);
  return res;
}

function requestHeaders() {
  const headers = {
    // Mandatory: without these the device answers {"Error":"none secure connection"}.
    'X-Requested-With': 'XMLHttpRequest',
    Referer: baseUrl + '/',
    Accept: 'application/json, text/plain, */*',
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) U30ProPanel/0.0.1',
  };
  if (cookie) headers.Cookie = cookie;
  return headers;
}

function captureCookie(res) {
  const raw = res.headers.getSetCookie?.() ?? [];
  for (const line of raw) {
    const first = line.split(';')[0];
    if (first && first.includes('=')) cookie = first;
  }
  // Fallback for older runtimes.
  const single = res.headers.get('set-cookie');
  if (!cookie && single) cookie = single.split(';')[0];
}

async function jsonOrText(res) {
  const text = await res.text();
  try {
    return { json: JSON.parse(text), text };
  } catch {
    return { json: null, text };
  }
}

// ---------------------------------------------------------------------------
// goform primitives
// ---------------------------------------------------------------------------

const GET_CMD = '/goform/goform_get_cmd_process';
const SET_CMD = '/goform/goform_set_cmd_process';

async function readCmd(cmdList) {
  const res = await httpGet(GET_CMD, {
    cmd: cmdList.join(','),
    multi_data: '1',
    isTest: 'false',
    _: String(Date.now()),
  });
  return jsonOrText(res);
}

async function readField(field) {
  const { json } = await readCmd([field]);
  return json && Object.prototype.hasOwnProperty.call(json, field) ? json[field] : undefined;
}

/**
 * Batch reads, then fall back to single-field reads. Some firmware rejects an
 * entire batch if one unknown field is present.
 */
async function readMany(fields, batchSize = 16, sink = {}) {
  for (let i = 0; i < fields.length; i += batchSize) {
    const batch = fields.slice(i, i + batchSize);
    let parsed = null;
    try {
      const { json, text } = await readCmd(batch);
      parsed = json;
      if (!json) sink.__errors = [...(sink.__errors ?? []), { batch, error: text.slice(0, 200) }];
    } catch (err) {
      sink.__errors = [...(sink.__errors ?? []), { batch, error: String(err.message ?? err) }];
    }
    const hit = batch.filter((f) => parsed && Object.prototype.hasOwnProperty.call(parsed, f));
    for (const f of hit) sink[f] = parsed[f];
    const missed = batch.filter((f) => !hit.includes(f));
    if (hit.length === 0) {
      for (const f of missed) {
        try {
          const value = await readField(f);
          if (value !== undefined) sink[f] = value;
        } catch (err) {
          sink.__errors = [...(sink.__errors ?? []), { field: f, error: String(err.message ?? err) }];
        }
      }
    }
  }
  return sink;
}

function sha256HexUpper(input) {
  return createHash('sha256').update(input, 'utf8').digest('hex').toUpperCase();
}

async function loginSha256(salt) {
  const hash = sha256HexUpper(sha256HexUpper(password) + salt);
  return postLogin({ goformId: 'LOGIN', user: 'admin', password: hash, isTest: 'false' });
}

async function loginBase64() {
  return postLogin({
    goformId: 'LOGIN',
    user: 'admin',
    password: Buffer.from(password, 'utf8').toString('base64'),
    isTest: 'false',
  });
}

async function postLogin(form) {
  const res = await httpPost(SET_CMD, form);
  const { json, text } = await jsonOrText(res);
  return { result: json?.result, json, text: json ? undefined : text.slice(0, 300), cookie };
}

async function logout() {
  try {
    await httpPost(SET_CMD, { goformId: 'LOGOUT', isTest: 'false' });
  } catch {
    // best effort
  }
}

// ---------------------------------------------------------------------------
// discovery
// ---------------------------------------------------------------------------

async function probePort(port) {
  const candidate = `http://${gateway}${port === 80 ? '' : `:${port}`}`;
  try {
    const res = await fetch(`${candidate}${GET_CMD}?cmd=LD&multi_data=1&isTest=false&_=${Date.now()}`, {
      headers: {
        'X-Requested-With': 'XMLHttpRequest',
        Referer: `${candidate}/`,
        Accept: 'application/json, text/plain, */*',
      },
      signal: AbortSignal.timeout(timeoutMs),
    });
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* html login page */ }
    return { port, ok: res.ok, hasLd: Boolean(json && json.LD), body: text.slice(0, 200) };
  } catch (err) {
    return { port, ok: false, hasLd: false, error: String(err.message ?? err) };
  }
}

async function chooseEndpoint() {
  const ports = explicitPort ? [explicitPort] : [80, 8080];
  const attempts = [];
  for (const port of ports) {
    const attempt = await probePort(port);
    attempts.push(attempt);
    if (attempt.hasLd) {
      baseUrl = `http://${gateway}${port === 80 ? '' : `:${port}`}`;
      return { port, attempts };
    }
  }
  return { port: null, attempts };
}

async function detectLogin(port) {
  // LD may be readable on a port that did not answer the preflight, e.g. if the
  // preflight hit a redirect. Retry against the chosen base URL.
  let salt = null;
  try {
    const { json } = await readCmd(['LD']);
    salt = json?.LD;
  } catch { /* ignore */ }

  const attempts = [];
  if (salt) {
    attempts.push({ scheme: 'ld-sha256', salt, ...(await loginSha256(salt)) });
    if (attempts.at(-1).result === 0 || attempts.at(-1).result === '0') {
      return { scheme: 'ld-sha256', salt, attempts };
    }
  }
  attempts.push({ scheme: 'base64', ...(await loginBase64()) });
  if (attempts.at(-1).result === 0 || attempts.at(-1).result === '0') {
    return { scheme: 'base64', salt, attempts };
  }
  return { scheme: null, salt, attempts };
}

async function readWritesToken() {
  const { json } = await readCmd(['wa_inner_version', 'cr_version']);
  const versions = json ?? {};
  const { json: rdJson } = await readCmd(['RD']);
  const rd = rdJson?.RD;
  if (!versions.wa_inner_version || !versions.cr_version || !rd) return null;
  const inner = sha256HexUpper(String(versions.wa_inner_version) + String(versions.cr_version));
  return {
    ad: sha256HexUpper(inner + String(rd)),
    wa_inner_version: versions.wa_inner_version,
    cr_version: versions.cr_version,
    rd,
  };
}

function normalizeStations(value) {
  if (value == null) return null;
  if (Array.isArray(value)) return value;
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) return [];
    try {
      const parsed = JSON.parse(trimmed);
      return Array.isArray(parsed) ? parsed : [parsed];
    } catch {
      return { unparsed: trimmed };
    }
  }
  return value;
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

const report = {
  generatedAt: new Date().toISOString(),
  gateway,
  port: null,
  baseUrl: null,
  firmware: {},
  login: {},
  fields: {},
  groups: {},
  stations: null,
  acl: null,
  writeToken: null,
  endpointAttempts: [],
  notes: [],
};

try {
  const endpoint = await chooseEndpoint();
  report.endpointAttempts = endpoint.attempts;
  if (!endpoint.port) {
    report.notes.push('no port answered cmd=LD; is the Mac joined to the device WiFi?');
    throw new Error('device not reachable');
  }
  report.port = endpoint.port;
  report.baseUrl = baseUrl;

  const login = await detectLogin(endpoint.port);
  report.login = {
    scheme: login.scheme,
    saltPresent: Boolean(login.salt),
    attempts: login.attempts.map((a) => ({ scheme: a.scheme, result: a.result, error: a.text })),
  };
  if (!login.scheme) {
    report.notes.push('login failed with both ld-sha256 and base64; password may be wrong or firmware differs');
  } else {
    for (const [group, fields] of Object.entries(FIELD_GROUPS)) {
      const sink = {};
      await readMany(fields, 16, sink);
      const { __errors, ...values } = sink;
      report.groups[group] = {
        requested: fields.length,
        available: Object.keys(values).length,
        values,
        ...( __errors?.length ? { errors: __errors } : {} ),
      };
      Object.assign(report.fields, values);
    }

    const stationSink = {};
    await readMany([...STATION_FIELDS, ...ACL_FIELDS], 8, stationSink);
    const aclSink = {};

    if (stationSink.station_list == null) {
      // UFI-AXIS observed that combining station_list with other fields can
      // return empty on some firmware; retry alone.
      try {
        const { json } = await readCmd(['station_list']);
        if (json && json.station_list != null) stationSink.station_list = json.station_list;
      } catch { /* ignore */ }
    }
    if (stationSink.lan_station_list == null) {
      try {
        const { json } = await readCmd(['lan_station_list']);
        if (json && json.lan_station_list != null) stationSink.lan_station_list = json.lan_station_list;
      } catch { /* ignore */ }
    }
    if (stationSink.queryDeviceAccessControlList == null) {
      try {
        const { json } = await readCmd(['queryDeviceAccessControlList']);
        if (json && json.queryDeviceAccessControlList != null) {
          stationSink.queryDeviceAccessControlList = json.queryDeviceAccessControlList;
        }
      } catch { /* ignore */ }
    }

    report.stations = {
      stationList: normalizeStations(stationSink.station_list),
      lanStationList: normalizeStations(stationSink.lan_station_list),
      stationNum: stationSink.station_num,
      hostNameList: normalizeStations(stationSink.hostNameList),
      wifiAccessStaNum: report.fields.wifi_access_sta_num,
    };

    let acl = stationSink.queryDeviceAccessControlList;
    if (acl == null) {
      // U30 Pro answers with a flat object ({"AclMode":...}) instead of
      // nesting it under the command name.
      try {
        const { json } = await readCmd(['queryDeviceAccessControlList']);
        const flatKeys = ['AclMode', 'BlackMacList', 'WhiteMacList', 'BlackNameList', 'WhiteNameList'];
        if (json && flatKeys.some((key) => Object.prototype.hasOwnProperty.call(json, key))) {
          acl = json;
        }
      } catch { /* ignore */ }
    }
    if (typeof acl === 'string') {
      try { acl = JSON.parse(acl); } catch { /* keep string */ }
    }
    report.acl = acl ?? null;
    Object.assign(aclSink, acl && typeof acl === 'object' ? acl : {});
    report.writeToken = await readWritesToken();

    report.firmware = {
      model_name: report.fields.model_name,
      hardware_version: report.fields.hardware_version,
      wa_inner_version: report.fields.wa_inner_version,
      cr_version: report.fields.cr_version,
      wa_version: report.fields.wa_version,
      web_version: report.fields.web_version,
      network_type: report.fields.network_type,
    };

    report.notes.push(login.scheme === 'base64'
      ? 'logged in with legacy base64 scheme — this is NOT the U30 Air family; adjust the Android client.'
      : 'logged in with the U30 Air ld-sha256 scheme.');
    if (report.writeToken) {
      report.notes.push('AD write token derivation succeeded (read-only; AD was not used).');
    }
  }
} catch (err) {
  report.error = String(err.message ?? err);
} finally {
  await logout();
}

mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, JSON.stringify(report, null, 2) + '\n', 'utf8');

// ---------------------------------------------------------------------------
// summary
// ---------------------------------------------------------------------------

const pad = (s, n) => String(s ?? '').padEnd(n);
console.log(`\nU30 Pro probe  ${report.generatedAt}`);
console.log(`gateway        ${gateway}`);
console.log(`port           ${report.port ?? 'unreachable'}`);
console.log(`login          ${report.login.scheme ?? 'FAILED'}`);
if (report.login.attempts?.length) {
  console.log(`  attempts     ${report.login.attempts.map((a) => `${a.scheme}=${a.result}`).join(', ')}`);
}
for (const [group, info] of Object.entries(report.groups ?? {})) {
  console.log(`${pad(group, 14)} ${info.available}/${info.requested} fields`);
}
if (report.stations) {
  const count = (list) => (Array.isArray(list) ? list.length : list ? '?' : 0);
  console.log(`${pad('stations', 14)} wifi=${count(report.stations.stationList)} lan=${count(report.stations.lanStationList)}`);
}
console.log(`write token    ${report.writeToken ? 'available' : 'unavailable'}`);
console.log(`report         ${outPath}`);
if (report.error) console.log(`error          ${report.error}`);
if (report.notes.length) {
  console.log('\nnotes:');
  for (const note of report.notes) console.log(`  - ${note}`);
}
console.log('');