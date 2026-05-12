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
            "W każdym zapytaniu otrzymasz dzisiejszą datę - używaj jej do precyzyjnego planowania terminów.\n" +
            "Wnioskuj na podstawie wypowiedzi, czy jest to zapytanie o zadania, prośba o dodanie zadania, czy informacja o usunięciu zadania.\n" +
            "Jeśli chcesz wykonać akcję, na końcu swojej odpowiedzi dodaj specjalny blok JSON w formacie: \n" +
            "ACTION_JSON: {\"action\": \"ADD\", \"task\": {\"description\": \"...\", \"deadline\": \"dd-MM-yyyy\", \"coolness\": 1-5, \"estimatedTime\": min, \"type\": \"H/E/R/L/D\", \"extraInfo\": \"...\"}}\n" +
            "lub\n" +
            "ACTION_JSON: {\"action\": \"DELETE\", \"id\": ID_ZADANIA}\n" +
                    // poniższe cztery linijki zostały dodane przeze mnie ~Dorota
            "Dla 'extraInfo' używaj:\n" +
            "- Dla R (Recurring): Liczba dni (np. '7')\n" +
            "- Dla L (Large): Całkowita liczba kroków (np. '5')\n" +
            "- Dla D (Dependent): ID zadania nadrzędnego (np. '12')\n" +
            "Gdy dodajesz zadanie, domyślaj się brakujących parametrów na podstawie kontekstu lub ustaw rozsądne wartości domyślne.";

    private static final String SORTING_SYSTEM_INSTRUCTION =
            "You are a master task scheduler. Use the provided today's date to calculate urgency. Analyze the tasks and return a JSON object with the optimal order of task IDs based on these rules:\n" +
            "1. D (Dependent): 'extraInfo' contains the ID of a parent task. This task MUST be scheduled AFTER its parent.\n" +
            "2. H (Hard): Fixed deadline tasks. These have the highest priority to ensure completion on time.\n" +
            "3. R (Recurring): 'extraInfo' is the frequency in days (e.g., '7'). These repeat regularly; prioritize them as their next due date approaches.\n" +
            "4. L (Large): 'extraInfo' is progress formatted as 'current/total' (e.g., '2/5'). These are multi-step tasks; prioritize them based on the amount of work remaining relative to the deadline.\n" +
            "5. E (Elastic): Flexible tasks with no strict time requirements.\n" +
            "Priority Order: H > R > L > E (with D always after parent). Tie-breakers: 1. Earlier deadline first. 2. Lower 'coolness' (unpleasant tasks first to get them over with).\n" +
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
