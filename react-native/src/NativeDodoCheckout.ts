import type { TurboModule } from 'react-native';
import type {
  UnsafeObject,
  EventEmitter,
} from 'react-native/Libraries/Types/CodegenTypes';
import { TurboModuleRegistry } from 'react-native';

/**
 * Parameters passed across the bridge. Flat (not nested) because TurboModule
 * codegen models fixed-shape objects best; the JS surface in `index.tsx`
 * exposes the nested `options` shape and flattens into this.
 */
export type NativeCheckoutParams = {
  checkoutUrl: string;
  returnUrl: string;
};

export type NativeCheckoutResult = {
  status: string;
  paymentId?: string;
  subscriptionId?: string;
  licenseKeys?: string[];
  customerEmail?: string;
  raw: UnsafeObject; // { [key: string]: string }
};

export type NativeCheckoutEvent = {
  type: string; // "checkout.opened" | "checkout.return_received" | "checkout.closed"
};

export type NativeAbandonedSession = {
  sessionId: string;
  createdAt: number; // epoch seconds
};

export interface Spec extends TurboModule {
  start(params: NativeCheckoutParams): Promise<NativeCheckoutResult>;
  getAbandonedSession(): Promise<NativeAbandonedSession | null>;
  clearAbandonedSession(): Promise<void>;

  // iOS only (Android's Custom Tab surface is fully self-contained via
  // Activities; this is a no-op stub there). Required: SFSafariViewController
  // has no in-process way to catch its own return URL, so incoming URLs must
  // be forwarded here from the app's own Linking handling. Returns true if
  // the URL belonged to an in-flight checkout.
  handleOpenURL(url: string): Promise<boolean>;

  // Codegen EventEmitter — replaces deprecated addListener/removeListeners +
  // NativeEventEmitter. Emits via emitOnCheckoutEvent on native. Imported
  // from the CodegenTypes module directly (not the 'react-native' package's
  // CodegenTypes namespace) since codegen on RN <0.80 can't resolve
  // namespaced generic type references.
  readonly onCheckoutEvent: EventEmitter<NativeCheckoutEvent>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('DodoCheckout');
