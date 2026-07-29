// Expo config plugin: ["@dodopayments/react-native-checkout", { "scheme": "myapp.checkout" }]
// Writes Android dodoCallbackScheme + iOS CFBundleURLTypes. No-op without { scheme }.

const BLOCKED_SCHEMES = new Set([
  'http',
  'https',
  'file',
  'content',
  'data',
  'about',
  'intent',
  'mailto',
  'tel',
  'sms',
  'geo',
  'javascript',
]);

/**
 * @param {unknown} props
 * @returns {string | null} normalized scheme, or null when scheme was omitted (no-op)
 */
function resolveScheme(props) {
  const opts = props == null ? {} : props;
  if (opts.scheme === undefined || opts.scheme === null) {
    return null;
  }
  if (typeof opts.scheme !== 'string' || !opts.scheme.trim()) {
    throw new Error(
      '[@dodopayments/react-native-checkout] plugin option "scheme" must be a non-empty string ' +
        '(e.g. "myapp.checkout"), not a full URL.'
    );
  }

  const trimmed = opts.scheme.trim();
  const raw = trimmed.includes('://')
    ? trimmed.slice(0, trimmed.indexOf('://'))
    : trimmed.split('/')[0];

  if (!raw || !/^[A-Za-z][A-Za-z0-9+.-]*$/.test(raw)) {
    throw new Error(
      `[@dodopayments/react-native-checkout] invalid scheme ${JSON.stringify(opts.scheme)}. ` +
        'Use a custom scheme token like "myapp.checkout" (no spaces).'
    );
  }

  const scheme = raw.toLowerCase();
  if (BLOCKED_SCHEMES.has(scheme)) {
    throw new Error(
      `[@dodopayments/react-native-checkout] scheme ${JSON.stringify(scheme)} is not allowed. ` +
        'Pass a custom app scheme (e.g. "myapp.checkout"), not http(s) or a system scheme. ' +
        'If you have a full return URL, use only its scheme part.'
    );
  }
  return scheme;
}

/** @deprecated use resolveScheme — kept for tests */
function normalizeScheme(raw) {
  try {
    if (raw === undefined || raw === null) return null;
    return resolveScheme({ scheme: raw });
  } catch {
    return null;
  }
}

/**
 * Insert `line` immediately before the closing `}` of the first `defaultConfig { ... }`.
 * @param {string} contents
 * @param {string} line
 */
function appendInDefaultConfig(contents, line) {
  const match = contents.match(/defaultConfig\s*\{/);
  if (!match || match.index === undefined) {
    throw new Error(
      '[@dodopayments/react-native-checkout] no defaultConfig { } in android/app/build.gradle'
    );
  }
  let depth = 1;
  let i = match.index + match[0].length;
  while (i < contents.length && depth > 0) {
    const ch = contents[i];
    if (ch === '{') depth += 1;
    else if (ch === '}') depth -= 1;
    i += 1;
  }
  if (depth !== 0) {
    throw new Error(
      '[@dodopayments/react-native-checkout] could not parse defaultConfig { } in android/app/build.gradle'
    );
  }
  const closeAt = i - 1;
  // Match typical Expo/RN indent inside defaultConfig (8 spaces).
  return contents.slice(0, closeAt) + `        ${line}\n` + contents.slice(closeAt);
}

function applyAndroid(contents, scheme) {
  const assignment = `manifestPlaceholders["dodoCallbackScheme"] = "${scheme}"`;

  // Bracket form already present — update value.
  const bracket =
    /manifestPlaceholders\s*\[\s*["']dodoCallbackScheme["']\s*\]\s*=\s*["'][^"']*["']/;
  if (bracket.test(contents)) {
    return contents.replace(bracket, assignment);
  }

  // Map key already present — update value in place.
  if (/dodoCallbackScheme\s*:\s*["'][^"']*["']/.test(contents)) {
    return contents.replace(
      /dodoCallbackScheme\s*:\s*["'][^"']*["']/,
      `dodoCallbackScheme: "${scheme}"`
    );
  }

  // Whole-map assignment without our key — merge into the map so a later
  // `manifestPlaceholders = [...]` does not wipe a line we inject first.
  if (/manifestPlaceholders\s*=\s*\[/.test(contents)) {
    return contents.replace(
      /manifestPlaceholders\s*=\s*\[/,
      (m) => `${m}\n            dodoCallbackScheme: "${scheme}",`
    );
  }

  // No existing placeholders — append at end of defaultConfig so later
  // whole-map assignments that prebuild/other plugins add earlier in the
  // block cannot overwrite us (and we win if we run after them).
  return appendInDefaultConfig(contents, assignment);
}

function applyIos(plist, scheme) {
  const types = Array.isArray(plist.CFBundleURLTypes)
    ? [...plist.CFBundleURLTypes]
    : [];
  if (
    types.some(
      (t) =>
        Array.isArray(t.CFBundleURLSchemes) &&
        t.CFBundleURLSchemes.includes(scheme)
    )
  ) {
    return { ...plist, CFBundleURLTypes: types };
  }
  types.push({
    CFBundleURLName: `dodopayments.checkout.${scheme}`,
    CFBundleURLSchemes: [scheme],
  });
  return { ...plist, CFBundleURLTypes: types };
}

function collectExpoSchemes(config) {
  const out = [];
  const push = (v) => {
    if (typeof v === 'string' && v.trim()) out.push(v.trim().toLowerCase());
    else if (Array.isArray(v)) v.forEach(push);
  };
  push(config.scheme);
  push(config.android && config.android.scheme);
  push(config.ios && config.ios.scheme);
  return out;
}

function loadConfigPlugins() {
  try {
    // Prefer the version Expo itself depends on (pnpm / Yarn PnP safe).
    return require('expo/config-plugins');
  } catch {
    try {
      return require('@expo/config-plugins');
    } catch {
      throw new Error(
        '[@dodopayments/react-native-checkout] needs expo (or @expo/config-plugins) ' +
          'to run the config plugin. Install expo and re-run prebuild.'
      );
    }
  }
}

function withDodoCheckout(config, props) {
  const scheme = resolveScheme(props);
  if (!scheme) return config;

  const expoSchemes = collectExpoSchemes(config);
  if (expoSchemes.includes(scheme)) {
    console.warn(
      `[@dodopayments/react-native-checkout] scheme "${scheme}" matches expo.scheme ` +
        '(or android/ios.scheme). On Android that registers the same scheme on MainActivity ' +
        'and BrowserRedirectActivity, which can send the checkout return to the wrong activity. ' +
        'Prefer a dedicated scheme such as "myapp.checkout" and returnUrl "myapp.checkout://return".'
    );
  }

  const plugins = loadConfigPlugins();

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
module.exports.withDodoCheckout = withDodoCheckout;
module.exports.resolveScheme = resolveScheme;
module.exports.normalizeScheme = normalizeScheme;
module.exports.applyAndroid = applyAndroid;
module.exports.applyIos = applyIos;
module.exports.appendInDefaultConfig = appendInDefaultConfig;
module.exports.collectExpoSchemes = collectExpoSchemes;
