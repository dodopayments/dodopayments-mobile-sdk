#
# Podspec for the iOS side of the dodopayments_checkout Flutter plugin.
#
# The iOS core (DodoCheckout, Swift) is BUNDLED AS SOURCE into this pod rather
# than pulled in as a CocoaPods dependency, because CocoaPods stops accepting
# updates in Dec 2026 (plan §6/§7). The core types therefore live in the same
# module as the plugin — the plugin code does not `import DodoCheckout`.
#
# The core is vendored into ios/DodoCheckoutCore/ by scripts/sync-ios-core.sh
# rather than globbed from ../../swift directly — CocoaPods does not reliably
# resolve source_files globs that escape the podspec's own directory through
# the plugin symlink Flutter installs under. Run that script (it defaults to
# copying the sibling ../../swift package) before `pod install` for local dev;
# the publish script runs it against the tagged iOS release for release
# builds so the published pub archive is self-contained.
#
Pod::Spec.new do |s|
  s.name             = 'dodopayments_checkout'
  s.version          = '0.1.0'
  s.summary          = "Dodo Payments hosted checkout in an in-app WebView."
  s.description      = <<-DESC
Thin Flutter bridge to the Dodo Payments iOS checkout core. Opens the hosted
checkout in a WKWebView, intercepts the return_url navigation, and returns a
typed result. Zero networking; holds no API key.
                       DESC
  s.homepage         = 'https://github.com/dodopayments/dodopayments-checkout-flutter'
  s.license          = { :type => 'MIT', :file => '../LICENSE' }
  s.author           = { 'Dodo Payments' => 'support@dodopayments.com' }
  s.source           = { :path => '.' }

  s.source_files     = 'Classes/**/*.swift', 'DodoCheckoutCore/**/*.swift'

  s.dependency 'Flutter'
  # Aligned with mobile-sdk/swift (Package.swift platforms iOS 16 / language mode 6).
  s.platform         = :ios, '16.0'
  s.swift_version    = '6.0'

  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    # Flutter.framework does not contain an i386 slice.
    'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386'
  }
end
