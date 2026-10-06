// The same limits and normalization as the API (LocalAttributes in the backend), applied while typing so the
// user gets immediate feedback; the API remains the authority.
export const MAX_TAGS = 10
export const MAX_TAG_LENGTH = 30

export interface AddTagResult {
  tags: string[]
  error?: string
}

export function addTag(tags: string[], raw: string): AddTagResult {
  const tag = raw.trim().toLowerCase()
  if (tag === '' || tags.includes(tag)) return { tags }
  if (tag.length > MAX_TAG_LENGTH) return { tags, error: `Tags can have at most ${MAX_TAG_LENGTH} characters.` }
  if (tags.length >= MAX_TAGS) return { tags, error: `You can have up to ${MAX_TAGS} tags.` }
  return { tags: [...tags, tag] }
}
