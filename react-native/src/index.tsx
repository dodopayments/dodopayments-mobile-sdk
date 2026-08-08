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

/**
 * The outcome of a checkout, read off the `return_url` query string.
 *
 * `cancelled` is the odd one out: it means the user dismissed the browser
 * before any return URL arrived, so the SDK never learned the outcome. **It is
 * not a decline** — the user may well have paid and closed the sheet while the
 * hosted "Payment Successful" page counted down its redirect. Don't show a
 * failure screen for it; call `getAbandonedSession()` and reconcile the
 * session server-side.
 *
 * `pending` is the other non-answer: besides genuinely async methods
 * (`status=processing`, any `requires_*`), it is the fallback the native cores
 * use for a missing or unrecognized `status`, so a malformed return URL lands
 * here too — possibly with no `paymentId` either. Reconcile it the same way.
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
  | { type: 'checkout.return_received' }
  | { type: 'checkout.closed' };

/**
 * Customizes the checkout browser's chrome. `android`/`ios` carry options
 * that only exist on that one platform — native only ever reads its own
 * bag. Colors are hex strings, `"#RRGGBB"` or `"#AARRGGBB"`.
 *
 * No shared/top-level fields: `toolbarColor` was originally shared, but
 * iOS's equivalent (`preferredBarTintColor`) is deprecated as of iOS 26 with
 * no replacement and confirmed to have no visible effect there — not worth
 * a color knob that's already inert on the majority of iOS devices. Same
 * reasoning killed iOS's `controlTintColor` outright (it rested on the
 * identically-deprecated `preferredControlTintColor`).
 */
export interface BrowserCustomization {
  android?: {
    toolbarColor?: string;
    /** `'default'` is the system "X" icon; `'back'` is a back-arrow the SDK draws itself. */
    closeButtonStyle?: 'default' | 'back';
    closeButtonPosition?: 'start' | 'end';
    /** Hides the toolbar's share icon when `false`. This is the option that answers "can we hide the share/overflow chrome" — on Android only; iOS has no API for either. */
    shareButtonEnabled?: boolean;
    showTitleEnabled?: boolean;
    urlBarHidingEnabled?: boolean;
    bookmarksButtonEnabled?: boolean;
    downloadsButtonEnabled?: boolean;
    secondaryToolbarColor?: string;
    navigationBarColor?: string;
    navigationBarDividerColor?: string;
    /** Forces the Custom Tab's light/dark appearance regardless of the system setting. */
    colorScheme?: 'system' | 'light' | 'dark';
  };
  ios?: {
    dismissButtonStyle?: 'done' | 'close' | 'cancel';
    /** Only has a visible effect when presentationStyle is 'fullScreen' — 'pageSheet' keeps the bars pinned regardless. */
    barCollapsingEnabled?: boolean;
    /** 'pageSheet' (default) is a card that leaves the app visible behind it and supports swipe-to-dismiss; 'fullScreen' covers the whole screen. */
    presentationStyle?: 'pageSheet' | 'fullScreen';
    /** Forces the sheet's light/dark appearance regardless of the system setting. */
    colorScheme?: 'system' | 'light' | 'dark';
  };
}

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
  /** Appearance customization for the checkout browser. */
  customization?: BrowserCustomization;
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
    const { checkoutUrl, returnUrl, customization, onEvent } = params;

    const subscription = onEvent
      ? subscribeToCheckoutEvents((event) => {
          switch (event.type) {
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
        customizationJson: customization
          ? JSON.stringify(customization)
          : undefined,
      });
      return mapResult(native);
    } catch (error) {
      throw toCheckoutError(error);
    } finally {
      subscription?.remove();
    }
  },

  /**
   * The session of a checkout that ended without a confirmed outcome, or null.
   *
   * Set whenever the SDK never saw a return URL it could resolve to a durable
   * outcome — the app was killed mid-flow, `start` resolved `cancelled`
   * because the user dismissed the browser, or it resolved `pending`, which is
   * also the fallback for an unparseable return URL. Check it on launch *and*
   * after every `cancelled` or `pending` result, reconcile the session
   * server-side, then call `clearAbandonedSession()` once the outcome is
   * terminal.
   */
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
