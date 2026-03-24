import globals from 'globals';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import js from '@eslint/js';
import { FlatCompat } from '@eslint/eslintrc';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const compat = new FlatCompat({
  baseDirectory: __dirname,
  recommendedConfig: js.configs.recommended,
  allConfig: js.configs.all
});

export default [...compat.extends('eslint:recommended'), {
  languageOptions: {
    globals: {
      ...globals.browser,
    },

    ecmaVersion: 'latest',
    sourceType: 'module',
  },

  rules: {
    complexity: ['error', {
      max: 5,
    }],

    'function-paren-newline': ['error', 'multiline-arguments'],
    'linebreak-style': ['error', 'unix'],
    'max-depth': ['error', 2],

    'max-len': ['error', {
      code: 80,
      ignoreComments: true,
      ignoreStrings: true,
      ignoreTemplateLiterals: true,
      ignoreTrailingComments: true,
      ignoreUrls: true,
    }],

    'max-lines-per-function': ['error', {
      max: 35,
      skipComments: true,
    }],

    'max-nested-callbacks': ['error', 3],
    'max-statements': ['error', 10],
    'newline-after-var': ['error', 'always'],

    'no-bitwise': ['error', {
      int32Hint: true,
    }],

    'no-cond-assign': ['error', 'always'],

    'no-console': ['error', {
      allow: ['warn', 'error'],
    }],

    'no-plusplus': ['error', {
      allowForLoopAfterthoughts: true,
    }],

    'padding-line-between-statements': ['error', {
      blankLine: 'always',
      prev: '*',
      next: 'return',
    }],

    semi: ['error', 'always'],
    'space-before-function-paren': ['error', 'never'],
  },
}];
