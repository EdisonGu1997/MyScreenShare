package com.example.myscreenshare.util;

import android.content.Context;
import android.util.TypedValue;

public class ScreenUtil {

    public static int dp2px(int dp, Context context) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, dp, context.getResources().getDisplayMetrics());
    }
}
