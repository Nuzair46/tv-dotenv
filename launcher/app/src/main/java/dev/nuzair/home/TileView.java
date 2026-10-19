package dev.nuzair.home;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Small native focusable card; it only redraws on focus/data changes. */
final class TileView extends View {
    static final int APP = 0, INPUT = 1, PROGRAM = 2, CONTROL = 3, EMPTY = 4;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path path = new Path();
    private Drawable icon;
    private Bitmap artwork;
    private String title, subtitle = "";
    private final int type;
    private float progress;
    boolean reduceMotion;
    TileView(Context c, String name, int kind, Drawable drawable) {
        super(c); title = name; type = kind; icon = drawable;
        setId(View.generateViewId()); setFocusable(true); setClickable(true);
        setContentDescription(name); setSoundEffectsEnabled(true);
        setDefaultFocusHighlightEnabled(false);
        setOnFocusChangeListener((v, focused) -> {
            animate().cancel(); float scale = focused ? (type == CONTROL ? 1.08f : 1.045f) : 1f;
            if (reduceMotion) { setScaleX(scale); setScaleY(scale); }
            else animate().scaleX(scale).scaleY(scale).setDuration(170).setInterpolator(new DecelerateInterpolator()).start();
            setTranslationZ(focused ? 6f : 0f); invalidate();
        });
    }
    void setSubtitle(String value) { subtitle = value == null ? "" : value; setContentDescription(title + (subtitle.isEmpty() ? "" : ", " + subtitle)); invalidate(); }
    void setArtwork(Bitmap b) { artwork = b; invalidate(); }
    void setProgress(float p) { progress = Math.max(0f, Math.min(1f, p)); invalidate(); }
    private void label(Canvas c, String value, float x, float y, float size, int color, float maxWidth) {
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); text.setTextSize(size); text.setColor(color);
        c.drawText(TextUtils.ellipsize(value, text, Math.max(1,maxWidth), TextUtils.TruncateAt.END).toString(), x, y, text);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), unit = getResources().getDisplayMetrics().widthPixels / 1920f;
        float pad = 2 * unit, radius = (type == CONTROL ? 32 : 22) * unit;
        box.set(pad, pad, w - pad, h - pad);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
        paint.setShader(new LinearGradient(0,0,0,h,
            isFocused() ? 0x3DE9EDF4 : 0x12E9EDF4, isFocused() ? 0x24C5CBD4 : 0x08CBD0D8, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(box,radius,radius,paint); paint.setShader(null);
        if (artwork != null && type == PROGRAM) {
            canvas.save(); path.reset(); path.addRoundRect(box,radius,radius,Path.Direction.CW); canvas.clipPath(path);
            float ratio = Math.max(w / artwork.getWidth(), h / artwork.getHeight());
            float bw = artwork.getWidth()*ratio, bh = artwork.getHeight()*ratio;
            canvas.drawBitmap(artwork, null, new RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2), paint);
            paint.setShader(new LinearGradient(0,h*.2f,0,h,new int[]{0x00000000,0xE9000000},null,Shader.TileMode.CLAMP));
            canvas.drawRect(box,paint); paint.setShader(null); canvas.restore();
        }
        paint.setColor(isFocused() ? 0xC2F0F3F7 : 0x1FFFFFFF); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((isFocused() ? 2.3f : 1f)*unit); canvas.drawRoundRect(box,radius,radius,paint); paint.setStyle(Paint.Style.FILL);
        if (icon != null) {
            float size = type == APP ? 108*unit : type == CONTROL ? 29*unit : type == INPUT ? 32*unit : 45*unit;
            float x = type == INPUT ? 30*unit : (w-size)/2;
            float y = (h-size)/2;
            icon.setBounds(Math.round(x),Math.round(y),Math.round(x+size),Math.round(y+size)); icon.setAlpha(isFocused()?255:224); icon.draw(canvas);
        }
        if (type == APP && icon == null) label(canvas,title,24*unit,h/2+8*unit,28*unit,0xFFF0F1F3,w-48*unit);
        if (type == INPUT) {
            label(canvas,title,82*unit,subtitle.isEmpty()?h/2+8*unit:h/2-3*unit,25*unit,0xFFF0F1F3,w-105*unit);
            if (!subtitle.isEmpty()) label(canvas,subtitle,82*unit,h/2+23*unit,18*unit,0xFFAFB5BF,w-105*unit);
        }
        if (type == PROGRAM) {
            label(canvas,title,18*unit,h-39*unit,24*unit,0xFFF4F5F7,w-36*unit);
            label(canvas,subtitle,18*unit,h-15*unit,17*unit,0xFFBFC4CC,w-36*unit);
            if (progress > 0f) {
                paint.setColor(0x66FFFFFF); canvas.drawRoundRect(18*unit,h-7*unit,w-18*unit,h-4*unit,2,2,paint);
                paint.setColor(0xFFF0F2F5); canvas.drawRoundRect(18*unit,h-7*unit,18*unit+(w-36*unit)*progress,h-4*unit,2,2,paint);
            }
        }
        if (type == EMPTY) {
            label(canvas,title,32*unit,h/2-3*unit,28*unit,0xFFE8EBEF,w-64*unit);
            label(canvas,subtitle,32*unit,h/2+31*unit,21*unit,0xFF9EA4AD,w-64*unit);
        }
    }
}
