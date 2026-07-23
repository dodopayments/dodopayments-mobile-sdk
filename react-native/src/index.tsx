import NativeDodoCheckout from './NativeDodoCheckout';
import type { NativeCheckoutResult } from './NativeDodoCheckout';
import { subscribeToCheckoutEvents } from './events';

/**
 * `@dodopayments/react-native-checkout`
 *
 * Thin wrapper around the native iOS/Android checkout cores. It contains no
 * checkout logic — it passes the call through and returns the result. The
 * result is a UI hint, never proof of payment: grant access server-side from
 * the webhook.
 */

export type CheckoutStatus =
  | 'succeeded'
  | 'failed'
  | 'cancelled'
  | 'pending'
  | 'expired';

export interface CheckoutResult {
  status: CheckoutStatus;
  paymentId?: string;
  subscriptionId?: string;
  licenseKeys?: string[];
  customerEmail?: string;
  /** All query params from the return_url, verbatim. */
  raw: Record<string, string>;
}

export type CheckoutEvent =
  | { type: 'checkout.opened' }
  | { type: 'checkout.navigation'; host?: string }
  | { type: 'checkout.return_received' }
  | { type: 'checkout.closed' };

export interface CheckoutParams {
  checkoutUrl: string;
  /**
   * The URL the SDK watches for. Any absolute URL the checkout ends up
   * navigating to; matched on scheme+host+path. Its scheme must be
   * registered as a URL type in Info.plist (iOS) / an intent-filter on the
   * SDK's redirect activity (Android), so the OS routes the checkout return
   * back to this app (`SFSafariViewController` on iOS, a Custom Tab on
   * Android has no in-process way to catch its own return URL).
   *
   * iOS only: you must also forward incoming URLs from your app's own
   * `Linking` handling into `DodoCheckout.handleOpenURL(url)`.
   */
  returnUrl: string;
  /** Lifecycle callback for logging/analytics only — never decide outcome from events. */
  onEvent?: (event: CheckoutEvent) => void;
}

export interface AbandonedSession {
  sessionId: string;
  createdAt: Date;
}

export type CheckoutErrorCode =
  | 'INVALID_CHECKOUT_URL'
  | 'INVALID_RETURN_URL'
  | 'ALREADY_IN_PROGRESS'
  | 'PLATFORM_ERROR';

/** Thrown by `start` for misuse or platform failure. A cancel or a declined
 * payment is a *result* (`cancelled` / `failed`), never a thrown error. */
export class CheckoutError extends Error {
  readonly code: CheckoutErrorCode;
  constructor(code: CheckoutErrorCode, message?: string) {
    super(message ?? code);
    this.name = 'CheckoutError';
    this.code = code;
  }
}

const KNOWN_CODES: CheckoutErrorCode[] = [
  'INVALID_CHECKOUT_URL',
  'INVALID_RETURN_URL',
  'ALREADY_IN_PROGRESS',
  'PLATFORM_ERROR',
];

function toCheckoutError(error: unknown): CheckoutError {
  const code = (error as { code?: string } | undefined)?.code;
  const message = (error as { message?: string } | undefined)?.message;
  if (typeof code === 'string' && (KNOWN_CODES as string[]).includes(code)) {
    return new CheckoutError(code as CheckoutErrorCode, message);
  }
  return new CheckoutError('PLATFORM_ERROR', message ?? String(error));
}

function mapResult(native: NativeCheckoutResult): CheckoutResult {
  const rawObject = (native.raw ?? {}) as Record<string, unknown>;
  const raw: Record<string, string> = {};
  for (const key of Object.keys(rawObject)) {
    raw[key] = String(rawObject[key]);
  }
  return {
    status: native.status as CheckoutStatus,
    paymentId: native.paymentId,
    subscriptionId: native.subscriptionId,
    licenseKeys: native.licenseKeys,
    customerEmail: native.customerEmail,
    raw,
  };
}

export const DodoCheckout = {
  /**
   * Present Dodo's hosted checkout in a system browser tab
   * (`SFSafariViewController` on iOS, a Custom Tab on Android) and resolve
   * with the outcome. Throws `CheckoutError` for invalid input or platform
   * failure.
   */
  async start(params: CheckoutParams): Promise<CheckoutResult> {
    const { checkoutUrl, returnUrl, onEvent } = params;

    const subscription = onEvent
      ? subscribeToCheckoutEvents((event) => {
          switch (event.type) {
            case 'checkout.navigation':
              onEvent({ type: 'checkout.navigation', host: event.host });
              break;
            case 'checkout.opened':
            case 'checkout.return_received':
            case 'checkout.closed':
              onEvent({ type: event.type });
              break;
            default:
              break;
          }
        })
      : undefined;

    try {
      const native = await NativeDodoCheckout.start({
        checkoutUrl,
        returnUrl,
      });
      return mapResult(native);
    } catch (error) {
      throw toCheckoutError(error);
    } finally {
      subscription?.remove();
    }
  },

  /** The session of a checkout the app was killed mid-flow in, or null. */
  async getAbandonedSession(): Promise<AbandonedSession | null> {
    const native = await NativeDodoCheckout.getAbandonedSession();
    if (!native) return null;
    return {
      sessionId: native.sessionId,
      createdAt: new Date(native.createdAt * 1000),
    };
  },

  /** Clears the abandoned-session record after reconciliation. */
  async clearAbandonedSession(): Promise<void> {
    await NativeDodoCheckout.clearAbandonedSession();
  },

  /**
   * iOS only — forward incoming URLs here from your app's own `Linking`
   * handling (e.g. `Linking.addEventListener('url', ({ url }) => { ... })`).
   * Required for the checkout return; a no-op returning `false` on Android.
   * Returns `true` if the URL belonged to an in-flight checkout.
   */
  async handleOpenURL(url: string): Promise<boolean> {
    return NativeDodoCheckout.handleOpenURL(url);
  },
};

export default DodoCheckout;
