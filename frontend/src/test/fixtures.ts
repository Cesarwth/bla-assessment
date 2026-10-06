import type { EvolutionNode, PageResponse, PokemonDetail, PokemonSummary } from '../shared/api/types'

export function summary(id: number, name: string, types: string[] = ['normal'], weightKg = 10): PokemonSummary {
  return { id, name, spriteUrl: `https://img.test/sprites/${id}.png`, types, weightKg, abilities: ['run-away'] }
}

export const bulbasaur: PokemonSummary = {
  id: 1,
  name: 'bulbasaur',
  spriteUrl: 'https://img.test/sprites/1.png',
  types: ['grass', 'poison'],
  weightKg: 6.9,
  abilities: ['overgrow', 'chlorophyll'],
}

export function pageOf<T>(items: T[], { page = 0, size = 12, totalItems = 1351 } = {}): PageResponse<T> {
  return { items, page, size, totalItems, totalPages: Math.ceil(totalItems / size) }
}

function node(id: number, name: string, requirements: string[] = [], evolvesTo: EvolutionNode[] = []): EvolutionNode {
  return { id, name, spriteUrl: `https://img.test/sprites/${id}.png`, requirements, evolvesTo }
}

const noEvolutionDetail = {
  heightM: 0.4,
  genus: null,
  description: null,
  evolutionChain: null,
}

export const pikachu: PokemonDetail = {
  id: 25,
  name: 'pikachu',
  imageUrl: 'https://img.test/artwork/25.png',
  spriteUrl: 'https://img.test/sprites/25.png',
  types: ['electric'],
  weightKg: 6,
  heightM: 0.4,
  abilities: ['static', 'lightning-rod'],
  stats: [
    { name: 'hp', baseValue: 35 },
    { name: 'attack', baseValue: 55 },
    { name: 'defense', baseValue: 40 },
    { name: 'special-attack', baseValue: 50 },
    { name: 'special-defense', baseValue: 50 },
    { name: 'speed', baseValue: 90 },
  ],
  genus: 'Mouse Pokémon',
  description: 'When several of these POKéMON gather, their electricity could build and cause lightning storms.',
  evolutionChain: node(172, 'pichu', [], [node(25, 'pikachu', ['Level up (happiness 220)'], [node(26, 'raichu', ['Use thunder-stone'])])]),
}

export const eevee: PokemonDetail = {
  ...pikachu,
  id: 133,
  name: 'eevee',
  imageUrl: 'https://img.test/artwork/133.png',
  types: ['normal'],
  abilities: ['run-away', 'adaptability'],
  genus: 'Evolution Pokémon',
  description: 'Its genetic code is irregular.',
  evolutionChain: node(133, 'eevee', [], [
    node(134, 'vaporeon', ['Use water-stone']),
    node(135, 'jolteon', ['Use thunder-stone']),
    node(136, 'flareon', ['Use fire-stone']),
  ]),
}

/** A Pokémon with nothing optional: no image, no text, no evolution data. */
export const ditto: PokemonDetail = {
  ...pikachu,
  ...noEvolutionDetail,
  id: 132,
  name: 'ditto',
  imageUrl: null,
  spriteUrl: null,
  types: ['normal'],
  weightKg: 4,
  abilities: ['limber'],
}

export const mew: PokemonDetail = {
  ...pikachu,
  id: 151,
  name: 'mew',
  evolutionChain: node(151, 'mew'),
}
