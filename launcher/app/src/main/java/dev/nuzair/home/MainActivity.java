package dev.nuzair.home;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    static final String ACTION_AMBIENT="dev.nuzair.home.AMBIENT",ACTION_LISTINGS="dev.nuzair.home.LISTINGS";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService queries=Executors.newSingleThreadExecutor(), images=Executors.newFixedThreadPool(2);
    private final ArrayList<ArrayList<TileView>> rows=new ArrayList<>();
    private List<AppRepository.App> apps=new ArrayList<>();
    private List<ChannelRepository.Item> programs=new ArrayList<>();
    private List<String> inputIds=new ArrayList<>();
    private int page,appPage,appGeneration,generation,lastRow=1,lastColumn=0,wakeKey=-1;
    private float scale=1;
    private FrameLayout root,chrome,appLayer,inputLayer,programLayer;
    private TextView clock,date,watchHeading,appHeading;
    private VideoBackdrop video;
    private IconStore icons;
    private ChannelRepository channels;
    private SharedPreferences prefs;
    private boolean foreground,ambient,observerRegistered,appsLoaded;
    private TvInputManager inputManager;
    private final Runnable idle=()->{if(!QuickPanel.visible())setAmbient(true);else resetIdle();};
    private final Runnable reload=()->refreshChannels();
    private final Runnable tick=new Runnable(){public void run(){updateClock();if(foreground)handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    private final ContentObserver observer=new ContentObserver(handler){@Override public void onChange(boolean self){handler.removeCallbacks(reload);handler.postDelayed(reload,600);}};
    private final BroadcastReceiver packages=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(foreground)refreshApps();}};
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences=(p,key)->{
        if(key.equals("wallpaper")){if(foreground&&p.getBoolean(key,true)){video.setVisibility(View.VISIBLE);video.resumeVideo();}else{video.pauseVideo();video.setVisibility(View.GONE);}}
        if(key.equals("reduce_motion"))for(ArrayList<TileView> row:rows)for(TileView t:row)t.reduceMotion=p.getBoolean(key,false);
    };
    private final TvInputManager.TvInputCallback inputCallback=new TvInputManager.TvInputCallback(){
        @Override public void onInputStateChanged(String id,int state){refreshInputs();}
        @Override public void onInputAdded(String id){refreshInputs();}
        @Override public void onInputRemoved(String id){refreshInputs();}
        @Override public void onInputUpdated(String id){refreshInputs();}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("home",MODE_PRIVATE);icons=new IconStore(this);channels=new ChannelRepository(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        DisplayMetrics dm=new DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(dm);scale=Math.min(dm.widthPixels/1920f,dm.heightPixels/1080f);
        root=new FrameLayout(this);root.setBackgroundColor(0xFF080808);root.setClipChildren(false);root.setClipToPadding(false);
        video=new VideoBackdrop(this);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x29000000,0x00000000,0x45000000}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        chrome=new FrameLayout(this);chrome.setClipChildren(false);chrome.setClipToPadding(false);root.addView(chrome,new FrameLayout.LayoutParams(-1,-1));
        for(int i=0;i<4;i++)rows.add(new ArrayList<>());
        date=text("",22,0xFFAAB0B8,false);place(chrome,date,104,65,1000,36);
        place(chrome,text("Home",64,0xFFF3F4F6,false),100,103,800,86);
        clock=text("",32,0xFFE5E8ED,false);clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(root,clock,1505,65,230,50);
        appHeading=heading("Apps",104,246);appLayer=new FrameLayout(this);appLayer.setClipChildren(false);place(chrome,appLayer,0,300,1920,174);
        heading("Inputs",104,583);inputLayer=new FrameLayout(this);inputLayer.setClipChildren(false);place(chrome,inputLayer,0,632,1920,86);
        watchHeading=heading("Continue Watching",104,774);programLayer=new FrameLayout(this);programLayer.setClipChildren(false);place(chrome,programLayer,0,825,1920,166);
        setContentView(root);inputManager=(TvInputManager)getSystemService(TV_INPUT_SERVICE);refreshInputs();showPrograms();
        IntentFilter packageFilter=new IntentFilter();for(String a:new String[]{Intent.ACTION_PACKAGE_ADDED,Intent.ACTION_PACKAGE_REMOVED,Intent.ACTION_PACKAGE_CHANGED,Intent.ACTION_PACKAGE_REPLACED})packageFilter.addAction(a);packageFilter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packages,packageFilter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(packages,packageFilter);
        prefs.registerOnSharedPreferenceChangeListener(preferences);updateClock();
        Log.i("CustomTV","Created; dynamic TV apps; scale="+scale);
    }
    private int px(float design){return Math.round(design*scale);}
    private void place(FrameLayout parent,View v,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(px(w),px(h));p.leftMargin=px(x);p.topMargin=px(y);parent.addView(v,p);}
    private TextView text(String value,int size,int color,boolean medium){TextView t=new TextView(this);t.setText(value);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*scale);t.setTextColor(color);t.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);t.setFocusable(false);return t;}
    private TextView heading(String title,int x,int y){TextView t=text(title,28,0xFFE0E4EA,true);place(chrome,t,x,y,1450,44);return t;}
    private TileView tile(String title,int kind,String icon){TileView t=new TileView(this,title,kind,icon==null?null:icons.get(icon));t.reduceMotion=prefs.getBoolean("reduce_motion",false);return t;}
    private void updateClock(){if(clock==null)return;Date now=new Date();clock.setText(new SimpleDateFormat(android.text.format.DateFormat.is24HourFormat(this)?"HH:mm":"h:mm a",Locale.getDefault()).format(now));date.setText(new SimpleDateFormat("EEEE, d MMMM",Locale.ENGLISH).format(now).toUpperCase(Locale.ENGLISH));}
    @Override protected void onStart(){
        super.onStart();foreground=true;
        if(inputManager!=null)inputManager.registerCallback(inputCallback,handler);
        try{getContentResolver().registerContentObserver(Uri.parse("content://android.media.tv"),true,observer);observerRegistered=true;}catch(SecurityException ignored){}
    }
    @Override protected void onResume(){
        super.onResume();foreground=true;setAmbient(false);
        if(prefs.getBoolean("wallpaper",true)){video.setVisibility(View.VISIBLE);video.resumeVideo();}else video.setVisibility(View.GONE);
        handler.removeCallbacks(tick);tick.run();refreshApps();refreshInputs();refreshChannels();resetIdle();handleAction();
    }
    @Override protected void onPause(){video.pauseVideo();handler.removeCallbacks(idle);super.onPause();}
    @Override protected void onStop(){
        foreground=false;generation++;handler.removeCallbacks(tick);handler.removeCallbacks(idle);handler.removeCallbacks(reload);
        if(observerRegistered){getContentResolver().unregisterContentObserver(observer);observerRegistered=false;}
        if(inputManager!=null)inputManager.unregisterCallback(inputCallback);video.pauseVideo();super.onStop();
    }
    @Override protected void onDestroy(){video.pauseVideo();prefs.unregisterOnSharedPreferenceChangeListener(preferences);unregisterReceiver(packages);queries.shutdownNow();images.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_BACKGROUND)channels.trim();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);setAmbient(false);appPage=0;showApps();focus(1,0);resetIdle();}
    private void handleAction(){String action=getIntent().getAction();if(ACTION_AMBIENT.equals(action)){setAmbient(true);getIntent().setAction(Intent.ACTION_MAIN);}else if(ACTION_LISTINGS.equals(action)){requestListings();getIntent().setAction(Intent.ACTION_MAIN);}}
    @Override public void onUserInteraction(){super.onUserInteraction();resetIdle();}
    private void resetIdle(){handler.removeCallbacks(idle);if(foreground)handler.postDelayed(idle,120000);}
    private void setAmbient(boolean enabled){
        boolean wasAmbient=ambient;
        if(enabled&&!wasAmbient){int[] at=current();lastRow=at[0];lastColumn=at[1];}
        ambient=enabled;chrome.animate().cancel();chrome.setAlpha(enabled?0f:1f);chrome.setVisibility(enabled?View.INVISIBLE:View.VISIBLE);clock.setAlpha(enabled?.8f:1f);
        if(!enabled&&wasAmbient)focus(lastRow,lastColumn);
    }
    private int[] current(){View v=getCurrentFocus();for(int r=0;r<rows.size();r++){int c=rows.get(r).indexOf(v);if(c>=0)return new int[]{r,c};}return new int[]{lastRow,lastColumn};}
    private void focus(int r,int c){if(rows.get(r).isEmpty())return;lastRow=r;lastColumn=Math.min(Math.max(0,c),rows.get(r).size()-1);rows.get(r).get(lastColumn).requestFocus();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        int key=e.getKeyCode();
        if(wakeKey==key){if(e.getAction()==KeyEvent.ACTION_UP)wakeKey=-1;return true;}
        if(ambient&&e.getAction()==KeyEvent.ACTION_DOWN&&key!=KeyEvent.KEYCODE_VOLUME_UP&&key!=KeyEvent.KEYCODE_VOLUME_DOWN&&key!=KeyEvent.KEYCODE_VOLUME_MUTE){setAmbient(false);wakeKey=key;resetIdle();return true;}
        if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_SETTINGS){if(e.getAction()==KeyEvent.ACTION_UP&&!e.isCanceled())HomeOverrideService.openPanel(this,getWindow().getDecorView().getWindowToken());return true;}
        if(e.getAction()==KeyEvent.ACTION_DOWN){
            resetIdle();int[] at=current();int r=at[0],c=at[1];
            if(key==KeyEvent.KEYCODE_BACK){appPage=0;showApps();focus(1,0);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
                int next=r+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1);if(next>=0&&next<rows.size()){if(rows.get(next).isEmpty())next+=key==KeyEvent.KEYCODE_DPAD_UP?-1:1;if(next>=0&&next<rows.size())focus(next,c);}return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){
                int next=c+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);
                if(r==1&&next>=rows.get(r).size()&&(appPage+1)*5<apps.size()){appPage++;showApps();focus(1,0);}
                else if(r==1&&next<0&&appPage>0){appPage--;showApps();focus(1,4);}
                else if(r==3&&next>=rows.get(r).size()&&(page+1)*5<programs.size()){page++;showPrograms();focus(3,0);}
                else if(r==3&&next<0&&page>0){page--;showPrograms();focus(3,4);}
                else if(next>=0&&next<rows.get(r).size())focus(r,next);return true;
            }
        }
        if(e.getAction()==KeyEvent.ACTION_UP&&(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT))return true;
        return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed(){focus(1,0);}
    private void refreshApps(){
        if(queries.isShutdown())return;final int stamp=++appGeneration;
        queries.execute(()->{List<AppRepository.App> found=AppRepository.load(this);handler.post(()->{
            if(!foreground||stamp!=appGeneration)return;int[] at=current();apps=found;appPage=Math.min(appPage,Math.max(0,(apps.size()-1)/5));showApps();if(!appsLoaded){appsLoaded=true;focus(1,0);}else if(at[0]==1)focus(1,at[1]);
        });});
    }
    private void showApps(){
        if(appLayer==null)return;appLayer.removeAllViews();rows.get(1).clear();appHeading.setText("Apps"+(apps.size()>5?"     "+(appPage+1)+" / "+((apps.size()+4)/5):""));
        for(int n=0;n<5&&appPage*5+n<apps.size();n++){
            AppRepository.App app=apps.get(appPage*5+n);TileView t=new TileView(this,app.label,TileView.APP,icons.app(app));t.reduceMotion=prefs.getBoolean("reduce_motion",false);
            t.setOnClickListener(v->open(app.intent()));rows.get(1).add(t);place(appLayer,t,104+n*345,0,310,174);
        }
    }
    private void launchApp(String pkg){Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(i==null)i=getPackageManager().getLaunchIntentForPackage(pkg);if(i==null){Toast.makeText(this,"App is not available",Toast.LENGTH_SHORT).show();return;}open(i);}
    private void open(Intent i){try{startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException e){Log.w("NuzairHome","Unable to launch activity",e);Toast.makeText(this,"This action is unavailable. TV settings are still accessible from the gear.",Toast.LENGTH_LONG).show();}}
    private void refreshInputs(){
        if(inputManager==null||inputLayer==null)return;int[] at=current();
        try{
            List<TvInputInfo> all=inputManager.getTvInputList(),physical=new ArrayList<>();
            for(TvInputInfo info:all)if(info.getType()==TvInputInfo.TYPE_HDMI&&info.getParentId()==null)physical.add(info);
            physical.sort(Comparator.comparing(i->String.valueOf(i.loadLabel(this))));
            inputLayer.removeAllViews();rows.get(2).clear();inputIds.clear();
            for(int n=0;n<physical.size()&&n<3;n++){
                TvInputInfo info=physical.get(n);String label=String.valueOf(info.loadLabel(this)),device="";String target=info.getId();
                for(TvInputInfo child:all)if(info.getId().equals(child.getParentId())){device=String.valueOf(child.loadLabel(this));if(inputManager.getInputState(child.getId())==TvInputManager.INPUT_STATE_CONNECTED)target=child.getId();}
                boolean connected=inputManager.getInputState(info.getId())==TvInputManager.INPUT_STATE_CONNECTED;
                boolean ps5=device.toLowerCase(Locale.ROOT).contains("playstation");String title=device.isEmpty()?label:device;
                TileView t=tile(title,TileView.INPUT,ps5?"gamepad":"hdmi");t.setSubtitle(device.isEmpty()?(connected?"Connected":"Available"):label+" · "+(connected?"Connected":"Available"));
                final String input=target;t.setOnClickListener(v->open(new Intent(Intent.ACTION_VIEW).setDataAndType(TvContract.buildChannelUriForPassthroughInput(input),"vnd.android.cursor.item/channel")));
                rows.get(2).add(t);inputIds.add(target);place(inputLayer,t,104+n*575,0,540,84);
            }
            if(rows.get(2).isEmpty()){
                TileView t=tile("Input settings",TileView.INPUT,"hdmi");t.setSubtitle("Open TV settings");t.setOnClickListener(v->open(new Intent(Settings.ACTION_SETTINGS)));rows.get(2).add(t);place(inputLayer,t,104,0,540,84);
            }
            if(at[0]==2)focus(2,at[1]);
        }catch(RuntimeException e){Log.w("NuzairHome","HDMI enumeration failed",e);}
    }
    private void refreshChannels(){
        if(!foreground||queries.isShutdown())return;final int stamp=++generation;
        queries.execute(()->{List<ChannelRepository.Item> loaded=channels.load();handler.post(()->{
            if(!foreground||stamp!=generation)return;int[] at=current();programs=loaded;page=Math.min(page,Math.max(0,(programs.size()-1)/5));showPrograms();if(at[0]==3)focus(3,at[1]);
        });});
    }
    private void showPrograms(){
        if(programLayer==null)return;programLayer.removeAllViews();rows.get(3).clear();
        watchHeading.setText("Continue Watching"+(programs.size()>5?"     "+(page+1)+" / "+((programs.size()+4)/5):""));
        if(programs.isEmpty()){
            TileView t=tile("Continue in Nuvio",TileView.EMPTY,null);
            t.setSubtitle(channels.denied?"Allow TV listings access in launcher settings":"In-progress titles appear here when Nuvio shares them");
            t.setOnClickListener(v->{if(channels.denied)requestListings();else launchApp("com.nuvio.tv");});rows.get(3).add(t);place(programLayer,t,104,0,1690,166);return;
        }
        final int stamp=generation;
        for(int j=0;j<5&&page*5+j<programs.size();j++){
            ChannelRepository.Item item=programs.get(page*5+j);TileView t=tile(item.title,TileView.PROGRAM,null);t.setSubtitle(item.subtitle());
            if(item.duration>0)t.setProgress((float)item.position/item.duration);
            t.setOnClickListener(v->{try{open(ChannelRepository.intentFor(item));}catch(Exception e){launchApp(item.pkg);}});
            rows.get(3).add(t);place(programLayer,t,104+j*345,0,310,166);
            if(!images.isShutdown())images.execute(()->{Bitmap b=channels.artwork(item.image);if(b!=null)handler.post(()->{if(foreground&&stamp==generation&&t.getParent()==programLayer)t.setArtwork(b);});});
        }
    }
    private void requestListings(){if(checkSelfPermission("android.permission.READ_TV_LISTINGS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},10);else refreshChannels();}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==10)refreshChannels();}
    @Override public void dump(String prefix,FileDescriptor fd,PrintWriter out,String[] args){
        super.dump(prefix,fd,out,args);int[] at=current();
        out.println(prefix+"NUZAIR foreground="+foreground+" ambient="+ambient+" focus="+at[0]+":"+at[1]+" page="+page+" programs="+programs.size()+" inputs="+inputIds.size()+" apps="+apps.size()+" appPage="+appPage+" wallpaper="+prefs.getBoolean("wallpaper",true));
        for(String id:inputIds)out.println(prefix+"NUZAIR input="+id);
        out.println(prefix+"CustomTV sidebar="+QuickPanel.describe()+" reduce_motion="+prefs.getBoolean("reduce_motion",false));
        out.println(prefix+"CustomTV video="+video.describe());
        for(AppRepository.App app:apps)out.println(prefix+"CustomTV app="+app.pkg);
    }
}
