package cc.gen.second.utils;

import cc.gen.second.config;
import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client for DeviantArt OAuth2 API.
 * Handles token refresh, image upload to Sta.sh, and publishing.
 */
public class DeviantArtClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeviantArtClient.class);

    private static final String TOKEN_URL = "https://www.deviantart.com/oauth2/token";
    private static final String STASH_SUBMIT_URL = "https://www.deviantart.com/api/v1/oauth2/stash/submit";
    private static final String STASH_PUBLISH_URL = "https://www.deviantart.com/api/v1/oauth2/stash/publish";

    private final String clientId;
    private final String clientSecret;
    private String refreshToken;
    private String accessToken;
    private long tokenExpiresAt;

    private final OkHttpClient httpClient;

    public DeviantArtClient() {
        this.clientId = config.get("DA_CLIENT_ID");
        this.clientSecret = config.get("DA_CLIENT_SECRET");
        this.refreshToken = config.get("DA_REFRESH_TOKEN");

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public boolean isConfigured() {
        return clientId != null && clientSecret != null && refreshToken != null
                && !clientId.isEmpty() && !clientSecret.isEmpty() && !refreshToken.isEmpty();
    }

    /**
     * Refreshes the OAuth2 access token using the stored refresh token.
     */
    private synchronized void ensureAccessToken() throws IOException {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return;
        }

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", refreshToken)
                .build();

        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);

            if (!response.isSuccessful() || json.has("error")) {
                throw new IOException("Token refresh failed: " + responseBody);
            }

            this.accessToken = json.getString("access_token");
            int expiresIn = json.getInt("expires_in");
            this.tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 60) * 1000L;

            // DA may return a new refresh token
            if (json.has("refresh_token")) {
                this.refreshToken = json.getString("refresh_token");
                LOGGER.info("DeviantArt refresh token was rotated. Update DA_REFRESH_TOKEN in .env if needed.");
            }

            LOGGER.info("DeviantArt access token refreshed, expires in {} seconds", expiresIn);
        }
    }

    /**
     * Upload an image to Sta.sh and publish it as a deviation.
     *
     * @param imageUrl      URL of the image to download and upload
     * @param title         Deviation title
     * @param tags          List of tags
     * @param isAiGenerated Whether the art was created with AI tools
     * @param isMature      Whether to mark as mature content
     * @param artistComment Optional artist comment/description
     * @return Published deviation URL, or error message
     */
    public String postDeviation(String imageUrl, String title, List<String> tags,
                                boolean isAiGenerated, boolean isMature,
                                String artistComment) throws IOException {
        ensureAccessToken();

        // Step 1: Download image from URL
        byte[] imageData;
        String fileName = extractFileName(imageUrl, title);
        String mimeType = guessMimeType(fileName);

        Request downloadReq = new Request.Builder().url(imageUrl).build();
        try (Response dlResponse = httpClient.newCall(downloadReq).execute()) {
            if (!dlResponse.isSuccessful()) {
                throw new IOException("Failed to download image: HTTP " + dlResponse.code());
            }
            imageData = dlResponse.body().bytes();
        }

        LOGGER.info("Downloaded image: {} ({} bytes)", fileName, imageData.length);

        // Step 2: Upload to Sta.sh
        long itemId = submitToStash(imageData, fileName, mimeType, title, artistComment, tags);
        LOGGER.info("Uploaded to Sta.sh with itemid: {}", itemId);

        // Step 3: Publish from Sta.sh
        String deviationUrl = publishFromStash(itemId, isAiGenerated, isMature);
        LOGGER.info("Published deviation: {}", deviationUrl);

        return deviationUrl;
    }

    private long submitToStash(byte[] imageData, String fileName, String mimeType,
                               String title, String artistComment, List<String> tags) throws IOException {
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("access_token", accessToken)
                .addFormDataPart("title", title)
                .addFormDataPart("file", fileName,
                        RequestBody.create(imageData, MediaType.parse(mimeType)));

        if (artistComment != null && !artistComment.isEmpty()) {
            builder.addFormDataPart("artist_comments", artistComment);
        }

        for (String tag : tags) {
            builder.addFormDataPart("tags[]", tag.trim());
        }

        Request request = new Request.Builder()
                .url(STASH_SUBMIT_URL)
                .post(builder.build())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body().string();
            JSONObject json = new JSONObject(body);

            // DA may return errors with HTTP 200 due to chunked encoding
            if (json.has("error")) {
                throw new IOException("Stash submit failed: " + json.getString("error_description"));
            }

            if (!"success".equals(json.optString("status"))) {
                throw new IOException("Stash submit failed: " + body);
            }

            return json.getLong("itemid");
        }
    }

    private String publishFromStash(long itemId, boolean isAiGenerated, boolean isMature) throws IOException {
        FormBody.Builder builder = new FormBody.Builder()
                .add("access_token", accessToken)
                .add("itemid", String.valueOf(itemId))
                .add("agree_tos", "1")
                .add("agree_submission", "1")
                .add("feature", "1")
                .add("allow_comments", "1")
                .add("is_ai_generated", isAiGenerated ? "1" : "0")
                .add("is_mature", isMature ? "1" : "0");

        Request request = new Request.Builder()
                .url(STASH_PUBLISH_URL)
                .post(builder.build())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body().string();
            JSONObject json = new JSONObject(body);

            if (json.has("error")) {
                throw new IOException("Publish failed: " + json.getString("error_description"));
            }

            if (!"success".equals(json.optString("status"))) {
                throw new IOException("Publish failed: " + body);
            }

            return json.getString("url");
        }
    }

    private String extractFileName(String url, String fallbackTitle) {
        try {
            String path = new URL(url).getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (!name.isEmpty() && name.contains(".")) {
                return name;
            }
        } catch (Exception ignored) {}
        return fallbackTitle.replaceAll("[^a-zA-Z0-9_-]", "_") + ".png";
    }

    private String guessMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/png";
    }
}
