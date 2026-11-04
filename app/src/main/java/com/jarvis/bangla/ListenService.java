package com.jarvis.bangla;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * পরীক্ষামূলক: স্ক্রিন চালু থাকলে নাম ধরে ডাকার অপেক্ষায় থাকে।
 * ডাকার শব্দ চেনা Google-এর অনলাইন শোনার সেবা দিয়ে হয়, তাই চালু থাকলে এই সময়ের সব কথা Google-এর কাছে যায়।
 */
public class ListenService extends Service {

    static volatile boolean running = false;
    static final String CHANNEL = "jarvis_listen";

    private Handler main;
    private SpeechRecognizer sr;
    private boolean screenOn = true;
    private boolean busy = false;
    private boolean awaitingCommand = false;
    private BroadcastReceiver screenReceiver;

    private final Runnable restart = new Runnable() {
        @Override
        public void run() {
            startListening();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        main = new Handler(Looper.getMainLooper());

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(
                new NotificationChannel(CHANNEL, "জার্ভিস শুনছে", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("জার্ভিস শুনছে")
                .setContentText("স্ক্রিন চালু থাকলে নাম ধরে ডাকুন")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(7, n);
            }
        } catch (Exception e) {
            AppLog.log("⚠️ সবসময় শোনা চালু করা গেল না: " + e.getMessage());
            stopSelf();
            return;
        }
        running = true;

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        screenOn = pm.isInteractive();
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    screenOn = false;
                    main.removeCallbacks(restart);
                    destroyRecognizer();
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    screenOn = true;
                    scheduleRestart(500);
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, f);

        AppLog.log("👂 সবসময় শোনা চালু (স্ক্রিন চালু থাকলে)");
        if (screenOn) startListening();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (main != null) main.removeCallbacks(restart);
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) { }
        }
        destroyRecognizer();
        AppLog.log("👂 সবসময় শোনা বন্ধ");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void scheduleRestart(long delayMs) {
        main.removeCallbacks(restart);
        main.postDelayed(restart, delayMs);
    }

    private void destroyRecognizer() {
        if (sr != null) {
            try {
                sr.destroy();
            } catch (Exception ignored) { }
            sr = null;
        }
    }

    private void startListening() {
        if (!running || !screenOn || busy) return;
        destroyRecognizer();
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
                awaitingCommand = false;
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    AppLog.log("⚠️ মাইকের অনুমতি নেই, সবসময় শোনা বন্ধ করছি");
                    stopSelf();
                } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    destroyRecognizer();
                    scheduleRestart(1500);
                } else if (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    scheduleRestart(300);
                } else {
                    scheduleRestart(2500);
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String heard = (list != null && !list.isEmpty()) ? list.get(0) : "";
                handleHeard(heard);
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        try {
            sr.startListening(i);
        } catch (Exception e) {
            scheduleRestart(2500);
        }
    }

    private void handleHeard(String heard) {
        if (heard.isEmpty()) {
            scheduleRestart(300);
            return;
        }
        AppLog.log("👂 " + heard);
        if (awaitingCommand) {
            awaitingCommand = false;
            runCommand(heard);
            return;
        }
        String rest = Wake.strip(heard);
        if (rest == null) {
            scheduleRestart(200);
            return;
        }
        if (rest.replace(" ", "").length() > 2) {
            runCommand(rest);
        } else {
            // শুধু নাম বলা হয়েছে: "জি, বলুন" বলে পরের কথাটা কাজ হিসেবে নেয়
            busy = true;
            destroyRecognizer();
            final Context app = getApplicationContext();
            new Thread(() -> {
                Voice.speak(app, "জি, বলুন");
                main.post(() -> {
                    busy = false;
                    awaitingCommand = true;
                    startListening();
                });
            }).start();
        }
    }

    private void runCommand(final String command) {
        final String key = getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("key", "");
        if (key.isEmpty()) {
            AppLog.log("আগে Gemini key সেভ করুন");
            scheduleRestart(500);
            return;
        }
        busy = true;
        destroyRecognizer();
        final Context app = getApplicationContext();
        new Thread(() -> {
            try {
                Controller.handle(app, key, command, AppLog::log);
            } finally {
                main.post(() -> {
                    busy = false;
                    if (screenOn) startListening();
                });
            }
        }).start();
    }
}
