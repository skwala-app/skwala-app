package com.skwala.app;

import android.content.Context;

/** app version shown in "About" (read from the installed package) */
final class BuildConfigCompat {
    private BuildConfigCompat() { }

    @SuppressWarnings("deprecation")
    static String versionName(Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return v == null ? "1.0" : v;
        } catch (Exception e) {
            return "1.0";
        }
    }
}
