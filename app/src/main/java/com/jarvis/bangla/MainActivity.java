package com.jarvis.bangla;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    TextView status;
    TextView logView;
    ScrollView scroll;
    EditText keyInput;
    EditText cmdInput;
    Button bubbleBtn;
    Button listenBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Voice.init(this);

        ScrollView outer = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(40), dp(16), dp(24));
        outer.addView(root);

        TextView title = new TextView(this);
        title.setText("জার্ভিস");
        title.setTextSize(28);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        root.addView(button("১. Accessibility চালু করুন", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        root.addView(button("২. অন্য অ্যাপের উপরে দেখানোর অনুমতি", v ->
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())))));
        root.addView(button("৩. মাইক, কন্টাক্ট, কল ও নোটিফিকেশনের অনুমতি", v -> {
            List<String> perms = new ArrayList<>();
            perms.add(Manifest.permission.RECORD_AUDIO);
            perms.add(Manifest.permission.READ_CONTACTS);
            perms.add(Manifest.permission.CALL_PHONE);
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS);
            requestPermissions(perms.toArray(new String[0]), 1);
        }));

        keyInput = new EditText(this);
        keyInput.setHint("৪. আপনার Gemini API key এখানে বসান");
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(keyInput);
        root.addView(button("key সেভ করুন", v -> {
            String k = keyInput.getText().toString().replaceAll("[\\s\"'“”‘’]", "");
            prefs().edit().putString("key", k).apply();
            keyInput.setText("");
            log(k.isEmpty() ? "key মুছে ফেলা হয়েছে" : "key সেভ হয়েছে (" + k.length() + " অক্ষর)");
            refreshStatus();
        }));
        root.addView(button("key ঠিক আছে কিনা পরীক্ষা করুন", v -> {
            final String k = apiKey();
            if (k.isEmpty()) {
                log("আগে key সেভ করুন");
                return;
            }
            log("key পরীক্ষা হচ্ছে...");
            new Thread(() -> log(Brain.checkKey(k))).start();
        }));

        Button mic = button("🎤  বলুন", v -> startListening());
        mic.setTextSize(22);
        root.addView(mic);

        cmdInput = new EditText(this);
        cmdInput.setHint("অথবা এখানে লিখে দিন");
        root.addView(cmdInput);
        root.addView(button("পাঠান", v -> {
            String c = cmdInput.getText().toString().trim();
            if (c.isEmpty()) return;
            cmdInput.setText("");
            runCommand(c);
        }));

        bubbleBtn = button("", v -> toggleBubble());
        root.addView(bubbleBtn);
        listenBtn = button("", v -> toggleListening());
        root.addView(listenBtn);

        logView = new TextView(this);
        logView.setTextSize(14);
        logView.setPadding(0, dp(12), 0, 0);
        root.addView(logView);

        setContentView(outer);
        scroll = outer;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        logView.setText(AppLog.all());
        AppLog.listener = line -> runOnUiThread(() -> {
            logView.append(line + "\n");
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    protected void onPause() {
        AppLog.listener = null;
        super.onPause();
    }

    SharedPreferences prefs() {
        return getSharedPreferences("jarvis", Context.MODE_PRIVATE);
    }

    String apiKey() {
        return prefs().getString("key", "");
    }

    String keyPreview() {
        String k = apiKey();
        if (k.isEmpty()) return "";
        String head = k.substring(0, Math.min(4, k.length()));
        String tail = k.substring(Math.max(0, k.length() - 2));
        return " " + head + "…" + tail + " (" + k.length() + " অক্ষর)";
    }

    void refreshStatus() {
        boolean acc = JarvisService.get() != null;
        boolean overlay = Settings.canDrawOverlays(this);
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        boolean contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasKey = !apiKey().isEmpty();
        status.setText(
                (acc ? "✅" : "❌") + " Accessibility\n"
                        + (overlay ? "✅" : "❌") + " অন্য অ্যাপের উপরে দেখানো\n"
                        + (mic ? "✅" : "❌") + " মাইক    " + (contacts ? "✅" : "❌") + " কন্টাক্ট\n"
                        + (hasKey ? "✅" : "❌") + " Gemini key" + keyPreview());
        boolean bubbleOn = prefs().getBoolean("bubble", true);
        bubbleBtn.setText("ভাসমান 🎤 বাটন: " + (bubbleOn ? "চালু (বন্ধ করুন)" : "বন্ধ (চালু করুন)"));
        listenBtn.setText("নাম ধরে ডাকা (পরীক্ষামূলক): "
                + (ListenService.running ? "চালু (বন্ধ করুন)" : "বন্ধ (চালু করুন)"));
    }

    void toggleBubble() {
        boolean on = !prefs().getBoolean("bubble", true);
        prefs().edit().putBoolean("bubble", on).apply();
        JarvisService svc = JarvisService.get();
        if (svc != null) {
            if (on) svc.showBubble();
            else svc.hideBubble();
        } else if (on) {
            log("ভাসমান বাটন দেখাতে আগে Accessibility চালু করুন");
        }
        refreshStatus();
    }

    void toggleListening() {
        if (ListenService.running) {
            stopService(new Intent(this, ListenService.class));
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                return;
            }
            if (apiKey().isEmpty()) {
                log("আগে Gemini key সেভ করুন");
                return;
            }
            try {
                startForegroundService(new Intent(this, ListenService.class));
            } catch (Exception e) {
                log("⚠️ চালু করা গেল না: " + e.getMessage());
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::refreshStatus, 700);
    }

    void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }
        if (apiKey().isEmpty()) {
            log("আগে Gemini key সেভ করুন");
            return;
        }
        final Context app = getApplicationContext();
        new Thread(() -> {
            log("🎤 শুনছি...");
            String heard = Voice.listenOnce(app);
            if (heard.isEmpty()) {
                log("কিছু শুনতে পাইনি");
                return;
            }
            log("🎤 " + heard);
            String rest = Wake.strip(heard);
            String command = (rest != null && !rest.isEmpty()) ? rest : heard;
            Controller.handle(app, apiKey(), command, AppLog::log);
        }).start();
    }

    void runCommand(final String command) {
        if (apiKey().isEmpty()) {
            log("আগে Gemini key সেভ করুন");
            return;
        }
        final Context app = getApplicationContext();
        log("⌨️ " + command);
        new Thread(() -> Controller.handle(app, apiKey(), command, AppLog::log)).start();
    }

    void log(String line) {
        AppLog.log(line);
    }

    Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
