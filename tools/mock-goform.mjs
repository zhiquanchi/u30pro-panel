#!/usr/bin/env node
/**
 * Development-only mock of the ZTE goform admin API.
 *
 * It exists so tools/probe.mjs (and manual curl experiments) can be exercised
 * without a device. It is NOT a device emulator and is never shipped.
 *
 * Usage:
 *   node tools/mock-goform.mjs [--port 8787] [--password test123]
 */

import { createHash } from 'node:crypto';
import { createServer } from 'node:http';

const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, token, index, all) => {
    if (token.startsWith('--')) pairs.push([token.slice(2), all[index + 1]?.startsWith('--') ? true : all[index + 1]]);
    return pairs;
  }, []),
);

const PORT = Number(args.port ?? 8787);
const PASSWORD = args.password ?? 'test123';
const LD = 'mock-ld-123';
const SESSION = 'JSESSIONID=mock-session-1';

const sha256Upper = (value) => createHash('sha256').update(value, 'utf8').digest('hex').toUpperCase();

const FIELDS = {
  network_type: 'ENDC',
  ppp_status: 'ipv4_ipv6_connected',
  network_provider: 'MOBILE',
  network_provider_fullname: 'China Mobile',
  network_signalbar: '4',
  Z5g_rsrp: '-88',
  Z5g_SINR: '21.5',
  Z5g_rsrq: '-12',
  network_lte_rsrp: '-92',
  lte_rsrq: '-13',
  nr5g_action_band: 'n78',
  network_Z5g_CELLINFO_band: 'n78',
  network_Z5g_PCI: '450',
  network_Z5g_CELL_ID: '88442211',
  wan_lte_ca: 'ca_activated',
  flux_realtime_rx_thrpt: '5242880',
  flux_realtime_tx_thrpt: '1048576',
  flux_realtime_rx_bytes: '734003200',
  flux_realtime_tx_bytes: '104857600',
  flux_realtime_time: '91234',
  flux_monthly_rx_bytes: '10737418240',
  flux_monthly_tx_bytes: '2147483648',
  wa_inner_version: 'U30ProV1.0.0B24',
  cr_version: 'CR-MOCK',
  wa_version: 'wa-mock',
  web_version: 'web-mock',
  hardware_version: 'MU5358',
  model_name: 'U30 Pro',
  imei: '860000000000001',
  imsi: '460000000000001',
  iccid: '89860000000000000001',
  msisdn: '+8613800000000',
  mac_address: 'AA:BB:CC:00:11:22',
  lan_ipaddr: '192.168.0.1',
  wan_ipaddr: '10.20.30.40',
  battery_vol_percent: '87',
  battery_charging: '1',
  sms_unread_num: '2',
  wifi_access_sta_num: '2',
  wifi_onoff_state: '1',
  wifi_chip1_ssid1_ssid: 'U30Pro-Mock',
  temperature: '0',
  // temperature deliberately returns "0": the app treats 0 as "sensor not
  // available" and hides the row. Do not show 0.0 ℃ on real hardware.
  cpu_utility: '17',
  mem_utility: '42',
};

const STATION_LIST = [
  { hostname: 'iPhone', mac_addr: 'AA:BB:CC:DD:EE:01', ip_addr: '192.168.0.2' },
  { hostname: 'MacBook', mac_addr: 'AA:BB:CC:DD:EE:02', ip_addr: '192.168.0.3' },
];

// Deliberately exposed as a JSON-encoded string to exercise both parser shapes.
const LAN_STATION_LIST = JSON.stringify([
  { hostname: 'NAS', mac_addr: 'AA:BB:CC:DD:EE:03', ip_addr: '192.168.0.4' },
]);

const state = {
  blacklist: ['AA:BB:CC:DD:EE:03'],
  authenticated: false,
  writes: [],
};

function json(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Set-Cookie': `${SESSION}; Path=/goform; HttpOnly`,
    'Content-Length': Buffer.byteLength(body),
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (chunk) => { data += chunk; });
    req.on('end', () => resolve(Object.fromEntries(new URLSearchParams(data))));
  });
}

function requireHeaders(req, res) {
  if (req.headers['x-requested-with'] !== 'XMLHttpRequest') {
    json(res, 200, { Error: 'none secure connection' });
    return false;
  }
  if (!req.headers.referer?.includes('127.0.0.1')) {
    json(res, 200, { Error: 'none secure connection' });
    return false;
  }
  return true;
}

function valueFor(key) {
  switch (key) {
    case 'LD': return LD;
    case 'RD': return state.authenticated ? 'mock-rd-456' : 'not logged in';
    case 'station_list': return STATION_LIST;
    case 'lan_station_list': return LAN_STATION_LIST;
    case 'hostNameList': return JSON.stringify(STATION_LIST.map((s) => ({ mac: s.mac_addr, hostname: s.hostname })));
    case 'queryDeviceAccessControlList': return {
      AclMode: '2',
      BlackMacList: state.blacklist.join(';'),
      WhiteMacList: '',
      WhiteNameList: '',
      BlackNameList: '',
    };
    case 'station_num': return String(STATION_LIST.length);
    case 'sms_unread_num': return '2';
    default: return FIELDS[key];
  }
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`);

  if (req.method === 'GET' && url.pathname === '/goform/goform_get_cmd_process') {
    if (!requireHeaders(req, res)) return;
    const cmds = (url.searchParams.get('cmd') ?? '').split(',').filter(Boolean);
    if (!state.authenticated && !cmds.includes('LD')) {
      json(res, 200, { result: 'not logged in' });
      return;
    }
    const payload = {};
    for (const cmd of cmds) {
      const value = valueFor(cmd);
      if (value !== undefined) payload[cmd] = value;
    }
    json(res, 200, payload);
    return;
  }

  if (req.method === 'POST' && url.pathname === '/goform/goform_set_cmd_process') {
    if (!requireHeaders(req, res)) return;
    const form = await readBody(req);
    const goformId = form.goformId;

    if (goformId === 'LOGIN') {
      const expected = sha256Upper(sha256Upper(PASSWORD) + LD);
      if (form.password === expected) {
        state.authenticated = true;
        json(res, 200, { result: 0 });
      } else if (form.password === Buffer.from(PASSWORD).toString('base64')) {
        state.authenticated = true;
        json(res, 200, { result: 0 });
      } else {
        json(res, 200, { result: 3 });
      }
      return;
    }

    if (!state.authenticated) {
      json(res, 200, { result: 'not logged in' });
      return;
    }

    if (goformId === 'LOGOUT') {
      state.authenticated = false;
      json(res, 200, { result: 0 });
      return;
    }

    if (!form.AD) {
      json(res, 200, { result: 'missing AD' });
      return;
    }

    state.writes.push({ goformId, form });
    if (goformId === 'setDeviceAccessControlList') {
      state.blacklist = (form.BlackMacList ?? '').split(';').filter(Boolean);
      json(res, 200, { result: 0 });
      return;
    }
    if (goformId === 'EDIT_HOSTNAME') {
      const target = STATION_LIST.find((s) => s.mac_addr.toUpperCase() === (form.mac ?? '').toUpperCase());
      if (target) target.hostname = form.hostname;
      json(res, 200, { result: 0 });
      return;
    }
    json(res, 200, { result: 0 });
    return;
  }

  if (req.method === 'GET' && url.pathname === '/__state') {
    json(res, 200, state);
    return;
  }

  res.writeHead(404).end();
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`mock goform listening on http://127.0.0.1:${PORT} (password: ${PASSWORD})`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close(() => process.exit(0));
  });
}