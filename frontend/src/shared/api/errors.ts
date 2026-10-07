export interface FieldError {
  field: string
  message: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

/** What to tell the user about a failure: the backend writes people-friendly messages, so they pass through. */
export function describeError(error: unknown): string {
  if (!isApiError(error)) return 'Something unexpected happened. Please try again.'
  if (error.code === 'CATALOG_UNAVAILABLE' || error.code === 'CATALOG_TIMEOUT') {
    return 'The Pokémon catalog is not answering right now. Please try again in a moment.'
  }
  return error.message
}
