// Reads a PDF the way a printer's customer would: counts the pages, draws them and finds the QR codes.
// pdf.js draws the page; jsQR reads the codes. No QR code is trusted from the API's own data: they are decoded from the picture.
import { createCanvas } from '@napi-rs/canvas';
import jsQR from 'jsqr';
import { getDocument } from 'pdfjs-dist/legacy/build/pdf.mjs';

/** Opens the PDF (bytes) and gives the number of pages and a function to draw a page. */
export async function openPdf(bytes) {
  const doc = await getDocument({ data: new Uint8Array(bytes), useSystemFonts: false, verbosity: 0 }).promise;
  return {
    pages: doc.numPages,
    doc,
    /**
     * The QR codes on one page (1-based), drawn at `dpi` and read by jsQR. jsQR loses its way on a whole page with a big dark
     * background, so the page is read through a small window that slides over it (window 40 mm, step 8 mm): some window always holds
     * a whole code with a margin around it. Every code found is listed once.
     */
    async qrCodes(pageNumber, dpi = 150) {
      const page = await doc.getPage(pageNumber);
      const viewport = page.getViewport({ scale: dpi / 72 });
      const canvas = createCanvas(Math.ceil(viewport.width), Math.ceil(viewport.height));
      const context = canvas.getContext('2d');
      context.fillStyle = '#fff';
      context.fillRect(0, 0, canvas.width, canvas.height);
      await page.render({ canvasContext: context, canvas, viewport }).promise;
      const px = (mm) => Math.round((mm / 25.4) * dpi);
      const window = px(40);
      const step = px(8);
      const found = new Set();
      for (let y = 0; y < canvas.height; y += step) {
        for (let x = 0; x < canvas.width; x += step) {
          const w = Math.min(window, canvas.width - x);
          const h = Math.min(window, canvas.height - y);
          if (w < px(20) || h < px(20)) continue;
          const image = context.getImageData(x, y, w, h);
          const code = jsQR(image.data, w, h, { inversionAttempts: 'dontInvert' });
          if (code) found.add(code.data);
        }
      }
      page.cleanup();
      return [...found];
    },
  };
}

/** How many cards fit on one sheet, worked out from the sheet and card sizes alone (chapter 9.6: bleed, crop marks, a strip for the credit line). */
export function cardsPerSheet({ paperMm = [210, 297], cardMm = [63, 88], bleedMm = 3, gapMm = 2 } = {}) {
  const margin = bleedMm + 0.5 + 3 + 0.5; // bleed, the gap before the crop marks, their length, a little air
  const bottom = margin + 5; // and the strip for the credit line
  const cols = Math.floor((paperMm[0] - 2 * margin + gapMm) / (cardMm[0] + gapMm) + 1e-9);
  const rows = Math.floor((paperMm[1] - margin - bottom + gapMm) / (cardMm[1] + gapMm) + 1e-9);
  return cols * rows;
}

/** Reads the manifest CSV that comes with an export: one row per card. */
export function readManifest(csv) {
  const lines = csv.replace(/^﻿/, '').trim().split(/\r?\n/);
  const header = splitCsv(lines[0]);
  return lines.slice(1).map((line) => Object.fromEntries(splitCsv(line).map((v, i) => [header[i], v])));
}

function splitCsv(line) {
  const out = [];
  let current = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (quoted) {
      if (c === '"' && line[i + 1] === '"') {
        current += '"';
        i++;
      } else if (c === '"') quoted = false;
      else current += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') {
      out.push(current);
      current = '';
    } else current += c;
  }
  out.push(current);
  return out;
}
