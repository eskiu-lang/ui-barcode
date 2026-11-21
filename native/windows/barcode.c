// <barcode> on Windows: no scanner. The "barcode" view is an empty child window that reports
// unavailable (ScanUnavailable) once it is mounted, as a browser without BarcodeDetector does,
// so an app builds and links and can say there is no scanner. It never reports a code.
#include "ui_native.h"

static const wchar_t *kEmitter = L"eskiu-barcode-emitter";

// A timer on the view itself: it fires after the mount, and dies with the view.
static void CALLBACK report(HWND view, UINT message, UINT_PTR timer, DWORD time) {
    (void)message;
    (void)time;
    KillTimer(view, timer);
    UiViewEmitter *emitter = (UiViewEmitter *)GetPropW(view, kEmitter);
    if (emitter) ui_view_emit(emitter, "unavailable", "");
}

static HWND create(HWND parent, UiViewEmitter *emitter) {
    HWND view = CreateWindowExW(0, L"STATIC", L"", WS_CHILD | WS_VISIBLE, 0, 0, 0, 0, parent, NULL,
                                GetModuleHandleW(NULL), NULL);
    if (!view) return NULL;
    SetPropW(view, kEmitter, (HANDLE)emitter);
    SetTimer(view, 1, 0, report);
    return view;
}

static void set(HWND view, const char *name, const char *value) {
    (void)view;
    (void)name;
    (void)value;
}

UI_NATIVE_SETUP(barcode) { ui_register_view("barcode", create, set); }
