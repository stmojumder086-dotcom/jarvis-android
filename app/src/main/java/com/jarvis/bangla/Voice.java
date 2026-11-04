package com.jarvis.bangla;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** কথা বলা (TTS) ও শোনা (STT)। */
public class Voice {

    static TextToSpeech tts;
    static volatile boolean ttsReady = false;
    static volatile CountDownLatch speakLatch;

    static synchronized void init(Context ctx) {
        if (tts != null) return;
        tts = new TextToSpeech(ctx.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                int r = tts.setLanguage(new Locale("bn", "BD"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(new Locale("bn"));
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { }
                    @Override public void onDone(String utteranceId) { release(); }
                    @Override public void onError(String utteranceId) { release(); }
                    private void release() {
                        CountDownLatch l = speakLatch;
                        if (l != null) l.countDown();
                    }
                });
                ttsReady = true;
            }
        });
    }

    /** বলা শেষ হওয়া পর্যন্ত অপেক্ষা করে। ব্যাকগ্রাউন্ড থ্রেড থেকে ডাকতে হবে। */
    static void speak(Context ctx, String text) {
        if (text == null || text.isEmpty()) return;
        init(ctx);
        for (int i = 0; i < 30 && !ttsReady; i++) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) { }
        }
        if (!ttsReady) return;
        CountDownLatch l = new CountDownLatch(1);
        speakLatch = l;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime());
        try {
            l.await(25, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) { }
    }

    /** একবার শুনে বাংলা লেখা ফেরত দেয় (না শুনলে খালি লেখা)। ব্যাকগ্রাউন্ড থ্রেড থেকে ডাকতে হবে। */
    static String listenOnce(Context ctx) {
        final String[] result = {""};
        final CountDownLatch latch = new CountDownLatch(1);
        final Context app = ctx.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> {
            final SpeechRecognizer sr = SpeechRecognizer.createSpeechRecognizer(app);
            sr.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onPartialResults(Bundle partialResults) { }
                @Override public void onEvent(int eventType, Bundle params) { }
                @Override public void onError(int error) {
                    sr.destroy();
                    latch.countDown();
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> list =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) result[0] = list.get(0);
                    sr.destroy();
                    latch.countDown();
                }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            sr.startListening(i);
        });
        try {
            latch.await(25, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) { }
        return result[0];
    }
}
