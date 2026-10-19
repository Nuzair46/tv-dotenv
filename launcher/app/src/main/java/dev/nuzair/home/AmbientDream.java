package dev.nuzair.home;

import android.graphics.Color;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextClock;

/** Optional system screensaver, selected through Android's supported screensaver settings. */
public final class AmbientDream extends DreamService {
    private VideoBackdrop video;
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow(); setInteractive(false); setFullscreen(true); setScreenBright(false);
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        video = new VideoBackdrop(this); root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        TextClock clock = new TextClock(this); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("h:mm");
        clock.setTextSize(25); clock.setTextColor(0xDDEEF0F3); clock.setPadding(0, 24, 48, 0);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        root.addView(clock, lp); setContentView(root);
    }
    @Override public void onDreamingStarted() { super.onDreamingStarted(); if (video != null) video.resumeVideo(); }
    @Override public void onDreamingStopped() { if (video != null) video.pauseVideo(); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() { if (video != null) video.pauseVideo(); super.onDetachedFromWindow(); }
}
