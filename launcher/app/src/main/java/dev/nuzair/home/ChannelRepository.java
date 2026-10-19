package dev.nuzair.home;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.util.LruCache;
import java.io.*;
import java.net.*;
import java.util.*;

final class ChannelRepository {
    static final Set<String> ALLOWED = new HashSet<>(Arrays.asList("com.nuvio.tv", "app.smarttube.fdroid", "com.spotify.tv.android", "com.valvesoftware.steamlink"));
    static final class Item {
        String title, pkg, intent, image, description, key;
        long position, duration, engagement, order;
        String subtitle() { return "com.nuvio.tv".equals(pkg) ? "Nuvio" : "app.smarttube.fdroid".equals(pkg) ? "SmartTube" : "com.spotify.tv.android".equals(pkg) ? "Spotify" : "Steam Link"; }
    }
    private final Context context;
    private final LruCache<String,Bitmap> images = new LruCache<String,Bitmap>(6*1024*1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getAllocationByteCount(); }
    };
    boolean denied;
    ChannelRepository(Context c) { context=c.getApplicationContext(); }
    private static String str(Cursor c,String k) { int i=c.getColumnIndex(k);return i<0||c.isNull(i)?"":c.getString(i); }
    private static long number(Cursor c,String k) { int i=c.getColumnIndex(k);return i<0||c.isNull(i)?0:c.getLong(i); }
    List<Item> load() {
        denied=false; LinkedHashMap<String,Item> result=new LinkedHashMap<>();
        for(String table:new String[]{"watch_next_program","preview_program"}) {
            try(Cursor c=context.getContentResolver().query(Uri.parse("content://android.media.tv/"+table),null,null,null,null)) {
                if(c==null)continue;
                while(c.moveToNext()) {
                    String pkg=str(c,"package_name"); if(!ALLOWED.contains(pkg))continue;
                    // Nuvio's preview channel is its Continue Watching feed. Other apps must use Watch Next.
                    if(table.equals("preview_program")&&!pkg.equals("com.nuvio.tv"))continue;
                    Item p=new Item();p.pkg=pkg;p.title=str(c,"title");p.intent=str(c,"intent_uri");p.image=str(c,"poster_art_uri");
                    if(p.image.isEmpty())p.image=str(c,"thumbnail_uri");
                    p.position=number(c,"last_playback_position_millis");p.duration=number(c,"duration_millis");p.engagement=number(c,"last_engagement_time_utc_millis");p.order=number(c,"weight");
                    if(p.title.isEmpty()||p.intent.isEmpty())continue;
                    if(p.duration>0 && (double)p.position/p.duration>=0.99)continue;
                    p.key=pkg+":"+p.intent; result.putIfAbsent(p.key,p);
                }
            } catch(SecurityException e) { denied=true; Log.w("NuzairHome","TV listings permission unavailable"); }
            catch(Exception e) { Log.w("NuzairHome","Unable to read "+table,e); }
        }
        ArrayList<Item> list=new ArrayList<>(result.values());
        list.sort((a,b)->a.engagement==b.engagement?Long.compare(b.order,a.order):Long.compare(b.engagement,a.engagement));
        if(list.size()>25)list=new ArrayList<>(list.subList(0,25));
        Log.i("NuzairHome","Continue watching: "+list.size()+" items; permissionDenied="+denied);
        return list;
    }
    static Intent intentFor(Item item) throws java.net.URISyntaxException {
        if(!ALLOWED.contains(item.pkg))throw new SecurityException("Untrusted provider");
        Intent i=Intent.parseUri(item.intent,Intent.URI_INTENT_SCHEME);
        if(i.getComponent()!=null && !item.pkg.equals(i.getComponent().getPackageName()))throw new SecurityException("Cross-package link");
        i.setSelector(null);i.setClipData(null);i.setPackage(item.pkg);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); return i;
    }
    Bitmap artwork(String value) {
        if(value==null||value.isEmpty())return null;
        Bitmap cached=images.get(value); if(cached!=null)return cached;
        try {
            Uri uri=Uri.parse(value);byte[] bytes;
            if("https".equals(uri.getScheme())) {
                URL url=new URL(value); if(url.getUserInfo()!=null)return null;
                HttpURLConnection con=(HttpURLConnection)url.openConnection();con.setConnectTimeout(4000);con.setReadTimeout(4000);con.setInstanceFollowRedirects(false);
                try {if(con.getResponseCode()!=200)return null;try(InputStream in=con.getInputStream()){bytes=readLimited(in);}}finally{con.disconnect();}
            } else if("content".equals(uri.getScheme())) {
                try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)return null;bytes=readLimited(in);}
            } else return null;
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
            if(o.outWidth<=0||o.outHeight<=0)return null;
            o.inSampleSize=1;while(o.outWidth/o.inSampleSize>720 || o.outHeight/o.inSampleSize>480)o.inSampleSize*=2;
            o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
            Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(b!=null)images.put(value,b);return b;
        }catch(Exception e){Log.d("NuzairHome","Artwork not available");return null;}
    }
    private static byte[] readLimited(InputStream in)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>4*1024*1024)throw new IOException("Artwork too large");out.write(buf,0,n);}return out.toByteArray();
    }
    void trim() { images.evictAll(); }
}
