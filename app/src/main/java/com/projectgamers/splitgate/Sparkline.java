// Copyright (C) 2026 Project Gamers
// SPDX-License-Identifier: GPL-3.0-or-later
package com.projectgamers.splitgate;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/**
 * Canli trafik grafigi: indirme ve yukleme egrileri; yeni ornek saga girer, grafik saniyede bir yenilenir.
 *
 * Pil: bilerek kayma animasyonu yoktur. Her animasyon karesi pencerenin tamamini yeniden cizdirir (olculdu: kare basina
 * ~10 ms islemci). Egri her ornekte (saniyede bir) bir kez bit eslemeye cizilir ve tek bir kare ile gosterilir.
 */
final class Sparkline extends View {
    static final int DOWN = 0xFF2EE6A8;
    static final int UP = 0xFF5B8CFF;
    private static final int GRID = 0x22FFFFFF;

    private final RateWindow data = new RateWindow(60);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final Path area = new Path();
    private final LinearGradient[] fills = new LinearGradient[2];
    private float fillH;
    private Bitmap bmp;
    private Canvas bmpCanvas;
    private boolean dirty = true;

    Sparkline(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(d * 2f);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(d);
        grid.setColor(GRID);
    }

    void push(double down, double up) {
        data.push(down, up);
        dirty = true;
        invalidate();
    }

    void clear() {
        data.clear();
        dirty = true;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bmp = null;
        bmpCanvas = null;
        dirty = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        bmp = null; // bellek: yeniden gorununce tekrar cizilir
        bmpCanvas = null;
        dirty = true;
        super.onDetachedFromWindow();
    }

    private float step() {
        return getWidth() / (float) (data.size() - 1);
    }

    /** Egriyi bit eslemeye cizer. Ornek i, bit eslemede (i+1)*adim noktasindadir; solda bir adimlik duz giris vardir. */
    private void render() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float step = step();
        int bw = (int) Math.ceil(w + step) + 4;
        if (bmp == null || bmp.getWidth() != bw || bmp.getHeight() != h) {
            bmp = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
            bmpCanvas = new Canvas(bmp);
        }
        bmp.eraseColor(Color.TRANSPARENT);
        double peak = data.peak();
        for (int k = 0; k < 2; k++) {
            boolean down = k == 0;
            int col = down ? DOWN : UP;
            path.reset();
            curve(path, down, h, peak, step);
            area.set(path);
            area.lineTo(data.size() * step, h);
            area.lineTo(0, h);
            area.close();
            if (fills[k] == null || fillH != h) {
                fills[k] = new LinearGradient(0, 0, 0, h, (col & 0x00FFFFFF) | 0x50000000, col & 0x00FFFFFF,
                        Shader.TileMode.CLAMP);
                if (k == 1) fillH = h;
            }
            fill.setShader(fills[k]);
            bmpCanvas.drawPath(area, fill);
            fill.setShader(null);
            line.setColor(col);
            bmpCanvas.drawPath(path, line);
        }
        dirty = false;
    }

    /** Noktalardan yumusak (orta nokta kareli) egri kurar. */
    private void curve(Path out, boolean down, float h, double peak, float step) {
        int n = data.size();
        float px = 0, py = 0;
        for (int i = 0; i < n; i++) {
            double v = down ? data.down(i) : data.up(i);
            float x = (i + 1) * step;
            float y = (float) (h - 4 - (v / peak) * (h - 10));
            if (i == 0) {
                out.moveTo(0, y);
                out.lineTo(x, y);
            } else {
                out.quadTo(px, py, (px + x) / 2f, (py + y) / 2f);
            }
            px = x;
            py = y;
        }
        out.lineTo(px, py);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        for (int i = 1; i <= 3; i++) c.drawLine(0, h * i / 4f, w, h * i / 4f, grid);
        if (dirty) render();
        if (bmp == null) return;
        // bit eslemin solundaki bir adimlik duz giris seridi gorunmesin
        c.drawBitmap(bmp, -step(), 0, blit);
    }
}
