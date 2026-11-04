package com.jarvis.bangla;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/** ব্যবহারকারীর কথা Gemini-কে পাঠিয়ে সিদ্ধান্ত অনুযায়ী কাজ করে। */
public class Controller {

    static void handle(Context ctx, String apiKey, String command, Agent.Logger log) {
        JarvisService svc = JarvisService.get();
        if (svc != null) svc.setBubble("⏳");
        try {
            handleInner(ctx, apiKey, command, log);
        } finally {
            JarvisService again = JarvisService.get();
            if (again != null) again.setBubble("🎤");
        }
    }

    private static void handleInner(Context ctx, String apiKey, String command, Agent.Logger log) {
        JSONObject r;
        try {
            r = Brain.route(ctx, apiKey, command);
        } catch (Exception e) {
            log.log("⚠️ " + e.getMessage());
            Voice.speak(ctx, "এখন বুঝতে পারছি না। ইন্টারনেট বা key-তে সমস্যা হতে পারে");
            return;
        }
        String say = r.optString("say");
        JSONArray actions = r.optJSONArray("actions");
        Brain.remember(command, say + (actions != null ? " " + actions : ""));
        if (!say.isEmpty()) {
            log.log("🤖 " + say);
            Voice.speak(ctx, say);
        }
        if (actions == null) return;
        for (int i = 0; i < actions.length(); i++) {
            JSONObject a = actions.optJSONObject(i);
            if (a == null) continue;
            try {
                runAction(ctx, apiKey, a, log);
            } catch (Exception e) {
                log.log("⚠️ কাজ করতে সমস্যা: " + e.getMessage());
            }
        }
    }

    /** {দেখানোর নাম, নম্বর}: বলা নম্বর থাকলে সেটা, নইলে ফোনবুক থেকে। */
    static String[] target(Context ctx, JSONObject a) {
        String digits = PhoneOps.digitsOnly(a.optString("number", ""));
        if (digits.length() >= 7) return new String[]{digits, digits};
        String name = a.has("contact") ? a.optString("contact") : a.optString("name");
        String latin = a.has("contact_latin") ? a.optString("contact_latin") : a.optString("name_latin");
        return PhoneOps.findContact(ctx, name, latin);
    }

    static void say(Context ctx, Agent.Logger log, String text) {
        log.log("🤖 " + text);
        Voice.speak(ctx, text);
    }

    static void runAction(Context ctx, String apiKey, JSONObject a, Agent.Logger log) {
        String type = a.optString("type");
        switch (type) {
            case "open_app": {
                if (!PhoneOps.launchApp(ctx, a.optString("package"))) {
                    say(ctx, log, "এই অ্যাপটা খুলতে পারিনি");
                }
                break;
            }
            case "youtube_search": {
                PhoneOps.openUrl(ctx, "https://www.youtube.com/results?search_query="
                        + PhoneOps.enc(a.optString("query")), "com.google.android.youtube");
                break;
            }
            case "google_search": {
                PhoneOps.openUrl(ctx, "https://www.google.com/search?q="
                        + PhoneOps.enc(a.optString("query")), null);
                break;
            }
            case "find_contact": {
                String[] t = target(ctx, a);
                if (t == null) {
                    say(ctx, log, "ফোনবুকে এই নামে কাউকে পাইনি");
                } else {
                    say(ctx, log, t[0] + "-এর নম্বর " + t[1]);
                }
                break;
            }
            case "call": {
                String[] t = target(ctx, a);
                if (t == null) {
                    say(ctx, log, "ফোনবুকে এই নামে কাউকে পাইনি");
                    break;
                }
                JarvisService svc = JarvisService.get();
                if (svc == null) {
                    say(ctx, log, "আগে Accessibility চালু করুন");
                    break;
                }
                if (svc.confirm(t[0] + "-কে কল করবো?")) {
                    PhoneOps.call(ctx, PhoneOps.normalize(t[1]));
                } else {
                    say(ctx, log, "ঠিক আছে, কল করিনি");
                }
                break;
            }
            case "whatsapp": {
                String message = a.optString("message");
                String[] t = target(ctx, a);
                if (t == null || message.isEmpty()) {
                    say(ctx, log, "ফোনবুকে এই নামে কাউকে পাইনি");
                    break;
                }
                JarvisService svc = JarvisService.get();
                if (svc == null) {
                    say(ctx, log, "আগে Accessibility চালু করুন");
                    break;
                }
                if (!svc.confirm(t[0] + "-কে লিখবো: " + message + "। পাঠাবো?")) {
                    say(ctx, log, "ঠিক আছে, পাঠাইনি");
                    break;
                }
                String link = "https://wa.me/" + PhoneOps.normalize(t[1])
                        + "?text=" + PhoneOps.enc(message);
                PhoneOps.openUrl(ctx, link, PhoneOps.whatsappPackage(ctx));
                if (svc.tapSend()) {
                    say(ctx, log, "মেসেজ পাঠানো হয়েছে");
                } else {
                    say(ctx, log, "মেসেজ লেখা আছে, কিন্তু সেন্ড বাটন পাইনি। নিজে সেন্ড চাপুন");
                }
                break;
            }
            case "agent": {
                Agent.run(ctx, apiKey, a.optString("goal"), log);
                break;
            }
            default:
                break;
        }
    }
}
