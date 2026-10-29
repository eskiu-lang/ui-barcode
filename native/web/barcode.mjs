// <barcode> on the web: the "barcode" view, a <video> on getUserMedia (cropped to fill its box)
// read by the browser's BarcodeDetector five times a second. Browsers without BarcodeDetector
// (Firefox, desktop Safari) say unavailable.
const names = {qr: 'qr_code', aztec: 'aztec', datamatrix: 'data_matrix', pdf417: 'pdf417', ean13: 'ean_13',
  ean8: 'ean_8', upca: 'upc_a', upce: 'upc_e', code128: 'code_128', code39: 'code_39', code93: 'code_93',
  itf: 'itf', codabar: 'codabar'};
const formatOf = detected => Object.keys(names).find(key => names[key] === detected) || 'unknown';

export function setup(ui) {
  ui.registerView('barcode', {
    create(emit) {
      const video = document.createElement('video');
      video.autoplay = true;
      video.muted = true;
      video.playsInline = true;
      Object.assign(video.style, {width: '100%', height: '100%', objectFit: 'cover', background: '#000'});
      let stream = null, timer = 0, detector = null, scanning = true, formats = 'all';
      let last = '', lastTime = 0;
      const makeDetector = () => {
        const wanted = formats === 'all' ? Object.values(names) : formats.split(',').map(f => names[f]).filter(Boolean);
        try { detector = new window.BarcodeDetector({formats: wanted}); } catch (e) { detector = null; }
      };
      const scan = async () => {
        if (!scanning || !detector || video.readyState < 2) return;
        let codes = [];
        try { codes = await detector.detect(video); } catch (e) { return; }
        for (const code of codes) {
          const now = performance.now();
          if (code.rawValue === last && now - lastTime < 1500) { lastTime = now; continue; }
          last = code.rawValue;
          lastTime = now;
          emit('code', `${formatOf(code.format)} ${code.rawValue}`);
        }
      };
      if (!('BarcodeDetector' in window) || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        setTimeout(() => emit('unavailable', ''), 0);
      } else {
        makeDetector();
        navigator.mediaDevices.getUserMedia({video: {facingMode: 'environment'}})
          .then(s => { stream = s; video.srcObject = s; timer = setInterval(scan, 200); })
          .catch(error => emit(error && error.name === 'NotAllowedError' ? 'denied' : 'unavailable', ''));
      }
      return {
        element: video,
        set(name, value) {
          if (name === 'scanning') scanning = value !== '0';
          else if (name === 'formats') { formats = value || 'all'; if (detector) makeDetector(); }
        },
        dispose() {
          clearInterval(timer);
          if (stream) stream.getTracks().forEach(track => track.stop());
        },
      };
    },
  });
}
