'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { prepareReleaseFixture } = require('./helpers/release-fixture.cjs');
const root = path.resolve(__dirname, '..');
function probe(entry) {
  return spawnSync(process.execPath, ['--no-global-search-paths', '-e',
    "const r=require('node:module').createRequire(process.argv[1]);const sharp=r('sharp');console.log(JSON.stringify({version:sharp.versions.sharp,entry:r.resolve('sharp')}));", entry],
    { env: {...process.env, NODE_PATH: ''}, encoding: 'utf8', timeout: 15000 });
}
test('release fixture provides the exact installed sharp dependency without NODE_PATH', () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'ld-release-fixture-'));
  try {
    const replacement = prepareReleaseFixture(root, path.join(temp, 'release'));
    assert.equal(fs.existsSync(path.join(replacement, 'src/server.cjs')), true);
    const result = probe(path.join(replacement, 'src/server.cjs'));
    assert.equal(result.status, 0, result.stderr);
    assert.equal(JSON.parse(result.stdout).version, require('../package.json').dependencies.sharp);
    assert.equal(fs.realpathSync(path.join(replacement, 'node_modules')), fs.realpathSync(path.join(root, 'node_modules')));
  } finally { fs.rmSync(temp, {recursive:true, force:true}); }
});
test('negative control: a copied release without its dependency link cannot load sharp', () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'ld-release-negative-'));
  try {
    const replacement = prepareReleaseFixture(root, path.join(temp, 'release'));
    fs.unlinkSync(path.join(replacement, 'node_modules'));
    const result = probe(path.join(replacement, 'src/server.cjs'));
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Cannot find module 'sharp'/);
    assert.equal(fs.existsSync(path.join(root, 'node_modules')), true);
  } finally { fs.rmSync(temp, {recursive:true, force:true}); }
});
test('release fixture refuses an uninstalled source instead of silently using global dependencies', () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'ld-release-uninstalled-'));
  try {
    const source=path.join(temp,'source'),dest=path.join(temp,'release');fs.mkdirSync(source);
    assert.throws(() => prepareReleaseFixture(source,dest), {code:'ENOENT'});
    assert.equal(fs.existsSync(dest),false);
  } finally { fs.rmSync(temp,{recursive:true,force:true}); }
});
