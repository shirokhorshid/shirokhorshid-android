package com.psiphon3;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.tabs.TabLayout;

public class PsiphonTabLayout extends TabLayout {
    public PsiphonTabLayout(@NonNull Context context) {
        super(context);
        setTabMode(MODE_SCROLLABLE);
    }

    public PsiphonTabLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setTabMode(MODE_SCROLLABLE);
    }

    public PsiphonTabLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setTabMode(MODE_SCROLLABLE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getTabMode() == MODE_FIXED || getTabCount() == 0) {
            return;
        }
        int screenWidth = getContext().getApplicationContext().getResources().getDisplayMetrics().widthPixels;
        int totalWidth = 0;
        int maxWidth = 0;
        for (int i = 0; i < getTabCount(); i++) {
            int tabWidth = getTabAt(i).view.getMeasuredWidth();
            totalWidth += tabWidth;
            maxWidth = Math.max(maxWidth, tabWidth);
        }
        if (totalWidth < screenWidth && screenWidth / getTabCount() >= maxWidth) {
            setTabMode(TabLayout.MODE_FIXED);
        }
    }
}
