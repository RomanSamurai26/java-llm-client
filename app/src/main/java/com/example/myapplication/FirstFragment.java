package com.example.myapplication;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.databinding.FragmentFirstBinding;

import ApiClient.ApiClient;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private static final String CHAT_FILE = "chat_history.json";
    private static final String TASKS_FILE = "tasks.json";
    private boolean useSay1 = true;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadChatHistory();

        chatAdapter = new ChatAdapter(chatMessages);
        binding.recyclerViewChat.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewChat.setAdapter(chatAdapter);

        // Ustawienie początkowego zdjęcia Sebastiana
        binding.ivFixedBotAvatar.setImageResource(R.drawable.sebastian_wheit);
        updateBotPresence(false);

        if (!chatMessages.isEmpty()) {
            binding.scrollViewChat.post(() -> binding.scrollViewChat.fullScroll(View.FOCUS_DOWN));
        }

        binding.buttonFirst.setOnClickListener(v -> {
            String userPrompt = binding.edittextPrompt.getText().toString().trim();
            if (userPrompt.isEmpty()) {
                Toast.makeText(getContext(), "Wpisz prompt!", Toast.LENGTH_SHORT).show();
                return;
            }

            binding.ivFixedBotAvatar.setImageResource(R.drawable.sebastian_think);

            addMessage(new ChatMessage(userPrompt, true));
            binding.edittextPrompt.setText("");
            binding.buttonFirst.setEnabled(false);
            hideKeyboard();

            String currentDate = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(new java.util.Date());
            String tasksContext = loadTasksForContext();
            String fullPrompt = "DZISIEJSZA DATA: " + currentDate + "\n\nKONTEKST ZADAŃ:\n" + tasksContext + "\n\nPYTANIE: " + userPrompt;

            ApiClient.sendPrompt(fullPrompt, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            binding.ivFixedBotAvatar.setImageResource(R.drawable.sebastian_wheit);
                            addMessage(new ChatMessage("Błąd połączenia: " + e.getMessage(), false));
                            binding.buttonFirst.setEnabled(true);
                        });
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response resp = response) {
                        String responseBody = resp.body().string();
                        if (resp.isSuccessful()) {
                            JSONObject json = new JSONObject(responseBody);
                            String rawText = json.getJSONArray("candidates")
                                    .getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                    .getJSONObject(0)
                                    .getString("text");

                            processModelResponse(rawText);
                        } else {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    binding.ivFixedBotAvatar.setImageResource(R.drawable.sebastian_wheit);
                                    addMessage(new ChatMessage("Błąd serwera (kod " + resp.code() + ").", false));
                                    binding.buttonFirst.setEnabled(true);
                                });
                            }
                        }
                    } catch (Exception e) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                binding.ivFixedBotAvatar.setImageResource(R.drawable.sebastian_wheit);
                                addMessage(new ChatMessage("Błąd przetwarzania: " + e.getMessage(), false));
                                binding.buttonFirst.setEnabled(true);
                            });
                        }
                    }
                }
            });
        });
    }

    private void updateBotPresence(boolean isNewBotMessage) {
        String lastBotMsg = null;
        for (int i = chatMessages.size() - 1; i >= 0; i--) {
            if (!chatMessages.get(i).isUser) {
                lastBotMsg = chatMessages.get(i).text;
                break;
            }
        }

        if (lastBotMsg != null) {
            binding.layoutBotPresence.setVisibility(View.VISIBLE);
            binding.tvLastBotMessage.setText(lastBotMsg);
            
            if (isNewBotMessage) {
                if (useSay1) {
                    binding.ivFixedBotAvatar.setImageResource(R.drawable.sebastian_say_1);
                } else {
                    binding.ivFixedBotAvatar.setImageResource(R.drawable.sebastian_say_2);
                }
                useSay1 = !useSay1;
            }
        } else {
            binding.layoutBotPresence.setVisibility(View.GONE);
            binding.ivFixedBotAvatar.setImageResource(R.drawable.sebastian_wheit);
        }
        
        binding.scrollViewChat.post(() -> binding.scrollViewChat.fullScroll(View.FOCUS_DOWN));
    }

    private void processModelResponse(String rawText) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            String visibleText = rawText;
            try {
                if (rawText.contains("ACTION_JSON:")) {
                    String[] parts = rawText.split("ACTION_JSON:");
                    visibleText = parts[0].trim();
                    String jsonPart = parts[1].trim();
                    
                    JSONObject actionObj = new JSONObject(jsonPart);
                    String action = actionObj.getString("action");

                    if ("ADD".equals(action)) {
                        addTaskFromAI(actionObj.getJSONObject("task"));
                    } else if ("DELETE".equals(action)) {
                        deleteTaskFromAI(actionObj.getInt("id"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            addMessage(new ChatMessage(visibleText, false));
            binding.buttonFirst.setEnabled(true);
        });
    }

    private void addTaskFromAI(JSONObject taskJson) throws Exception {
        List<SecondFragment.Task> tasks = loadAllTasks();
        List<SecondFragment.Task> doneTasks = loadDoneTasks();
        
        int maxId = 0;
        for (SecondFragment.Task t : tasks) if (t.id > maxId) maxId = t.id;
        for (SecondFragment.Task t : doneTasks) if (t.id > maxId) maxId = t.id;
        int newId = maxId + 1;

        String type = taskJson.optString("type", "E");
        String extraInfo = taskJson.optString("extraInfo", "");

        if ("R".equals(type)) {
            if (extraInfo.isEmpty() || !extraInfo.matches("\\d+")) {
                extraInfo = "7";
            }
        } else if ("L".equals(type)) {
            if (!extraInfo.contains("/")) {
                if (extraInfo.matches("\\d+")) {
                    extraInfo = "0/" + extraInfo;
                } else {
                    extraInfo = "0/3";
                }
            }
        }
        
        SecondFragment.Task newTask = new SecondFragment.Task(
            newId,
            taskJson.optString("description", "Bez opisu"),
            taskJson.optString("deadline", "dd-MM-yyyy"),
            taskJson.optInt("coolness", 3),
            taskJson.optInt("estimatedTime", 0),
            type,
            extraInfo
        );
        
        tasks.add(newTask);
        saveAllTasks(tasks);
        Toast.makeText(getContext(), "Lokaj dodał zadanie: " + newTask.description, Toast.LENGTH_SHORT).show();
    }

    private void deleteTaskFromAI(int taskId) throws Exception {
        List<SecondFragment.Task> tasks = loadAllTasks();
        boolean removed = false;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id == taskId) {
                tasks.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            saveAllTasks(tasks);
            Toast.makeText(getContext(), "Lokaj usunął zadanie.", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideKeyboard() {
        View view = getView();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private List<SecondFragment.Task> loadDoneTasks() {
        List<SecondFragment.Task> list = new ArrayList<>();
        try (FileInputStream fis = requireContext().openFileInput("done_tasks.json");
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                list.add(SecondFragment.Task.fromJSON(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private List<SecondFragment.Task> loadAllTasks() {
        List<SecondFragment.Task> list = new ArrayList<>();
        try (FileInputStream fis = requireContext().openFileInput(TASKS_FILE);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                list.add(SecondFragment.Task.fromJSON(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void saveAllTasks(List<SecondFragment.Task> tasks) throws Exception {
        JSONArray arr = new JSONArray();
        for (SecondFragment.Task t : tasks) arr.put(t.toJSON());
        try (FileOutputStream fos = requireContext().openFileOutput(TASKS_FILE, Context.MODE_PRIVATE)) {
            fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private String loadTasksForContext() {
        List<SecondFragment.Task> tasks = loadAllTasks();
        if (tasks.isEmpty()) return "(Brak zadań)";
        StringBuilder sb = new StringBuilder();
        for (SecondFragment.Task t : tasks) {
            sb.append(String.format("#%d: %s (Deadline: %s, Rodzaj: %s)\n", t.id, t.description, t.deadline, t.type));
        }
        return sb.toString();
    }

    private void addMessage(ChatMessage message) {
        chatMessages.add(message);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        saveChatHistory();
        updateBotPresence(!message.isUser);
    }

    private void saveChatHistory() {
        try {
            JSONArray arr = new JSONArray();
            for (ChatMessage m : chatMessages) {
                JSONObject o = new JSONObject();
                o.put("text", m.text); o.put("isUser", m.isUser);
                arr.put(o);
            }
            try (FileOutputStream fos = requireContext().openFileOutput(CHAT_FILE, Context.MODE_PRIVATE)) {
                fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadChatHistory() {
        chatMessages.clear();
        try (FileInputStream fis = requireContext().openFileInput(CHAT_FILE);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                chatMessages.add(new ChatMessage(o.getString("text"), o.getBoolean("isUser")));
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroyView() { super.onDestroyView(); binding = null; }

    private static class ChatMessage {
        String text; boolean isUser;
        ChatMessage(String text, boolean isUser) { this.text = text; this.isUser = isUser; }
    }

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<ChatMessage> messages;
        ChatAdapter(List<ChatMessage> m) { this.messages = m; }
        @Override public int getItemViewType(int p) { return messages.get(p).isUser ? 1 : 2; }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int v) {
            View view = LayoutInflater.from(p.getContext()).inflate(v == 1 ? R.layout.item_chat_user : R.layout.item_chat_bot, p, false);
            return v == 1 ? new UserVH(view) : new BotVH(view);
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
            if (h instanceof UserVH) ((UserVH) h).t.setText(messages.get(p).text);
            else {
                BotVH botVH = (BotVH) h;
                if (p == findLastBotMessageIndex()) {
                    botVH.itemView.setVisibility(View.GONE);
                    botVH.itemView.setLayoutParams(new RecyclerView.LayoutParams(0, 0));
                } else {
                    botVH.itemView.setVisibility(View.VISIBLE);
                    botVH.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    botVH.t.setText(messages.get(p).text);
                }
            }
        }

        private int findLastBotMessageIndex() {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (!messages.get(i).isUser) return i;
            }
            return -1;
        }

        @Override public int getItemCount() { return messages.size(); }
        class UserVH extends RecyclerView.ViewHolder { TextView t; UserVH(View v) { super(v); t = v.findViewById(R.id.text_message_user); } }
        class BotVH extends RecyclerView.ViewHolder { TextView t; BotVH(View v) { super(v); t = v.findViewById(R.id.text_message_bot); } }
    }
}
