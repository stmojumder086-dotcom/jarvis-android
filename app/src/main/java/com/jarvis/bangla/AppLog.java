package com.jarvis.bangla;

/** সব জায়গার লগ এক জায়গায় রাখে, যাতে অ্যাপ খুললে দেখা যায়। */
public class AppLog {

    interface Listener {
        void onLine(String line);
    }

    static final StringBuilder text = new StringBuilder();
    static volatile Listener listener;

    static synchronized void log(String line) {
        text.append(line).append("\n");
        if (text.length() > 20000) text.delete(0, 5000);
        Listener l = listener;
        if (l != null) l.onLine(line);
    }

    static synchronized String all() {
        return text.toString();
    }
}
