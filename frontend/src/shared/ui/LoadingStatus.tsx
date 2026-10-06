/** Tells assistive technology that something is loading; the visible skeletons are decorative. */
export function LoadingStatus({ label = 'Loading Pokémon…' }: { label?: string }) {
  return (
    <div role="status">
      <span className="visually-hidden">{label}</span>
    </div>
  )
}
