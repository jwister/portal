import { beforeEach, describe, expect, it } from 'vitest'

import { LOCALE_STORAGE_KEY, resolveInitialLanguage, setStoredLanguage } from '../index'

describe('portal language selection', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('selects Chinese when the browser language starts with zh', () => {
    expect(resolveInitialLanguage(['zh-CN', 'en-US'])).toBe('zh-CN')
  })

  it('uses English for a non-Chinese browser language', () => {
    expect(resolveInitialLanguage(['de-DE'])).toBe('en')
  })

  it('persists an explicit language choice', () => {
    setStoredLanguage('en')

    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('en')
  })
})
