// Expo config plugin: ["@dodopayments/react-native-checkout", { "scheme": "myappcheckout" }]
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
        '(e.g. "myappcheckout"), not a full URL.'
    );
  }

  const trimmed = opts.scheme.trim();
  const raw = (
    trimmed.includes('://')
      ? trimmed.slice(0, trimmed.indexOf('://'))
      : trimmed.split('/')[0]
  ).replace(/:$/, ''); // tolerate the "myapp:" form

  if (!raw || !/^[A-Za-z][A-Za-z0-9+.-]*$/.test(raw)) {
    throw new Error(
      `[@dodopayments/react-native-checkout] invalid scheme ${JSON.stringify(opts.scheme)}. ` +
        'Use a custom scheme token like "myappcheckout" (no spaces).'
    );
  }

  const scheme = raw.toLowerCase();
  if (BLOCKED_SCHEMES.has(scheme)) {
    throw new Error(
      `[@dodopayments/react-native-checkout] scheme ${JSON.stringify(scheme)} is not allowed. ` +
        'Pass a custom app scheme (e.g. "myappcheckout"), not http(s) or a system scheme. ' +
        'If you have a full return URL, use only its scheme part.'
    );
  }
  return scheme;
}

/**
 * Blank out Groovy comments — and, when `blankStrings`, string literals too —
 * preserving length and line breaks so every index maps 1:1 back onto
 * `contents`. String tracking is always on (a URL like "https://x" must not
 * be read as starting a `//` comment); `blankStrings` only decides whether the
 * literal survives in the mask.
 *
 * Two views are needed: braces are counted with strings blanked (so a `{`
 * inside a literal can't skew the depth), while `manifestPlaceholders["..."]`
 * is matched with strings intact but comments gone (so a commented-out
 * placeholder is never spliced into, which would emit invalid Gradle).
 * @param {string} contents
 * @param {boolean} blankStrings
 * @returns {string}
 */
function maskNonCode(contents, blankStrings = true) {
  const out = contents.split('');
  const blank = (from, to) => {
    for (let j = from; j < to && j < out.length; j += 1) {
      if (out[j] !== '\n') out[j] = ' ';
    }
  };
  let i = 0;
  while (i < contents.length) {
    const two = contents.slice(i, i + 2);
    if (two === '//') {
      const nl = contents.indexOf('\n', i);
      const end = nl === -1 ? contents.length : nl;
      blank(i, end);
      i = end;
      continue;
    }
    if (two === '/*') {
      const close = contents.indexOf('*/', i + 2);
      const end = close === -1 ? contents.length : close + 2;
      blank(i, end);
      i = end;
      continue;
    }
    const ch = contents[i];
    if (ch === '"' || ch === "'") {
      const triple = contents.slice(i, i + 3);
      const quote = triple === ch + ch + ch ? triple : ch;
      let j = i + quote.length;
      while (j < contents.length) {
        if (contents[j] === '\\') {
          j += 2;
          continue;
        }
        if (contents.slice(j, j + quote.length) === quote) {
          j += quote.length;
          break;
        }
        j += 1;
      }
      const end = Math.min(j, contents.length);
      if (blankStrings) blank(i, end);
      i = end;
      continue;
    }
    i += 1;
  }
  return out.join('');
}

/**
 * Bounds of the first real `defaultConfig { ... }` block: `open` is the index
 * just after `{`, `close` the index of the matching `}`. `code` is the
 * comments-only mask, for matching patterns that contain string literals.
 * @param {string} contents
 * @returns {{ open: number, close: number, code: string }}
 */
