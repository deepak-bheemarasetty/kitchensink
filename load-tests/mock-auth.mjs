import http from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const KEY_DIR = path.resolve('.keys');
const PRIV_KEY_PATH = path.join(KEY_DIR, 'private.pem');
const PUB_KEY_PATH = path.join(KEY_DIR, 'public.pem');

if (!fs.existsSync(KEY_DIR)) {
  fs.mkdirSync(KEY_DIR, { recursive: true });
}

let privateKeyPem;
let publicKeyPem;

if (!fs.existsSync(PRIV_KEY_PATH) || !fs.existsSync(PUB_KEY_PATH)) {
  const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: 2048,
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  });
  fs.writeFileSync(PRIV_KEY_PATH, privateKey);
  fs.writeFileSync(PUB_KEY_PATH, publicKey);
  privateKeyPem = privateKey;
  publicKeyPem = publicKey;
} else {
  privateKeyPem = fs.readFileSync(PRIV_KEY_PATH, 'utf8');
  publicKeyPem = fs.readFileSync(PUB_KEY_PATH, 'utf8');
}

const pubKeyObj = crypto.createPublicKey(publicKeyPem);
const jwkPublicKey = pubKeyObj.export({ format: 'jwk' });
const keyId = 'load-test-key-1';
jwkPublicKey.kid = keyId;
jwkPublicKey.use = 'sig';
jwkPublicKey.alg = 'RS256';

const PORT = 9089;
const ISSUER = `http://localhost:${PORT}/`;
const AUDIENCE = 'https://kitchensink-api';

function base64UrlEncode(strOrBuffer) {
  const buf = Buffer.isBuffer(strOrBuffer) ? strOrBuffer : Buffer.from(strOrBuffer, 'utf8');
  return buf.toString('base64url');
}

/**
 * Generates an RS256 signed JWT for the given email/username.
 * @param {string} email 
 * @param {number} expiresInSeconds 
 * @returns {string} Signed JWT
 */
export function generateToken(email, expiresInSeconds = 7200) {
  const header = {
    alg: 'RS256',
    typ: 'JWT',
    kid: keyId,
  };

  const now = Math.floor(Date.now() / 1000);
  const payload = {
    iss: ISSUER,
    sub: email,
    aud: AUDIENCE,
    email: email,
    iat: now,
    exp: now + expiresInSeconds,
  };

  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedPayload = base64UrlEncode(JSON.stringify(payload));
  const message = `${encodedHeader}.${encodedPayload}`;

  const signer = crypto.createSign('RSA-SHA256');
  signer.update(message);
  const signature = signer.sign(privateKeyPem);
  const encodedSignature = base64UrlEncode(signature);

  return `${message}.${encodedSignature}`;
}

let server = null;

export function startMockAuthServer(port = PORT) {
  return new Promise((resolve, reject) => {
    if (server) return resolve(server);

    server = http.createServer((req, res) => {
      const url = new URL(req.url, `http://localhost:${port}`);

      if (url.pathname === '/.well-known/jwks.json') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ keys: [jwkPublicKey] }));
      } else if (url.pathname === '/.well-known/openid-configuration') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          issuer: ISSUER,
          jwks_uri: `${ISSUER}.well-known/jwks.json`,
          response_types_supported: ['code', 'token', 'id_token'],
          subject_types_supported: ['public'],
          id_token_signing_alg_values_supported: ['RS256'],
        }));
      } else {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Not Found' }));
      }
    });

    server.listen(port, () => {
      console.log(`[MockAuth] Mock JWKS OIDC server running at ${ISSUER}`);
      resolve(server);
    });

    server.on('error', (err) => {
      if (err.code === 'EADDRINUSE') {
        // If already listening on 9089, reuse
        console.log(`[MockAuth] Server already active on port ${port}`);
        resolve(server);
      } else {
        reject(err);
      }
    });
  });
}

export function stopMockAuthServer() {
  return new Promise((resolve) => {
    if (server) {
      server.close(() => {
        server = null;
        resolve();
      });
    } else {
      resolve();
    }
  });
}

// Standalone execution support
if (process.argv[1] && process.argv[1].endsWith('mock-auth.mjs')) {
  startMockAuthServer().then(() => {
    console.log(`Test token sample: ${generateToken('admin@load.test')}`);
  });
}
