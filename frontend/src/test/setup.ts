import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
});

const existingLocalStorage = Object.getOwnPropertyDescriptor(window, 'localStorage')?.value as
  | Storage
  | undefined;

if (!existingLocalStorage) {
  const storage = createMemoryStorage();
  Object.defineProperty(window, 'localStorage', {
    value: storage,
    configurable: true
  });
  Object.defineProperty(globalThis, 'localStorage', {
    value: storage,
    configurable: true
  });
}

function createMemoryStorage(): Storage {
  const values = new Map<string, string>();

  return {
    get length() {
      return values.size;
    },
    clear() {
      values.clear();
    },
    getItem(key: string) {
      return values.get(key) ?? null;
    },
    key(index: number) {
      return Array.from(values.keys())[index] ?? null;
    },
    removeItem(key: string) {
      values.delete(key);
    },
    setItem(key: string, value: string) {
      values.set(key, value);
    }
  };
}
