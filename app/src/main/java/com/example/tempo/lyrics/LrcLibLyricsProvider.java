package com.example.tempo.lyrics;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class LrcLibLyricsProvider implements LyricsProvider {

    private final String baseUrl;
    private static final String DEFAULT_BASE_URL = "https://lrclib.net/api";

    // Song Candidate Object Structure DTO returned by LRCLIB
    public static class Candidate {
        public final String id;
        public final String trackName;
        public final String artistName;
        public final String plainLyrics;
        public final String syncedLyrics;

        public Candidate(String id, String trackName, String artistName, String plainLyrics, String syncedLyrics) {
            this.id = id;
            this.trackName = trackName;
            this.artistName = artistName;
            this.plainLyrics = plainLyrics;
            this.syncedLyrics = syncedLyrics;
        }
    }

    public LrcLibLyricsProvider() {
        this.baseUrl = DEFAULT_BASE_URL;
    }

    public LrcLibLyricsProvider(String baseUrl) {
        this.baseUrl = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : DEFAULT_BASE_URL;
    }

    @Override
    public String[] lookup(String title, String artist) {
        if (TextUtils.isEmpty(title)) return null;
        try {
            String q = URLEncoder.encode(title, "UTF-8");
            String url = baseUrl + "/search?q=" + q;
            return tryFetch(url, title);
        } catch (Exception ignored) {}
        return null;
    }

    // Search and return all candidates that contain lyrics (plain or synced). User can then choose.
    public java.util.List<Candidate> searchCandidates(String title) {
        java.util.List<Candidate> out = new java.util.ArrayList<>();
        if (TextUtils.isEmpty(title)) return out;
        try {
            String q = URLEncoder.encode(title, "UTF-8");
            String url = baseUrl + "/search?q=" + q;
            String json = httpGet(url);
            if (json == null) return out;
            if (json.trim().startsWith("[")) {
                org.json.JSONArray arr = new org.json.JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject it = arr.optJSONObject(i);
                    if (it == null) continue;
                    String[] ex = extractFromObject(it);
                    String plain = ex == null ? null : ex[0];
                    String lrc = ex == null ? null : ex[1];
                    String id = optStringOrNull(it, "id");
                    String track = optStringOrNull(it, "trackName");
                    if (track == null) track = optStringOrNull(it, "name");
                    String artist = optStringOrNull(it, "artistName");
                    // If no lyrics in the search item, try fetching details by id
                    if (plain == null && lrc == null && id != null) {
                        String[] detail = tryFetchDetailById(id);
                        if (detail != null) {
                            plain = detail[0];
                            lrc = detail[1];
                        }
                    }
                    if (plain == null && lrc == null) continue;
                    out.add(new Candidate(id, track, artist, plain, lrc));
                }
            } else {
                org.json.JSONObject root = new org.json.JSONObject(json);
                String[] ex = extractFromObject(root);
                String plain = ex == null ? null : ex[0];
                String lrc = ex == null ? null : ex[1];
                if (plain != null || lrc != null) {
                    String id = findIdInObject(root);
                    String track = optStringOrNull(root, "trackName");
                    if (track == null) track = optStringOrNull(root, "name");
                    String artist = optStringOrNull(root, "artistName");
                    out.add(new Candidate(id, track, artist, plain, lrc));
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    // Primary fetch that considers the original title for matching
    private String[] tryFetch(String urlStr, String originalTitle) {
        try {
            String json = httpGet(urlStr);
            if (json == null) return null;

            if (json.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(json);
                String normTitle = normalizeTitle(originalTitle);

                String[] exactBoth = null;
                String[] exactAny = null;
                String[] firstBoth = null;
                String[] firstAny = null;

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject it = arr.optJSONObject(i);
                    if (it == null) continue;

                    String[] found = extractFromObject(it);
                    boolean hasPlain = found != null && found[0] != null;
                    boolean hasLrc = found != null && found[1] != null;
                    boolean isExact = matchesTitle(it, normTitle);

                    if (isExact) {
                        if (hasPlain && hasLrc) return found; // immediate best
                        if (found != null && exactAny == null) exactAny = found;
                        if (hasPlain && hasLrc && exactBoth == null) exactBoth = found;
                    }

                    if (found != null) {
                        if (hasPlain && hasLrc && firstBoth == null) firstBoth = found;
                        if (firstAny == null) firstAny = found;
                    } else {
                        // no lyrics directly in item > try detail by id
                        String id = findIdInObject(it);
                        if (id != null) {
                            String[] detail = tryFetchDetailById(id);
                            if (detail != null) {
                                boolean dPlain = detail[0] != null;
                                boolean dLrc = detail[1] != null;
                                if (isExact && dPlain && dLrc) return detail;
                                if (isExact && exactAny == null) exactAny = detail;
                                if (dPlain && dLrc && firstBoth == null) firstBoth = detail;
                                if (firstAny == null) firstAny = detail;
                            }
                        }
                    }
                }

                if (exactBoth != null) return exactBoth;
                if (exactAny != null) return exactAny;
                if (firstBoth != null) return firstBoth;
                if (firstAny != null) return firstAny;

                return null;
            }

            // root object
            JSONObject root = new JSONObject(json);
            String[] extracted = extractFromObject(root);
            if (extracted != null) return extracted;

            String id = findIdInObject(root);
            if (id != null) return tryFetchDetailById(id);

            // inspect data/result blocks as fallback
            if (root.has("data")) {
                Object data = root.get("data");
                if (data instanceof JSONObject) {
                    String[] ext = extractFromObject((JSONObject) data);
                    if (ext != null) return ext;
                } else if (data instanceof JSONArray) {
                    JSONArray darr = (JSONArray) data;
                    for (int i = 0; i < darr.length(); i++) {
                        JSONObject it = darr.optJSONObject(i);
                        if (it == null) continue;
                        String[] ext = extractFromObject(it);
                        if (ext != null) return ext;
                    }
                }
            }

            if (root.has("result") && root.opt("result") instanceof JSONObject) {
                String[] ext = extractFromObject(root.optJSONObject("result"));
                if (ext != null) return ext;
            }

        } catch (Exception ignored) {}
        return null;
    }

    private String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "TempoLyricsClient/1.0");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                br.close();
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // Extract plain and synced lyrics from a result object. Returns {plain, lrc}
    private String[] extractFromObject(JSONObject root) {
        try {
            String plain = null;
            String lrc = null;

            // skip instrumentals
            try {
                if (root.has("instrumental") && root.optBoolean("instrumental")) return null;
            } catch (Exception ignored) {}

            // plain lyrics
            if (root.has("plainLyrics")) plain = optStringOrNull(root, "plainLyrics");

            // synced/LRC lyrics
            if (root.has("syncedLyrics")) lrc = optStringOrNull(root, "syncedLyrics");

            if (plain == null && lrc == null) return null;
            return new String[]{plain, lrc};
        } catch (Exception ignored) {}
        return null;
    }

    private String findIdInObject(JSONObject obj) {
        try {
            String key = "id";
            if (obj.has(key)) {
                String v = optStringOrNull(obj, key);
                if (v != null) return v;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String[] tryFetchDetailById(String id) {
        try {
            String idEndpoint = baseUrl + "/get/" + URLEncoder.encode(id, "UTF-8");

            String json = httpGet(idEndpoint);
            if (json == null) return null;
            if (json.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject it = arr.optJSONObject(i);
                    if (it == null) continue;
                    String[] ext = extractFromObject(it);
                    if (ext != null) return ext;
                }
            } else {
                JSONObject root = new JSONObject(json);
                String[] ext = extractFromObject(root);
                if (ext != null) return ext;
            }

        } catch (Exception ignored) {}
        return null;
    }

    private static String optStringOrNull(JSONObject obj, String key) {
        String v = obj.optString(key, null);
        if (v == null) return null;
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    // Normalize title for matching: trim, remove file extensions and surrounding punctuation
    private String normalizeTitle(String t) {
        if (t == null) return "";
        String s = t.trim();
        s = s.replaceAll("(?i)\\.(mp3|wav|flac|m4a)$", "");
        s = s.replaceAll("\\s+", " ");
        s = s.replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "");
        return s;
    }

    private boolean matchesTitle(JSONObject it, String normTitle) {
        // Per LRCLIB result shape we expect `trackName` and `name` fields for title
        try {
            String[] keys = new String[]{"trackName", "name"};
            for (String k : keys) {
                if (it.has(k)) {
                    String v = it.optString(k, null);
                    if (v != null && normalizeTitle(v).equals(normTitle)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
