/*
 * The archive's own image viewer: shows a Data Object's image, decoded on the
 * server through its Representation Information (/api/data-objects/{id}/pixels.json),
 * in the page, with nothing to install. The pixels are fetched once; stretch,
 * limits, colours, zoom and panning are all done here. Pointing at a pixel
 * shows its value and units.
 */
(function () {
    const root = document.querySelector('.image-viewer');
    if (!root) {
        return;
    }
    const q = name => root.querySelector('[data-image-' + name + ']');
    const panel = q('panel'), canvas = q('canvas'), readout = q('readout'), show = q('show');
    const controls = {stretch: q('stretch'), limits: q('limits'), colours: q('colours'), zoom: q('zoom'), flip: q('flip')};
    const ctx = canvas.getContext('2d');

    let image = null;      // {name, width, height, unit, description}
    let values = null;     // Float64Array, row 0 first; NaN where there's no value
    let sorted = null;     // the finite values, sorted, for the limits
    let bitmap = null;     // the image drawn at one screen pixel per image pixel
    let scale = 1, offsetX = 0, offsetY = 0;

    show.addEventListener('click', () => {
        show.disabled = true;
        show.textContent = i18n('decoding');
        fetch(root.dataset.pixels)
            .then(r => r.ok ? r.json() : r.text().then(t => { throw new Error(t); }))
            .then(load)
            .catch(e => {
                show.disabled = false;
                show.textContent = i18n('image.show');
                panel.hidden = false;
                readout.textContent = e.message;
            });
    });

    function load(data) {
        image = data;
        values = new Float64Array(data.width * data.height);
        const finite = [];
        data.pixels.forEach((row, y) => row.forEach((v, x) => {
            values[y * data.width + x] = v === null ? NaN : v;
            if (v !== null) {
                finite.push(v);
            }
        }));
        sorted = Float64Array.from(finite).sort();
        show.hidden = true;
        panel.hidden = false;
        sizeCanvas();
        repaint();
        fit();
        Object.values(controls).forEach(c => c.addEventListener('change', () => {
            if (c === controls.zoom) {
                zoomTo(c.value);
            } else {
                repaint();
            }
        }));
    }

    function sizeCanvas() {
        const width = Math.min(Math.max(root.clientWidth - 4, 256), 900);
        canvas.width = width;
        canvas.height = Math.round(Math.min(width * image.height / image.width, 640)) || 512;
    }

    /** The display limits: the middle {percent}% of the values. */
    function limits() {
        const n = sorted.length;
        if (n === 0) {
            return [0, 1];
        }
        const cut = (100 - Number(controls.limits.value)) / 200;
        const lo = sorted[Math.floor(cut * (n - 1))], hi = sorted[Math.ceil((1 - cut) * (n - 1))];
        return hi > lo ? [lo, hi] : [lo, lo + 1];
    }

    const stretches = {
        linear: t => t,
        sqrt: t => Math.sqrt(t),
        log: t => Math.log10(1 + 999 * t) / 3,
        asinh: t => Math.asinh(10 * t) / Math.asinh(10)
    };
    const clamp = t => Math.max(0, Math.min(1, t));
    const colours = {
        grey: t => [t, t, t],
        heat: t => [clamp(3 * t), clamp(3 * t - 1), clamp(3 * t - 2)],
        cool: t => [t, 1 - t, 1],
        rainbow: t => hsv((1 - t) * 240)
    };

    function hsv(h) {
        const x = 1 - Math.abs((h / 60) % 2 - 1);
        return h < 60 ? [1, x, 0] : h < 120 ? [x, 1, 0] : h < 180 ? [0, 1, x] : h < 240 ? [0, x, 1] : [x, 0, 1];
    }

    /** Redraws the image's pixels with the chosen stretch, limits and colours. */
    function repaint() {
        const [lo, hi] = limits();
        const stretch = stretches[controls.stretch.value], colour = colours[controls.colours.value];
        const w = image.width, h = image.height;
        const off = document.createElement('canvas');
        off.width = w;
        off.height = h;
        const offCtx = off.getContext('2d');
        const out = offCtx.createImageData(w, h);
        for (let y = 0; y < h; y++) {
            const shownRow = controls.flip.checked ? h - 1 - y : y;
            for (let x = 0; x < w; x++) {
                const v = values[y * w + x];
                const i = (shownRow * w + x) * 4;
                if (Number.isNaN(v)) {
                    out.data[i + 3] = 0;
                    continue;
                }
                const [r, g, b] = colour(stretch(clamp((v - lo) / (hi - lo))));
                out.data[i] = Math.round(r * 255);
                out.data[i + 1] = Math.round(g * 255);
                out.data[i + 2] = Math.round(b * 255);
                out.data[i + 3] = 255;
            }
        }
        offCtx.putImageData(out, 0, 0);
        bitmap = off;
        draw();
    }

    function draw() {
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.fillStyle = '#222';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.imageSmoothingEnabled = false;
        ctx.setTransform(scale, 0, 0, scale, offsetX, offsetY);
        ctx.drawImage(bitmap, 0, 0);
    }

    function fit() {
        scale = Math.min(canvas.width / image.width, canvas.height / image.height);
        offsetX = (canvas.width - image.width * scale) / 2;
        offsetY = (canvas.height - image.height * scale) / 2;
        draw();
    }

    function zoomTo(value) {
        if (value === 'fit') {
            fit();
            return;
        }
        zoomAround(canvas.width / 2, canvas.height / 2, Number(value) / scale);
    }

    function zoomAround(cx, cy, factor) {
        offsetX = cx - (cx - offsetX) * factor;
        offsetY = cy - (cy - offsetY) * factor;
        scale *= factor;
        draw();
    }

    canvas.addEventListener('wheel', e => {
        if (!image) {
            return;
        }
        e.preventDefault();
        const r = canvas.getBoundingClientRect();
        zoomAround(e.clientX - r.left, e.clientY - r.top, e.deltaY < 0 ? 1.25 : 0.8);
    }, {passive: false});

    let drag = null;
    canvas.addEventListener('mousedown', e => { drag = {x: e.clientX, y: e.clientY}; });
    window.addEventListener('mouseup', () => { drag = null; });
    canvas.addEventListener('mousemove', e => {
        if (!image) {
            return;
        }
        if (drag) {
            offsetX += e.clientX - drag.x;
            offsetY += e.clientY - drag.y;
            drag = {x: e.clientX, y: e.clientY};
            draw();
        }
        const r = canvas.getBoundingClientRect();
        const px = Math.floor((e.clientX - r.left - offsetX) / scale);
        const shownRow = Math.floor((e.clientY - r.top - offsetY) / scale);
        if (px < 0 || px >= image.width || shownRow < 0 || shownRow >= image.height) {
            readout.textContent = describe('');
            return;
        }
        const row = controls.flip.checked ? image.height - 1 - shownRow : shownRow;
        const v = values[row * image.width + px];
        readout.textContent = describe(i18n('image.position', px + 1, row + 1,
            Number.isNaN(v) ? i18n('image.noValue') : v + (image.unit ? ' ' + image.unit : '')));
    });

    function describe(position) {
        const what = image.description ? ' — ' + image.description : '';
        return (position || i18n('image.point')) + what + ' ' + i18n('image.size', image.width, image.height);
    }
})();
