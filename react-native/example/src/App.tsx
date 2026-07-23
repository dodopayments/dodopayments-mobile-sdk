import React, { useEffect, useState } from 'react';
import { Button, Linking, SafeAreaView, Text, View } from 'react-native';
import { DodoCheckout, type CheckoutResult } from '@dodopayments/react-native-checkout';

// Replace with a checkout_url minted by your backend (POST /checkouts).
const CHECKOUT_URL = 'https://test.checkout.dodopayments.com/session/cks_REPLACE_ME';
const RETURN_URL = 'myapp://checkout/return';

export default function App() {
  const [result, setResult] = useState<CheckoutResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Required: SFSafariViewController (iOS) has no in-process way to catch
  // its own return URL, so the OS routes it back via Linking instead.
  useEffect(() => {
    const subscription = Linking.addEventListener('url', ({ url }) => {
      DodoCheckout.handleOpenURL(url);
    });
    return () => subscription.remove();
  }, []);

  const pay = async () => {
    setError(null);
    try {
      const r = await DodoCheckout.start({
        checkoutUrl: CHECKOUT_URL,
        returnUrl: RETURN_URL,
        onEvent: (e) => console.log('event', e.type),
      });
      setResult(r);
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <SafeAreaView>
      <View style={{ padding: 24, gap: 16 }}>
        <Button title="Pay with Dodo" onPress={pay} />
        {result && <Text>Status: {result.status} {result.paymentId ?? ''}</Text>}
        {error && <Text>Error: {error}</Text>}
      </View>
    </SafeAreaView>
  );
}
