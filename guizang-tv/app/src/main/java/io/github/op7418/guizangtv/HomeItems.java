package io.github.op7418.guizangtv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import org.json.JSONArray;
import java.util.*;

/** Stable app/source identities; removing an entry never uninstalls an app. */
final class HomeItems {
    private static final int MAX_ITEMS = 9;
    final Context context;
    final DeviceProfile profile;
    final LinkedHashMap<String,String> names = new LinkedHashMap<>();
    final List<String> order = new ArrayList<>();
    HomeItems(Context c) {
        context = c;
        profile = DeviceProfile.get(c);
        for (DeviceProfile.Entry entry : profile.home) names.put(entry.id, entry.label);
        discover();
        try {
            JSONArray data = new JSONArray(c.getSharedPreferences("home-items",0).getString("order","[]"));
            for (int i=0;i<data.length() && order.size()<MAX_ITEMS;i++) {
                String id = data.getString(i);
                if (validId(id) && !order.contains(id)) order.add(id);
            }
        } catch (Exception ignored) { order.clear(); }
        if (order.isEmpty()) order.addAll(defaults());
        pinStockHomeLast(order);
    }
    /** The TV's own launcher is a fallback, so it always sits at the bottom of the list. */
    private void pinStockHomeLast(List<String> ids) {
        if (profile.stockHome == null) return;
        String stock = profile.stockHome.getPackageName();
        if (ids.remove(stock)) ids.add(stock);
    }
    /** Profile-declared entries, or a few discovered apps on an unknown TV. */
    private List<String> defaults() {
        List<String> ids = new ArrayList<>();
        if (!profile.home.isEmpty()) {
            for (DeviceProfile.Entry entry : profile.home) ids.add(entry.id);
            return ids;
        }
        for (String id : names.keySet()) {
            if (profile.isStockHome(id)) continue;
            ids.add(id);
            if (ids.size() >= 6) break;
        }
        if (ids.isEmpty() && profile.stockHome != null) ids.add(profile.stockHome.getPackageName());
        return ids;
    }
    void discover() {
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String id = r.activityInfo.packageName;
                if (!id.equals(context.getPackageName()) && r.activityInfo.enabled && r.activityInfo.exported)
                    names.putIfAbsent(id, profile.label(id, r.loadLabel(context.getPackageManager()).toString()));
            }
        }
    }
    String name(String id) { return names.getOrDefault(id, context.getSharedPreferences("home-items",0).getString("name:"+id,id)); }
    static boolean validId(String id) { return id.equals("hdmi:1") || id.equals("hdmi:2") || id.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+"); }
    boolean save(List<String> next) {
        if (next.isEmpty() || next.size()>MAX_ITEMS || new HashSet<>(next).size()!=next.size()) return false;
        for(String id : next) if(!validId(id)) return false;
        List<String> saved = new ArrayList<>(next);
        pinStockHomeLast(saved);
        android.content.SharedPreferences.Editor e = context.getSharedPreferences("home-items",0).edit()
                .putString("order",new JSONArray(saved).toString());
        for(String id:saved) e.putString("name:"+id,name(id));
        if(!e.commit()) return false;
        order.clear(); order.addAll(saved); return true;
    }
}
