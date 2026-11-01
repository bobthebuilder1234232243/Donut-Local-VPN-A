// Copyright (C) 2026 Project Gamers
// SPDX-License-Identifier: GPL-3.0-or-later
package com.projectgamers.splitgate;

/** Son N saniyenin indirme / yukleme hizlari (bayt/sn). Android'e bagli degil, JVM'de test edilir. */
final class RateWindow {
    private final double[] down;
    private final double[] up;

    RateWindow(int size) {
        if (size < 2) throw new IllegalArgumentException("size");
        down = new double[size];
        up = new double[size];
    }

    int size() {
        return down.length;
    }

    /** Yeni ornek eklenir, en eski dusurulur. Eksi ya da sayi olmayan degerler 0 sayilir (sayac sifirlanmis olabilir). */
    void push(double d, double u) {
        System.arraycopy(down, 1, down, 0, down.length - 1);
        System.arraycopy(up, 1, up, 0, up.length - 1);
        down[down.length - 1] = clean(d);
        up[up.length - 1] = clean(u);
    }

    void clear() {
        java.util.Arrays.fill(down, 0);
        java.util.Arrays.fill(up, 0);
    }

    double down(int i) {
        return down[i];
    }

    double up(int i) {
        return up[i];
    }

    /** Cizim olcegi icin en buyuk deger; sessizken grafik dalgalanmasin diye en az 10 KB/sn. */
    double peak() {
        double m = 10 * 1024;
        for (int i = 0; i < down.length; i++) m = Math.max(m, Math.max(down[i], up[i]));
        return m;
    }

    private static double clean(double v) {
        return v > 0 && !Double.isInfinite(v) ? v : 0;
    }
}
