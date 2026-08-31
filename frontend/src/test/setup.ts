import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

const values = new Map<string, string>()

Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: {
    clear: () => values.clear(),
    getItem: (key: string) => values.get(key) ?? null,
    removeItem: (key: string) => values.delete(key),
    setItem: (key: string, value: string) => values.set(key, value),
  },
})

Object.defineProperty(HTMLCanvasElement.prototype, 'getContext', {
  configurable: true,
  value: () => ({
    clearRect: () => undefined,
    fillRect: () => undefined,
    fillStyle: '',
    getImageData: () => ({ data: new Uint8ClampedArray(4) }),
  }),
})

Object.defineProperty(HTMLCanvasElement.prototype, 'toDataURL', {
  configurable: true,
  value: () => 'data:image/png;base64,',
})

class TestResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

Object.defineProperty(globalThis, 'ResizeObserver', {
  configurable: true,
  value: TestResizeObserver,
})

Object.defineProperty(Range.prototype, 'getBoundingClientRect', {
  configurable: true,
  value: () => ({
    bottom: 0,
    height: 0,
    left: 0,
    right: 0,
    top: 0,
    width: 0,
    x: 0,
    y: 0,
    toJSON: () => ({}),
  }),
})

afterEach(cleanup)
