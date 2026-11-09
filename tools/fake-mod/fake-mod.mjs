#!/usr/bin/env node
// The fake mod: a stand-in for the Minecraft mod that speaks the mod protocol (spec 18.5). Phase 39.
//
//   node tools/fake-mod/fake-mod.mjs --url ws://localhost:8080/ws/mod --token <server token>            interactive
//   node tools/fake-mod/fake-mod.mjs --scenario tools/fake-mod/scenarios/lobby.json --url … --token …  scripted
//
// Exit codes: 0 fine, 1 the connection or a scenario step failed, 2 wrong command line, 3 incompatible protocol version.
import fs from 'node:fs';
import { FakeModClient, runScenario } from './client.mjs';
import { PROTOCOL_VERSION, ProtocolMismatchError } from './protocol.mjs';

const HELP = `Fake mod for PokeDnD (protocol version ${PROTOCOL_VERSION})

Usage: node tools/fake-mod/fake-mod.mjs --url <ws-url> --token <server-token> [options]

  --url <url>           the API's mod socket, for example ws://localhost:8080/ws/mod (or env FAKE_MOD_URL)
  --token <token>       the server token shown once when the server was registered (or env FAKE_MOD_TOKEN)
  --scenario <file>     run a JSON scenario and exit (omit for the interactive mode: it runs until Ctrl-C)
  --walk-delay <ms>     time between a walk command and the arrival report (default 1500)
  --skin-delay <ms>     time between skin.apply and skin.applied (default 200)
  --block <ids>         comma-separated trainer ids whose walks end in walk.blocked
  --fail-skin <ids>     comma-separated trainer ids whose skin cannot be applied
  --quiet               do not print every message, only what the mod would show
  --help                this text
`;

function parseArgs(argv) {
  const args = { quiet: false };
  const valueOf = { '--url': 'url', '--token': 'token', '--scenario': 'scenario', '--walk-delay': 'walkDelay', '--skin-delay': 'skinDelay', '--block': 'block', '--fail-skin': 'failSkin' };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--help' || a === '-h') args.help = true;
    else if (a === '--quiet') args.quiet = true;
    else if (valueOf[a]) {
      if (argv[i + 1] === undefined) throw new Error(`${a} needs a value.`);
      args[valueOf[a]] = argv[++i];
    } else throw new Error(`Unknown option ${a}. Try --help.`);
  }
  return args;
}

const list = (text) => (text ? text.split(',').map((s) => s.trim()).filter(Boolean) : undefined);
const number = (text, name) => { const n = Number(text); if (!Number.isInteger(n) || n < 0) throw new Error(`${name} must be a whole number of milliseconds.`); return n; };

async function main() {
  let args;
  try { args = parseArgs(process.argv.slice(2)); } catch (e) { console.error(e.message); return 2; }
  if (args.help) { console.log(HELP); return 0; }

  let scenario = null;
  if (args.scenario) {
    try { scenario = JSON.parse(fs.readFileSync(args.scenario, 'utf8')); } catch (e) { console.error(`Cannot read the scenario ${args.scenario}: ${e.message}`); return 2; }
  }
  const url = args.url ?? process.env.FAKE_MOD_URL ?? scenario?.url;
  const token = args.token ?? process.env.FAKE_MOD_TOKEN ?? scenario?.token;
  if (!url || !token) { console.error('Both --url and --token are needed (or FAKE_MOD_URL and FAKE_MOD_TOKEN).\n\n' + HELP); return 2; }

  const options = { ...(scenario?.options ?? {}) };
  try {
    if (args.walkDelay !== undefined) options.walkDelayMs = number(args.walkDelay, '--walk-delay');
    if (args.skinDelay !== undefined) options.skinDelayMs = number(args.skinDelay, '--skin-delay');
  } catch (e) { console.error(e.message); return 2; }
  if (args.block) options.blockedTrainers = list(args.block);
  if (args.failSkin) options.failSkins = list(args.failSkin);

  const print = (line) => console.log(line);
  try {
    if (scenario) {
      const result = await runScenario({ ...scenario, options }, { url, token, print, verbose: !args.quiet });
      console.log(`Scenario finished: ${result.steps} steps passed.`);
      return 0;
    }
    const client = new FakeModClient({ url, token, options, print, verbose: !args.quiet });
    await client.start();
    print('Waiting for commands. Press Ctrl-C to stop.');
    process.once('SIGINT', () => client.close());
    await client.closedPromise();
    if (client.fatal) throw client.fatal;
    return 0;
  } catch (e) {
    console.error(e.message);
    return e instanceof ProtocolMismatchError ? 3 : 1;
  }
}

process.exitCode = await main();
