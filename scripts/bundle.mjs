// The esbuild settings the production build (esbuild.mjs) and the development
// watcher (dev.mjs) share. The output directory belongs to the Gradle task
// buildFrontend and joins the classpath from there, so a file esbuild writes
// is served on the next request.
import { glob } from 'glob';

const SRC = 'src/main/resources/static';
const OUT = 'build/frontend/static';

const ktorStaticUrlPlugin = {
  name: 'ktor-static-urls',
  setup(build) {
    build.onResolve({ filter: /^\/static\// }, args => ({
      path: args.path,
      external: true,
    }));
  },
};

/**
 * The esbuild options for the page bundles.
 *
 * @param {{ dev: boolean }} mode `dev` keeps the code readable and adds
 * inline source maps.
 * @returns {Promise<import("esbuild").BuildOptions>} The build options.
 */
export async function bundleOptions({ dev }) {
  const jsEntries = await glob(`${SRC}/js/**/*.page.js`);
  const cssEntries = await glob(`${SRC}/css/**/*.page.css`);

  if (jsEntries.length === 0 && cssEntries.length === 0) {
    throw new Error('No entry points found: check the glob patterns.');
  }

  return {
    entryPoints: [...jsEntries, ...cssEntries],
    bundle: true,
    minify: !dev,
    sourcemap: dev ? 'inline' : false,
    outdir: OUT,
    outbase: SRC,
    splitting: true,
    format: 'esm',
    chunkNames: 'js/chunks/[name]-[hash]',
    platform: 'browser',
    target: ['es2022'],
    logLevel: 'info',
    plugins: [ktorStaticUrlPlugin],
  };
}
