package dev.nuzair.home;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

final class IconStore {
    private final Resources pack;
    private final Context context;
    IconStore(Context c) {
        context=c;pack = c.getResources();
    }
    Drawable app(AppRepository.App app){
        if(app.icon!=null)return get(app.icon);
        try{Drawable d=context.getPackageManager().getApplicationIcon(app.pkg).mutate();if(d instanceof BitmapDrawable)((BitmapDrawable)d).setFilterBitmap(true);ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);d.setColorFilter(new ColorMatrixColorFilter(gray));return d;}
        catch(Exception e){return new Outline("app");}
    }
    Drawable get(String name) {
        if (pack != null) {
            int id = pack.getIdentifier(name,"drawable","dev.nuzair.home");
            if (id != 0) try { return pack.getDrawable(id,null).mutate(); } catch (RuntimeException ignored) {}
        }
        return new Outline(name);
    }
    /** Icons for controls and a graceful outline fallback if the icon pack is removed. */
    private static final class Outline extends Drawable {
        private final Paint p = new Paint(3); private final String name; private int alpha=255;
        Outline(String name) { this.name=name; }
        @Override public void draw(Canvas c) {
            c.save(); c.translate(getBounds().left,getBounds().top); c.scale(getBounds().width()/64f,getBounds().height()/64f);
            p.setColor(0xFFF0F2F5); p.setAlpha(alpha); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            if (name.equals("settings")) {
                c.drawCircle(32,32,9,p); c.drawCircle(32,32,22,p);
                for(int i=0;i<8;i++){c.save();c.rotate(i*45,32,32);c.drawLine(32,5,32,12,p);c.restore();}
            } else if(name.equals("hdmi") || name.equals("gamepad")) {
                c.drawRoundRect(5,10,59,48,7,7,p);c.drawLine(22,56,42,56,p);c.drawLine(32,48,32,56,p);
                if(name.equals("gamepad")){c.drawLine(18,25,18,35,p);c.drawLine(13,30,23,30,p);c.drawCircle(43,26,2,p);c.drawCircle(49,33,2,p);}
                else {c.drawLine(23,22,40,22,p);c.drawLine(40,22,40,37,p);c.drawLine(40,37,23,37,p);}
            } else if(name.equals("youtube")) {
                c.drawRoundRect(3,12,61,52,11,11,p);Path path=new Path();path.moveTo(26,23);path.lineTo(42,32);path.lineTo(26,41);path.close();c.drawPath(path,p);
            } else if(name.equals("spotify")) {
                c.drawCircle(32,32,26,p);for(int i=0;i<3;i++)c.drawArc(12+i*2,21+i*8,52-i*2,42+i*8,210,115,false,p);
            } else if(name.equals("google_play_store")) {
                Path path=new Path();path.moveTo(12,7);path.lineTo(55,32);path.lineTo(12,57);path.close();c.drawPath(path,p);c.drawLine(12,7,41,41,p);c.drawLine(12,57,41,23,p);
            } else if(name.equals("nuvio")) {
                Path path=new Path();path.moveTo(14,51);path.lineTo(14,13);path.lineTo(50,51);path.lineTo(50,13);c.drawPath(path,p);
            } else {c.drawCircle(32,32,24,p);c.drawLine(20,44,42,20,p);}
            c.restore();
        }
        @Override public void setAlpha(int a) { alpha=a; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
