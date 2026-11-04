package com.jarvis.bangla;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Gemini-র সাথে কথা বলে: ব্যবহারকারীর কথা বুঝে কী করতে হবে ঠিক করে। */
public class Brain {

    static final String[] MODELS = {
            "gemini-flash-lite-latest", "gemini-2.5-flash-lite", "gemini-2.5-flash"
    };

    static final List<String[]> history = new ArrayList<>();

    static final String ROUTER_PROMPT = """
You are a Bangla voice assistant running ON the user's Android phone.
The user speaks Bangla (speech-to-text may contain mistakes).
Work out what they want and reply with ONLY a JSON object, no other text:

{"say": "<short natural Bangla reply to speak aloud, max 2 sentences>",
 "actions": [ ... ]}

Allowed actions (use only these):
- {"type": "open_app", "package": "<exact package name copied from the installed apps list>"}
- {"type": "youtube_search", "query": "<search text>"}
- {"type": "google_search", "query": "<search text>"}
- {"type": "call", "contact": "<name as said>", "contact_latin": "<your best English spelling>", "number": "<digits, only if the user said a phone number>"}
- {"type": "whatsapp", "contact": "<name as said>", "contact_latin": "<English spelling>", "number": "<digits, only if the user said a number>", "message": "<text>"}
- {"type": "find_contact", "name": "<name as said>", "name_latin": "<English spelling>"}
- {"type": "agent", "goal": "<the full task to do inside an app, in English, keeping names and message texts exactly as the user said them>"}

Rules:
- If the user only asks a question or chats, answer in "say" and use "actions": [].
- If the request is unclear, ask a short clarifying question in "say" with no actions.
- To open an app, pick the package from the installed apps list that best matches what the user said (they may say the name in Bangla; labels may be English). If two WhatsApp apps exist, choose com.whatsapp.w4b. If nothing matches, say the app is not installed and use no action.
- For call, whatsapp and find_contact the contact can be ANY name the user says; the app searches the phone's contact list itself. If the user says a phone number digit by digit (Bangla or English digits), put it in "number" as English digits and leave contact empty.
- Use "agent" when the task needs working INSIDE an app: searching for a person in Messenger, Telegram or Instagram, sending a message in any app other than WhatsApp, browsing, changing a phone setting, and so on. A separate screen agent will look at the screen, tap and type by itself and will ask the user before sending anything. For simply opening an app, WhatsApp messages, calls and web or YouTube searches, use the specific actions instead.
- Use the recent conversation to resolve references such as "him", "that app", "again". If the user continues a task in an app, use "agent" with the complete goal.
- Names are often garbled by speech-to-text; do not refuse because a name looks unfamiliar, pass it on as said.
- Never invent URLs or package names that are not in the list.
""";

    static final String AGENT_PROMPT = """
You operate an Android phone for the user by looking at its screen, one step at a time.
You get: the goal, the steps done so far, and the list of on-screen elements
(index: "label" [id] flags @(x,y)).
Reply with ONLY one JSON object for the NEXT single step:

{"thought": "<very short reason>", "do": "<action>", ...fields}

Actions:
- {"do": "tap", "index": N}                        tap element N from the list
- {"do": "type", "text": "<text>"}                 type into the focused or first text field (tap the field first); any language works
- {"do": "key", "key": "enter|back|home"}
- {"do": "swipe", "direction": "up|down|left|right"}    "up" scrolls the content down
- {"do": "wait"}                                   the screen is loading
- {"do": "open_app", "package": "<package name>"}
- {"do": "share_text", "package": "<package name>", "text": "<text>"}   opens that app's share screen with the text already filled in
- {"do": "confirm", "question": "<short Bangla yes/no question>"}
- {"do": "done", "say": "<short Bangla message about the result>"}
- {"do": "fail", "say": "<short Bangla reason>"}

Rules:
- One step per reply. Use what you see on the screen.
- Before sending a message, posting, calling, buying, deleting or any irreversible action, FIRST use "confirm" naming the person and the exact text. Never send without a "yes".
- Never enter passwords, OTPs or payment details. If asked for them, use "fail".
- Speech-to-text garbles names (for example one Bangla name heard as a similar sounding word). Match names approximately against names visible on the screen. If several are similar or you are unsure, use "confirm" naming the person.
- If the same screen repeats after your action, try something different. If stuck, use "fail".
""";