function findDefaultConfig(contents) {
  const structure = maskNonCode(contents, true);
  const code = maskNonCode(contents, false);
  const match = structure.match(/\bdefaultConfig\s*\{/);
  if (!match || match.index === undefined) {
    throw new Error(
      '[@dodopayments/react-native-checkout] no defaultConfig { } in android/app/build.gradle'
    );
  }
  const open = match.index + match[0].length;
  let depth = 1;
  let i = open;
  while (i < structure.length && depth > 0) {
    const ch = structure[i];
    if (ch === '{') depth += 1;
    else if (ch === '}') depth -= 1;
    i += 1;
  }
  if (depth !== 0) {
    throw new Error(
      '[@dodopayments/react-native-checkout] could not parse defaultConfig { } in android/app/build.gradle'
    );
  }
  return { open, close: i - 1, code };
}

/**
 * Insert `line` on its own line just before the closing `}` of `defaultConfig`,
 * matching the indentation already used inside the block.
 * @param {string} contents
 * @param {string} line
 */
function appendInDefaultConfig(contents, line) {
  const { close } = findDefaultConfig(contents);
  const lineStart = contents.lastIndexOf('\n', close - 1) + 1;
  const beforeBrace = contents.slice(lineStart, close);

  // Closing brace on its own line: reuse its indent + one level for our line
  // and leave the brace exactly where it was.
  if (/^[ \t]*$/.test(beforeBrace)) {
    const indent = beforeBrace + (beforeBrace.includes('\t') ? '\t' : '    ');
    return (
      contents.slice(0, lineStart) +
      `${indent}${line}\n` +
      contents.slice(lineStart)
    );
  }
  // Single-line block (`defaultConfig { ... }`): break before the brace.
  return contents.slice(0, close) + `\n    ${line}\n` + contents.slice(close);
}

/**
 * Replace the first regex match that lies inside [open, close) of the masked
 * `code`, applying the replacement to the real `contents`.
 * @returns {string | null} updated contents, or null when there is no match
 */
function replaceInRange(contents, code, open, close, regex, replacement) {
  const scoped = code.slice(open, close);
  const found = scoped.match(regex);
  if (!found || found.index === undefined) return null;
  const at = open + found.index;
  const end = at + found[0].length;
  const head = contents.slice(0, at);
  const matched = contents.slice(at, end);
  const tail = contents.slice(end);
  return head + replacement(matched, tail) + tail;
}

function applyAndroid(contents, scheme) {
  const assignment = `manifestPlaceholders["dodoCallbackScheme"] = "${scheme}"`;
  // Everything is scoped to defaultConfig: a map in buildTypes/productFlavors
  // only applies to that one variant, so merging into it would leave every
  // other variant without the placeholder and fail manifest merging.
  const { open, close, code } = findDefaultConfig(contents);

  // Bracket form already present — update value.
  const bracket = replaceInRange(
    contents,
    code,
    open,
    close,
    /manifestPlaceholders\s*\[\s*["']dodoCallbackScheme["']\s*\]\s*=\s*["'][^"']*["']/,
    () => assignment
  );
  if (bracket !== null) return bracket;

  // Map key already present — update value in place.
  const mapKey = replaceInRange(
    contents,
    code,
    open,
    close,
    /dodoCallbackScheme\s*:\s*["'][^"']*["']/,
    () => `dodoCallbackScheme: "${scheme}"`
  );
  if (mapKey !== null) return mapKey;

  // `[:]` is Groovy's empty map literal — it must lose the `:` once it has a
  // key, otherwise the merged result is a syntax error.
  const emptyMap = replaceInRange(
    contents,
    code,
    open,
    close,
    /manifestPlaceholders(\s*=\s*)\[\s*:\s*\]/,
    (m) => m.replace(/\[[\s\S]*\]/, `[dodoCallbackScheme: "${scheme}"]`)
  );
  if (emptyMap !== null) return emptyMap;

  // Whole-map assignment without our key — merge into the map so a later
  // `manifestPlaceholders = [...]` does not wipe a line we inject first.
  const map = replaceInRange(
    contents,
    code,
    open,
    close,
    /manifestPlaceholders\s*=\s*\[/,
    (m, tail) => {
      const sep = /^\s*\]/.test(tail) ? '' : /^\s/.test(tail) ? ',' : ', ';
      return `${m}dodoCallbackScheme: "${scheme}"${sep}`;
    }
  );
  if (map !== null) return map;

  // No existing placeholders — append at the end of defaultConfig so a
  // whole-map assignment added earlier in the block cannot overwrite us.
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
        'Prefer a dedicated scheme such as "myappcheckout" and returnUrl "myappcheckout://return".'
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
module.exports.applyAndroid = applyAndroid;
module.exports.applyIos = applyIos;
module.exports.appendInDefaultConfig = appendInDefaultConfig;
module.exports.findDefaultConfig = findDefaultConfig;
module.exports.maskNonCode = maskNonCode;
module.exports.collectExpoSchemes = collectExpoSchemes;
