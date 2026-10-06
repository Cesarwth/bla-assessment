import { describeError, isApiError } from '../../shared/api/errors'

export interface FormErrors {
  /** Messages keyed by the name of the field they belong to. */
  fields: Record<string, string>
  /** Anything that does not belong to a single field. */
  general?: string
}

// The API reports some problems with a code instead of a field list; these belong to one input.
const FIELD_OF_CODE: Record<string, string> = {
  USERNAME_TAKEN: 'username',
  EMAIL_TAKEN: 'email',
  WEAK_PASSWORD: 'password',
  INVALID_CURRENT_PASSWORD: 'currentPassword',
}

/** `codeFields` re-routes a code to another input, e.g. WEAK_PASSWORD to "newPassword" in the reset and change forms. */
export function toFormErrors(error: unknown, codeFields: Record<string, string> = {}): FormErrors {
  if (isApiError(error)) {
    if (error.fieldErrors.length > 0) {
      const fields: Record<string, string> = {}
      for (const { field, message } of error.fieldErrors) fields[field] ??= message
      return { fields }
    }
    const field = codeFields[error.code] ?? FIELD_OF_CODE[error.code]
    if (field) return { fields: { [field]: error.message } }
  }
  return { fields: {}, general: describeError(error) }
}
