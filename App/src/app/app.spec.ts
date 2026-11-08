import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [App] }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows the app name and the placeholder text (English source text)', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('PokeDnD');
    expect(text).toContain('placeholder home screen');
  });

  it('offers links to the English and the Portuguese build', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const hrefs = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('nav a')).map((a) =>
      a.getAttribute('href'),
    );
    expect(hrefs).toEqual(['/en/', '/pt/']);
  });
});
