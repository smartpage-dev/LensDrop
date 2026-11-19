'use strict';
// Test-only release replacement: reuse the runner-installed dependency tree.
// Production deployment still installs dependencies normally; no network or
// global NODE_PATH fallback is used to construct this isolated test fixture.
const fs = require('node:fs');
const path = require('node:path');
const FILES = ['server.js', 'package.json', 'src', 'shared', 'public'];
function prepareReleaseFixture(root, destination) {
  const dependencies = fs.realpathSync(path.join(root, 'node_modules'));
  if (!fs.statSync(dependencies).isDirectory()) throw new Error('Test dependencies are not a directory');
  fs.mkdirSync(destination);
  for (const name of FILES) fs.cpSync(path.join(root, name), path.join(destination, name), { recursive: true });
  fs.symlinkSync(dependencies, path.join(destination, 'node_modules'), process.platform === 'win32' ? 'junction' : 'dir');
  return destination;
}
module.exports = { prepareReleaseFixture };
