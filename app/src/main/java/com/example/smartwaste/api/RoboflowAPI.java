package com.example.smartwaste.api;

import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class RoboflowAPI {

    private static final String TAG = "RoboflowAPI";
    private static final String API_URL = "https://serverless.roboflow.com/infer/workflows/cohya/detect-count-and-visualize-2";
    private static final String API_KEY = "VQCfMYZF4XPYpufU46nk";

    private final OkHttpClient client;

    public static class Prediction {
        public final String className;
        public final float confidence;
        public final RectF boundingBox;

        public Prediction(String className, float confidence, RectF boundingBox) {
            this.className = className;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }
    }

    public interface ApiCallback {
        void onSuccess(List<Prediction> predictions);
        void onError(String error);
    }

    public RoboflowAPI() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void detectGarbage(String base64ImageData, ApiCallback callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("api_key", API_KEY);
            JSONObject imageObject = new JSONObject();
            imageObject.put("type", "base64");
            imageObject.put("value", base64ImageData);
            JSONObject inputs = new JSONObject();
            inputs.put("image", imageObject);
            payload.put("inputs", inputs);

            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(payload.toString(), JSON);

            Request request = new Request.Builder()
                    .url(API_URL).addHeader("Content-Type", "application/json").post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    callback.onError("Koneksi gagal: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    // PERBAIKAN: Logika penanganan respons yang lebih aman
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            String errorBody = responseBody != null ? responseBody.string() : "No response body";
                            callback.onError("HTTP Error: " + response.code() + " | " + errorBody);
                            return;
                        }

                        if (responseBody == null) {
                            callback.onError("Gagal: Response body kosong.");
                            return;
                        }

                        String bodyString = responseBody.string();
                        Log.d(TAG, "Full Response: " + bodyString);
                        List<Prediction> predictions = parseResponse(bodyString);
                        callback.onSuccess(predictions);

                    } catch (IOException | JSONException e) {
                        Log.e(TAG, "Error processing response", e);
                        callback.onError("Gagal memproses respons: " + e.getMessage());
                    }
                }
            });
        } catch (JSONException e) {
            callback.onError("Gagal membuat request: " + e.getMessage());
        }
    }

    private List<Prediction> parseResponse(String responseBody) throws JSONException {
        List<Prediction> predictionList = new ArrayList<>();
        JSONObject jsonResponse = new JSONObject(responseBody);

        JSONArray outputs = jsonResponse.optJSONArray("outputs");
        if (outputs == null) return predictionList;

        for (int i = 0; i < outputs.length(); i++) {
            JSONObject output = outputs.getJSONObject(i);
            Object predictionsObj = output.opt("predictions");

            JSONArray predictions = null;
            if (predictionsObj instanceof JSONObject && ((JSONObject) predictionsObj).has("predictions")) {
                predictions = ((JSONObject) predictionsObj).getJSONArray("predictions");
            } else if (predictionsObj instanceof JSONArray) {
                predictions = (JSONArray) predictionsObj;
            }

            if (predictions == null) continue;

            for (int j = 0; j < predictions.length(); j++) {
                JSONObject p = predictions.getJSONObject(j);

                // PERBAIKAN: Menggunakan opt... untuk menghindari crash jika key tidak ada
                String className = getReadableClassName(p.optString("class", "Unknown"));
                float confidence = (float) p.optDouble("confidence", 0.0);

                float x = (float) p.optDouble("x", 0.0);
                float y = (float) p.optDouble("y", 0.0);
                float width = (float) p.optDouble("width", 0.0);
                float height = (float) p.optDouble("height", 0.0);

                // Hanya tambahkan jika datanya valid
                if (width > 0 && height > 0) {
                    float left = x - (width / 2);
                    float top = y - (height / 2);
                    float right = x + (width / 2);
                    float bottom = y + (height / 2);

                    RectF box = new RectF(left, top, right, bottom);
                    predictionList.add(new Prediction(className, confidence, box));
                }
            }
        }
        return predictionList;
    }

    private String getReadableClassName(String className) {
        switch (className.toLowerCase()) {
            case "b3": return "Sampah B3";
            case "anorganik": return "Sampah Anorganik";
            case "organik": return "Sampah Organik";
            default: return className;
        }
    }
}
