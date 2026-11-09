import { TestRequest, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture } from '@angular/core/testing';

export const sleep = (ms: number) => new Promise<void>((done) => setTimeout(done, ms));

/**
 * Lets the screen catch up: change detection and a few turns of the event loop. Used instead of `fixture.whenStable()`, which would
 * wait for HTTP calls that a test has not answered yet.
 */
export async function flushUi(fixture: ComponentFixture<unknown>, rounds = 3): Promise<void> {
  for (let i = 0; i < rounds; i++) {
    fixture.detectChanges();
    await sleep(0);
  }
  fixture.detectChanges();
}

/** The next request to this address, even when the app only makes it after a few turns of the event loop (a route guard, for example). */
export async function nextRequest(http: HttpTestingController, url: string): Promise<TestRequest> {
  for (let i = 0; i < 100; i++) {
    const found = http.match(url);
    if (found.length) return found[0];
    await sleep(5);
  }
  throw new Error('No request to ' + url + ' was made');
}

/**
 * Presses the control with this `data-testid` the way a person would. Material chips and switches put the real button inside their
 * host element, so the click goes to that button when there is one.
 */
export function press(root: HTMLElement, testId: string): void {
  const host = root.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
  if (!host) throw new Error('Nothing with data-testid=' + testId);
  (host.querySelector<HTMLElement>('button') ?? host).click();
}
