// <barcode> on Android: Camera2 into a TextureView, cropped to fill the box the layout gives
// it, and small YUV frames read by ML Kit's barcode scanner (bundled with the app). Codes and
// failures go to the NativeView's handler (src/barcode.esk).
package org.eskiu.barcode;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.util.Arrays;
import java.util.HashMap;
import org.eskiu.ui.UiActivity;

public final class BarcodeView extends TextureView implements TextureView.SurfaceTextureListener {
    private static final HashMap<String, Integer> FORMATS = new HashMap<>();
    static {
        FORMATS.put("qr", Barcode.FORMAT_QR_CODE); FORMATS.put("aztec", Barcode.FORMAT_AZTEC);
        FORMATS.put("datamatrix", Barcode.FORMAT_DATA_MATRIX); FORMATS.put("pdf417", Barcode.FORMAT_PDF417);
        FORMATS.put("ean13", Barcode.FORMAT_EAN_13); FORMATS.put("ean8", Barcode.FORMAT_EAN_8);
        FORMATS.put("upca", Barcode.FORMAT_UPC_A); FORMATS.put("upce", Barcode.FORMAT_UPC_E);
        FORMATS.put("code128", Barcode.FORMAT_CODE_128); FORMATS.put("code39", Barcode.FORMAT_CODE_39);
        FORMATS.put("code93", Barcode.FORMAT_CODE_93); FORMATS.put("itf", Barcode.FORMAT_ITF);
        FORMATS.put("codabar", Barcode.FORMAT_CODABAR);
    }

    private final UiActivity.ViewEvents events;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader frames;
    private Size preview;
    private int sensorOrientation;
    private HandlerThread thread;
    private Handler handler;
    private BarcodeScanner scanner;
    private String formats = "all";
    private volatile boolean scanning = true;
    private volatile boolean busy;
    private String lastValue = "";
    private long lastTime;

    public static void setup() {
        UiActivity.registerView("barcode", new UiActivity.ViewFactory() {
            @Override public View create(Context context, UiActivity.ViewEvents events) { return new BarcodeView(context, events); }
            @Override public void set(View view, String name, String value) {
                BarcodeView scanner = (BarcodeView)view;
                if (name.equals("scanning")) scanner.scanning = !value.equals("0");
                else if (name.equals("formats")) {
                    String next = value.isEmpty() ? "all" : value;
                    if (!next.equals(scanner.formats)) { scanner.formats = next; scanner.makeScanner(); }
                }
            }
        });
    }

    BarcodeView(Context context, UiActivity.ViewEvents events) {
        super(context);
        this.events = events;
        setSurfaceTextureListener(this);
        makeScanner();
    }

    private void makeScanner() {
        if (scanner != null) scanner.close();
        int first = 0;
        int[] more = new int[0];
        if (!formats.equals("all")) {
            java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
            for (String name : formats.split(",")) { Integer f = FORMATS.get(name.trim()); if (f != null) list.add(f); }
            if (!list.isEmpty()) {
                first = list.get(0);
                more = new int[list.size() - 1];
                for (int i = 1; i < list.size(); i++) more[i - 1] = list.get(i);
            }
        }
        BarcodeScannerOptions options = first == 0 ? new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
            : new BarcodeScannerOptions.Builder().setBarcodeFormats(first, more).build();
        scanner = BarcodeScanning.getClient(options);
    }

    private void emit(String name, String payload) { post(() -> events.emit(name, payload)); }

