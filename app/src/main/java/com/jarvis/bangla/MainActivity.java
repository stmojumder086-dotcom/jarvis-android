package com.jarvis.bangla;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    TextView status;
    TextView logView;
    ScrollView scroll;
    EditText keyInput;
    EditText cmdInput;

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
        root.addView(button("৩. মাইক, কন্টাক্ট ও কলের অনুমতি", v ->
                requestPermissions(new String[]{
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE}, 1)));

        keyInput = new EditText(this);
        keyInput.setHint("৪. আপনার Gemini API key এখানে বসান");
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(keyInput);
        root.addView(button("key সেভ করুন", v -> {
            String k = keyInput.getText().toString().trim();
            prefs().edit().putString("key", k).apply();
            keyInput.setText("");
            log(k.isEmpty() ? "key মুছে ফেলা হয়েছে" : "key সেভ হয়েছে");
            refreshStatus();
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
    }

    SharedPreferences prefs() {
        return getSharedPreferences("jarvis", Context.MODE_PRIVATE);
    }

    String apiKey() {
        return prefs().getString("key", "");
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
                        + (hasKey ? "✅" : "❌") + " Gemini key");
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
            Controller.handle(app, apiKey(), heard, this::log);
        }).start();
    }

    void runCommand(final String command) {
        if (apiKey().isEmpty()) {
            log("আগে Gemini key সেভ করুন");
            return;
        }
        final Context app = getApplicationContext();
        log("⌨️ " + command);
        new Thread(() -> Controller.handle(app, apiKey(), command, this::log)).start();
    }

    void log(final String line) {
        runOnUiThread(() -> {
            logView.append(line + "\n");
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
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
