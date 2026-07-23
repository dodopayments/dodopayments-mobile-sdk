import NativeDodoCheckout from './NativeDodoCheckout';
import type { NativeCheckoutEvent } from './NativeDodoCheckout';

export interface CheckoutEventSubscription {
  remove(): void;
}

/** Subscribe to native lifecycle events for the duration of one checkout. */
export function subscribeToCheckoutEvents(
  listener: (event: NativeCheckoutEvent) => void
): CheckoutEventSubscription {
  return NativeDodoCheckout.onCheckoutEvent(listener);
}
