package ApiClient;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    private static final String API_KEY = "";
    private static final String MODEL_NAME = "gemini-2.5-flash-lite";

    private static final String SYSTEM_INSTRUCTION = 
            "Jesteś Lokajem asystentem, który pomaga swojemu panu realizować zadania i optymalizować ich kolejność. " +
            "Musisz zwracać się z szacunkiem do swojego pana, używając zwrotu 'panie' lub 'paniczu'.\n\n" +
            "Wnioskuj na podstawie wypowiedzi, czy jest to zapytanie o zadania, prośba o dodanie zadania, czy informacja o usunięciu zadania.\n" +
            "Jeśli chcesz wykonać akcję, na końcu swojej odpowiedzi dodaj specjalny blok JSON w formacie: \n" +
            "ACTION_JSON: {\"action\": \"ADD\", \"task\": {\"description\": \"...\", \"deadline\": \"dd-mm-yyyy\", \"coolness\": 1-5, \"estimatedTime\": min, \"type\": \"H/E/R/L/D\", \"extraInfo\": \"...\"}}\n" +
            "lub\n" +
            "ACTION_JSON: {\"action\": \"DELETE\", \"id\": ID_ZADANIA}\n" +
            "Gdy dodajesz zadanie, domyślaj się brakujących parametrów na podstawie kontekstu lub ustaw rozsądne wartości domyślne.";

    private static final String SORTING_SYSTEM_INSTRUCTION =
            "Schedule tasks using: Dependencies (task after its dependency), Priority: H > R (if due today) > E, " +
            "with L like E but earlier if large vs time to deadline R only if last_done + interval ≤ today " +
            "Earlier deadline = higher priority Tie-breaker: lower coolness first. " +
            "types: H - hard, E - elastic, R - recurring, L - large, D - dependent " +
            "Return ONLY JSON: { \"order\": [task_ids_in_order] }";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public static void sendPrompt(String prompt, Callback callback) {
        try {
            String finalPrompt = SYSTEM_INSTRUCTION + "\n\n" + prompt;

            JSONObject json = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject textObj = new JSONObject();

            textObj.put("text", finalPrompt);
            parts.put(textObj);
            contentObj.put("parts", parts);
            contents.put(contentObj);
            json.put("contents", contents);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            // Używamy endpointu v1 dla stabilności
            String url = "https://generativelanguage.googleapis.com/v1/models/" + MODEL_NAME + ":generateContent?key=" + API_KEY;

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(callback);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void sendSortingPrompt(String prompt, Callback callback) {
        try {
            String finalPrompt = SORTING_SYSTEM_INSTRUCTION + "\n\n" + prompt;

            JSONObject json = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject textObj = new JSONObject();

            textObj.put("text", finalPrompt);
            parts.put(textObj);
            contentObj.put("parts", parts);
            contents.put(contentObj);
            json.put("contents", contents);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            // Używamy endpointu v1 dla stabilności
            String url = "https://generativelanguage.googleapis.com/v1/models/" + MODEL_NAME + ":generateContent?key=" + API_KEY;

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
