// The native TurboModule is mocked so this suite exercises only the JS surface,
// per plan §12.4 ("Jest for the JS surface (mock native)").

const startMock = jest.fn();
const getAbandonedMock = jest.fn();
const clearAbandonedMock = jest.fn();
let eventListener: ((event: { type: string }) => void) | null =
  null;
const removeMock = jest.fn();

jest.mock('../src/NativeDodoCheckout', () => ({
  __esModule: true,
  default: {
    start: (...args: unknown[]) => startMock(...args),
    getAbandonedSession: () => getAbandonedMock(),
    clearAbandonedSession: () => clearAbandonedMock(),
    onCheckoutEvent: jest.fn(),
  },
}));

jest.mock('../src/events', () => ({
  __esModule: true,
  subscribeToCheckoutEvents: (
    listener: (e: { type: string; host?: string }) => void
  ) => {
    eventListener = listener;
    return { remove: removeMock };
  },
}));

import { DodoCheckout, CheckoutError } from '../src/index';

beforeEach(() => {
  jest.clearAllMocks();
  eventListener = null;
});

describe('DodoCheckout.start', () => {
  it('maps a succeeded native result to a typed result', async () => {
    startMock.mockResolvedValue({
      status: 'succeeded',
      paymentId: 'pay_1',
      raw: { payment_id: 'pay_1', status: 'succeeded' },
    });

    const result = await DodoCheckout.start({
      checkoutUrl: 'https://test.checkout.dodopayments.com/session/cks_1',
      returnUrl: 'myapp://checkout/return',
    });

    expect(result.status).toBe('succeeded');
    expect(result.paymentId).toBe('pay_1');
    expect(result.raw.payment_id).toBe('pay_1');
  });

  it('passes checkoutUrl and returnUrl through to native params', async () => {
    startMock.mockResolvedValue({ status: 'cancelled', raw: {} });

    await DodoCheckout.start({
      checkoutUrl: 'https://checkout.dodopayments.com/session/cks_1',
      returnUrl: 'myapp://checkout/return',
    });

    expect(startMock).toHaveBeenCalledWith(
      expect.objectContaining({
        checkoutUrl: 'https://checkout.dodopayments.com/session/cks_1',
        returnUrl: 'myapp://checkout/return',
      })
    );
  });

  it('maps a known native error code to a CheckoutError', async () => {
    startMock.mockRejectedValue({
      code: 'ALREADY_IN_PROGRESS',
      message: 'already open',
    });

    await expect(
      DodoCheckout.start({
        checkoutUrl: 'https://checkout.dodopayments.com/session/cks_1',
        returnUrl: 'myapp://checkout/return',
      })
    ).rejects.toMatchObject({ code: 'ALREADY_IN_PROGRESS' });
  });

  it('maps an unknown error to PLATFORM_ERROR', async () => {
    startMock.mockRejectedValue(new Error('boom'));

    await expect(
      DodoCheckout.start({
        checkoutUrl: 'https://checkout.dodopayments.com/session/cks_1',
        returnUrl: 'myapp://checkout/return',
      })
    ).rejects.toBeInstanceOf(CheckoutError);
  });

  it('forwards lifecycle events to onEvent and unsubscribes after', async () => {
    startMock.mockImplementation(async () => {
      eventListener?.({ type: 'checkout.opened' });
      eventListener?.({ type: 'checkout.return_received' });
      return { status: 'succeeded', paymentId: 'pay_1', raw: {} };
    });

    const events: string[] = [];
    await DodoCheckout.start({
      checkoutUrl: 'https://checkout.dodopayments.com/session/cks_1',
      returnUrl: 'myapp://checkout/return',
      onEvent: (e) => events.push(e.type),
    });

    expect(events).toEqual(['checkout.opened', 'checkout.return_received']);
    expect(removeMock).toHaveBeenCalledTimes(1);
  });
});

describe('DodoCheckout.getAbandonedSession', () => {
  it('returns null when there is no abandoned session', async () => {
    getAbandonedMock.mockResolvedValue(null);
    expect(await DodoCheckout.getAbandonedSession()).toBeNull();
  });

  it('converts epoch seconds to a Date', async () => {
    getAbandonedMock.mockResolvedValue({
      sessionId: 'cks_1',
      createdAt: 1_700_000_000,
    });
    const session = await DodoCheckout.getAbandonedSession();
    expect(session?.sessionId).toBe('cks_1');
    expect(session?.createdAt).toEqual(new Date(1_700_000_000 * 1000));
  });
});
