"use strict";
// Host entry point for direct Node startup and synchronous CommonJS loaders.
// A configuration rejection MUST throw back to a loader. Returning undefined
// makes LiteSpeed probe undefined.listen and hides the original error.
let app;
try {
  const { prepareHosting } = require('./src/hosting.cjs');
  const { createApp } = require('./src/server.cjs');
  const { version } = require('./package.json');
  const port = process.env.PORT === undefined ? 3000 : Number(process.env.PORT);
  if (!Number.isInteger(port) || port < 0 || port > 65535) {
    const error = new Error('PORT must be a numeric TCP port assigned by the host.');
    error.code = 'invalid_port';
    throw error;
  }
  app = createApp(prepareHosting());
  // A valid export also supports loaders that inspect the listen interface.
  module.exports = app.server;
  app.server.on('error', error => {
    console.error(JSON.stringify({ type: 'listen_error', code: error.code || 'listen_failed' }));
    app.close().finally(() => process.exit(1));
  });
  app.server.listen(port, process.env.HOST || '0.0.0.0', () => {
    // LiteSpeed overrides address(): it can return a Unix socket string or
    // undefined rather than Node's TCP AddressInfo. Never log its private path.
    const address = app.server.address();
    const tcpPort = address && typeof address === 'object' && Number.isInteger(address.port)
      ? address.port : null;
    console.log(JSON.stringify({ type: 'lensdrop_ready', version, port: tcpPort,
      transport: tcpPort === null ? 'managed-socket' : 'tcp',
      profile: 'hostinger-candidate', storage: 'single-instance-local-disk',
      initialization_enabled: process.env.LENSDROP_INITIALIZE_STORAGE === '1' }));
  });
  let stopping = false;
  for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => {
    if (stopping) return; stopping = true;
    app.close().then(() => process.exit(0), error => {
      console.error(JSON.stringify({ type: 'shutdown_error', code: error.code || 'shutdown_failed' }));
      process.exit(1);
    });
  });
} catch (error) {
  const code = error.code || 'configuration_error';
  console.error(JSON.stringify({ type: 'startup_rejected', code, message: error.message }));
  process.exitCode = 1;
  // Do not start an unauthenticated fallback server or create a default key.
  // A direct invocation exits with the structured diagnostic. A host loader
  // must see a failed require(), not a successful require() of undefined.
  if (require.main !== module) throw error;
}
