// <barcode> on iOS: an AVCaptureSession with a metadata output, its preview filling the view
// (cropped). Codes and failures go to the NativeView's handler (src/barcode.esk).
#import <AVFoundation/AVFoundation.h>
#import "ui_native.h"

// The format names of src/barcode.esk and their AVFoundation types.
static NSDictionary<NSString *, AVMetadataObjectType> *formatTypes(void) {
    static NSDictionary *types;
    if (types) return types;
    NSMutableDictionary *all = [@{@"qr": AVMetadataObjectTypeQRCode, @"aztec": AVMetadataObjectTypeAztecCode,
        @"datamatrix": AVMetadataObjectTypeDataMatrixCode, @"pdf417": AVMetadataObjectTypePDF417Code,
        @"ean13": AVMetadataObjectTypeEAN13Code, @"ean8": AVMetadataObjectTypeEAN8Code,
        @"upce": AVMetadataObjectTypeUPCECode, @"code128": AVMetadataObjectTypeCode128Code,
        @"code39": AVMetadataObjectTypeCode39Code, @"code93": AVMetadataObjectTypeCode93Code,
        @"itf": AVMetadataObjectTypeITF14Code} mutableCopy];
    if (@available(iOS 15.4, *)) all[@"codabar"] = AVMetadataObjectTypeCodabarCode;
    types = all;
    return types;
}

@interface EskiuBarcodeView : UIView <AVCaptureMetadataOutputObjectsDelegate>
@property(nonatomic, copy) UiViewEmit emit;
@property(nonatomic, strong) AVCaptureSession *session;
@property(nonatomic, strong) AVCaptureMetadataOutput *output;
@property(nonatomic, copy) NSString *formats;
@property(nonatomic) BOOL scanning;
@property(nonatomic, copy) NSString *lastValue;
@property(nonatomic) CFAbsoluteTime lastTime;
@end

@implementation EskiuBarcodeView
+ (Class)layerClass { return AVCaptureVideoPreviewLayer.class; }

- (NSArray<AVMetadataObjectType> *)wantedTypes {
    NSArray *available = self.output.availableMetadataObjectTypes;
    NSMutableArray *types = [NSMutableArray new];
    BOOL all = self.formats.length == 0 || [self.formats isEqualToString:@"all"];
    for (NSString *name in formatTypes()) {
        if (!all && ![[self.formats componentsSeparatedByString:@","] containsObject:name]) continue;
        AVMetadataObjectType type = formatTypes()[name];
        if ([available containsObject:type]) [types addObject:type];
    }
    // UPC-A reads as an EAN-13 with a leading 0.
    if (!all && [[self.formats componentsSeparatedByString:@","] containsObject:@"upca"] &&
        [available containsObject:AVMetadataObjectTypeEAN13Code] && ![types containsObject:AVMetadataObjectTypeEAN13Code])
        [types addObject:AVMetadataObjectTypeEAN13Code];
    return types;
}

- (void)start {
    [self stop];
    AVCaptureDevice *device = [AVCaptureDevice defaultDeviceWithDeviceType:AVCaptureDeviceTypeBuiltInWideAngleCamera
                                                                 mediaType:AVMediaTypeVideo position:AVCaptureDevicePositionBack];
    AVCaptureDeviceInput *input = device ? [AVCaptureDeviceInput deviceInputWithDevice:device error:nil] : nil;
    if (!input) { self.emit(@"unavailable", @""); return; }
    AVCaptureSession *session = [AVCaptureSession new];
    AVCaptureMetadataOutput *output = [AVCaptureMetadataOutput new];
    if (![session canAddInput:input] || ![session canAddOutput:output]) { self.emit(@"unavailable", @""); return; }
    [session addInput:input];
    [session addOutput:output];
    [output setMetadataObjectsDelegate:self queue:dispatch_get_main_queue()];
    self.session = session;
    self.output = output;
    output.metadataObjectTypes = self.scanning ? [self wantedTypes] : @[];
    AVCaptureVideoPreviewLayer *preview = (AVCaptureVideoPreviewLayer *)self.layer;
    preview.videoGravity = AVLayerVideoGravityResizeAspectFill;
    preview.session = session;
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{ [session startRunning]; });
}

- (void)stop {
    AVCaptureSession *session = self.session;
    self.session = nil;
    self.output = nil;
    if (session) dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{ [session stopRunning]; });
}

- (void)didMoveToWindow {
    [super didMoveToWindow];
    if (!self.window) { [self stop]; return; }
    if (![AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo]) { self.emit(@"unavailable", @""); return; }
    AVAuthorizationStatus status = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
    if (status == AVAuthorizationStatusAuthorized) { [self start]; return; }
    if (status != AVAuthorizationStatusNotDetermined) { self.emit(@"denied", @""); return; }
    [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL granted) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (!granted) self.emit(@"denied", @"");
            else if (self.window) [self start];
        });
    }];
}

- (void)update {
    if (self.output) self.output.metadataObjectTypes = self.scanning ? [self wantedTypes] : @[];
}

- (void)captureOutput:(AVCaptureOutput *)output didOutputMetadataObjects:(NSArray<__kindof AVMetadataObject *> *)objects
       fromConnection:(AVCaptureConnection *)connection {
    if (!self.scanning) return;
    for (AVMetadataObject *object in objects) {
        if (![object isKindOfClass:AVMetadataMachineReadableCodeObject.class]) continue;
        NSString *value = ((AVMetadataMachineReadableCodeObject *)object).stringValue;
        if (!value) continue;
        NSString *format = [formatTypes() allKeysForObject:object.type].firstObject ?: @"unknown";
        // An EAN-13 starting with 0 is a UPC-A when that is what was asked for.
        if ([format isEqualToString:@"ean13"] && [value hasPrefix:@"0"] &&
            [[self.formats componentsSeparatedByString:@","] containsObject:@"upca"]) {
            format = @"upca";
            value = [value substringFromIndex:1];
        }
        CFAbsoluteTime now = CFAbsoluteTimeGetCurrent();
        if ([value isEqualToString:self.lastValue] && now - self.lastTime < 1.5) { self.lastTime = now; continue; }
        self.lastValue = value;
        self.lastTime = now;
        self.emit(@"code", [NSString stringWithFormat:@"%@ %@", format, value]);
    }
}
@end

UI_NATIVE_SETUP(barcode) {
    ui_register_view(@"barcode", ^UIView *(UiViewEmit emit) {
        EskiuBarcodeView *view = [EskiuBarcodeView new];
        view.backgroundColor = UIColor.blackColor;
        view.emit = emit;
        view.formats = @"all";
        view.scanning = YES;
        return view;
    }, ^(UIView *view, NSString *name, NSString *value) {
        EskiuBarcodeView *scanner = (EskiuBarcodeView *)view;
        if ([name isEqualToString:@"formats"]) scanner.formats = value.length ? value : @"all";
        else if ([name isEqualToString:@"scanning"]) scanner.scanning = ![value isEqualToString:@"0"];
        [scanner update];
    });
}
