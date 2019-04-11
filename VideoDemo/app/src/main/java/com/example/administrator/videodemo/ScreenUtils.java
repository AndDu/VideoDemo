package com.example.administrator.videodemo;

import android.content.Context;

class ScreenUtils {


    private static double screenHeight;

    public static int getScreenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    public static int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }
}
