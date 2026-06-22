package com.viceandroid.c64;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class IgdbService {
    private static final int PLATFORM_C64_ID = 15;
    private static final String GAME_FIELDS =
            "name, cover.url, summary, first_release_date, platforms.name, involved_companies.company.name, involved_companies.publisher";
    private static final String CLIENT_ID = BuildConfig.IGDB_CLIENT_ID;
    private static final String CLIENT_SECRET = BuildConfig.IGDB_CLIENT_SECRET;

    private static IgdbService instance;

    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, IgdbGame> gameCache = new HashMap<>();
    private final Map<Integer, Bitmap> coverCache = new HashMap<>();
    private String accessToken;

    static final class IgdbGame {
        int id;
        String name;
        String summary;
        String coverUrl;
        String releaseDate;
        String publisher;
    }

    interface GameCallback {
        void onGameLoaded(IgdbGame game);
    }

    interface CoverCallback {
        void onCoverLoaded(Bitmap cover);
    }

    static synchronized IgdbService getInstance(Context context) {
        if (instance == null) {
            instance = new IgdbService(context.getApplicationContext());
        }
        return instance;
    }

    private IgdbService(Context context) {
        this.context = context;
        loadGameCacheFromDisk();
    }

    boolean hasCredentials() {
        return CLIENT_ID != null && !CLIENT_ID.isBlank()
                && CLIENT_SECRET != null && !CLIENT_SECRET.isBlank();
    }

    void lookupGame(String gameName, GameCallback callback) {
        String key = cacheKey(gameName);
        IgdbGame cached = gameCache.get(key);
        if (cached != null) {
            mainHandler.post(() -> callback.onGameLoaded(cached));
            return;
        }
        executor.execute(() -> {
            try {
                if (!hasCredentials() || key.isEmpty()) {
                    mainHandler.post(() -> callback.onGameLoaded(null));
                    return;
                }
                List<IgdbGame> games = searchC64GamesBlocking(gameName, 10);
                IgdbGame result = pickBestMatch(gameName, games);
                if (result != null) {
                    cacheGame(gameName, result);
                }
                mainHandler.post(() -> callback.onGameLoaded(result));
            } catch (Exception ignored) {
                mainHandler.post(() -> callback.onGameLoaded(null));
            }
        });
    }

    void cacheGame(String key, IgdbGame game) {
        if (key == null || key.isBlank() || game == null) {
            return;
        }
        gameCache.put(cacheKey(key), game);
        if (game.name != null && !game.name.isBlank()) {
            gameCache.put(cacheKey(game.name), game);
        }
        saveGameCacheToDisk();
    }

    IgdbGame getCachedGame(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return gameCache.get(cacheKey(key));
    }

    String encodeGame(IgdbGame game) {
        if (game == null) {
            return "";
        }
        try {
            return cachedGameJson(game).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    IgdbGame decodeGame(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return parseCachedGame(new JSONObject(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    void loadCover(String coverUrl, int gameId, CoverCallback callback) {
        if (coverUrl == null || coverUrl.isBlank() || gameId == 0) {
            mainHandler.post(() -> callback.onCoverLoaded(null));
            return;
        }
        Bitmap cached = coverCache.get(gameId);
        if (cached != null) {
            mainHandler.post(() -> callback.onCoverLoaded(cached));
            return;
        }
        executor.execute(() -> {
            try {
                File cacheDir = new File(context.getCacheDir(), "igdb_c64_covers");
                if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                    mainHandler.post(() -> callback.onCoverLoaded(null));
                    return;
                }
                File cachedFile = new File(cacheDir, gameId + ".jpg");
                if (cachedFile.isFile()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(cachedFile.getAbsolutePath());
                    if (bitmap != null) {
                        coverCache.put(gameId, bitmap);
                        mainHandler.post(() -> callback.onCoverLoaded(bitmap));
                        return;
                    }
                }
                String finalUrl = coverUrl.startsWith("//") ? "https:" + coverUrl : coverUrl;
                finalUrl = finalUrl.replace("t_thumb", "t_cover_big");
                HttpURLConnection conn = (HttpURLConnection) new URL(finalUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                Bitmap bitmap;
                try (InputStream input = conn.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(input);
                } finally {
                    conn.disconnect();
                }
                if (bitmap != null) {
                    coverCache.put(gameId, bitmap);
                    try (FileOutputStream output = new FileOutputStream(cachedFile)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output);
                    }
                }
                Bitmap result = bitmap;
                mainHandler.post(() -> callback.onCoverLoaded(result));
            } catch (Exception ignored) {
                mainHandler.post(() -> callback.onCoverLoaded(null));
            }
        });
    }

    private List<IgdbGame> searchC64GamesBlocking(String query, int limit) throws Exception {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isEmpty()) {
            return new ArrayList<>();
        }
        ensureAccessToken();
        String escaped = safeQuery.replace("\\", "\\\\").replace("\"", "\\\"");
        String body = "search \"" + escaped + "\"; fields " + GAME_FIELDS
                + "; where platforms = (" + PLATFORM_C64_ID + "); limit " + limit + ";";
        return parseGames(executeIgdbRequest(body));
    }

    private void ensureAccessToken() throws Exception {
        if (accessToken != null && !accessToken.isBlank()) {
            return;
        }
        URL authUrl = new URL("https://id.twitch.tv/oauth2/token?client_id=" + CLIENT_ID
                + "&client_secret=" + CLIENT_SECRET + "&grant_type=client_credentials");
        HttpURLConnection conn = (HttpURLConnection) authUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        JSONObject json = new JSONObject(readStream(conn.getInputStream()));
        accessToken = json.getString("access_token");
        conn.disconnect();
    }

    private String executeIgdbRequest(String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.igdb.com/v4/games").openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Client-ID", CLIENT_ID);
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "text/plain");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (OutputStream output = conn.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        }
        String response = readStream(conn.getInputStream());
        conn.disconnect();
        return response;
    }

    private List<IgdbGame> parseGames(String response) {
        List<IgdbGame> games = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(response);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                IgdbGame game = new IgdbGame();
                game.id = item.optInt("id", 0);
                game.name = item.optString("name", "");
                game.summary = item.optString("summary", "");
                if (item.has("cover") && !item.isNull("cover")) {
                    game.coverUrl = item.getJSONObject("cover").optString("url", "");
                }
                long releaseTimestamp = item.optLong("first_release_date", 0L);
                if (releaseTimestamp > 0L) {
                    game.releaseDate = new SimpleDateFormat("yyyy", Locale.US)
                            .format(new Date(releaseTimestamp * 1000L));
                }
                JSONArray companies = item.optJSONArray("involved_companies");
                if (companies != null) {
                    for (int c = 0; c < companies.length(); c++) {
                        JSONObject company = companies.getJSONObject(c);
                        if (company.optBoolean("publisher", false)
                                && company.has("company") && !company.isNull("company")) {
                            game.publisher = company.getJSONObject("company").optString("name", "");
                            break;
                        }
                    }
                }
                games.add(game);
            }
        } catch (Exception ignored) {
        }
        return games;
    }

    private IgdbGame pickBestMatch(String query, List<IgdbGame> games) {
        if (games == null || games.isEmpty()) {
            return null;
        }
        String normalizedQuery = normalizeTitle(query);
        IgdbGame best = null;
        int bestScore = 0;
        for (IgdbGame game : games) {
            if (game == null || game.name == null) {
                continue;
            }
            String normalizedName = normalizeTitle(game.name);
            int score = normalizedName.equals(normalizedQuery) ? 100
                    : normalizedName.startsWith(normalizedQuery) || normalizedQuery.startsWith(normalizedName) ? 70
                    : normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName) ? 50
                    : 0;
            if (score > bestScore) {
                bestScore = score;
                best = game;
            }
        }
        return bestScore >= 50 ? best : null;
    }

    private String normalizeTitle(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private File getGameCacheFile() {
        return new File(context.getCacheDir(), "igdb_c64_games.json");
    }

    private void loadGameCacheFromDisk() {
        try {
            File cacheFile = getGameCacheFile();
            if (!cacheFile.isFile()) {
                return;
            }
            byte[] data = new byte[(int) cacheFile.length()];
            try (FileInputStream input = new FileInputStream(cacheFile)) {
                if (input.read(data) <= 0) {
                    return;
                }
            }
            JSONArray array = new JSONObject(new String(data, StandardCharsets.UTF_8)).getJSONArray("games");
            for (int i = 0; i < array.length(); i++) {
                IgdbGame game = parseCachedGame(array.getJSONObject(i));
                gameCache.put(cacheKey(game.name), game);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveGameCacheToDisk() {
        executor.execute(() -> {
            try {
                JSONArray array = new JSONArray();
                for (IgdbGame game : gameCache.values()) {
                    array.put(cachedGameJson(game));
                }
                JSONObject root = new JSONObject();
                root.put("games", array);
                try (FileOutputStream output = new FileOutputStream(getGameCacheFile())) {
                    output.write(root.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
            }
        });
    }

    private JSONObject cachedGameJson(IgdbGame game) throws Exception {
        JSONObject item = new JSONObject();
        item.put("id", game.id);
        item.put("name", game.name);
        item.put("summary", game.summary);
        item.put("coverUrl", game.coverUrl);
        item.put("releaseDate", game.releaseDate);
        item.put("publisher", game.publisher);
        return item;
    }

    private IgdbGame parseCachedGame(JSONObject item) {
        IgdbGame game = new IgdbGame();
        game.id = item.optInt("id", 0);
        game.name = item.optString("name", "");
        game.summary = item.optString("summary", "");
        game.coverUrl = item.optString("coverUrl", "");
        game.releaseDate = item.optString("releaseDate", "");
        game.publisher = item.optString("publisher", "");
        return game;
    }

    private String cacheKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private String readStream(InputStream input) throws Exception {
        byte[] buffer = new byte[8192];
        StringBuilder builder = new StringBuilder();
        int read;
        while ((read = input.read(buffer)) >= 0) {
            builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }
}
