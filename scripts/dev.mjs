// The development loop `./start` runs: esbuild rebuilds the bundles in place,
// Pebble reads the templates from the sources, and a change to the Kotlin
// code or to another resource compiles once and restarts the application
// process. The old process keeps serving until the new code compiles, so a
// compile error leaves the last good version running.
//
// Type `rs` and Enter to restart by hand. The arguments go to the
// application, e.g. `node scripts/dev.mjs -port=8090`.
import { spawn } from 'node:child_process';
import { watch } from 'node:fs';
import process from 'node:process';
import * as esbuild from 'esbuild';
import { bundleOptions } from './bundle.mjs';

const SOURCES = 'src/main';
const LAUNCHER = 'build/dev/launch';
const STOP_TIMEOUT_MS = 5_000;
const DEBOUNCE_MS = 200;

/**
 * Writes a line prefixed with `[dev]` to the standard output.
 *
 * @param {string} message The line to write.
 * @returns {void}
 */
function log(message) {
  process.stdout.write(`\x1b[36m[dev]\x1b[0m ${message}\n`);
}

/**
 * Tells whether a changed file needs a compile and a restart. The running
 * process already sees the page templates, which Pebble reads from the
 * sources, and the stylesheets and scripts, which esbuild bundles.
 *
 * @param {string} path The file path relative to `src/main`.
 * @returns {boolean} `true` when the application must restart.
 */
function needsRestart(path) {
  if (path.startsWith('resources/templates/')) return false;
  if (path.startsWith('resources/static/css/')) return false;
  if (path.startsWith('resources/static/js/')) return false;

  return path.startsWith('kotlin/') || path.startsWith('resources/');
}

/**
 * Runs a command and waits for it to exit.
 *
 * @param {string} command The executable.
 * @param {string[]} args The arguments.
 * @returns {Promise<number>} The exit code.
 */
function run(command, args) {
  return new Promise(resolve => {
    spawn(command, args, { stdio: ['ignore', 'inherit', 'inherit'] })
      .on('exit', code => resolve(code));
  });
}

// ---- Frontend --------------------------------------------------------------

const bundles = await esbuild.context(await bundleOptions({ dev: true }));

await bundles.rebuild();
await bundles.watch();

// ---- Application -----------------------------------------------------------

let app = null;

/**
 * Compiles the application and writes the launcher script.
 *
 * @returns {Promise<boolean>} `true` when the compilation succeeded.
 */
async function compile() {
  log('compiling');
  const started = Date.now();
  // buildFrontend is left out: the watcher above owns build/frontend.
  const code = await run('./gradlew', [
    'devLauncher', '-x', 'buildFrontend', '--console=plain', '--quiet',
  ]);

  if (code !== 0) {
    log('\x1b[31mcompilation failed, the previous version keeps running\x1b[0m');

    return false;
  }
  log(`compiled in ${((Date.now() - started) / 1000).toFixed(1)} s`);

  return true;
}

/**
 * Stops the running application. Ktor's graceful shutdown gives the open
 * requests a moment; past that the process is killed.
 *
 * @returns {Promise<void>} Resolves once the process has exited.
 */
function stop() {
  const child = app;

  if (!child || child.exitCode !== null) return Promise.resolve();
  const exiting = new Promise(resolve => child.once('exit', resolve));

  child.stopping = true;
  child.kill('SIGTERM');
  const timer = setTimeout(() => child.kill('SIGKILL'), STOP_TIMEOUT_MS);

  return exiting.finally(() => clearTimeout(timer));
}

/**
 * Starts the application from the launcher script.
 *
 * @returns {void}
 */
function start() {
  const child = spawn(LAUNCHER, process.argv.slice(2), {
    stdio: ['ignore', 'inherit', 'inherit'],
  });

  child.on('exit', code => {
    if (!child.stopping) {
      log(`the application exited with code ${code}, waiting for a change`);
    }
  });
  app = child;
}

let busy = false;
let pending = false;

/**
 * Compiles and restarts the application. A change during a build queues one
 * more build instead of running two at once.
 *
 * @returns {Promise<void>} Resolves once no build is pending.
 */
async function rebuild() {
  if (busy) {
    pending = true;

    return;
  }
  busy = true;
  do {
    pending = false;
    if (await compile()) {
      await stop();
      start();
    }
  } while (pending);
  busy = false;
}

let debounce = null;

watch(SOURCES, { recursive: true }, (_event, file) => {
  if (!file || !needsRestart(file)) return;
  clearTimeout(debounce);
  // An editor saving several files at once, or through a temporary file,
  // triggers one build.
  debounce = setTimeout(rebuild, DEBOUNCE_MS);
});

process.stdin.setEncoding('utf8');
process.stdin.on('data', line => {
  if (line.trim() === 'rs') rebuild();
});

/**
 * Stops the watcher and the application, then exits.
 *
 * @returns {Promise<void>} Never resolves: the process exits.
 */
async function shutdown() {
  await bundles.dispose();
  await stop();
  process.exit(0);
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

await rebuild();
log('watching: templates and CSS/JS reload on refresh, Kotlin and other resources restart');
