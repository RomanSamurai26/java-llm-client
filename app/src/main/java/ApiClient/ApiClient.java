package ApiClient;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    // Twój klucz z gemini aistudio.google.com
    private static final String API_KEY = "AIzaSyCCmwzoVGufTvvQfK64b8sIygiEs4TxFzI";
    
    // Model Gemini 2.0 Flash - najszybszy obecnie dostępny
    private static final String MODEL_NAME = "gemini-2.5-flash-lite";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public static void sendPrompt(String prompt, Callback callback) {
        try {
            // Budowanie struktury JSON dla Gemini
            JSONObject json = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject textObj = new JSONObject();

            textObj.put("text", prompt);
            parts.put(textObj);
            contentObj.put("parts", parts);
            contents.put(contentObj);
            json.put("contents", contents);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            // Używamy endpointu v1beta dla najnowszych modeli
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent?key=" + API_KEY;

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(callback);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
