import styles from './PokemonImage.module.css'

interface Props {
  src: string | null
  alt: string
  /** Rendered pixel size; fixed width/height keep the layout from jumping while the image loads. */
  size: number
  fallback: string
  priority?: boolean
}

export function PokemonImage({ src, alt, size, fallback, priority = false }: Props) {
  if (!src) return <div className={styles.placeholder}>{fallback}</div>
  return (
    <img
      className={styles.image}
      src={src}
      alt={alt}
      width={size}
      height={size}
      loading={priority ? 'eager' : 'lazy'}
      decoding="async"
    />
  )
}
