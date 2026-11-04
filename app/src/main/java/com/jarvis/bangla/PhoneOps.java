package com.jarvis.bangla;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** ফোনের সরাসরি কাজ: অ্যাপ খোলা, কন্টাক্ট খোঁজা, কল, WhatsApp লিংক ইত্যাদি। */
public class PhoneOps {

    /** নম্বর 01 দিয়ে শুরু হলে এই দেশের কোড বসবে (বাংলাদেশ = 880)। */
    static final String COUNTRY_CODE = "880";

    static List<String[]> installedApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (!seen.add(pkg)) continue;
            out.add(new String[]{String.valueOf(ri.loadLabel(pm)), pkg});
        }
        return out;
    }

    static boolean isInstalled(Context ctx, String pkg) {
        try {
            ctx.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** দুটো WhatsApp থাকলে Business-কে আগে ধরে। */
    static String whatsappPackage(Context ctx) {
        if (isInstalled(ctx, "com.whatsapp.w4b")) return "com.whatsapp.w4b";
        return "com.whatsapp";
    }

    static boolean launchApp(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (pkg.equals("com.whatsapp") || pkg.equals("com.whatsapp.w4b")) {
            pkg = whatsappPackage(ctx);
        }
        Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void openUrl(Context ctx, String url, String pkg) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (pkg != null && isInstalled(ctx, pkg)) i.setPackage(pkg);
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException e) {
            i.setPackage(null);
            try {
                ctx.startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }

    static void shareText(Context ctx, String pkg, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        if (pkg != null && !pkg.isEmpty()) i.setPackage(pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    static void call(Context ctx, String number) {
        boolean allowed = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
        Intent i = new Intent(allowed ? Intent.ACTION_CALL : Intent.ACTION_DIAL,
                Uri.parse("tel:+" + number));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    // ---------- কন্টাক্ট ----------

    /** {নাম, নম্বর} এর তালিকা। */
    static List<String[]> contacts(Context ctx) {
        List<String[]> out = new ArrayList<>();
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return out;
        }
        Cursor cur = ctx.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null);
        if (cur == null) return out;
        try {
            while (cur.moveToNext()) {
                String name = cur.getString(0);
                String number = cur.getString(1);
                if (name != null && number != null) out.add(new String[]{name, number});
            }
        } finally {
            cur.close();
        }
        return out;
    }

    static String[] findContact(Context ctx, String name, String latin) {
        List<String> terms = new ArrayList<>();
        if (name != null && !name.trim().isEmpty()) terms.add(name.trim().toLowerCase());
        if (latin != null && !latin.trim().isEmpty()) terms.add(latin.trim().toLowerCase());
        if (terms.isEmpty()) return null;
        String[] best = null;
        double bestScore = 0;
        for (String[] c : contacts(ctx)) {
            String low = c[0].toLowerCase();
            double score = 0;
            for (String t : terms) {
                if (low.contains(t) || (low.length() >= 2 && t.contains(low))) {
                    score = Math.max(score, 1.0);
                } else {
                    score = Math.max(score, similarity(t, low));
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return bestScore >= 0.6 ? best : null;
    }

    static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1.0;
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return 1.0 - (double) prev[b.length()] / max;
    }

    /** বাংলা অঙ্কসহ যেকোনো লেখা থেকে শুধু ইংরেজি অঙ্ক বের করে। */
    static String digitsOnly(String s) {
        StringBuilder sb = new StringBuilder();
        if (s == null) return "";
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) sb.append(Character.digit(ch, 10));
        }
        return sb.toString();
    }

    /** 01712-345678 -> 8801712345678 */
    static String normalize(String raw) {
        String d = digitsOnly(raw);
        if (d.startsWith("00")) d = d.substring(2);
        if (d.startsWith("0")) d = COUNTRY_CODE + d.substring(1);
        return d;
    }
}
