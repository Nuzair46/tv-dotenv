package dev.nuzair.home;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** Direct hardware video surface: no WebView, TextureView, GL shader or audio focus. */
final class VideoBackdrop extends SurfaceView implements SurfaceHolder.Callback {
    private MediaPlayer player;
    private boolean active;
    private int position;
    VideoBackdrop(Context c) {
        super(c); setFocusable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        getHolder().addCallback(this);
        getHolder().setFixedSize(1920, 1080);
    }
    void resumeVideo() { active = true; open(); }
    // Queried only by dumpsys for validation, never polled during playback.
    String describe() {
        if (player == null) return "released active="+active;
        try { return "playing="+player.isPlaying()+" position="+player.getCurrentPosition()+" duration="+player.getDuration()+" metrics="+player.getMetrics(); }
        catch (IllegalStateException e) { return "preparing active="+active; }
    }
    void pauseVideo() {
        active = false;
        if (player != null) {
            try { position = player.getCurrentPosition(); } catch (IllegalStateException ignored) {}
            player.release(); player = null;
        }
    }
    private void open() {
        if (!active || player != null || !getHolder().getSurface().isValid()) return;
        final MediaPlayer p = new MediaPlayer(); player = p;
        try (AssetFileDescriptor a = getResources().openRawResourceFd(R.raw.neural)) {
            p.setDataSource(a.getFileDescriptor(), a.getStartOffset(), a.getLength());
            p.setDisplay(getHolder()); p.setVolume(0f, 0f); p.setLooping(true);
            p.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            p.setOnPreparedListener(mp -> {
                if (player != mp || !active) return;
                if (position > 0) mp.seekTo(position, MediaPlayer.SEEK_CLOSEST);
                mp.start(); Log.i("NuzairHome", "Wallpaper playing");
            });
            p.setOnErrorListener((mp, what, extra) -> {
                Log.w("NuzairHome", "Wallpaper error " + what + "/" + extra);
                if (player == mp) { mp.release(); player = null; }
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