    @Override protected void onDetachedFromWindow() {
        close();
        if (scanner != null) { scanner.close(); scanner = null; }
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) close();
        else if (isAvailable() && device == null && isAttachedToWindow()) open();
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        UiActivity.requestPermissions(new String[] {Manifest.permission.CAMERA}, granted -> {
            if (!granted) { emit("denied", ""); return; }
            if (isAttachedToWindow()) open();
        });
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { crop(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { close(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}

    @SuppressLint("MissingPermission")
    private void open() {
        if (thread != null) return;
        if (getContext().checkSelfPermission(Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        CameraManager manager = (CameraManager)getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            String chosen = null;
            for (String id : manager.getCameraIdList()) {
                Integer lens = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (lens != null && lens == CameraCharacteristics.LENS_FACING_BACK) { chosen = id; break; }
                if (chosen == null) chosen = id;
            }
            if (chosen == null) { emit("unavailable", ""); return; }
            CameraCharacteristics info = manager.getCameraCharacteristics(chosen);
            Integer orientation = info.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = orientation == null ? 90 : orientation;
            StreamConfigurationMap map = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            preview = largest(map.getOutputSizes(SurfaceTexture.class), 1920);
            // Small frames are enough for codes and keep each scan quick.
            Size scan = largest(map.getOutputSizes(ImageFormat.YUV_420_888), 1280);
            thread = new HandlerThread("barcode");
            thread.start();
            handler = new Handler(thread.getLooper());
            frames = ImageReader.newInstance(scan.getWidth(), scan.getHeight(), ImageFormat.YUV_420_888, 2);
            frames.setOnImageAvailableListener(this::frameReady, handler);
            manager.openCamera(chosen, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice opened) { device = opened; startPreview(); }
                @Override public void onDisconnected(CameraDevice gone) { gone.close(); device = null; }
                @Override public void onError(CameraDevice failed, int error) { failed.close(); device = null; emit("unavailable", ""); }
            }, handler);
        } catch (CameraAccessException | RuntimeException error) {
            emit("unavailable", "");
        }
    }

    // The largest size whose longer side is at most `limit`.
    private static Size largest(Size[] sizes, int limit) {
        Size best = sizes[0];
        long area = 0;
        for (Size size : sizes) {
            long a = (long)size.getWidth() * size.getHeight();
            if (Math.max(size.getWidth(), size.getHeight()) <= limit && a > area) { best = size; area = a; }
        }
        return best;
    }

    private void startPreview() {
        try {
            SurfaceTexture texture = getSurfaceTexture();
            if (texture == null || device == null) return;
            texture.setDefaultBufferSize(preview.getWidth(), preview.getHeight());
            Surface surface = new Surface(texture);
            CaptureRequest.Builder request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(surface);
            request.addTarget(frames.getSurface());
            device.createCaptureSession(Arrays.asList(surface, frames.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    session = configured;
                    try {
                        request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        configured.setRepeatingRequest(request.build(), null, handler);
                    } catch (CameraAccessException | IllegalStateException error) { /* closed meanwhile */ }
                    post(BarcodeView.this::crop);
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed) { emit("unavailable", ""); }
            }, handler);
        } catch (CameraAccessException | IllegalStateException error) {
            emit("unavailable", "");
        }
    }

    // As the camera package's view: the sensor's landscape buffer turned upright and scaled to
    // cover the view.
    private void crop() {
        if (preview == null || getWidth() == 0 || getHeight() == 0) return;
        boolean turned = sensorOrientation % 180 != 0;
        float bufferWidth = turned ? preview.getHeight() : preview.getWidth();
        float bufferHeight = turned ? preview.getWidth() : preview.getHeight();
        float cover = Math.max(getWidth() / bufferWidth, getHeight() / bufferHeight);
        Matrix matrix = new Matrix();
        matrix.setScale(bufferWidth * cover / getWidth(), bufferHeight * cover / getHeight(), getWidth() / 2f, getHeight() / 2f);
        setTransform(matrix);
    }

    // One frame at a time goes to the scanner; frames that come while it works are dropped.
    private void frameReady(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        BarcodeScanner current = scanner;
        if (!scanning || busy || current == null) { image.close(); return; }
        busy = true;
        current.process(InputImage.fromMediaImage(image, sensorOrientation))
            .addOnSuccessListener(codes -> {
                for (Barcode code : codes) {
                    String value = code.getRawValue();
                    if (value == null || !scanning) continue;
                    long now = SystemClock.elapsedRealtime();
                    if (value.equals(lastValue) && now - lastTime < 1500) { lastTime = now; continue; }
                    lastValue = value;
                    lastTime = now;
                    emit("code", formatName(code.getFormat()) + " " + value);
                }
            })
            .addOnCompleteListener(task -> { image.close(); busy = false; });
    }

    private static String formatName(int format) {
        for (java.util.Map.Entry<String, Integer> entry : FORMATS.entrySet()) if (entry.getValue() == format) return entry.getKey();
        return "unknown";
    }

    private void close() {
        if (session != null) { session.close(); session = null; }
        if (device != null) { device.close(); device = null; }
        if (frames != null) { frames.close(); frames = null; }
        if (thread != null) { thread.quitSafely(); thread = null; }
    }
}
