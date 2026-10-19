package dev.nuzair.home;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.SystemClock;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** Direct hardware video surface: no WebView, TextureView, GL shader or audio focus. */
final class VideoBackdrop extends FrameLayout implements SurfaceHolder.Callback {
    private MediaPlayer player;
    private boolean active;
    private final SurfaceView surface;
    private final ImageView poster;
    private long openedAt;
    private long firstFrameMs = -1;
    VideoBackdrop(Context c) {
        super(c); setFocusable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        surface = new SurfaceView(c);
        surface.setFocusable(false);
        surface.getHolder().addCallback(this);
        surface.getHolder().setFixedSize(1920, 1080);
        addView(surface, new LayoutParams(-1, -1));
        poster = new ImageView(c);
        poster.setImageResource(R.drawable.neural_poster);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(poster, new LayoutParams(-1, -1));
    }
    void resumeVideo() { active = true; open(); }
    // Queried only by dumpsys for validation, never polled during playback.
    String describe() {
        if (player == null) return "released active="+active;
        try { return "poster="+(poster.getVisibility()==VISIBLE)+" firstFrameMs="+firstFrameMs+" playing="+player.isPlaying()+" position="+player.getCurrentPosition()+" duration="+player.getDuration()+" metrics="+player.getMetrics(); }
        catch (IllegalStateException e) { return "preparing active="+active; }
    }
    void pauseVideo() {
        active = false;
        poster.setVisibility(VISIBLE);
        if (player != null) {
            player.release(); player = null;
        }
    }
    private void open() {
        if (!active || player != null || !surface.getHolder().getSurface().isValid()) return;
        poster.setVisibility(VISIBLE);
        openedAt = SystemClock.elapsedRealtime(); firstFrameMs = -1;
        final MediaPlayer p = new MediaPlayer(); player = p;
        try (AssetFileDescriptor a = getResources().openRawResourceFd(R.raw.neural)) {
            p.setDataSource(a.getFileDescriptor(), a.getStartOffset(), a.getLength());
            p.setDisplay(surface.getHolder()); p.setVolume(0f, 0f); p.setLooping(true);
            p.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            p.setOnPreparedListener(mp -> {
                if (player != mp || !active) return;
                mp.start(); Log.i("NuzairHome", "Wallpaper playing");
            });
            p.setOnInfoListener((mp, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && player == mp && active) {
                    firstFrameMs = SystemClock.elapsedRealtime() - openedAt;
                    poster.setVisibility(INVISIBLE);
                    Log.i("NuzairHome", "Wallpaper first frame in " + firstFrameMs + " ms");
                }
                return false;
            });
            p.setOnErrorListener((mp, what, extra) -> {
                Log.w("NuzairHome", "Wallpaper error " + what + "/" + extra);
                if (player == mp) { poster.setVisibility(VISIBLE); mp.release(); player = null; }
                return true;
            });
            p.prepareAsync();
        } catch (Exception e) {
            Log.w("NuzairHome", "Wallpaper unavailable", e);
            p.release(); if (player == p) player = null;
        }
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { open(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { boolean wasActive = active; pauseVideo(); active = wasActive; }
}
