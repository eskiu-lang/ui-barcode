// <barcode> on macOS: no scanner yet. The "barcode" view is an empty view that reports
// unavailable (ScanUnavailable) once it is mounted, as a browser without BarcodeDetector does,
// so an app builds and links and can say there is no scanner. It never reports a code.
#import "ui_native.h"

UI_NATIVE_SETUP(barcode) {
    ui_register_view(@"barcode", ^NSView *(UiViewEmit emit) {
        // After the mount: the host dispatches an emit on the main thread at once.
        dispatch_async(dispatch_get_main_queue(), ^{ emit(@"unavailable", @""); });
        return [[NSView alloc] initWithFrame:NSZeroRect];
    }, ^(NSView *view, NSString *name, NSString *value) {
        (void)view;
        (void)name;
        (void)value;
    });
}
