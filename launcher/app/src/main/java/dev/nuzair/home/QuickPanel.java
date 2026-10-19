package dev.nuzair.home;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.*;
import android.os.IBinder;
import android.provider.Settings;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** A lightweight, on-demand control panel; system settings retain hardware ownership. */
final class QuickPanel {
    private static QuickPanel current;
    private final Context context; private final WindowManager wm; private final SharedPreferences prefs;
    private final ArrayList<Card> cards=new ArrayList<>();
    private FrameLayout root,body; private TextView heading; private float scale; private boolean added; private String page="main"; private int selected;
    static boolean visible(){return current!=null;}
    static String describe(){return current==null?"closed":current.page+" focus="+current.selected+" rows="+current.cards.size();}
    static void toggle(Context c,IBinder token){if(current!=null){hide();return;}QuickPanel next=new QuickPanel(c,token);if(next.added)current=next;}
    static void hide(){if(current!=null){QuickPanel old=current;current=null;try{old.wm.removeViewImmediate(old.root);}catch(RuntimeException ignored){}}}
    private QuickPanel(Context c,IBinder token){
        context=c;wm=c.getSystemService(WindowManager.class);prefs=c.getSharedPreferences("home",Context.MODE_PRIVATE);
        DisplayMetrics dm=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);scale=dm.heightPixels/1080f;
        root=new FrameLayout(c){@Override public boolean dispatchKeyEvent(KeyEvent e){
            int key=e.getKeyCode();boolean up=e.getAction()==KeyEvent.ACTION_UP;
            if(key==KeyEvent.KEYCODE_BACK){if(up){if(page.equals("main"))hide();else if(page.equals("screensaver")){showAppearance();focus(2);}else{boolean appearance=page.equals("appearance");showMain();if(appearance)focus(5);}}return true;}
            if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_SETTINGS){if(up)hide();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(up&&!e.isCanceled()&&!cards.isEmpty())cards.get(selected).action.run();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(!up)move(key);return true;}
            return super.dispatchKeyEvent(e);
        }};
        GradientDrawable backdrop=new GradientDrawable();backdrop.setColor(0xFF121316);backdrop.setCornerRadius(px(30));backdrop.setStroke(px(1),0xFF2C2E32);root.setBackground(backdrop);root.setClipToOutline(true);root.setFocusableInTouchMode(true);
        heading=label("Settings",34,0xFFF0F2F5);place(root,heading,36,38,330,50);
        TextView clock=label(new SimpleDateFormat(android.text.format.DateFormat.is24HourFormat(c)?"HH:mm":"h:mm a",Locale.getDefault()).format(new Date()),22,0xFF92969D);clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(root,clock,352,42,130,42);
        body=new FrameLayout(c);place(root,body,24,124,472,740);
        TextView footer=label("CustomTV v0.1",17,0xFF72777F);place(root,footer,40,902,440,28);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(px(520),px(960),token==null?WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY:WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,WindowManager.LayoutParams.FLAG_DIM_BEHIND,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.RIGHT|Gravity.CENTER_VERTICAL;p.x=px(36);p.dimAmount=.14f;p.token=token;p.setTitle("CustomTV quick settings");p.windowAnimations=0;
        try{wm.addView(root,p);added=true;showMain();}catch(RuntimeException e){Log.e("CustomTV","Sidebar failed",e);Toast.makeText(c,"Unable to open controls",Toast.LENGTH_SHORT).show();}
    }
    private int px(float value){return Math.round(value*scale);}
    private void place(FrameLayout parent,View view,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(px(w),px(h));p.leftMargin=px(x);p.topMargin=px(y);parent.addView(view,p);}
    private TextView label(String text,int size,int color){TextView t=new TextView(context);t.setText(text);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));t.setTextColor(color);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private void begin(String name,String title){page=name;heading.setText(title);body.removeAllViews();cards.clear();selected=0;}
    private void focus(int index){if(cards.isEmpty())return;selected=Math.max(0,Math.min(cards.size()-1,index));cards.get(selected).requestFocus();for(Card c:cards)c.invalidate();}
    private void move(int key){
        if(cards.isEmpty())return;Card from=cards.get(selected);int target=selected;double best=Double.MAX_VALUE;
        for(int n=0;n<cards.size();n++){Card to=cards.get(n);float dx=to.cx-from.cx,dy=to.cy-from.cy;boolean vertical=key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;
            float primary=vertical?dy:dx,cross=vertical?dx:dy;if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_LEFT)primary=-primary;
            if(primary<=1||Math.abs(cross)>primary*1.5f)continue;double score=primary+Math.abs(cross)*4;if(score<best){best=score;target=n;}}
        focus(target);
    }
    private void card(String title,String subtitle,String glyph,Runnable action,int x,int y,int w,int h){Card c=new Card(title,subtitle,glyph,action,x+w/2f,y+h/2f);cards.add(c);place(body,c,x,y,w,h);}
    private void row(String title,String sub,String glyph,Runnable action){card(title,sub,glyph,action,0,cards.size()*100,472,88);}
    private Intent nativePage(String action){return new Intent(action).setPackage("com.android.tv.settings");}
    private Intent component(String name){return new Intent().setComponent(new ComponentName("com.android.tv.settings",name));}
    private void open(Intent intent){hide();try{context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));}catch(RuntimeException e){Log.w("CustomTV","Control destination failed",e);Toast.makeText(context,"This setting is unavailable",Toast.LENGTH_SHORT).show();}}
    private void showMain(){
        begin("main","Settings");
        row("Inputs","","input",this::showInputs);
        row("Wi-Fi","","wifi",()->open(nativePage(Settings.ACTION_WIFI_SETTINGS)));
        row("Bluetooth","","bluetooth",()->open(component("com.android.tv.settings.slice.SliceActivity").putExtra("slice_uri","content://com.android.tv.settings.accessories.sliceprovider/general")));
        row("Picture","","sun",()->open(nativePage(Settings.ACTION_DISPLAY_SETTINGS)));
        row("Sound","","sound",()->open(nativePage(Settings.ACTION_SOUND_SETTINGS)));
        row("Appearance","","appearance",this::showAppearance);
        row("All settings","","settings",()->open(nativePage(Settings.ACTION_SETTINGS)));
        focus(0);
    }
    private void showScreensaver(){begin("screensaver","Screensaver");row("Start now","Video & clock","screen",()->open(new Intent(context,MainActivity.class).setAction(MainActivity.ACTION_AMBIENT)));row("System screensaver","TV screensaver settings","settings",()->open(component("com.android.tv.settings.device.display.daydream.DaydreamActivity")));focus(0);}
    private void showInputs(){
        begin("inputs","Input source");TvInputManager manager=context.getSystemService(TvInputManager.class);
        if(manager!=null){List<TvInputInfo> all=manager.getTvInputList(),physical=new ArrayList<>();for(TvInputInfo i:all)if(i.getType()==TvInputInfo.TYPE_HDMI&&i.getParentId()==null)physical.add(i);physical.sort(Comparator.comparing(i->String.valueOf(i.loadLabel(context))));
            for(TvInputInfo i:physical){String label=String.valueOf(i.loadLabel(context)),device="",target=i.getId();for(TvInputInfo child:all)if(i.getId().equals(child.getParentId())){device=String.valueOf(child.loadLabel(context));if(manager.getInputState(child.getId())==TvInputManager.INPUT_STATE_CONNECTED)target=child.getId();}final String id=target;row(label,device,"input",()->open(new Intent(Intent.ACTION_VIEW).setDataAndType(TvContract.buildChannelUriForPassthroughInput(id),"vnd.android.cursor.item/channel")));}}
        row("Input & CEC settings","TV input configuration","settings",()->open(component("com.android.tv.settings.system.InputsActivity")));focus(0);
    }
    private void showAppearance(){
        begin("appearance","Appearance");
        row("Animated background",prefs.getBoolean("wallpaper",true)?"On":"Off","screen",()->{prefs.edit().putBoolean("wallpaper",!prefs.getBoolean("wallpaper",true)).apply();showAppearance();});
        row("Reduce motion",prefs.getBoolean("reduce_motion",false)?"On":"Off","appearance",()->{prefs.edit().putBoolean("reduce_motion",!prefs.getBoolean("reduce_motion",false)).apply();showAppearance();focus(1);});
        row("Screensaver","Video & clock","screen",this::showScreensaver);
        if(context.checkSelfPermission("android.permission.READ_TV_LISTINGS")!=PackageManager.PERMISSION_GRANTED)row("Continue Watching","Allow TV listings","screen",()->open(new Intent(context,MainActivity.class).setAction(MainActivity.ACTION_LISTINGS)));
        focus(0);
    }
    private final class Card extends View {
        final String title,subtitle,glyph;final Runnable action;final float cx,cy;final Paint p=new Paint(3);
        Card(String title,String subtitle,String glyph,Runnable action,float cx,float cy){super(context);this.title=title;this.subtitle=subtitle;this.glyph=glyph;this.action=action;this.cx=cx;this.cy=cy;setFocusable(true);setDefaultFocusHighlightEnabled(false);setContentDescription(title+(subtitle.isEmpty()?"":", "+subtitle));}
        @Override protected void onFocusChanged(boolean on,int direction,Rect r){super.onFocusChanged(on,direction,r);invalidate();}
        @Override protected void onDraw(Canvas raw){super.onDraw(raw);raw.save();raw.scale(scale,scale);float w=getWidth()/scale,h=getHeight()/scale;boolean on=hasFocus();
            p.setStyle(Paint.Style.FILL);
            if(on){p.setColor(0xFF2C2E33);raw.drawRoundRect(1,1,w-1,h-1,18,18,p);}
            raw.save();raw.translate(22,(h-32)/2);drawIcon(raw,glyph);raw.restore();
            p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(25);p.setColor(on?0xFFF5F6F8:0xFFC3C6CC);
            raw.drawText(title,76,subtitle.isEmpty()?h/2+9:38,p);
            if(!subtitle.isEmpty()){p.setTextSize(17);p.setColor(0xFF9298A1);raw.drawText(subtitle,76,65,p);}
            // A quiet chevron, not another card border or persistent highlight.
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(on?0xFFCFD3DA:0xFF555A62);
            if(subtitle.isEmpty()){line(raw,w-31,h/2-5,w-26,h/2);line(raw,w-26,h/2,w-31,h/2+5);}
            raw.restore();
        }
        private void line(Canvas c,float x1,float y1,float x2,float y2){c.drawLine(x1,y1,x2,y2,p);}
        private void drawIcon(Canvas c,String icon){
            p.setColor(0xFFE4E8EE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(icon.equals("wifi")){c.drawArc(-1,3,33,37,220,100,false,p);c.drawArc(5,11,27,33,220,100,false,p);c.drawArc(11,19,21,29,220,100,false,p);c.drawCircle(16,29,1,p);}
            else if(icon.equals("bluetooth")){Path a=new Path();a.moveTo(15,1);a.lineTo(26,10);a.lineTo(7,25);a.moveTo(7,7);a.lineTo(26,22);a.lineTo(15,31);a.close();c.drawPath(a,p);}
            else if(icon.equals("settings")){Path gear=new Path();for(int n=0;n<32;n++){double a=n*Math.PI/16;float r=(n%4==1||n%4==2)?15:12;float x=16+(float)Math.cos(a)*r,y=16+(float)Math.sin(a)*r;if(n==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();c.drawPath(gear,p);c.drawCircle(16,16,5,p);}
            else if(icon.equals("sun")){c.drawCircle(16,16,7,p);for(int n=0;n<8;n++){double a=n*Math.PI/4;line(c,16+(float)Math.cos(a)*12,16+(float)Math.sin(a)*12,16+(float)Math.cos(a)*15,16+(float)Math.sin(a)*15);}}
            else if(icon.equals("sound")){Path a=new Path();a.moveTo(3,12);a.lineTo(10,12);a.lineTo(18,5);a.lineTo(18,27);a.lineTo(10,20);a.lineTo(3,20);a.close();c.drawPath(a,p);c.drawArc(12,5,31,27,-55,110,false,p);}
            else if(icon.equals("appearance")){for(int n=0;n<3;n++){float y=6+n*10;line(c,2,y,30,y);c.drawCircle(n==1?12:22,y,3,p);}}
            else {c.drawRoundRect(1,4,31,25,4,4,p);line(c,11,30,21,30);line(c,16,25,16,30);if(icon.equals("input")){line(c,6,14,24,14);line(c,19,9,24,14);line(c,19,19,24,14);}}
        }
    }
}
