package dev.nuzair.home;

import android.content.*;
import android.content.pm.*;
import java.util.*;

/** Discover enabled TV apps on package changes, keeping the existing favorites first. */
final class AppRepository {
    static final String[] PINNED={"com.nuvio.tv","app.smarttube.fdroid","com.spotify.tv.android","com.valvesoftware.steamlink","com.android.vending"};
    static final String[] ICONS={"nuvio","youtube","spotify","steamlink","google_play_store"};
    static final class App {
        final String pkg,label,icon;final ComponentName component;
        App(ResolveInfo r,String icon,PackageManager pm){pkg=r.activityInfo.packageName;label=r.loadLabel(pm).toString();component=new ComponentName(pkg,r.activityInfo.name);this.icon=icon;}
        Intent intent(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);}
    }
    static List<App> load(Context context){
        PackageManager pm=context.getPackageManager();Map<String,ResolveInfo> found=new HashMap<>();
        for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0)){
            String pkg=r.activityInfo.packageName;
            if(pkg.equals(context.getPackageName())||!r.activityInfo.enabled||!r.activityInfo.applicationInfo.enabled)continue;
            try{if(pm.isPackageSuspended(pkg))continue;}catch(Exception ignored){}
            found.putIfAbsent(pkg,r);
        }
        List<App> result=new ArrayList<>();
        for(int n=0;n<PINNED.length;n++){ResolveInfo r=found.remove(PINNED[n]);if(r!=null)result.add(new App(r,ICONS[n],pm));}
        List<App> added=new ArrayList<>();
        for(ResolveInfo r:found.values()){
            // Hardware/settings utilities and factory promotional clients belong outside the app row.
            // Newly installed user TV apps appear automatically without maintaining another allowlist.
            if((r.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0)continue;
            added.add(new App(r,null,pm));
        }
        added.sort(Comparator.comparing(a->a.label.toLowerCase(Locale.ROOT)));result.addAll(added);return result;
    }
}
