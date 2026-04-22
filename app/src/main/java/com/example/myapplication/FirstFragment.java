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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Wczytaj historię z pliku
        loadChatHistory();

        chatAdapter = new ChatAdapter(chatMessages);
        binding.recyclerViewChat.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewChat.setAdapter(chatAdapter);

        if (!chatMessages.isEmpty()) {
            binding.recyclerViewChat.scrollToPosition(chatMessages.size() - 1);
        }

        binding.buttonFirst.setOnClickListener(v -> {
            String userPrompt = binding.edittextPrompt.getText().toString().trim();
            if (userPrompt.isEmpty()) {
                Toast.makeText(getContext(), "Wpisz prompt!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Dodajemy widoczną wiadomość użytkownika do listy
            addMessage(new ChatMessage(userPrompt, true));
            binding.edittextPrompt.setText("");
            binding.buttonFirst.setEnabled(false);

            // Przygotowujemy kontekst z zadaniami dla AI
            String tasksContext = loadTasksForContext();
            String fullPrompt = "Oto lista moich zadań:\n" + tasksContext + 
                               "\n\nUżytkownik pyta: " + userPrompt + 
                               "\nOdpowiedz na podstawie moich zadań jeśli pytanie ich dotyczy.";

            ApiClient.sendPrompt(fullPrompt, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            addMessage(new ChatMessage("Błąd połączenia: " + e.getMessage(), false));
                            binding.buttonFirst.setEnabled(true);
                        });
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response resp = response) {
                        String responseBody = resp.body().string();
                        String resultText;

                        if (resp.isSuccessful()) {
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                resultText = json.getJSONArray("candidates")
                                        .getJSONObject(0)
                                        .getJSONObject("content")
                                        .getJSONArray("parts")
                                        .getJSONObject(0)
                                        .getString("text");
                            } catch (Exception e) {
                                resultText = "Błąd parsowania: " + e.getMessage();
                            }
                        } else {
                            resultText = "Błąd serwera (kod " + resp.code() + ")";
                        }

                        final String finalResult = resultText;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                addMessage(new ChatMessage(finalResult, false));
                                binding.buttonFirst.setEnabled(true);
                            });
                        }
                    }
                }
            });
        });
    }

    private String loadTasksForContext() {
        try (FileInputStream fis = requireContext().openFileInput("tasks.json");
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            
            JSONArray jsonArray = new JSONArray(sb.toString());
            if (jsonArray.length() == 0) return "(Brak zadań na liście)";

            StringBuilder tasksList = new StringBuilder();
            for (int i = 0; i < jsonArray.length(); i++) {
                tasksList.append("- ").append(jsonArray.getString(i)).append("\n");
            }
            return tasksList.toString();
        } catch (IOException e) {
            return "(Nie udało się wczytać pliku zadań)";
        } catch (Exception e) {
            return "(Błąd odczytu zadań)";
        }
    }

    private void addMessage(ChatMessage message) {
        chatMessages.add(message);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        binding.recyclerViewChat.scrollToPosition(chatMessages.size() - 1);
        saveChatHistory();
    }

    private void saveChatHistory() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (ChatMessage msg : chatMessages) {
                JSONObject obj = new JSONObject();
                obj.put("text", msg.text);
                obj.put("isUser", msg.isUser);
                jsonArray.put(obj);
            }
            try (FileOutputStream fos = requireContext().openFileOutput(CHAT_FILE, Context.MODE_PRIVATE)) {
                fos.write(jsonArray.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadChatHistory() {
        chatMessages.clear();
        try (FileInputStream fis = requireContext().openFileInput(CHAT_FILE);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            
            JSONArray jsonArray = new JSONArray(sb.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                chatMessages.add(new ChatMessage(obj.getString("text"), obj.getBoolean("isUser")));
            }
        } catch (IOException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class ChatMessage {
        String text;
        boolean isUser;
        ChatMessage(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<ChatMessage> messages;
        private static final int VIEW_TYPE_USER = 1;
        private static final int VIEW_TYPE_BOT = 2;

        ChatAdapter(List<ChatMessage> messages) {
            this.messages = messages;
        }

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).isUser ? VIEW_TYPE_USER : VIEW_TYPE_BOT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_USER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
                return new UserViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_bot, parent, false);
                return new BotViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessage message = messages.get(position);
            if (holder instanceof UserViewHolder) {
                ((UserViewHolder) holder).textUser.setText(message.text);
            } else {
                ((BotViewHolder) holder).textBot.setText(message.text);
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView textUser;
            UserViewHolder(View itemView) {
                super(itemView);
                textUser = itemView.findViewById(R.id.text_message_user);
            }
        }

        class BotViewHolder extends RecyclerView.ViewHolder {
            TextView textBot;
            BotViewHolder(View itemView) {
                super(itemView);
                textBot = itemView.findViewById(R.id.text_message_bot);
            }
        }
    }
}
