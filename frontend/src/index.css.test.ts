import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

// The palette lives in src/index.css only; the test reads it from there so the values checked are the shipped ones.
const css = readFileSync(resolve(process.cwd(), 'src/index.css'), 'utf8')
const token = (name: string) => css.match(new RegExp(`--color-type-${name}: (#[0-9a-f]{6});`))?.[1]

const TYPES = [
  'normal', 'fire', 'water', 'electric', 'grass', 'ice', 'fighting', 'poison', 'ground',
  'flying', 'psychic', 'bug', 'rock', 'ghost', 'dragon', 'dark', 'steel', 'fairy',
] as const // prettier-ignore

type Rgb = [number, number, number]
const rgb = (hex: string): Rgb => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16)) as Rgb

function luminance([r, g, b]: Rgb): number {
  const [lr, lg, lb] = [r, g, b].map((v) => {
    const c = v / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * lr! + 0.7152 * lg! + 0.0722 * lb!
}

function contrast(a: Rgb, b: Rgb): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi! + 0.05) / (lo! + 0.05)
}

const mixWithWhite = (c: Rgb, share: number): Rgb =>
  c.map((v) => Math.round(v * (1 - share) + 255 * share)) as Rgb

describe('type palette', () => {
  it.each(TYPES)('%s has a gradient pair, a pill color and an ink', (type) => {
    for (const part of ['from', 'to', 'pill', 'ink']) {
      expect(token(`${type}-${part}`), `${type}-${part}`).toBeDefined()
    }
    expect(css).toContain(`[data-type='${type}']`)
  })

  it.each(TYPES)('%s: the ink reaches WCAG AA (4.5:1) on its gradient and pill', (type) => {
    const ink = rgb(token(`${type}-ink`)!)
    const from = rgb(token(`${type}-from`)!)
    const to = rgb(token(`${type}-to`)!)
    const pill = rgb(token(`${type}-pill`)!)
    // The number sits on a glass pill: 15 % white over the gradient (type-glass), which is the worst case.
    const spots = [from, to, pill, mixWithWhite(from, 0.15), mixWithWhite(to, 0.15)]

    for (const spot of spots) expect(contrast(ink, spot)).toBeGreaterThanOrEqual(4.5)
  })
})

// Text never sits straight on the forest art: it sits on a translucent theme surface (the "glass": bg-surface/80)
// or on an opaque one. The art changes under the glass, so the check mixes the surface with the darkest and the
// lightest tone the art reaches (--backdrop-lo / --backdrop-hi) and requires AA on both.
const GLASS_ALPHA = 0.8
const darkStart = css.indexOf('@media (prefers-color-scheme: dark)')
const schemes = {
  light: css.slice(0, darkStart),
  dark: css.slice(darkStart),
} as const

function schemeColor(scheme: keyof typeof schemes, name: string): Rgb {
  const hex =
    schemes[scheme].match(new RegExp(`--${name}: (#[0-9a-f]{6});`))?.[1] ??
    schemes.light.match(new RegExp(`--${name}: (#[0-9a-f]{6});`))?.[1]
  if (!hex) throw new Error(`--${name} is not declared`)
  return rgb(hex)
}

const over = (top: Rgb, bottom: Rgb, alpha: number): Rgb =>
  top.map((v, i) => Math.round(v * alpha + bottom[i]! * (1 - alpha))) as Rgb

describe.each(['light', 'dark'] as const)('%s scheme: text on the forest backdrop', (scheme) => {
  const color = (name: string) => schemeColor(scheme, `color-${name}`)
  const backdrops = [schemeColor(scheme, 'backdrop-lo'), schemeColor(scheme, 'backdrop-hi')]

  it.each(['fg', 'muted', 'accent', 'danger'])(
    '%s reaches AA on glass over the darkest and lightest art',
    (name) => {
      for (const backdrop of backdrops) {
        const glass = over(color('surface'), backdrop, GLASS_ALPHA)
        expect(
          contrast(color(name), glass),
          `${name} on glass over ${backdrop}`,
        ).toBeGreaterThanOrEqual(4.5)
      }
    },
  )

  it('the page text reaches AA on the color shown while the art loads', () => {
    expect(contrast(color('fg'), color('canvas'))).toBeGreaterThanOrEqual(4.5)
  })

  it.each([
    ['primary-ink', 'primary-soft'],
    ['primary-ink', 'primary-soft-hover'],
    ['fg', 'surface-muted'],
    ['fg', 'primary-soft'],
    ['danger', 'surface'],
    ['danger', 'danger-surface'],
  ])('button text %s on %s reaches AA', (ink, fill) => {
    expect(contrast(color(ink), color(fill))).toBeGreaterThanOrEqual(4.5)
  })
})
