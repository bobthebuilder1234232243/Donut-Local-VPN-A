// Copyright (C) 2026 Project Gamers
// SPDX-License-Identifier: GPL-3.0-or-later
package com.projectgamers.splitgate;

/** Arayuzun Android'e bagli olmayan parcalarinin JVM testi (grafik veri penceresi). */
public class UiSelfTest {
    static int fails = 0;
    static int passes = 0;

    static void check(boolean ok, String msg) {
        if (ok) {
            passes++;
            System.out.println("ok    " + msg);
        } else {
            fails++;
            System.out.println("FAIL  " + msg);
        }
    }

    public static void main(String[] args) {
        RateWindow w = new RateWindow(5);
        check(w.size() == 5, "RateWindow: size is kept");
        check(w.peak() == 10 * 1024, "RateWindow: an idle graph keeps a 10 KB/s scale");
        for (int i = 1; i <= 8; i++) w.push(i * 1000.0, i * 10.0);
        check(w.down(4) == 8000.0 && w.down(0) == 4000.0, "RateWindow: only the newest 5 samples stay, newest last");
        check(w.up(4) == 80.0, "RateWindow: upload is kept separately from download");
        check(w.peak() == 10 * 1024, "RateWindow: peak never drops below the minimum scale");
        w.push(50_000.0, 100.0);
        check(w.peak() == 50_000.0, "RateWindow: peak follows the biggest sample");
        w.push(-5.0, Double.NaN);
        check(w.down(4) == 0.0 && w.up(4) == 0.0, "RateWindow: negative and NaN samples become 0 (counter reset)");
        w.push(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        check(w.down(4) == 0.0 && w.up(4) == 0.0, "RateWindow: infinite samples become 0");
        w.clear();
        boolean zero = true;
        for (int i = 0; i < w.size(); i++) zero &= w.down(i) == 0 && w.up(i) == 0;
        check(zero, "RateWindow: clear() zeroes both series");
        boolean threw = false;
        try {
            new RateWindow(1);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "RateWindow: a window smaller than 2 is rejected (curve needs 2 points)");
        System.out.println();
        System.out.println(passes + " passed, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
