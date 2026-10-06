/** Joins class names, skipping the falsy ones: `cx('a', isOn && 'b')`. */
export const cx = (...parts: (string | false | null | undefined)[]): string =>
  parts.filter(Boolean).join(' ')
