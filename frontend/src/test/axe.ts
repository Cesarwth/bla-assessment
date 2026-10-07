import { axe } from 'vitest-axe'
import { expect } from 'vitest'

/**
 * Runs axe on the rendered page. Two rules cannot run in jsdom, which has no layout engine or canvas:
 * color-contrast (the theme colors in src/index.css were chosen to meet WCAG AA instead) and
 * label-content-name-mismatch (it measures text on a canvas; by convention our aria-labels contain the
 * visible text, e.g. "Next page" for a link that reads "Next").
 */
export async function expectNoA11yViolations(container: HTMLElement): Promise<void> {
  const axeResults = await axe(container, {
    rules: {
      'color-contrast': { enabled: false },
      'label-content-name-mismatch': { enabled: false },
    },
  })
  expect(axeResults).toHaveNoViolations()
}
