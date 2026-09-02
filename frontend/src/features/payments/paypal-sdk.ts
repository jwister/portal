export interface PayPalButtons {
  render(selector: string | HTMLElement): Promise<void>;
  isEligible(): boolean;
}

export interface PayPalNamespace {
  Buttons: (options: PayPalButtonsOptions) => PayPalButtons;
}

export interface PayPalButtonsOptions {
  createOrder: () => Promise<string>;
  onApprove: (data: { orderID: string }) => Promise<void> | void;
  onCancel?: () => void;
  onError?: (error: unknown) => void;
}

interface PayPalWindow extends Window {
  paypal?: PayPalNamespace;
}

const PAYPAL_SCRIPT_ID = 'ztoken-paypal-sdk';

const scriptPromises = new Map<string, Promise<PayPalNamespace>>();

export function buildPayPalSdkUrl(clientId: string, mode: 'sandbox' | 'live'): string {
  const params = new URLSearchParams({
    'client-id': clientId,
    currency: 'USD',
    intent: 'capture',
  });
  if (mode === 'live') params.set('sdkBaseUrl', 'https://www.paypal.com');
  return `https://www.paypal.com/sdk/js?${params.toString()}`;
}

function existingScript(clientId: string, mode: 'sandbox' | 'live'): HTMLScriptElement | null {
  const url = buildPayPalSdkUrl(clientId, mode);
  const existing = document.querySelector<HTMLScriptElement>(`script[src="${url}"]`);
  return existing;
}

function ensureScriptElement(clientId: string, mode: 'sandbox' | 'live'): HTMLScriptElement {
  const found = existingScript(clientId, mode);
  if (found) return found;
  const url = buildPayPalSdkUrl(clientId, mode);
  const script = document.createElement('script');
  script.id = PAYPAL_SCRIPT_ID;
  script.dataset.paypalSdk = '1';
  script.src = url;
  script.async = true;
  document.head.appendChild(script);
  return script;
}

export function loadPayPalSdk(clientId: string, mode: 'sandbox' | 'live'): Promise<PayPalNamespace> {
  const url = buildPayPalSdkUrl(clientId, mode);
  const cached = scriptPromises.get(url);
  if (cached) return cached;

  const promise = new Promise<PayPalNamespace>((resolve, reject) => {
    if (typeof window === 'undefined') {
      reject(new Error('PayPal SDK can only be loaded in a browser context'));
      return;
    }
    const paypalWindow = window as PayPalWindow;
    if (paypalWindow.paypal) {
      resolve(paypalWindow.paypal);
      return;
    }

    const script = ensureScriptElement(clientId, mode);
    const onLoad = () => {
      const ready = (window as PayPalWindow).paypal;
      if (ready) resolve(ready);
      else reject(new Error('PayPal SDK loaded without exposing the namespace'));
    };
    const onError = () => reject(new Error('PayPal SDK failed to load'));
    script.addEventListener('load', onLoad, { once: true });
    script.addEventListener('error', onError, { once: true });
  });

  scriptPromises.set(url, promise);
  return promise;
}

export function __resetPayPalSdkCacheForTests(): void {
  scriptPromises.clear();
}