interface Props {
  src: string | null
  alt: string
  /** Rendered pixel size; fixed width/height keep the layout from jumping while the image loads. */
  size: number
  fallback: string
  priority?: boolean
}

export function PokemonImage({ src, alt, size, fallback, priority = false }: Props) {
  if (!src)
    return <div className="grid size-full place-items-center text-center text-sm">{fallback}</div>
  return (
    <img
      className="block size-full object-contain"
      src={src}
      alt={alt}
      width={size}
      height={size}
      loading={priority ? 'eager' : 'lazy'}
      decoding="async"
    />
  )
}
