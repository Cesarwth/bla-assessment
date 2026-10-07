/** Joins class names, skipping the falsy ones: `classNames('a', isOn && 'b')`. */
export const classNames = (...parts: (string | false | null | undefined)[]): string =>
  parts.filter(Boolean).join(' ')
