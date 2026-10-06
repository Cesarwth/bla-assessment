import { describe, expect, it } from 'vitest'
import { addTag, MAX_TAG_LENGTH, MAX_TAGS } from './tags'

describe('addTag', () => {
  it('trims and lowercases like the backend does', () => {
    expect(addTag(['a'], '  Starter ')).toEqual({ tags: ['a', 'starter'] })
  })

  it('ignores blanks and duplicates quietly', () => {
    expect(addTag(['starter'], '   ')).toEqual({ tags: ['starter'] })
    expect(addTag(['starter'], 'STARTER')).toEqual({ tags: ['starter'] })
  })

  it('refuses tags that are too long, explaining why', () => {
    const result = addTag([], 'x'.repeat(MAX_TAG_LENGTH + 1))

    expect(result.tags).toEqual([])
    expect(result.error).toBe(`Tags can have at most ${MAX_TAG_LENGTH} characters.`)
  })

  it('accepts a tag of exactly the maximum length', () => {
    expect(addTag([], 'x'.repeat(MAX_TAG_LENGTH)).error).toBeUndefined()
  })

  it('refuses an eleventh tag', () => {
    const full = Array.from({ length: MAX_TAGS }, (_, index) => `tag${index}`)

    const result = addTag(full, 'one-more')

    expect(result.tags).toEqual(full)
    expect(result.error).toBe(`You can have up to ${MAX_TAGS} tags.`)
  })

  it('does not mutate the list it was given', () => {
    const tags = ['a']
    addTag(tags, 'b')
    expect(tags).toEqual(['a'])
  })
})
