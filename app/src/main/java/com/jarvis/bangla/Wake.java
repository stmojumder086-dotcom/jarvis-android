package com.jarvis.bangla;

/** ডাকার নাম চেনা (বানান সামান্য ভুল হলেও)। */
public class Wake {

    static final String[] NAMES = {"জার্ভিস", "জারভিস", "জারবিস", "জার্বিস", "জাভিস"};
    /** "সার্ভিস" একটা সাধারণ শব্দ, তাই শুধু "হেই/ওকে"-র পরে ধরা হয়। */
    static final String[] SOFT = {"সার্ভিস"};
    static final String[] PREFIX = {"হেই", "হে", "ওকে", "হাই", "ও", "এই"};

    static boolean isPrefix(String t) {
        for (String p : PREFIX) if (p.equals(t)) return true;
        return false;
    }

    static boolean matches(String cand, boolean hadPrefix) {
        // "সার্ভিস" নিজে একটা সাধারণ শব্দ; "হেই/ওকে" ছাড়া একা বললে ডাক ধরা হয় না
        if (!hadPrefix) {
            for (String n : SOFT) if (n.equals(cand)) return false;
        }
        for (String n : NAMES) if (PhoneOps.similarity(cand, n) >= 0.75) return true;
        if (hadPrefix) {
            for (String n : SOFT) if (PhoneOps.similarity(cand, n) >= 0.75) return true;
        }
        return false;
    }

    /** নাম পেলে নামের পরের কথা (খালি হতে পারে) ফেরত দেয়; না পেলে null। */
    static String strip(String text) {
        if (text == null) return null;
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) return null;
        int start = 0;
        boolean hadPrefix = false;
        if (isPrefix(tokens[0]) && tokens.length > 1) {
            hadPrefix = true;
            start = 1;
        }
        for (int n = 1; n <= 2 && start + n <= tokens.length; n++) {
            StringBuilder cand = new StringBuilder();
            for (int k = start; k < start + n; k++) cand.append(tokens[k]);
            if (matches(cand.toString(), hadPrefix)) {
                StringBuilder rest = new StringBuilder();
                for (int k = start + n; k < tokens.length; k++) rest.append(tokens[k]).append(' ');
                return rest.toString().trim();
            }
        }
        return null;
    }
}
