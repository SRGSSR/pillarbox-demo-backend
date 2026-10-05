// The production bundles: minified, without source maps.
import * as esbuild from 'esbuild';
import { bundleOptions } from './bundle.mjs';

await esbuild.build(await bundleOptions({ dev: false }));
