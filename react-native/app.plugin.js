// Expo config plugin: ["@dodopayments/react-native-checkout", { "scheme": "myapp" }]
// Writes Android dodoCallbackScheme + iOS CFBundleURLTypes. No-op without { scheme }.

function normalizeScheme(raw) {
  if (typeof raw !== 'string' || !raw.trim()) return null;
  const s = raw.trim().includes('://')
    ? raw.trim().slice(0, raw.trim().indexOf('://'))
    : raw.trim().split('/')[0];
  return s && /^[A-Za-z][A-Za-z0-9+.-]*$/.test(s) ? s.toLowerCase() : null;
}

function applyAndroid(contents, scheme) {
  const line = `manifestPlaceholders["dodoCallbackScheme"] = "${scheme}"`;
  if (/manifestPlaceholders\s*\[\s*["']dodoCallbackScheme["']\s*\]\s*=/.test(contents)) {
    return contents.replace(
      /manifestPlaceholders\s*\[\s*["']dodoCallbackScheme["']\s*\]\s*=\s*["'][^"']*["']/,
      line
    );
  }
  if (/dodoCallbackScheme\s*:\s*["'][^"']*["']/.test(contents)) {
    return contents.replace(/dodoCallbackScheme\s*:\s*["'][^"']*["']/, `dodoCallbackScheme: "${scheme}"`);
  }
  if (!/defaultConfig\s*\{/.test(contents)) {
    throw new Error(
      '[@dodopayments/react-native-checkout] no defaultConfig { } in android/app/build.gradle'
    );
  }
  return contents.replace(/defaultConfig\s*\{/, (m) => `${m}\n        ${line}`);
}

function applyIos(plist, scheme) {
  const types = Array.isArray(plist.CFBundleURLTypes) ? [...plist.CFBundleURLTypes] : [];
  if (types.some((t) => Array.isArray(t.CFBundleURLSchemes) && t.CFBundleURLSchemes.includes(scheme))) {
    return { ...plist, CFBundleURLTypes: types };
  }
  types.push({
    CFBundleURLName: `dodopayments.checkout.${scheme}`,
    CFBundleURLSchemes: [scheme],
  });
  return { ...plist, CFBundleURLTypes: types };
}

function withDodoCheckout(config, props = {}) {
  const scheme = normalizeScheme(props.scheme);
  if (!scheme) return config;

  let plugins;
  try {
    plugins = require('@expo/config-plugins');
  } catch {
    throw new Error(
      '[@dodopayments/react-native-checkout] needs @expo/config-plugins (comes with expo)'
    );
  }

  config = plugins.withAppBuildGradle(config, (c) => {
    c.modResults.contents = applyAndroid(c.modResults.contents, scheme);
    return c;
  });
  config = plugins.withInfoPlist(config, (c) => {
    c.modResults = applyIos(c.modResults, scheme);
    return c;
  });
  return config;
}

module.exports = withDodoCheckout;
module.exports.normalizeScheme = normalizeScheme;
module.exports.applyAndroid = applyAndroid;
module.exports.applyIos = applyIos;
