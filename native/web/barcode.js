// <barcode> on the web: barcode_supported's question, as an emscripten library. The scanner
// itself is the "barcode" view (barcode.mjs).
mergeInto(LibraryManager.library, {
  barcode_native_supported: function() {
    const camera = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
    return typeof window !== 'undefined' && 'BarcodeDetector' in window && camera ? 1 : 0;
  },
});
