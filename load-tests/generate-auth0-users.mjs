import { createHash, randomBytes } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const userCount = 20;
const firstUserNumber = 2;
const emailDomain = process.env.AUTH0_TEST_EMAIL_DOMAIN || 'mail.com';
const outputDirectory = path.join(path.dirname(fileURLToPath(import.meta.url)), 'users');

if (createHash('md4').update('provider-check').digest('base64').length === 0) {
  throw new Error('MD4 provider is unavailable.');
}

const importUsers = [];
const credentials = [];

for (let index = 0; index < userCount; index += 1) {
  const username = `kitchensink.demo${String(firstUserNumber + index).padStart(2, '0')}`;
  const email = `${username}@${emailDomain}`;
  const password = randomBytes(24).toString('base64url');
  const hash = createHash('md4').update(password, 'utf8').digest('base64');

  importUsers.push({
    email,
    email_verified: false,
    custom_password_hash: {
      algorithm: 'md4',
      hash: { value: hash, encoding: 'base64' },
    },
  });
  credentials.push({ email, password });
}

await mkdir(outputDirectory, { recursive: true });
await writeFile(path.join(outputDirectory, 'auth0-import.json'), `${JSON.stringify(importUsers, null, 2)}\n`, { flag: 'wx' });
await writeFile(path.join(outputDirectory, 'credentials.json'), `${JSON.stringify(credentials, null, 2)}\n`, { flag: 'wx' });

console.log(`Created ${userCount} Auth0 users for @${emailDomain}.`);
console.log(`Import file: ${path.join(outputDirectory, 'auth0-import.json')}`);
console.log(`Local credentials: ${path.join(outputDirectory, 'credentials.json')} (Git-ignored; keep private)`);
console.warn('MD4 is legacy and suitable only for this disposable test tenant; never use these accounts or hashes in production.');
