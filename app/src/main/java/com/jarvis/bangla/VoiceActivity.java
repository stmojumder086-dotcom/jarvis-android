package com.jarvis.bangla;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/** ভাসমান বাটন চাপলে অন্য অ্যাপের উপরে স্বচ্ছভাবে খোলে, শুনে বন্ধ হয়ে যায়। */
public class VoiceActivity extends Activity {

    SpeechRecognizer sr;
    boolean finishedOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "আগে জার্ভিস অ্যাপে মাইকের অনুমতি দিন", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        final String key = getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("key", "");
        if (key.isEmpty()) {
            Toast.makeText(this, "আগে জার্ভিস অ্যাপে Gemini key সেভ করুন", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        TextView label = new TextView(this);
        label.setText("🎤  শুনছি...");
        label.setTextSize(20);
        label.setTextColor(0xFFFFFFFF);
        label.setBackgroundColor(0xE6202020);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        label.setPadding(pad * 2, pad, pad * 2, pad);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = pad * 4;
        root.addView(label, lp);
        root.setOnClickListener(v -> closeNow());
        setContentView(root);

        sr = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
        sr.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }

            @Override
            public void onError(int error) {
                Toast.makeText(VoiceActivity.this, "কিছু শুনতে পাইনি", Toast.LENGTH_SHORT).show();
                closeNow();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String heard = (list != null && !list.isEmpty()) ? list.get(0) : "";
                closeNow();
                if (heard.isEmpty()) return;
                String rest = Wake.strip(heard);
                final String command = (rest != null && !rest.isEmpty()) ? rest : heard;
                final Context app = getApplicationContext();
                AppLog.log("🎤 " + heard);
                new Thread(() -> {
                    Agent.sleep(900); // আগের অ্যাপটা সামনে ফিরে আসার সময় দেওয়া
                    Controller.handle(app, key, command, AppLog::log);
                }).start();
            }
        });

        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        sr.startListening(i);
    }

    void closeNow() {
        if (finishedOnce) return;
        finishedOnce = true;
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        if (sr != null) {
            sr.destroy();
            sr = null;
        }
        super.onDestroy();
    }
}
