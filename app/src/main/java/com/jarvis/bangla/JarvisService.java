package com.jarvis.bangla;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** ফোনের স্ক্রিন পড়া, ট্যাপ, টাইপ ও হ্যাঁ/না জিজ্ঞেস করার সেবা (Accessibility Service)। */
public class JarvisService extends AccessibilityService {

    static volatile JarvisService instance;

    static JarvisService get() {
        return instance;
    }

    static class Elem {
        String label;
        String id;
        boolean edit;
        boolean click;
        int x;
        int y;
        AccessibilityNodeInfo node;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    // ---------- স্ক্রিন পড়া ----------

    List<Elem> readScreen() {
        List<Elem> out = new ArrayList<>();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return out;
        walk(root, out, new HashSet<String>(), 0);
        return out;
    }

    private void walk(AccessibilityNodeInfo n, List<Elem> out, Set<String> seen, int depth) {
        if (n == null || out.size() >= 90 || depth > 40) return;
        if (n.isVisibleToUser()) {
            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            String text = t == null ? "" : t.toString().trim();
            String desc = d == null ? "" : d.toString().trim();
            String rawId = n.getViewIdResourceName();
            String id = rawId == null ? "" : rawId.substring(rawId.lastIndexOf('/') + 1);
            String cls = n.getClassName() == null ? "" : n.getClassName().toString();
            boolean edit = n.isEditable() || cls.contains("EditText");
            boolean click = n.isClickable();
            if (!text.isEmpty() || !desc.isEmpty() || edit || (click && !id.isEmpty())) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0) {
                    String label = !text.isEmpty() ? text : (!desc.isEmpty() ? desc : id);
                    label = label.replace('\n', ' ');
                    if (label.length() > 60) label = label.substring(0, 60);
                    String key = label + "@" + r.centerX() + "," + r.centerY();
                    if (seen.add(key)) {
                        Elem e = new Elem();
                        e.label = label;
                        e.id = id;
                        e.edit = edit;
                        e.click = click;
                        e.x = r.centerX();
                        e.y = r.centerY();
                        e.node = n;
                        out.add(e);
                    }
                }
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            walk(n.getChild(i), out, seen, depth + 1);
        }
    }

    // ---------- ট্যাপ, টাইপ, সোয়াইপ ----------

    boolean tap(Elem e) {
        if (e.node != null && e.node.isClickable()
                && e.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        return tapXY(e.x, e.y);
    }

    boolean tapXY(int x, int y) {
        Path p = new Path();
        p.moveTo(x, y);
        return gesture(p, 60);
    }

    boolean swipe(String dir) {
        DisplayMetrics m = getResources().getDisplayMetrics();
        int w = m.widthPixels;
        int h = m.heightPixels;
        int cx = w / 2;
        int cy = h / 2;
        Path p = new Path();
        switch (dir) {
            case "up":
                p.moveTo(cx, h * 0.7f);
                p.lineTo(cx, h * 0.3f);
                break;
            case "down":
                p.moveTo(cx, h * 0.3f);
                p.lineTo(cx, h * 0.7f);
                break;
            case "left":
                p.moveTo(w * 0.8f, cy);
                p.lineTo(w * 0.2f, cy);
                break;
            case "right":
                p.moveTo(w * 0.2f, cy);
                p.lineTo(w * 0.8f, cy);
                break;
            default:
                return false;
        }
        return gesture(p, 350);
    }

    private boolean gesture(Path path, long durationMs) {
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs))
                .build();
        final CountDownLatch latch = new CountDownLatch(1);
        boolean ok = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                latch.countDown();
            }
            @Override public void onCancelled(GestureDescription gestureDescription) {
                latch.countDown();
            }
        }, null);
        if (!ok) return false;
        try {
            latch.await(4, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) { }
        return true;
    }

    /** ফোকাসে থাকা (না থাকলে প্রথম) লেখার ঘরে লেখে। বাংলাসহ যেকোনো ভাষা চলে। */
    boolean setText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (target == null || !target.isEditable()) target = firstEditable(root, 0);
        if (target == null) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private AccessibilityNodeInfo firstEditable(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 40) return null;
        if (n.isVisibleToUser() && n.isEditable()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo found = firstEditable(n.getChild(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    boolean pressEnter() {
        if (Build.VERSION.SDK_INT < 30) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null) return false;
        return f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
    }

    boolean back() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    boolean home() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    /** WhatsApp-এর Send বাটন খুঁজে চাপে (কয়েকবার চেষ্টা করে)। */
    boolean tapSend() {
        for (int attempt = 0; attempt < 8; attempt++) {
            try { Thread.sleep(1200); } catch (InterruptedException ignored) { }
            for (Elem e : readScreen()) {
                String low = e.label.toLowerCase();
                if (e.id.equals("send") || low.equals("send") || e.label.contains("পাঠান")) {
                    return tap(e);
                }
            }
        }
        return false;
    }

    // ---------- হ্যাঁ/না জিজ্ঞেস (স্ক্রিনের নিচে বাটনসহ) ----------

    /** ব্যাকগ্রাউন্ড থ্রেড থেকে ডাকতে হবে। ব্যবহারকারী "হ্যাঁ" চাপলে true। */
    boolean confirm(final String question) {
        final boolean[] result = {false};
        final CountDownLatch latch = new CountDownLatch(1);
        final View[] holder = new View[1];
        final Handler main = new Handler(Looper.getMainLooper());
        final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        main.post(() -> {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackgroundColor(0xF2202020);
            box.setPadding(40, 40, 40, 40);

            TextView tv = new TextView(this);
            tv.setText(question);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(18);
            box.addView(tv);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button yes = new Button(this);
            yes.setText("হ্যাঁ");
            Button no = new Button(this);
            no.setText("না");
            LinearLayout.LayoutParams half =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(yes, half);
            row.addView(no, half);
            box.addView(row);

            yes.setOnClickListener(v -> {
                result[0] = true;
                removeOverlay(wm, box);
                latch.countDown();
            });
            no.setOnClickListener(v -> {
                removeOverlay(wm, box);
                latch.countDown();
            });

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM;
            holder[0] = box;
            wm.addView(box, lp);
        });

        Voice.speak(this, question);
        try {
            if (!latch.await(90, TimeUnit.SECONDS)) {
                main.post(() -> removeOverlay(wm, holder[0]));
            }
        } catch (InterruptedException ignored) { }
        return result[0];
    }

    private void removeOverlay(WindowManager wm, View v) {
        try {
            if (v != null) wm.removeView(v);
        } catch (Exception ignored) { }
    }
}
