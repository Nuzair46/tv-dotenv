package dev.nuzair.home;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.Context;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.view.accessibility.AccessibilityEvent;

/** Event-driven Home routing. No polling, screen inspection, gestures or app automation. */
public final class HomeOverrideService extends AccessibilityService {
    private static HomeOverrideService connected;
    private long lastLaunch;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Override protected void onServiceConnected() {
        connected=this;
        AccessibilityServiceInfo info=getServiceInfo();
        info.flags|=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        Log.i("CustomTV","Remote filter connected");
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        android.content.SharedPreferences state = getSharedPreferences("home", MODE_PRIVATE);
        // Cover a boot where the stock Home appeared before accessibility connected.
        // A later service reconnect in the same boot must not interrupt another app.
        if (state.getInt("override_boot", -2) != boot) {
            state.edit().putInt("override_boot", boot).apply();
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            if (power != null && power.isInteractive()) handler.postDelayed(this::showHome, 1200);
        }
    }
    private void showHome() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < 650) return;
        lastLaunch = now;
        dispatchHome();
    }
    private void dispatchHome() {
        QuickPanel.hide();
        Intent i = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(i); Log.i("CustomTV", "Home dispatched"); }
        catch (RuntimeException e) { Log.w("CustomTV", "Home dispatch failed", e); }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!"com.google.android.apps.tv.launcherx".contentEquals(e.getPackageName() == null ? "" : e.getPackageName())) return;
        // The stock package also owns the remote's quick-settings dashboard.
        // Redirect its HOME activity only; leave dashboards and dialogs usable.
        // Stock Home can launch itself again ~100 ms after our first redirect.
        // Do not apply the physical-button debounce to a new stock window event.
        if ("com.google.android.apps.tv.launcherx.home.HomeActivity".contentEquals(e.getClassName() == null ? "" : e.getClassName())) dispatchHome();
    }
    @Override protected boolean onKeyEvent(KeyEvent e) {
        int key=e.getKeyCode();
        if(key==KeyEvent.KEYCODE_POWER){handler.post(QuickPanel::hide);return false;}
        if(key==KeyEvent.KEYCODE_HOME){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)handler.post(this::showHome);return true;}
        if(key==KeyEvent.KEYCODE_SETTINGS){if(e.getAction()==KeyEvent.ACTION_UP&&!e.isCanceled())handler.post(()->{QuickPanel.toggle(this,null);Log.i("CustomTV","Remote gear: sidebar");});return true;}
        String target=null;
        if(key==KeyEvent.KEYCODE_ALL_APPS)target="com.android.vending";
        InputDevice device=e.getDevice();
        boolean remote=device!=null&&device.getVendorId()==0x2717&&device.getProductId()==0x32b9;
        // These codes were captured from the user's physical Xiaomi RC, not guessed.
        if(remote&&(key==193||key==194))target="com.nuvio.tv";
        if(remote&&key==190)target="app.smarttube.fdroid";
        if(target==null)return false;
        final String app=target;
        if(e.getAction()==KeyEvent.ACTION_UP&&!e.isCanceled())handler.post(()->{
            QuickPanel.hide();Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(app);
            try{if(i!=null){startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Log.i("CustomTV","Remote key "+key+": "+app);}}
            catch(RuntimeException ex){Log.w("CustomTV","Remote shortcut unavailable",ex);}
        });return true;
    }
    static void openPanel(Context activity,IBinder token){if(connected!=null)QuickPanel.toggle(connected,null);else QuickPanel.toggle(activity,token);}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {if(connected==this)connected=null;QuickPanel.hide();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
