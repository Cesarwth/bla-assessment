// Each firefly is a tiny glowing dot: a position and a phase, so they never blink together.
const FIREFLIES = [
  'top-[52%] left-[18%] [animation-delay:-1s]',
  'top-[64%] left-[31%] [animation-delay:-4s] [animation-duration:11s]',
  'top-[44%] left-[42%] [animation-delay:-6s]',
  'top-[70%] left-[55%] [animation-delay:-2.5s] [animation-duration:10s]',
  'top-[48%] left-[66%] [animation-delay:-5s]',
  'top-[60%] left-[78%] [animation-delay:-7s] [animation-duration:12s]',
  'top-[78%] left-[24%] [animation-delay:-3s] [animation-duration:9s]',
  'top-[75%] left-[88%] [animation-delay:-0.5s]',
] as const

/**
 * The forest behind everything: a fixed layer under the page (never `background-attachment: fixed`, which janks on
 * phones) that ignores the pointer, so it cannot swallow a click, and is hidden from assistive technology.
 * The art itself is CSS (`backdrop-art`); the fireflies only exist at night and only where motion is welcome (under prefers-reduced-motion they are
 * not rendered at all, since a static dot would just look like a stain).
 */
export function Backdrop() {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 -z-10 overflow-hidden backdrop-art"
    >
      {FIREFLIES.map((position) => (
        <span
          key={position}
          className={`absolute hidden size-1 rounded-full bg-firefly opacity-60 shadow-[0_0_8px_2px_var(--color-firefly)] motion-safe:animate-firefly motion-safe:dark:block ${position}`}
        />
      ))}
    </div>
  )
}
