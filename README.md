<p align="center">
  <img src="https://raw.githubusercontent.com/eskiu-lang/eskiu/main/assets/logo.png" alt="Eskiu, a kitten with a ball of yarn" width="140">
</p>

<h2 align="center">barcode</h2>
<p align="center">QR codes and barcodes read live from the camera, for Eskiu ui apps.</p>
<p align="center"><sub>Part of the <a href="https://github.com/eskiu-lang">Eskiu</a> project</sub></p>

---

```eskiu
import <ui>;
import <barcode>;

void Scan() {
    *StringState found = use_string("");
    BarcodeScanner(Style{grow: 1}, "qr", found.get()[0] == 0, void(Barcode code) {
        if (code.status == ScanCode) { found.set(code.value); }
    });
}
```

`BarcodeScanner(style, formats, scanning, on_code)` shows the back camera filling its box and
hands each code it reads to `on_code` while `scanning`: the same code again only after a
second and a half, so a code held in front of the camera is one call. `formats` is a comma
separated list of `qr`, `aztec`, `datamatrix`, `pdf417`, `ean13`, `ean8`, `upca`, `upce`,
`code128`, `code39`, `code93`, `itf`, `codabar`, or `"all"`.

`Barcode` has a `status` (`ScanCode`, `ScanDenied`, `ScanUnavailable`), the `format` read and
the `value`, borrowed for the call. On `ScanDenied`, `ui_open_settings()` takes the user to
the app's settings to allow the camera.

| Platform | Reads codes with |
|---|---|
| iOS | AVFoundation's metadata output (no extra library) |
| Android | ML Kit's barcode scanner, bundled with the app (`com.google.mlkit:barcode-scanning`, through the ui tool's Gradle build); no network, no Google Play services |
| Web | the browser's `BarcodeDetector` (Chrome on Android and the desktop, Safari on iOS 17+); unavailable elsewhere |

The first scanner mounted asks for the camera permission (`NSCameraUsageDescription`, which
an app's own `ui.toml` can word).

`examples/scan` reads a QR code and pauses until "Scan again" (`ui run ios` or
`ui run android`, on a device: simulators have no camera).


## Install

In an app made with `ui new`:

```bash
eskiuc add barcode --git https://github.com/eskiu-lang/ui-barcode --rev v0.1.0
```
## Tests

`eskiuc test` drives a scanner with the events a platform sends: codes of two formats, a value
with spaces, denied and unavailable, and pausing.

## License

MIT
