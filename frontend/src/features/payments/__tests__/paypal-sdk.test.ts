import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  __resetPayPalSdkCacheForTests,
  buildPayPalSdkUrl,
  loadPayPalSdk,
  type PayPalNamespace,
} from '../paypal-sdk'

const paypalNamespace: PayPalNamespace = {
  Buttons: () => ({ render: () => Promise.resolve(), isEligible: () => true }),
}

function installPayPalWindow(value: PayPalNamespace | undefined): void {
  Object.defineProperty(window, 'paypal', {
    configurable: true,
    writable: true,
    value,
  })
}

function readScript(): HTMLScriptElement | null {
  return document.querySelector<HTMLScriptElement>('script[data-paypal-sdk="1"]')
}

describe('paypal-sdk', () => {
  beforeEach(() => {
    __resetPayPalSdkCacheForTests()
    document.head.innerHTML = ''
    installPayPalWindow(undefined)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    document.head.innerHTML = ''
  })

  it('builds a Sandbox script URL without leaking secrets', () => {
    const url = buildPayPalSdkUrl('public-client', 'sandbox')
    expect(url).toContain('client-id=public-client')
    expect(url).toContain('currency=USD')
    expect(url).toContain('intent=capture')
    expect(url).not.toContain('client-secret')
  })

  it('loads the PayPal script once and resolves with the loaded namespace', async () => {
    let script: HTMLScriptElement | null = null
    const observer = new MutationObserver(() => {
      script = readScript()
    })
    observer.observe(document.head, { childList: true })

    const loadPromise = loadPayPalSdk('public-client', 'sandbox')

    await new Promise<void>((resolve) => queueMicrotask(resolve))
    const appended = readScript()
    expect(appended).not.toBeNull()
    expect(appended?.src).toContain('client-id=public-client')
    expect(appended?.src).toContain('currency=USD')
    observer.disconnect()

    installPayPalWindow(paypalNamespace)
    appended?.dispatchEvent(new Event('load'))

    await expect(loadPromise).resolves.toBe(paypalNamespace)
    script = appended
    void script

    const second = loadPayPalSdk('public-client', 'sandbox')
    installPayPalWindow(paypalNamespace)
    await expect(second).resolves.toBe(paypalNamespace)
    expect(document.head.querySelectorAll('script').length).toBe(1)
  })

  it('rejects when the PayPal script fails to load', async () => {
    const promise = loadPayPalSdk('public-client', 'sandbox')
    await new Promise<void>((resolve) => queueMicrotask(resolve))
    const script = readScript()
    script?.dispatchEvent(new Event('error'))
    await expect(promise).rejects.toThrow(/failed to load/i)
  })
})