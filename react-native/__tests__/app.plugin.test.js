const {
  resolveScheme,
  applyAndroid,
  applyIos,
  withDodoCheckout,
} = require('../app.plugin.js');

describe('resolveScheme', () => {
  it('no-ops when scheme is omitted', () => {
    expect(resolveScheme(undefined)).toBeNull();
    expect(resolveScheme(null)).toBeNull();
    expect(resolveScheme({})).toBeNull();
  });

  it('accepts bare and custom-scheme URL forms', () => {
    expect(resolveScheme({ scheme: 'myapp.checkout' })).toBe('myapp.checkout');
    expect(resolveScheme({ scheme: 'MyApp.Checkout://return' })).toBe(
      'myapp.checkout'
    );
  });

  it('rejects http(s) and other system schemes', () => {
    expect(() => resolveScheme({ scheme: 'https://example.com/r' })).toThrow(
      /not allowed|https/
    );
    expect(() => resolveScheme({ scheme: 'http://x' })).toThrow(/not allowed/);
    expect(() => resolveScheme({ scheme: 'mailto' })).toThrow(/not allowed/);
  });

  it('throws on present-but-invalid schemes', () => {
    expect(() => resolveScheme({ scheme: 'my app' })).toThrow(/invalid scheme/);
    expect(() => resolveScheme({ scheme: '' })).toThrow(/non-empty/);
    expect(() => resolveScheme({ scheme: 123 })).toThrow(/non-empty string/);
  });
});

describe('applyAndroid', () => {
  const base = `
android {
    defaultConfig {
        applicationId 'com.example.app'
        versionCode 1
    }
}
`;

  it('appends assignment at end of defaultConfig', () => {
    const out = applyAndroid(base, 'dostt');
    expect(out).toMatch(
      /versionCode 1\s*\n\s*manifestPlaceholders\["dodoCallbackScheme"\] = "dostt"\s*\n\s*\}/
    );
  });

  it('updates existing bracket assignment', () => {
    const src = base.replace(
      'versionCode 1',
      'manifestPlaceholders["dodoCallbackScheme"] = "old"\n        versionCode 1'
    );
    const out = applyAndroid(src, 'dostt');
    expect(out).toContain('= "dostt"');
    expect(out).not.toContain('"old"');
  });

  it('updates existing map key', () => {
    const src = base.replace(
      'versionCode 1',
      'manifestPlaceholders = [dodoCallbackScheme: "old"]\n        versionCode 1'
    );
    expect(applyAndroid(src, 'dostt')).toContain('dodoCallbackScheme: "dostt"');
  });

  it('merges into an existing unrelated whole-map assignment (no wipe)', () => {
    const src = `
android {
    defaultConfig {
        applicationId 'com.example.app'
        manifestPlaceholders = [appAuthRedirectScheme: 'com.example.app']
        versionCode 1
    }
}
`;
    const out = applyAndroid(src, 'myapp');
    expect(out).toMatch(
      /manifestPlaceholders\s*=\s*\[\s*\n\s*dodoCallbackScheme: "myapp",/
    );
    expect(out).toContain("appAuthRedirectScheme: 'com.example.app'");
    // Must not leave a pre-map bracket line that would be wiped by the map.
    expect(out).not.toMatch(
      /manifestPlaceholders\["dodoCallbackScheme"\][\s\S]*manifestPlaceholders\s*=/
    );
  });
});

describe('applyIos', () => {
  it('adds and is idempotent', () => {
    const once = applyIos({}, 'dostt');
    expect(once.CFBundleURLTypes[0].CFBundleURLSchemes).toEqual(['dostt']);
    expect(applyIos(once, 'dostt').CFBundleURLTypes).toHaveLength(1);
  });
});

describe('withDodoCheckout', () => {
  it('no-ops without scheme (including null props)', () => {
    const config = { name: 't', scheme: 'hostapp' };
    expect(withDodoCheckout(config, undefined)).toBe(config);
    expect(withDodoCheckout(config, null)).toBe(config);
    expect(withDodoCheckout(config, {})).toBe(config);
  });

  it('throws when scheme is invalid rather than silent no-op', () => {
    expect(() =>
      withDodoCheckout({ name: 't' }, { scheme: 'https://x.com' })
    ).toThrow(/not allowed/);
  });
});
