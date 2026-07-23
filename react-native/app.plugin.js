// Expo config plugin — a no-op passthrough.
//
// Works with Expo development builds (not Expo Go, which can't load custom
// native modules). Unlike the old deep-link design there is NO Info.plist URL
// scheme and NO Android manifest placeholder to write — the return is caught
// inside the WebView — and autolinking already links the native module, so this
// plugin has nothing to configure. Kept as a stable entry point for any future
// native config. Deliberately dependency-free so it resolves when the package is
// consumed via a local `file:` link.
module.exports = (config) => config;
