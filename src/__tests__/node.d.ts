/**
 * The few Node built-ins the drift-guard test needs, declared by hand.
 *
 * Pulling in `@types/node` instead would be simpler but is not viable: its DOM-ish web globals
 * (`FormData`, `Request`, `Response`, `AbortController`, …) collide with the ones React Native's
 * own `globals.d.ts` declares, producing dozens of TS2300/TS2403 errors that have nothing to do
 * with this package. Declaring the surface actually used keeps the test typechecked without
 * putting two conflicting global environments in the same program.
 */

declare const __dirname: string;

declare module 'node:fs' {
  export function readFileSync(path: string, encoding: 'utf8'): string;
}

declare module 'node:path' {
  export function join(...segments: string[]): string;
}
