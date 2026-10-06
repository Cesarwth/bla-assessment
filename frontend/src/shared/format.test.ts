import { describe, expect, it } from 'vitest'
import { formatHeight, formatId, formatLabel, formatName, formatWeight } from './format'

describe('formatting', () => {
  it('turns API slugs into readable labels', () => {
    expect(formatLabel('special-attack')).toBe('Special attack')
    expect(formatLabel('lightning-rod')).toBe('Lightning rod')
    expect(formatLabel('hp')).toBe('Hp')
  })

  it('turns Pokémon names into title case', () => {
    expect(formatName('pikachu')).toBe('Pikachu')
    expect(formatName('mr-mime')).toBe('Mr Mime')
    expect(formatName('deoxys-attack')).toBe('Deoxys Attack')
  })

  it('shows the Pokédex number with at least four digits', () => {
    expect(formatId(6)).toBe('#0006')
    expect(formatId(25)).toBe('#0025')
    expect(formatId(1025)).toBe('#1025')
    expect(formatId(10001)).toBe('#10001')
  })

  it('shows weight and height with one decimal', () => {
    expect(formatWeight(6)).toBe('6.0 kg')
    expect(formatWeight(100)).toBe('100.0 kg')
    expect(formatHeight(0.4)).toBe('0.4 m')
  })
})
