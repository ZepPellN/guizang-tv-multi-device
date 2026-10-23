package io.github.op7418.guizangtv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Everything that differs between TV brands.
 *
 * Profiles are read from {@code assets/devices.json} and matched top-down against
 * {@link Build#MANUFACTURER}, {@link Build#BRAND} and {@link Build#MODEL}. The first
 * match wins and the {@code generic} profile is the fallback, so supporting another
 * TV means adding a JSON entry, not touching Java. See docs/DEVICE-PROFILES.md.
 */
final class DeviceProfile {
    private static final String TAG = "TvHome.Profile";
    private static final String ASSET = "devices.json";
    private static volatile DeviceProfile cached;

    final String id;
    final String name;
    final String homeLabel;
    final boolean bootStart;
    final List<Entry> home = new ArrayList<>();
    final Map<String,String> labels = new LinkedHashMap<>();
    final ComponentName stockHome;
    final String stockHomeAction;
    final ComponentName hdmiComponent;
    final String hdmiAction;
    final String hdmiExtra;
    final int hdmiBase;
    final Set<Integer> hdmiPorts = new LinkedHashSet<>();
    final ComponentName inputsComponent;
    final String inputsAction;
    final Map<String,IntentSpec> settings = new LinkedHashMap<>();
    final Map<String,String> claims = new LinkedHashMap<>();

    static final class Entry {
        final String id;
        final String label;
        Entry(String id, String label) { this.id = id; this.label = label; }
    }

    static final class IntentSpec {
        final String action;
        final ComponentName component;
        IntentSpec(String action, ComponentName component) { this.action = action; this.component = component; }
    }

    private DeviceProfile(JSONObject json) {
        id = json.optString("id", "generic");
        name = json.optString("name", id);
        homeLabel = json.optString("homeLabel", "原桌面");
        bootStart = json.optBoolean("bootStart", false);

        JSONArray homes = json.optJSONArray("home");
        if (homes != null) for (int i = 0; i < homes.length(); i++) {
            JSONObject item = homes.optJSONObject(i);
            if (item == null) continue;
            String entryId = text(item, "id");
            if (entryId == null) continue;
            String label = text(item, "label");
            home.add(new Entry(entryId, label == null ? entryId : label));
        }

        JSONObject labelJson = json.optJSONObject("labels");
        if (labelJson != null) for (Iterator<String> it = labelJson.keys(); it.hasNext();) {
            String key = it.next();
            String value = text(labelJson, key);
            if (key != null && value != null) labels.put(key, value);
        }

        JSONObject stock = json.optJSONObject("stockHome");
        stockHome = stock == null ? null : component(text(stock, "component"));
        stockHomeAction = stock == null ? null : text(stock, "action");

        JSONObject hdmi = json.optJSONObject("hdmi");
        hdmiComponent = hdmi == null ? null : component(text(hdmi, "component"));
        hdmiAction = hdmi == null ? null : text(hdmi, "action");
        hdmiExtra = hdmi == null ? null : text(hdmi, "extra");
        hdmiBase = hdmi == null ? 0 : hdmi.optInt("base", 0);
        JSONArray ports = hdmi == null ? null : hdmi.optJSONArray("ports");
        if (ports != null) for (int i = 0; i < ports.length(); i++) hdmiPorts.add(ports.optInt(i));

        JSONObject inputs = json.optJSONObject("inputs");
        inputsComponent = inputs == null ? null : component(text(inputs, "component"));
        inputsAction = inputs == null ? null : text(inputs, "action");

        JSONObject settingsJson = json.optJSONObject("settings");
        if (settingsJson != null) for (Iterator<String> it = settingsJson.keys(); it.hasNext();) {
            String standard = it.next();
            JSONObject spec = settingsJson.optJSONObject(standard);
            if (spec == null) continue;
            settings.put(standard, new IntentSpec(text(spec, "action"), component(text(spec, "component"))));
        }

        JSONObject claimsJson = json.optJSONObject("claims");
        if (claimsJson != null) for (Iterator<String> it = claimsJson.keys(); it.hasNext();) {
            String key = it.next();
            String value = text(claimsJson, key);
            if (key != null && value != null) claims.put(key, value);
        }
    }

    static DeviceProfile get(Context context) {
        DeviceProfile local = cached;
        if (local != null) return local;
        synchronized (DeviceProfile.class) {
            if (cached == null) cached = load(context.getApplicationContext());
            return cached;
        }
    }

    /** For tests and for future per-user overrides. */
    static DeviceProfile parse(String json) throws Exception {
        return new DeviceProfile(new JSONObject(json));
    }

    private static DeviceProfile load(Context context) {
        try {
            JSONObject root = new JSONObject(readAsset(context));
            JSONArray profiles = root.optJSONArray("profiles");
            JSONObject fallback = null;
            if (profiles != null) for (int i = 0; i < profiles.length(); i++) {
                JSONObject item = profiles.optJSONObject(i);
                if (item == null) continue;
                if ("generic".equals(item.optString("id", ""))) { fallback = item; continue; }
                if (matches(item.optJSONObject("match"))) {
                    Log.i(TAG, "profile " + item.optString("id") + " for " + Build.MANUFACTURER + "/" + Build.MODEL);
                    return new DeviceProfile(item);
                }
            }
            if (fallback != null) return new DeviceProfile(fallback);
        } catch (Exception error) {
            Log.w(TAG, "cannot read " + ASSET + ", falling back to empty profile", error);
        }
        return new DeviceProfile(new JSONObject());
    }

    private static String readAsset(Context context) throws Exception {
        try (InputStream in = context.getAssets().open(ASSET)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static boolean matches(JSONObject match) {
        if (match == null || match.length() == 0) return false;
        return field(match, "manufacturer", Build.MANUFACTURER)
                && field(match, "brand", Build.BRAND)
                && field(match, "model", Build.MODEL);
    }

    private static boolean field(JSONObject match, String key, String value) {
        JSONArray patterns = match.optJSONArray(key);
        if (patterns == null || patterns.length() == 0) return true;
        for (int i = 0; i < patterns.length(); i++) if (glob(patterns.optString(i), value)) return true;
        return false;
    }

    /** Plain equality, or a glob when the pattern contains '*'. */
    static boolean glob(String pattern, String value) {
        if (pattern == null || value == null) return false;
        if (!pattern.contains("*")) return pattern.equalsIgnoreCase(value);
        StringBuilder regex = new StringBuilder();
        for (char ch : pattern.toCharArray()) {
            if (ch == '*') regex.append(".*");
            else if ("\\.[]{}()+-^$|?".indexOf(ch) >= 0) regex.append('\\').append(ch);
            else regex.append(ch);
        }
        return value.matches("(?i)" + regex);
    }

    private static String text(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) return null;
        String value = object.optString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }

    private static ComponentName component(String flattened) {
        if (flattened == null || !flattened.contains("/")) return null;
        try {
            return ComponentName.unflattenFromString(flattened);
        } catch (RuntimeException invalid) {
            Log.w(TAG, "bad component " + flattened, invalid);
            return null;
        }
    }

    boolean isStockHome(String packageId) {
        return stockHome != null && stockHome.getPackageName().equals(packageId);
    }

    /** Opens the TV's own launcher, as configured for this device. */
    boolean openStockHome(Context context) {
        if (stockHome == null) return false;
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(stockHome, 0);
            if (!info.enabled || !info.exported) return false;
            if (info.permission != null
                    && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)
                return false;
            context.startActivity(intent(stockHomeAction, stockHome)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            return true;
        } catch (Exception unavailable) {
            Log.w(TAG, "stock home unavailable", unavailable);
            return false;
        }
    }

    /** Switches to an HDMI input; returns false when this device has no mapping for it. */
    boolean openHdmi(Context context, int port) {
        if (hdmiComponent == null || !hdmiPorts.contains(port)) return false;
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(hdmiComponent, 0);
            if (!info.enabled || !info.exported) return false;
            if (info.permission != null
                    && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)
                return false;
            Intent target = intent(hdmiAction, hdmiComponent);
            if (hdmiExtra != null) target.putExtra(hdmiExtra, hdmiBase + port);
            context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception unavailable) {
            Log.w(TAG, "HDMI " + port + " unavailable", unavailable);
            return false;
        }
    }

    boolean showInputs(Context context) {
        if (inputsComponent == null || inputsAction == null) return false;
        try {
            ActivityInfo info = context.getPackageManager().getReceiverInfo(inputsComponent, 0);
            if (!info.exported || !info.enabled) return false;
            if (info.permission != null
                    && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)
                return false;
            context.sendBroadcast(new Intent(inputsAction).setComponent(inputsComponent));
            return true;
        } catch (Exception unavailable) {
            return false;
        }
    }

    /** Device-specific intent for a standard settings action, or the standard intent. */
    Intent settings(Context context, String standardAction) {
        IntentSpec spec = settings.get(standardAction);
        if (spec == null) return new Intent(standardAction);
        Intent target = intent(spec.action, spec.component);
        if (spec.component != null
                && target.resolveActivity(context.getPackageManager()) == null)
            return new Intent(standardAction);
        return target;
    }

    String label(String packageId, String original) {
        String mapped = labels.get(packageId);
        return mapped == null ? original : mapped;
    }

    /**
     * Some firmwares ignore the Android default-home setting and pick Home/boot
     * targets from system properties. Reclaiming them here keeps this launcher in
     * charge; the values are written with setprop so no extra permission is needed.
     */
    void claim(Context context) {
        if (claims.isEmpty()) return;
        String self = context.getPackageName();
        for (Map.Entry<String,String> entry : claims.entrySet())
            setTextProperty(entry.getKey(), entry.getValue().replace("$package", self));
    }

    void claimAsync(Context context) {
        if (claims.isEmpty()) return;
        Context app = context.getApplicationContext();
        new Thread(() -> claim(app), "device-claim").start();
    }

    private static void setTextProperty(String key, String value) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/setprop", key, value});
            int code = process.waitFor();
            Log.i(TAG, "setprop " + key + " exit=" + code);
        } catch (Exception error) {
            Log.w(TAG, "setprop " + key + " failed", error);
        }
    }

    private static Intent intent(String action, ComponentName component) {
        Intent intent = action == null ? new Intent(Intent.ACTION_MAIN) : new Intent(action);
        if (component != null) intent.setComponent(component);
        return intent;
    }
}
