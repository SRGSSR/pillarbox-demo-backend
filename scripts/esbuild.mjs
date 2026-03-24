import * as esbuild from 'esbuild';
import { glob } from 'glob';
import process from 'node:process';

const SRC = 'src/main/resources/static';
const OUT = 'build/resources/main/static';
const jsEntries = await glob(`${SRC}/js/**/*.page.js`);
const cssEntries = await glob(`${SRC}/css/**/*.page.css`);
const ktorStaticUrlPlugin = {
  name: 'ktor-static-urls',
  setup(build) {
    build.onResolve({filter: /^\/static\//}, args => ({
      path: args.path,
      external: true,
    }));
  },
};

if (jsEntries.length === 0 && cssEntries.length === 0) {
  console.error('No entry points found — check your glob patterns.');
  process.exit(1);
}

await esbuild.build({
  entryPoints: [...jsEntries, ...cssEntries],
  bundle: true,
  minify: true,
  sourcemap: false,
  outdir: OUT,
  outbase: SRC,
  platform: 'browser',
  target: ['es2020'],
  logLevel: 'info',
  plugins: [ktorStaticUrlPlugin]
});
