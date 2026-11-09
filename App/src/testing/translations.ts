import { clearTranslations, loadTranslations } from '@angular/localize';
import ptXlf from '../locale/messages.pt.xlf';

/**
 * The Portuguese texts of `messages.pt.xlf`, as `loadTranslations` wants them: placeholders `<ph equiv="min"/>` become `{$min}`.
 * Messages with a plural (ICU) are left out; the unit tests of the forms do not need them.
 */
export function portugueseTranslations(): Record<string, string> {
  const doc = new DOMParser().parseFromString(ptXlf, 'application/xml');
  const out: Record<string, string> = {};
  for (const unit of Array.from(doc.getElementsByTagName('unit'))) {
    const target = unit.getElementsByTagName('target')[0];
    if (!target) continue;
    let text = '';
    for (const node of Array.from(target.childNodes)) {
      if (node.nodeType === Node.TEXT_NODE) text += node.textContent;
      else if (node.nodeName === 'ph') text += `{$${(node as Element).getAttribute('equiv')}}`;
    }
    if (!text.startsWith('{VAR_PLURAL')) out[unit.getAttribute('id') ?? ''] = text;
  }
  return out;
}

/** Makes every `$localize` after this call answer in Portuguese. Call `useEnglish()` when the test is over. */
export function usePortuguese(): void {
  loadTranslations(portugueseTranslations());
}

export function useEnglish(): void {
  clearTranslations();
}
