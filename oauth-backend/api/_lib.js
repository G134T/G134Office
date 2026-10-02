const crypto = require('crypto');

const APP_ORIGIN = process.env.APP_ORIGIN || 'https://g134t.github.io';
const APP_PATH_PREFIX = process.env.APP_PATH_PREFIX || '/G134Office/';

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Missing environment variable ${name}`);
  return value;
}

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', APP_ORIGIN);
  res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Vary', 'Origin');
}

function sendJson(res, status, value) {
  cors(res);
  res.status(status).json(value);
}

function safeReturnTo(value) {
  try {
    const url = new URL(value || APP_ORIGIN + APP_PATH_PREFIX);
    if (url.origin !== APP_ORIGIN) throw new Error('Invalid return origin');
    if (!url.pathname.startsWith(APP_PATH_PREFIX)) throw new Error('Invalid return path');
    url.search = '';
    url.hash = '';
    return url.toString();
  } catch {
    return APP_ORIGIN + APP_PATH_PREFIX;
  }
}

function b64url(input) {
  return Buffer.from(input).toString('base64url');
}

function fromB64url(input) {
  return Buffer.from(input, 'base64url');
}

function stateKey() {
  return crypto.createHash('sha256').update(required('GITHUB_CLIENT_SECRET')).digest();
}

function makeState(returnTo) {
  const payload = JSON.stringify({
    returnTo: safeReturnTo(returnTo),
    nonce: crypto.randomBytes(18).toString('base64url'),
    iat: Date.now()
  });
  const encoded = b64url(payload);
  const sig = crypto.createHmac('sha256', stateKey()).update(encoded).digest('base64url');
  return `${encoded}.${sig}`;
}

function readState(value) {
  const [encoded, sig] = String(value || '').split('.');
  if (!encoded || !sig) throw new Error('Invalid OAuth state');
  const expected = crypto.createHmac('sha256', stateKey()).update(encoded).digest();
  const actual = fromB64url(sig);
  if (actual.length !== expected.length || !crypto.timingSafeEqual(actual, expected)) throw new Error('Invalid OAuth state');
  const payload = JSON.parse(fromB64url(encoded).toString('utf8'));
  if (!payload.iat || Date.now() - payload.iat > 10 * 60 * 1000) throw new Error('OAuth state expired');
  payload.returnTo = safeReturnTo(payload.returnTo);
  return payload;
}

function sessionKey() {
  return crypto.createHash('sha256').update('g134office-session\n' + required('GITHUB_CLIENT_SECRET')).digest();
}

function sealSession(payload) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', sessionKey(), iv);
  const plain = Buffer.from(JSON.stringify(payload), 'utf8');
  const encrypted = Buffer.concat([cipher.update(plain), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, encrypted]).toString('base64url');
}

function openSession(token) {
  const raw = fromB64url(token || '');
  if (raw.length < 29) throw new Error('Invalid session');
  const iv = raw.subarray(0, 12);
  const tag = raw.subarray(12, 28);
  const encrypted = raw.subarray(28);
  const decipher = crypto.createDecipheriv('aes-256-gcm', sessionKey(), iv);
  decipher.setAuthTag(tag);
  const payload = JSON.parse(Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8'));
  if (!payload.exp || Date.now() > payload.exp) throw new Error('Session expired');
  if (!payload.accessToken) throw new Error('Invalid session');
  return payload;
}

function readBearer(req) {
  const value = String(req.headers.authorization || '');
  if (!value.startsWith('Bearer ')) throw new Error('Authentication required');
  return openSession(value.slice(7));
}

async function github(path, accessToken, options = {}) {
  const response = await fetch('https://api.github.com' + path, {
    ...options,
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${accessToken}`,
      'X-GitHub-Api-Version': '2026-03-10',
      ...(options.headers || {})
    }
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data.message || `GitHub API ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return data;
}

module.exports = {
  APP_ORIGIN,
  cors,
  sendJson,
  required,
  safeReturnTo,
  makeState,
  readState,
  sealSession,
  readBearer,
  github
};
