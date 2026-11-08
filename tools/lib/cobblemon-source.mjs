// Gets the Kotlin sources of Cobblemon's per-species model classes (MPL-2.0) into vendor/cobblemon-src/.
//
// It uses a sparse, blob-filtered `git clone` of the tag: a handful of HTTP requests in total. (Fetching the ~300 files one by
// one through gitlab.com's raw-file API runs into its per-IP rate limit after a few dozen requests, and its archive endpoint
// refuses scripted requests with HTTP 406.)  Needs `git` on the PATH.
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const REPO_URL = 'https://gitlab.com/cable-mc/cobblemon.git';
const KOTLIN = 'common/src/main/kotlin/com/cobblemon/mod/common/';
const BLOCKBENCH = KOTLIN + 'client/render/models/blockbench/';
export const MODELS_DIR = BLOCKBENCH + 'pokemon';
export const POSE_TYPE_FILE = KOTLIN + 'entity/PoseType.kt';

function git(args, cwd) {
  const r = spawnSync('git', args, { cwd, encoding: 'utf8', env: { ...process.env, GIT_TERMINAL_PROMPT: '0' } });
  if (r.error) throw new Error(`could not run git: ${r.error.message} (is git installed?)`);
  if (r.status !== 0) throw new Error(`git ${args.join(' ')} failed:\n${r.stderr || r.stdout}`);
  return r.stdout.trim();
}

/**
 * Makes sure destDir is a checkout of tag `ref` containing the model classes and PoseType.kt.
 * Returns { modelsDir, poseTypeFile } (absolute paths).
 */
export function fetchModelSources(ref, destDir, log = console.log) {
  const have = fs.existsSync(path.join(destDir, '.git'));
  if (have && git(['describe', '--tags'], destDir) !== ref) { fs.rmSync(destDir, { recursive: true, force: true }); }
  if (!fs.existsSync(path.join(destDir, '.git'))) {
    fs.mkdirSync(path.dirname(destDir), { recursive: true });
    log(`cloning Cobblemon ${ref} (sparse) ...`);
    git(['clone', '--depth', '1', '--branch', ref, '--filter=blob:none', '--sparse', '--no-tags', REPO_URL, destDir]);
  }
  // the model classes, PoseType.kt, and (for reference when the converter meets a new construct) the frame and animation helpers
  git(['sparse-checkout', 'set', '--no-cone', `/${MODELS_DIR}/`, `/${POSE_TYPE_FILE}`, `/${BLOCKBENCH}frame/`, `/${BLOCKBENCH}animation/`], destDir);
  const modelsDir = path.join(destDir, MODELS_DIR), poseTypeFile = path.join(destDir, POSE_TYPE_FILE);
  if (!fs.existsSync(poseTypeFile) || !fs.existsSync(modelsDir)) throw new Error('sparse checkout did not produce the model classes');
  log(`cobblemon source ${ref} at ${git(['rev-parse', '--short', 'HEAD'], destDir)}`);
  return { modelsDir, poseTypeFile };
}
