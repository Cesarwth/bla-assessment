import type { ReactNode } from 'react'
import { formatId } from '../format'
import { PokemonImage } from './PokemonImage'

interface Props {
  /** The primary type: it picks the gradient. */
  type: string | undefined
  id: number
  /** The Pokémon's name as shown; it is also the alt text of the image. */
  name: string
  /** The official artwork, or the sprite when there is none. */
  imageSrc: string | null
  /** The title link. It must cover the card (`after:absolute after:inset-0`) so the card is one big target. */
  title: ReactNode
  /** Marks and controls that sit on the art, above the card-wide link. */
  extras?: ReactNode
  /** The inner light panel: pills and facts. */
  children: ReactNode
}

/**
 * The shared look of the catalog and collection cards: type gradient, glass number, glowing artwork and an inner
 * panel on theme surface colors, so the text there is readable in both color schemes.
 */
export function PokemonCardFrame({ type, id, name, imageSrc, title, extras, children }: Props) {
  return (
    <article
      className="relative flex h-full flex-col overflow-hidden rounded-3xl border-4 border-white/60 type-card shadow-xl motion-safe:animate-card-in motion-safe:transition-transform motion-safe:duration-200 motion-safe:focus-within:scale-105 motion-safe:hover:scale-105 has-[input:checked]:ring-4 has-[input:checked]:ring-primary has-[input:checked]:ring-offset-2 has-[input:checked]:ring-offset-canvas focus-within:ring-4 focus-within:ring-focus focus-within:ring-offset-2 focus-within:ring-offset-canvas"
      data-type={type ?? 'unknown'}
    >
      <div className="flex items-start justify-between gap-2 px-4 pt-4">
        <h2 className="mb-0 min-w-0 text-xl font-bold wrap-break-word capitalize">{title}</h2>
        <span className="type-glass rounded-full px-2.5 py-0.5 text-xs font-bold tabular-nums">
          {formatId(id)}
        </span>
      </div>
      <div className="relative grid place-items-center px-4 py-3">
        {/* Decorative glow behind the art. */}
        <div
          className="absolute size-40 rounded-full bg-white/30 blur-2xl sm:size-44"
          aria-hidden="true"
        />
        <div className="relative size-40 drop-shadow-xl sm:size-48">
          <PokemonImage src={imageSrc} alt={name} size={192} fallback="No image" />
        </div>
        {extras}
      </div>
      <div className="mx-3 mb-3 flex flex-1 flex-col gap-3 rounded-2xl bg-surface p-4 text-fg shadow-inner">
        {children}
      </div>
    </article>
  )
}
