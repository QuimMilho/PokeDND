// The fake mod (tools/fake-mod) as a helper for the end-to-end tests: it connects to the real API like the Minecraft mod would, and the
// test then looks at the messages it received (chapters 18.2 to 18.5).
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

/** One message of the log: `dir` is "in" for a message the API sent and "out" for one the fake mod sent. */
export interface LoggedMessage {
  dir: 'in' | 'out';
  message: { type: string; id: string; gameId?: string | null; ref?: string | null; data: Record<string, any> };
}

export interface RunningFakeMod {
  /** Everything that was sent and received, in order. */
  log: LoggedMessage[];
  /** The lines the fake mod would show on the screen ("JOIN CODE: K7M2QX", "Spawned fake player …"). */
  screen: string[];
  /** What the fake mod remembers: the lobby, the game state and its fake players. */
  snapshot(): { ready: boolean; serverId: string | null; gameState: string | null; lobby: { open: boolean; code?: string }; players: Record<string, { id: string; name: string }> };
  /** The messages of one type, from the API (`in`, the default) or from the fake mod (`out`). */
  messages(type: string, dir?: 'in' | 'out'): LoggedMessage['message'][];
  close(): void;
}

/** The folder of the fake mod, next to App/ (the tests run with App/ as the working directory). */
const CLIENT = resolve(process.cwd(), '..', 'tools', 'fake-mod', 'client.mjs');

/** Connects a fake mod to the API with the token of a registered server and waits for the API's hello.ack. */
export async function connectFakeMod(token: string, options: Record<string, unknown> = {}): Promise<RunningFakeMod> {
  const api = process.env['POKEDND_E2E_API'];
  if (!api) throw new Error('POKEDND_E2E_API is not set: the global setup did not run');
  const { FakeModClient } = await import(pathToFileURL(CLIENT).href);
  const screen: string[] = [];
  const client = new FakeModClient({
    url: `${api.replace(/^http/, 'ws')}/ws/mod`,
    token,
    options: { walkDelayMs: 100, skinDelayMs: 50, ...options },
    print: (line: string) => screen.push(line),
  });
  await client.start({ timeoutMs: 20_000 });
  return {
    log: client.log,
    screen,
    snapshot: () => client.mod.snapshot(),
    messages: (type, dir = 'in') => client.log.filter((e: LoggedMessage) => e.dir === dir && e.message.type === type).map((e: LoggedMessage) => e.message),
    close: () => client.close(),
  };
}
