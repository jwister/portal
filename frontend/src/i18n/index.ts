import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import en from './locales/en.json'
import zhCN from './locales/zh-CN.json'

export const LOCALE_STORAGE_KEY = 'ztoken.locale'

export type PortalLanguage = 'en' | 'zh-CN'

export function resolveInitialLanguage(languages: readonly string[]): PortalLanguage {
  const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
  if (stored === 'en' || stored === 'zh-CN') {
    return stored
  }
  return languages.some((language) => language.toLowerCase().startsWith('zh')) ? 'zh-CN' : 'en'
}

export function setStoredLanguage(language: PortalLanguage): void {
  localStorage.setItem(LOCALE_STORAGE_KEY, language)
}

const browserLanguages = typeof navigator === 'undefined' ? [] : navigator.languages

void i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    'zh-CN': { translation: zhCN },
  },
  lng: resolveInitialLanguage(browserLanguages),
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

export default i18n
