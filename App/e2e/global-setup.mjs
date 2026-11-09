// Starts the database, the API and the web server once for all end-to-end tests, and stops them at the end.
import { startStack } from './stack.mjs';

export default async function globalSetup() {
  const stack = await startStack({ log: (message) => console.log(`[stack] ${message}`) });
  // the tests read these (worker processes inherit them)
  process.env.POKEDND_E2E_URL = stack.url;
  process.env.POKEDND_E2E_API = stack.apiUrl;
  return async () => {
    await stack.stop();
  };
}
