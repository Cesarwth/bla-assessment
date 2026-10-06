import js from '@eslint/js'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import globals from 'globals'
import tseslint from 'typescript-eslint'

const features = ['auth', 'catalog', 'collection']

/**
 * Layering: a feature may use `shared/`, never another feature; `shared/` may not use features or the app shell.
 * Feature tests may render the whole `App` (they are integration tests of the composed UI), so only the app-shell
 * ban is lifted for them.
 */
const otherFeatures = (feature) =>
  features
    .filter((other) => other !== feature)
    .flatMap((other) => [`**/${other}`, `**/${other}/**`])

const featureIsolation = features.flatMap((feature) => {
  const noOtherFeature = {
    group: otherFeatures(feature),
    message:
      'Features never import each other: share through src/shared or compose in src/app/App.tsx.',
  }
  const noAppShell = {
    group: ['**/app', '**/app/**'],
    message: 'Features never import the app shell.',
  }
  return [
    {
      files: [`src/features/${feature}/**`],
      rules: { 'no-restricted-imports': ['error', { patterns: [noOtherFeature, noAppShell] }] },
    },
    {
      files: [`src/features/${feature}/**/*.test.{ts,tsx}`],
      rules: { 'no-restricted-imports': ['error', { patterns: [noOtherFeature] }] },
    },
  ]
})

export default tseslint.config(
  { ignores: ['dist', 'coverage'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: { ecmaVersion: 2022, globals: globals.browser },
    plugins: { 'react-hooks': reactHooks, 'react-refresh': reactRefresh },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
  ...featureIsolation,
  {
    files: ['src/shared/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/features', '**/features/**', '**/app', '**/app/**'],
              message: 'shared/ is the bottom layer: it must not import features or the app shell.',
            },
          ],
        },
      ],
    },
  },
  {
    // Test helpers legitimately export components next to plain functions.
    files: ['src/test/**', '**/*.test.{ts,tsx}'],
    rules: { 'react-refresh/only-export-components': 'off' },
  },
)
