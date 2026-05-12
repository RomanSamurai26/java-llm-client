package com.example.myapplication;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.databinding.FragmentSecondBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ApiClient.ApiClient;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;
    private TaskAdapter adapter;
    private final List<Task> taskList = new ArrayList<>();
    private final List<Task> doneTaskList = new ArrayList<>();
    private boolean showingDone = false;
    private static final String TASKS_FILE = "tasks.json";
    private static final String DONE_TASKS_FILE = "done_tasks.json";

    // --- Klasa modelu zadania ---
    public static class Task {
        int id;
        String description;
        String deadline;
        int coolness;
        int estimatedTime;
        String type;
        String extraInfo; // Time spent / Parent ID / etc.

        public Task(int id, String description, String deadline, int coolness, int estimatedTime, String type, String extraInfo) {
            this.id = id;
            this.description = description;
            this.deadline = deadline;
            this.coolness = coolness;
            this.estimatedTime = estimatedTime;
            this.type = type;
            this.extraInfo = extraInfo;
        }

        public JSONObject toJSON() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("description", description);
            obj.put("deadline", deadline);
            obj.put("coolness", coolness);
            obj.put("estimatedTime", estimatedTime);
            obj.put("type", type);
            obj.put("extraInfo", extraInfo);
            return obj;
        }

        public static Task fromJSON(JSONObject obj) throws Exception {
            return new Task(
                obj.getInt("id"),
                obj.getString("description"),
                obj.getString("deadline"),
                obj.getInt("coolness"),
                obj.getInt("estimatedTime"),
                obj.getString("type"),
                obj.getString("extraInfo")
            );
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadTasks();
        loadDoneTasks();

        adapter = new TaskAdapter(taskList);
        binding.recyclerViewTasks.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewTasks.setAdapter(adapter);

        binding.buttonToggleDone.setOnClickListener(v -> {
            showingDone = !showingDone;
            if (showingDone) {
                binding.buttonToggleDone.setText("Pokaż aktywne");
                binding.sortTasksButton.setEnabled(false);
                binding.titleTasks.setText("Wykonane Zadania");
                binding.formContainer.setVisibility(View.GONE);
                binding.buttonAddTask.setVisibility(View.GONE);
                adapter.setTasks(doneTaskList);
            } else {
                binding.buttonToggleDone.setText("Pokaż wykonane");
                binding.sortTasksButton.setEnabled(true);
                binding.titleTasks.setText("Nowe Zadanie");
                binding.formContainer.setVisibility(View.VISIBLE);
                binding.buttonAddTask.setVisibility(View.VISIBLE);
                adapter.setTasks(taskList);
            }
        });

        // Setup Task Type Dropdown
        String[] types = {"Hard", "Elastic", "Recurring", "Dependent"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, types);
        binding.etType.setAdapter(typeAdapter);
        binding.etType.setText("Elastic", false); // Domyślna wartość

        binding.etDeadline.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(),
                    (view1, year1, monthOfYear, dayOfMonth) -> {
                        String selectedDate = String.format(Locale.getDefault(), "%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, year1);
                        binding.etDeadline.setText(selectedDate);
                    }, year, month, day);
            datePickerDialog.show();
        });

        binding.buttonAddTask.setOnClickListener(v -> {
            String desc = binding.etDesc.getText().toString().trim();
            String deadline = binding.etDeadline.getText().toString().trim();
            String coolnessStr = binding.etCoolness.getText().toString().trim();
            String timeStr = binding.etTime.getText().toString().trim();
            String typeSelected = binding.etType.getText().toString().trim();
            String extra = binding.etExtra.getText().toString().trim();

            if (desc.isEmpty() || deadline.isEmpty()) {
                Toast.makeText(getContext(), "Opis i Deadline są wymagane!", Toast.LENGTH_SHORT).show();
                return;
            }

            String type = "";
            switch (typeSelected) {
                case "Hard": type = "H"; break;
                case "Elastic": type = "E"; break;
                case "Recurring": type = "R"; break;
                case "Dependent": type = "D"; break;
                default: type = "E"; break; // Domyślnie Elastic
            }

            int maxId = 0;
            for (Task t : taskList) {
                if (t.id > maxId) maxId = t.id;
            }
            int id = maxId + 1;
            int coolness = coolnessStr.isEmpty() ? 3 : Integer.parseInt(coolnessStr);
            int time = timeStr.isEmpty() ? 0 : Integer.parseInt(timeStr);

            Task newTask = new Task(id, desc, deadline, coolness, time, type, extra);
            taskList.add(newTask);
            adapter.notifyItemInserted(taskList.size() - 1);
            saveTasks();
            clearInputs();
        });
        binding.sortTasksButton.setOnClickListener(v -> {
            binding.errorMessage.setVisibility(View.GONE);
            binding.sortTasksButton.setEnabled(false);
            String today = new SimpleDateFormat("dd-MM-yyyy").format(new Date());
            String fullPrompt = " Today: " + today +" Tasks: " +tasksToJSON();

            ApiClient.sendSortingPrompt(fullPrompt, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            binding.errorMessage.setVisibility(View.VISIBLE);
                            String errorMessage = "Błąd połączenia: " + e.getMessage();
                            binding.errorMessage.setText(errorMessage);
                            binding.sortTasksButton.setEnabled(true);
                        });
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response resp = response) {
                        String responseBody = resp.body().string();
                        if (resp.isSuccessful()) {
                            // wyciągnięcie tekstu z odpowiedzi AI
                            JSONObject json = new JSONObject(responseBody);
                            String rawText = json.getJSONArray("candidates")
                                    .getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                    .getJSONObject(0)
                                    .getString("text");

                            processModelSorting(rawText);
                        } else {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    binding.errorMessage.setVisibility(View.VISIBLE);
                                    String errorMessage = "Błąd serwera (kod " + resp.code();
                                    binding.errorMessage.setText(errorMessage);
                                    binding.sortTasksButton.setEnabled(true);
                                });
                            }
                        }
                    } catch (Exception e) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                binding.errorMessage.setVisibility(View.VISIBLE);
                                String errorMessage = "Błąd przetwarzania: " + e.getMessage();
                                binding.errorMessage.setText(errorMessage);
                                binding.sortTasksButton.setEnabled(true);
                            });
                        }
                    }
                }
            });
        });
    }

    private void processModelSorting(String rawText) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            try {
                // 1. Wyciągamy same liczby i przecinki (usuwamy { } i inny tekst)
                String cleaned = rawText.replaceAll("[^0-9,]", "");
                String[] idStrings = cleaned.split(",");

                List<Task> sortedList = new ArrayList<>();
                 // 2. Dopasowujemy taski z obecnej listy do ID zwróconych przez AI
                for (String idStr : idStrings) {
                    if (idStr.trim().isEmpty()) continue;
                    int id = Integer.parseInt(idStr.trim());

                    for (Task t : taskList) {
                        if (t.id == id) {
                            sortedList.add(t);
                            break;
                        }
                    }
                }

                // 3. Dodajemy brakujące zadania (jeśli AI o jakimś zapomniało)
                for (Task t : taskList) {
                    if (!sortedList.contains(t)) {
                        sortedList.add(t);
                    }
                }

                // 4. Odświeżamy listę i UI
                taskList.clear();
                taskList.addAll(sortedList);
                adapter.notifyDataSetChanged();
                saveTasks();

            } catch (Exception e) {
                binding.errorMessage.setVisibility(View.VISIBLE);
                binding.errorMessage.setText("Błąd podczas sortowania: " + e.getMessage());
            } finally {
                binding.sortTasksButton.setEnabled(true);
            }
        });
    }


    private void clearInputs() {
        binding.etDesc.setText("");
        binding.etDeadline.setText("");
        binding.etCoolness.setText("");
        binding.etTime.setText("");
        binding.etType.setText("Elastic", false);
        binding.etExtra.setText("");
    }
    private String tasksToJSON() {
        JSONArray jsonArray = new JSONArray();
        try {
            for (Task t : taskList) {
                jsonArray.put(t.toJSON());
            }
        } catch (Exception e) { e.printStackTrace(); }
        return jsonArray.toString();
    }

    private void saveTasks() {
        saveListToFile(taskList, TASKS_FILE);
    }

    private void saveDoneTasks() {
        saveListToFile(doneTaskList, DONE_TASKS_FILE);
    }

    private void saveListToFile(List<Task> list, String filename) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Task t : list) {
                jsonArray.put(t.toJSON());
            }
            try (FileOutputStream fos = requireContext().openFileOutput(filename, Context.MODE_PRIVATE)) {
                fos.write(jsonArray.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadTasks() {
        loadListFromFile(taskList, TASKS_FILE);
    }

    private void loadDoneTasks() {
        loadListFromFile(doneTaskList, DONE_TASKS_FILE);
    }

    private void loadListFromFile(List<Task> list, String filename) {
        list.clear();
        try (FileInputStream fis = requireContext().openFileInput(filename);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray jsonArray = new JSONArray(sb.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(Task.fromJSON(jsonArray.getJSONObject(i)));
            }
        } catch (IOException ignored) {} catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
        private List<Task> tasks;
        public TaskAdapter(List<Task> tasks) { this.tasks = tasks; }

        public void setTasks(List<Task> newTasks) {
            this.tasks = newTasks;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
            return new TaskViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
            Task task = tasks.get(position);
            holder.tvId.setText("#" + task.id);
            holder.tvDesc.setText(task.description);
            holder.tvDeadline.setText("Deadline: " + task.deadline);

            if (showingDone) {
                holder.doneBtn.setImageResource(android.R.drawable.ic_menu_revert);
                holder.doneBtn.setColorFilter(getResources().getColor(android.R.color.holo_orange_dark, null));
            } else {
                holder.doneBtn.setImageResource(android.R.drawable.checkbox_on_background);
                holder.doneBtn.setColorFilter(getResources().getColor(android.R.color.holo_green_dark, null));
            }

            holder.doneBtn.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    Task t = tasks.remove(pos);
                    notifyItemRemoved(pos);
                    if (showingDone) {
                        taskList.add(t);
                    } else {
                        doneTaskList.add(0, t);
                    }
                    saveTasks();
                    saveDoneTasks();
                }
            });

            holder.deleteBtn.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    tasks.remove(pos);
                    notifyItemRemoved(pos);
                    if (showingDone) {
                        saveDoneTasks();
                    } else {
                        saveTasks();
                    }
                }
            });
        }

        @Override
        public int getItemCount() { return tasks.size(); }

        class TaskViewHolder extends RecyclerView.ViewHolder {
            TextView tvId, tvDesc, tvDeadline;
            ImageButton deleteBtn, doneBtn;
            public TaskViewHolder(@NonNull View itemView) {
                super(itemView);
                tvId = itemView.findViewById(R.id.tv_task_id);
                tvDesc = itemView.findViewById(R.id.tv_task_description);
                tvDeadline = itemView.findViewById(R.id.tv_task_deadline);
                deleteBtn = itemView.findViewById(R.id.button_delete_task);
                doneBtn = itemView.findViewById(R.id.button_done_task);
            }
        }
    }
}
