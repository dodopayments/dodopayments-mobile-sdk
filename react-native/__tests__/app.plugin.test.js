const { normalizeScheme, applyAndroid, applyIos } = require('../app.plugin.js');

describe('normalizeScheme', () => {
  it('normalizes bare and URL forms', () => {
    expect(normalizeScheme('myapp')).toBe('myapp');
    expect(normalizeScheme('MyApp://checkout/return')).toBe('myapp');
    expect(normalizeScheme('')).toBeNull();
    expect(normalizeScheme(null)).toBeNull();
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

  it('injects and updates', () => {
    const injected = applyAndroid(base, 'dostt');
    expect(injected).toContain('manifestPlaceholders["dodoCallbackScheme"] = "dostt"');

    const updated = applyAndroid(
      base.replace('versionCode 1', 'manifestPlaceholders["dodoCallbackScheme"] = "old"\n        versionCode 1'),
      'dostt'
    );
    expect(updated).toContain('= "dostt"');
    expect(updated).not.toContain('"old"');

    const map = applyAndroid(
      base.replace('versionCode 1', 'manifestPlaceholders = [dodoCallbackScheme: "old"]\n        versionCode 1'),
      'dostt'
    );
    expect(map).toContain('dodoCallbackScheme: "dostt"');
  });
});

describe('applyIos', () => {
  it('adds and is idempotent', () => {
    const once = applyIos({}, 'dostt');
    expect(once.CFBundleURLTypes[0].CFBundleURLSchemes).toEqual(['dostt']);
    const twice = applyIos(once, 'dostt');
    expect(twice.CFBundleURLTypes).toHaveLength(1);
  });
});