    static synchronized void remember(String user, String assistant) {
        history.add(new String[]{user, assistant});
        while (history.size() > 8) history.remove(0);
    }

    static synchronized String historyText() {
        StringBuilder sb = new StringBuilder();
        for (String[] h : history) {
            sb.append("User: ").append(h[0]).append("\nAssistant: ").append(h[1]).append("\n");
        }
        return sb.toString();
    }

    static JSONObject route(Context ctx, String apiKey, String command) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Current time: ").append(new java.util.Date()).append("\n");
        sb.append("Installed apps (label | package): ");
        int count = 0;
        for (String[] app : PhoneOps.installedApps(ctx)) {
            if (count++ >= 350) break;
            sb.append(app[0]).append(" | ").append(app[1]).append("; ");
        }
        sb.append("\n");
        String hist = historyText();
        if (!hist.isEmpty()) sb.append("Recent conversation (oldest first):\n").append(hist);
        sb.append("User now says: ").append(command);
        return parse(call(apiKey, ROUTER_PROMPT, sb.toString()));
    }

    static JSONObject parse(String raw) throws Exception {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            s = nl >= 0 ? s.substring(nl + 1) : s.substring(3);
            if (s.trim().endsWith("```")) {
                s = s.trim();
                s = s.substring(0, s.length() - 3);
            }
        }
        return new JSONObject(s.trim());
    }

    /** Gemini REST API ডাকে। ফ্রি সীমা ছুঁলে একবার অপেক্ষা করে আবার চেষ্টা করে। */
    static String call(String apiKey, String system, String userText) throws Exception {
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", system))));
        body.put("contents", new JSONArray().put(new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", userText)))));
        body.put("generationConfig", new JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.2));
        byte[] payload = body.toString().getBytes("UTF-8");

        Exception last = null;
        for (String model : MODELS) {
            for (int attempt = 0; attempt < 2; attempt++) {
                HttpURLConnection c = null;
                try {
                    URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                            + model + ":generateContent");
                    c = (HttpURLConnection) url.openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(60000);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("x-goog-api-key", apiKey);
                    c.setDoOutput(true);
                    try (OutputStream os = c.getOutputStream()) {
                        os.write(payload);
                    }
                    int code = c.getResponseCode();
                    if (code == 429 && attempt == 0) {
                        Thread.sleep(8000);
                        continue;
                    }
                    InputStream in = code == 200 ? c.getInputStream() : c.getErrorStream();
                    String resp = readAll(in);
                    if (code != 200) {
                        last = new Exception(model + " -> " + code + " " + shorten(resp));
                        break;
                    }
                    JSONObject j = new JSONObject(resp);
                    return j.getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts")
                            .getJSONObject(0).getString("text");
                } catch (Exception e) {
                    last = e;
                    break;
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }
        throw last != null ? last : new Exception("unknown error");
    }

    /** key ঠিক আছে কিনা পরীক্ষা করে একটা বার্তা ফেরত দেয়। */
    static String checkKey(String apiKey) {
        HttpURLConnection c = null;
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1");
            c = (HttpURLConnection) url.openConnection();
            c.setRequestProperty("x-goog-api-key", apiKey);
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            int code = c.getResponseCode();
            if (code == 200) return "✅ key ঠিক আছে";
            if (code == 400 || code == 403) {
                return "❌ key ঠিক নেই (" + code + "). AI Studio থেকে আবার কপি করে সেভ করুন";
            }
            return "⚠️ কোড " + code + ": " + shorten(readAll(c.getErrorStream()));
        } catch (Exception e) {
            return "⚠️ ইন্টারনেটে সমস্যা: " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    static String shorten(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
