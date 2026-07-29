// Stand-in for the host app's Expo install, so the mod-registration path is
// covered without taking a dependency on @expo/config-plugins.
jest.mock(
  'expo/config-plugins',
  () => ({
    withAppBuildGradle: (config, action) => {
      const next = action({
        ...config,
        modResults: { language: 'groovy', contents: config.__gradle },
      });
      return { ...config, __gradle: next.modResults.contents };
    },
    withInfoPlist: (config, action) => {
      const next = action({ ...config, modResults: config.__plist || {} });
      return { ...config, __plist: next.modResults };
    },
  }),
  { virtual: true }
);

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

  it('tolerates a trailing colon', () => {
    expect(resolveScheme({ scheme: 'myapp.checkout:' })).toBe('myapp.checkout');
  });

  it('throws on present-but-invalid schemes', () => {
    expect(() => resolveScheme({ scheme: 'my app' })).toThrow(/invalid scheme/);
    expect(() => resolveScheme({ scheme: 'my_app' })).toThrow(/invalid scheme/);
    expect(() => resolveScheme({ scheme: '1app' })).toThrow(/invalid scheme/);
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

  it('appends at end of defaultConfig, preserving indentation', () => {
    expect(applyAndroid(base, 'dostt')).toBe(`
android {
    defaultConfig {
        applicationId 'com.example.app'
        versionCode 1
        manifestPlaceholders["dodoCallbackScheme"] = "dostt"
    }
}
`);
  });

  it('is idempotent across repeated prebuilds, and retargets on change', () => {
    const once = applyAndroid(base, 'dostt');
    expect(applyAndroid(once, 'dostt')).toBe(once);
    expect(applyAndroid(once, 'other')).toBe(applyAndroid(base, 'other'));
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
    expect(out).toContain(
      `manifestPlaceholders = [dodoCallbackScheme: "myapp", appAuthRedirectScheme: 'com.example.app']`
    );
    // Must not leave a pre-map bracket line that would be wiped by the map.
    expect(out).not.toMatch(
      /manifestPlaceholders\["dodoCallbackScheme"\][\s\S]*manifestPlaceholders\s*=/
    );
  });

  it('merges into a multi-line map without trailing whitespace', () => {
    const src = `
android {
    defaultConfig {
        manifestPlaceholders = [
            appAuthRedirectScheme: 'com.example.app'
        ]
    }
}
`;
    expect(applyAndroid(src, 'myapp')).toContain(
      'manifestPlaceholders = [dodoCallbackScheme: "myapp",\n'
    );
  });

  it('rewrites the empty Groovy map literal instead of emitting `,:]`', () => {
    const src = base.replace(
      'versionCode 1',
      'manifestPlaceholders = [:]\n        versionCode 1'
    );
    const out = applyAndroid(src, 'myapp');
    expect(out).toContain('manifestPlaceholders = [dodoCallbackScheme: "myapp"]');
    expect(out).not.toContain(':]');
  });

  it('ignores a commented-out manifestPlaceholders map', () => {
    const src = base.replace(
      'versionCode 1',
      "// manifestPlaceholders = [appAuthRedirectScheme: 'com.example.app']\n        versionCode 1"
    );
    const out = applyAndroid(src, 'myapp');
    // The comment must survive untouched — splicing into it emits invalid Gradle.
    expect(out).toContain(
      "// manifestPlaceholders = [appAuthRedirectScheme: 'com.example.app']"
    );
    expect(out).toContain('manifestPlaceholders["dodoCallbackScheme"] = "myapp"');
  });

  it('ignores a commented-out dodoCallbackScheme key', () => {
    const src = base.replace(
      'versionCode 1',
      '/* manifestPlaceholders["dodoCallbackScheme"] = "old" */\n        versionCode 1'
    );
    const out = applyAndroid(src, 'myapp');
    expect(out).toContain('"old"');
    expect(out).toContain('manifestPlaceholders["dodoCallbackScheme"] = "myapp"');
  });

  it('does not hijack a map belonging to buildTypes or productFlavors', () => {
    const src = `
android {
    defaultConfig {
        versionCode 1
    }
    buildTypes {
        release {
            manifestPlaceholders = [appAuthRedirectScheme: 'com.example.app']
        }
    }
}
`;
    const out = applyAndroid(src, 'myapp');
    // A per-variant map would leave every other variant unresolved at merge time.
    expect(out).toContain(
      "manifestPlaceholders = [appAuthRedirectScheme: 'com.example.app']"
    );
    expect(out).toMatch(
      /defaultConfig \{\n        versionCode 1\n        manifestPlaceholders\["dodoCallbackScheme"\] = "myapp"\n    \}/
    );
  });

  it('counts braces outside string literals only', () => {
    const src = `
android {
    defaultConfig {
        resValue "string", "custom_scheme", "com.example{"
        versionCode 1
    }
}
dependencies {
    implementation "foo:bar:1.0"
}
`;
    const out = applyAndroid(src, 'myapp');
    expect(out).toMatch(
      /versionCode 1\n        manifestPlaceholders\["dodoCallbackScheme"\] = "myapp"\n    \}\n\}/
    );
  });

  it('throws when there is no defaultConfig block', () => {
    expect(() => applyAndroid('android {\n}\n', 'myapp')).toThrow(
      /no defaultConfig/
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

  const gradle = `
android {
    defaultConfig {
        versionCode 1
    }
}
`;

  it('registers both mods and writes gradle + plist', () => {
    const out = withDodoCheckout(
      { name: 't', scheme: 'myapp', __gradle: gradle },
      { scheme: 'myapp.checkout' }
    );
    expect(out.__gradle).toContain(
      'manifestPlaceholders["dodoCallbackScheme"] = "myapp.checkout"'
    );
    expect(out.__plist.CFBundleURLTypes).toEqual([
      {
        CFBundleURLName: 'dodopayments.checkout.myapp.checkout',
        CFBundleURLSchemes: ['myapp.checkout'],
      },
    ]);
  });

  it('warns only when the callback scheme collides with the app scheme', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});

    withDodoCheckout(
      { scheme: 'myapp', __gradle: gradle },
      { scheme: 'myapp.checkout' }
    );
    expect(warn).not.toHaveBeenCalled();

    withDodoCheckout({ scheme: 'MyApp', __gradle: gradle }, { scheme: 'myapp' });
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('matches expo.scheme'));

    warn.mockRestore();
  });
});
