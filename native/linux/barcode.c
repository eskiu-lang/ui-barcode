// <barcode> on Linux: no scanner. The "barcode" view is an empty widget that reports
// unavailable (ScanUnavailable) once it is mounted, as a browser without BarcodeDetector does,
// so an app builds and links and can say there is no scanner. It never reports a code.
#include "ui_native.h"

static GtkWidget *create(UiViewEmitter *emitter) {
    // ui_view_emit posts to the main loop, so the event comes after the mount.
    ui_view_emit(emitter, "unavailable", "");
    return gtk_box_new(GTK_ORIENTATION_VERTICAL, 0);
}

static void set(GtkWidget *view, const char *name, const char *value) {
    (void)view;
    (void)name;
    (void)value;
}

UI_NATIVE_SETUP(barcode) { ui_register_view("barcode", create, set); }
