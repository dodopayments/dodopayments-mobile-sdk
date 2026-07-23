#import <React/RCTBridgeModule.h>

#ifdef RCT_NEW_ARCH_ENABLED
#import <DodoCheckoutSpec/DodoCheckoutSpec.h>
#endif

// Bridges the codegen-generated TurboModule spec to the Swift implementation
// (`DodoCheckoutReactNativeImpl`). Emits lifecycle events via the codegen
// EventEmitter (`emitOnCheckoutEvent`), not RCTEventEmitter/NativeEventEmitter.

#ifdef RCT_NEW_ARCH_ENABLED
@interface DodoCheckout : NativeDodoCheckoutSpecBase <NativeDodoCheckoutSpec>
@end
#else
@interface DodoCheckout : NSObject <RCTBridgeModule>
@end
#endif

// Forward-declares the Swift implementation compiled in the same target.
@interface DodoCheckoutReactNativeImpl : NSObject
@property (nonatomic, copy) void (^eventEmitter)(NSDictionary *body);
- (void)startWithParams:(NSDictionary *)params
                resolve:(void (^)(id))resolve
                 reject:(void (^)(NSString *, NSString *, NSError *))reject;
- (void)getAbandonedSessionWithResolve:(void (^)(id))resolve
                                reject:(void (^)(NSString *, NSString *, NSError *))reject;
- (void)clearAbandonedSessionWithResolve:(void (^)(id))resolve
                                  reject:(void (^)(NSString *, NSString *, NSError *))reject;
- (void)handleOpenURLWithUrl:(NSString *)url
                     resolve:(void (^)(id))resolve
                      reject:(void (^)(NSString *, NSString *, NSError *))reject;
@end

@implementation DodoCheckout {
  DodoCheckoutReactNativeImpl *_impl;
}

RCT_EXPORT_MODULE()

- (instancetype)init {
  if (self = [super init]) {
    _impl = [DodoCheckoutReactNativeImpl new];
    __weak __typeof(self) weakSelf = self;
    _impl.eventEmitter = ^(NSDictionary *body) {
      __typeof(self) strongSelf = weakSelf;
      if (strongSelf == nil) {
        return;
      }
#ifdef RCT_NEW_ARCH_ENABLED
      [strongSelf emitOnCheckoutEvent:body];
#endif
    };
  }
  return self;
}

+ (BOOL)requiresMainQueueSetup { return YES; }

#ifdef RCT_NEW_ARCH_ENABLED

// Convert the codegen typed-params wrapper into an NSDictionary for the Swift impl.
static NSDictionary *DodoCheckoutParamsDictionary(
    JS::NativeDodoCheckout::NativeCheckoutParams &params) {
  NSMutableDictionary *dict = [NSMutableDictionary new];
  dict[@"checkoutUrl"] = params.checkoutUrl();
  dict[@"returnUrl"] = params.returnUrl();
  return dict;
}

- (void)start:(JS::NativeDodoCheckout::NativeCheckoutParams &)params
      resolve:(RCTPromiseResolveBlock)resolve
       reject:(RCTPromiseRejectBlock)reject {
  [_impl startWithParams:DodoCheckoutParamsDictionary(params)
                 resolve:resolve
                  reject:reject];
}

- (void)getAbandonedSession:(RCTPromiseResolveBlock)resolve
                     reject:(RCTPromiseRejectBlock)reject {
  [_impl getAbandonedSessionWithResolve:resolve reject:reject];
}

- (void)clearAbandonedSession:(RCTPromiseResolveBlock)resolve
                       reject:(RCTPromiseRejectBlock)reject {
  [_impl clearAbandonedSessionWithResolve:resolve reject:reject];
}

- (void)handleOpenURL:(NSString *)url
              resolve:(RCTPromiseResolveBlock)resolve
               reject:(RCTPromiseRejectBlock)reject {
  [_impl handleOpenURLWithUrl:url resolve:resolve reject:reject];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params {
  return std::make_shared<facebook::react::NativeDodoCheckoutSpecJSI>(params);
}

#else

RCT_EXPORT_METHOD(start:(NSDictionary *)params
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
  [_impl startWithParams:params resolve:resolve reject:reject];
}

RCT_EXPORT_METHOD(getAbandonedSession:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
  [_impl getAbandonedSessionWithResolve:resolve reject:reject];
}

RCT_EXPORT_METHOD(clearAbandonedSession:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
  [_impl clearAbandonedSessionWithResolve:resolve reject:reject];
}

RCT_EXPORT_METHOD(handleOpenURL:(NSString *)url
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
  [_impl handleOpenURLWithUrl:url resolve:resolve reject:reject];
}

#endif

@end
