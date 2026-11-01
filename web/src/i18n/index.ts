import en from './en.json';

export type Locale = 'en';
export type Key = keyof typeof en;

const catalogs: Record<Locale, Record<string, string>> = { en };

// Falls back to English so a missing translation never renders a bare key.
export function useT(locale: Locale = 'en') {
  return (key: Key): string => catalogs[locale]?.[key] ?? en[key];
}
