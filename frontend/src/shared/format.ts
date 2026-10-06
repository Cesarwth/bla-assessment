const capitalize = (word: string) => word.charAt(0).toUpperCase() + word.slice(1)

/** "special-attack" -> "Special attack": for stats, abilities and other labels. */
export function formatLabel(slug: string): string {
  return capitalize(slug.replaceAll('-', ' '))
}

/** "mr-mime" -> "Mr Mime": for Pokémon names. */
export function formatName(slug: string): string {
  return slug.split('-').map(capitalize).join(' ')
}

export function formatId(id: number): string {
  return `#${String(id).padStart(3, '0')}`
}

export function formatWeight(kg: number): string {
  return `${kg.toFixed(1)} kg`
}

export function formatHeight(meters: number): string {
  return `${meters.toFixed(1)} m`
}

export function formatList(slugs: string[]): string {
  return slugs.map(formatLabel).join(', ')
}
