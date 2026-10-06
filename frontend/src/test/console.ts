/** Console output captured during the current test; setup.ts fails the test if any is left at the end. */
export const consoleOutput: string[] = []

/**
 * For the rare test that provokes console output on purpose (React logs errors caught by an error boundary):
 * removes the entries matching `pattern` and returns them, so the test can assert they happened.
 */
export function expectConsoleOutput(pattern: RegExp): string[] {
  const matching = consoleOutput.filter((line) => pattern.test(line))
  for (const line of matching) consoleOutput.splice(consoleOutput.indexOf(line), 1)
  return matching
}
