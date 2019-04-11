package com.example.administrator.videodemo;

import android.content.Context;

class DensityUtil {
    public static int dip2px(Context context, int dp) {
        return (int) (context.getResources().getDisplayMetrics().density * dp + 0.5f);
    }
}
