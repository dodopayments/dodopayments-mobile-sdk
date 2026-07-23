require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "DodoCheckoutReactNative"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = "Dodo Payments"
  # Matches ../swift/Package.swift's iOS minimum — the vendored core (ios/DodoCore/) requires it.
  s.platforms    = { :ios => "16.0" }
  s.source       = { :git => "https://github.com/dodopayments/dodopayments-checkout-react-native.git", :tag => "#{s.version}" }

  # The bridge (ios/) plus a vendored copy of the Swift core (ios/DodoCore/).
  # The core is copied in at release time from the tagged iOS SDK because RN
  # ships iOS native code via CocoaPods, which stops accepting updates Dec 2026.
  # For local development run `scripts/sync-ios-core.sh` to copy ../swift into it.
  s.source_files = "ios/**/*.{h,m,mm,swift}"

  install_modules_dependencies(s)
end
