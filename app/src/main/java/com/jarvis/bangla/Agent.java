package com.jarvis.bangla;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** স্ক্রিন দেখে ধাপে ধাপে ট্যাপ/টাইপ করে একটা কাজ শেষ করে। */
public class Agent {

    interface Logger {
        void log(String s);
    }

    static final int MAX_STEPS = 15;

    /** এই ধরনের বাটন চাপার আগে ব্যবহারকারীর অনুমতি লাগবেই (Gemini ভুল করলেও)। */
    static final Pattern SENSITIVE = Pattern.compile(
            "(?i)\\b(send|post|share|pay|buy|purchase|delete|remove|call|order)\\b"
                    + "|পাঠা|পোস্ট|পেমেন্ট|কিন|ডিলিট|মুছ|কল");

    static void run(Context ctx, String apiKey, String goal, Logger log) {
        JarvisService svc = JarvisService.get();
        if (svc == null) {
            Voice.speak(ctx, "আগে Accessibility চালু করুন");
            return;
        }
        List<String> steps = new ArrayList<>();
        boolean confirmed = false;

        for (int n = 1; n <= MAX_STEPS; n++) {
            List<JarvisService.Elem> els = svc.readScreen();
            StringBuilder screen = new StringBuilder();
            for (int i = 0; i < els.size(); i++) {
                JarvisService.Elem e = els.get(i);
                screen.append(i).append(": \"").append(e.label).append("\"");
                if (!e.id.isEmpty()) screen.append(" [").append(e.id).append("]");
                if (e.edit) screen.append(" EDIT");
                if (e.click) screen.append(" CLICK");
                screen.append(" @(").append(e.x).append(",").append(e.y).append(")\n");
            }
            StringBuilder done = new StringBuilder();
            for (int i = 0; i < steps.size(); i++) {
                done.append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
            String userText = "Goal: " + goal + "\n"
                    + "Steps so far:\n" + (done.length() == 0 ? "(none)\n" : done)
                    + "On-screen elements:\n" + (screen.length() == 0 ? "(none found)" : screen);

            JSONObject d;
            try {
                d = Brain.parse(Brain.call(apiKey, Brain.AGENT_PROMPT, userText));
            } catch (Exception ex) {
                log.log("⚠️ এজেন্ট: " + ex.getMessage());
                Voice.speak(ctx, "এখন বুঝতে পারছি না, আবার চেষ্টা করুন");
                return;
            }
            String act = d.optString("do");
            log.log("ধাপ " + n + ": " + d.optString("thought") + " -> " + act);

            if (act.equals("done") || act.equals("fail")) {
                String say = d.optString("say", act.equals("done") ? "কাজ হয়েছে" : "পারলাম না");
                log.log("🤖 " + say);
                Voice.speak(ctx, say);
                Brain.remember("(ফোনের কাজ: " + goal + ")", act + ": " + say);
                return;

            } else if (act.equals("tap")) {
                int idx = d.optInt("index", -1);
                if (idx < 0 || idx >= els.size()) {
                    steps.add("tap failed: bad index");
                    continue;
                }
                JarvisService.Elem e = els.get(idx);
                if (SENSITIVE.matcher(e.label).find()) {
                    if (!confirmed && !svc.confirm("\"" + e.label + "\" চাপবো?")) {
                        Voice.speak(ctx, "ঠিক আছে, থামলাম");
                        return;
                    }
                    confirmed = false;
                }
                boolean ok = svc.tap(e);
                steps.add("tapped \"" + e.label + "\"" + (ok ? "" : " (failed)"));

            } else if (act.equals("type")) {
                String text = d.optString("text");
                boolean ok = svc.setText(text);
                steps.add(ok ? "typed \"" + text + "\""
                        : "could NOT type \"" + text + "\" (no text field focused; tap a field first)");

            } else if (act.equals("key")) {
                String key = d.optString("key");
                boolean ok = false;
                if (key.equals("back")) ok = svc.back();
                else if (key.equals("home")) ok = svc.home();
                else if (key.equals("enter")) ok = svc.pressEnter();
                steps.add("pressed " + key + (ok ? "" : " (failed)"));

            } else if (act.equals("swipe")) {
                String dir = d.optString("direction");
                svc.swipe(dir);
                steps.add("swiped " + dir);

            } else if (act.equals("wait")) {
                sleep(1500);
                steps.add("waited");

            } else if (act.equals("open_app")) {
                String pkg = d.optString("package");
                boolean ok = PhoneOps.launchApp(ctx, pkg);
                steps.add("opened " + pkg + (ok ? "" : " (failed)"));

            } else if (act.equals("share_text")) {
                String pkg = d.optString("package");
                PhoneOps.shareText(ctx, pkg, d.optString("text"));
                steps.add("opened share screen of " + pkg + " with the text");

            } else if (act.equals("confirm")) {
                String q = d.optString("question", "করবো?");
                if (!svc.confirm(q)) {
                    Voice.speak(ctx, "ঠিক আছে, করছি না");
                    return;
                }
                confirmed = true;
                steps.add("user confirmed: yes");

            } else {
                steps.add("unknown action " + act);
            }
            sleep(1300);
        }
        Voice.speak(ctx, "১৫ ধাপেও কাজটা শেষ করতে পারিনি");
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) { }
    }
}
