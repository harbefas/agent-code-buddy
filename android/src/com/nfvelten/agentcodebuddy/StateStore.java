package com.nfvelten.agentcodebuddy;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StateStore {
    private static final String PREFS_NAME = "buddy_state";
    private static final String KEY_REQUESTS = "requests";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_LAST_UPDATE = "last_update";
    private static final String KEY_PET_INDEX = "pet_index";
    private static final String KEY_TOKENS = "tokens";
    private static final String KEY_TOKENS_TODAY = "tokens_today";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_ENERGY = "energy";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_LAST_INTERACTION = "last_interaction";
    private static final String KEY_LAST_PROMPT_TIME = "last_prompt_time";
    private static final String KEY_HEART_UNTIL = "heart_until";
    private static final String KEY_DARK_THEME = "dark_theme";

    private final SharedPreferences prefs;

    public StateStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addRequest(String id, String agent, String tool, String title, String detail, String status, String decision) {
        try {
            JSONArray requests = getRequestsArray();
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("agent", agent != null ? agent : "agent");
            item.put("tool", tool != null ? tool : "tool");
            item.put("title", title != null ? title : "Approval request");
            item.put("detail", detail != null ? detail : "");
            item.put("status", status);
            item.put("decision", decision != null ? decision : "");
            item.put("created_at", System.currentTimeMillis());

            JSONArray newArr = new JSONArray();
            for (int i = 0; i < requests.length(); i++) {
                JSONObject obj = requests.getJSONObject(i);
                if (!obj.getString("id").equals(id)) {
                    newArr.put(obj);
                }
            }
            newArr.put(item);
            while (newArr.length() > 50) {
                JSONArray trimmed = new JSONArray();
                for (int i = 1; i < newArr.length(); i++) trimmed.put(newArr.get(i));
                newArr = trimmed;
            }

            int tokens = getTokens() + 50;
            int tokensToday = getTokensToday() + 50;
            List<String> entries = getEntries();
            entries.add(0, title);
            if (entries.size() > 8) entries = entries.subList(0, 8);
            JSONArray entriesArr = new JSONArray();
            for (String e : entries) entriesArr.put(e);

            prefs.edit()
                .putString(KEY_REQUESTS, newArr.toString())
                .putInt(KEY_TOKENS, tokens)
                .putInt(KEY_TOKENS_TODAY, tokensToday)
                .putString(KEY_ENTRIES, entriesArr.toString())
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis())
                .apply();
        } catch (JSONException e) {
        }
    }

    public void updateDecision(String id, String decision) {
        try {
            JSONArray requests = getRequestsArray();
            String toolName = "";
            for (int i = 0; i < requests.length(); i++) {
                JSONObject obj = requests.getJSONObject(i);
                if (obj.getString("id").equals(id)) {
                    obj.put("status", "decided");
                    obj.put("decision", decision);
                    toolName = obj.optString("tool", "");
                    break;
                }
            }
            long now = System.currentTimeMillis();
            long lastPrompt = getLastPromptTime();

            int tokens = getTokens() + 100;
            int tokensToday = getTokensToday() + 100;
            List<String> entries = getEntries();
            String label = decision.replace("_", " ");
            entries.add(0, label + " " + toolName);
            if (entries.size() > 8) entries = entries.subList(0, 8);
            JSONArray entriesArr = new JSONArray();
            for (String e : entries) entriesArr.put(e);

            SharedPreferences.Editor ed = prefs.edit()
                .putString(KEY_REQUESTS, requests.toString())
                .putInt(KEY_TOKENS, tokens)
                .putInt(KEY_TOKENS_TODAY, tokensToday)
                .putString(KEY_ENTRIES, entriesArr.toString())
                .putLong(KEY_LAST_UPDATE, now)
                .putLong(KEY_LAST_INTERACTION, now);
            if (lastPrompt > 0 && (now - lastPrompt) < 5000 && !"reject".equals(decision)) {
                ed.putLong(KEY_HEART_UNTIL, now + 8000);
            }
            ed.apply();
        } catch (JSONException e) {
        }
    }

    public void saveStats(int tokens, int tokensToday, List<String> entries) {
        try {
            JSONArray arr = new JSONArray();
            for (String e : entries) arr.put(e);
            prefs.edit()
                .putInt(KEY_TOKENS, tokens)
                .putInt(KEY_TOKENS_TODAY, tokensToday)
                .putString(KEY_ENTRIES, arr.toString())
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply();
        } catch (Exception e) {
        }
    }

    public int getTokens() { return prefs.getInt(KEY_TOKENS, 0); }
    public int getTokensToday() { return prefs.getInt(KEY_TOKENS_TODAY, 0); }

    public List<String> getEntries() {
        List<String> list = new ArrayList<>();
        try {
            String raw = prefs.getString(KEY_ENTRIES, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        } catch (Exception e) {
        }
        return list;
    }

    public int getPetIndex() { return prefs.getInt(KEY_PET_INDEX, 0); }
    public void setPetIndex(int idx) {
        prefs.edit().putInt(KEY_PET_INDEX, idx % PetSpecies.ALL.length).apply();
    }
    public void nextPet() { setPetIndex(getPetIndex() + 1); }

    public int getEnergy() { return prefs.getInt(KEY_ENERGY, 100); }
    public void setEnergy(int e) { prefs.edit().putInt(KEY_ENERGY, Math.max(0, Math.min(100, e))).apply(); }

    public int getLevel() { return prefs.getInt(KEY_LEVEL, 1); }
    public void setLevel(int l) { prefs.edit().putInt(KEY_LEVEL, Math.max(1, l)).apply(); }

    public long getLastInteractionTime() { return prefs.getLong(KEY_LAST_INTERACTION, 0); }
    public long getLastPromptTime() { return prefs.getLong(KEY_LAST_PROMPT_TIME, 0); }

    public long getHeartUntil() {
        return prefs.getLong(KEY_HEART_UNTIL, 0);
    }

    public void clearHeart() {
        prefs.edit().remove(KEY_HEART_UNTIL).apply();
    }

    public List<RequestItem> getRequests() {
        List<RequestItem> list = new ArrayList<>();
        try {
            JSONArray requests = getRequestsArray();
            for (int i = requests.length() - 1; i >= 0; i--) {
                JSONObject obj = requests.getJSONObject(i);
                list.add(new RequestItem(
                    obj.optString("id", ""),
                    obj.optString("agent", ""),
                    obj.optString("tool", ""),
                    obj.optString("title", ""),
                    obj.optString("detail", ""),
                    obj.optString("status", ""),
                    obj.optString("decision", "")
                ));
            }
        } catch (JSONException e) {
        }
        return list;
    }

    public List<RequestItem> getPending() {
        List<RequestItem> pending = new ArrayList<>();
        for (RequestItem item : getRequests()) {
            if ("pending".equals(item.status)) pending.add(item);
        }
        return pending;
    }

    public List<RequestItem> getHistory() {
        List<RequestItem> history = new ArrayList<>();
        for (RequestItem item : getRequests()) {
            if (!"pending".equals(item.status)) history.add(item);
        }
        return history;
    }

    public boolean hasPending() { return !getPending().isEmpty(); }

    public void saveServerUrl(String url) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public String getServerUrl() { return prefs.getString(KEY_SERVER_URL, null); }

    public long getLastUpdateTime() { return prefs.getLong(KEY_LAST_UPDATE, 0); }

    private JSONArray getRequestsArray() {
        try {
            String str = prefs.getString(KEY_REQUESTS, "[]");
            return new JSONArray(str);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public boolean isDarkTheme(boolean defaultValue) {
        return prefs.getBoolean(KEY_DARK_THEME, defaultValue);
    }

    public void setDarkTheme(boolean dark) {
        prefs.edit().putBoolean(KEY_DARK_THEME, dark).apply();
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }

    public static class RequestItem {
        public final String id;
        public final String agent;
        public final String tool;
        public final String title;
        public final String detail;
        public final String status;
        public final String decision;

        public RequestItem(String id, String agent, String tool, String title, String detail, String status, String decision) {
            this.id = id; this.agent = agent; this.tool = tool;
            this.title = title; this.detail = detail;
            this.status = status; this.decision = decision;
        }

        public String getDecisionLabel() {
            if ("allow_once".equals(decision)) return "Allowed once";
            if ("always_allow".equals(decision)) return "Always allowed";
            if ("reject".equals(decision)) return "Rejected";
            if ("timeout".equals(decision)) return "Timed out";
            return "Pending";
        }
    }
}
