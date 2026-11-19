"use strict";
const { randomBytes } = require('node:crypto');
const args = process.argv.slice(2);
if (args.some(x => x !== '--admin-only') || args.length > 1) {
  console.error('Usage: node scripts/generate-secrets.cjs [--admin-only]');
  process.exitCode = 1;
} else {
  console.log('LENSDROP_ADMIN_KEY=' + randomBytes(32).toString('hex'));
  if (!args.includes('--admin-only')) {
    console.log('LENSDROP_STORAGE_ID=' + randomBytes(16).toString('hex'));
    console.error('NEW EMPTY INSTALLATIONS ONLY. Save both values privately. Do not replace an existing storage identity to fix a startup error.');
  } else {
    console.error('Only an admin key was generated. No storage ID or data was changed. Prefer restoring an existing saved key; save a new key privately before use.');
  }
}
