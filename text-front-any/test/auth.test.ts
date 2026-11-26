import { describe, expect, it } from 'vitest';
import { ApiKeyStore, extractApiKey, MIN_KEY_LENGTH } from '../src/auth';

const KEY_A = 'a'.repeat(MIN_KEY_LENGTH);
const KEY_B = 'secret-key-for-team-b';

describe('ApiKeyStore.parse', () => {
  it('maps each key to its client name', () => {
    const store = ApiKeyStore.parse(`team-a:${KEY_A}, team-b:${KEY_B}`);
    expect(store.size).toBe(2);
    expect(store.identify(KEY_A)).toBe('team-a');
    expect(store.identify(KEY_B)).toBe('team-b');
  });

  it('allows ":" inside the key itself', () => {
    expect(ApiKeyStore.parse(`t:${KEY_B}:x`).identify(`${KEY_B}:x`)).toBe('t');
  });

  it.each([
    ['empty spec', ''],
    ['missing name', `:${KEY_A}`],
    ['missing key', 'team-a:'],
    ['no separator', 'team-a'],
    ['short key', 'team-a:short'],
    ['duplicate name', `t:${KEY_A},t:${KEY_B}`],
  ])('rejects %s', (_label, spec) => {
    expect(() => ApiKeyStore.parse(spec)).toThrow();
  });

  it.each([undefined, '', 'wrong-key-wrong-key', KEY_A.slice(1), `${KEY_A} `])('does not identify %j', (key) => {
    expect(ApiKeyStore.parse(`team-a:${KEY_A}`).identify(key)).toBeNull();
  });
});

describe('extractApiKey', () => {
  it.each<[Record<string, string | string[]>, string | undefined]>([
    [{ authorization: `Bearer ${KEY_A}` }, KEY_A],
    [{ authorization: `bearer   ${KEY_A}  ` }, KEY_A],
    [{ 'x-api-key': KEY_B }, KEY_B],
    [{ 'x-api-key': [KEY_B, 'other'] }, KEY_B],
    [{ authorization: `Basic ${KEY_A}` }, undefined],
    [{ 'x-api-key': '   ' }, undefined],
    [{}, undefined],
  ])('reads %j', (headers, expected) => {
    expect(extractApiKey(headers)).toBe(expected);
  });
});
