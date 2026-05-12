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
    private Task taskToEdit = null;
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
            hideKeyboard();
            if (showingDone) {
                binding.buttonToggleDone.setText(R.string.show_active);
                binding.sortTasksButton.setEnabled(false);
                binding.titleTasks.setText(R.string.done_tasks);
                binding.formContainer.setVisibility(View.GONE);
                binding.buttonAddTask.setVisibility(View.GONE);
                adapter.setTasks(doneTaskList);
            } else {
                binding.buttonToggleDone.setText(R.string.show_done);
                binding.sortTasksButton.setEnabled(true);
                binding.titleTasks.setText(R.string.new_task);
                binding.formContainer.setVisibility(View.VISIBLE);
                binding.buttonAddTask.setVisibility(View.VISIBLE);
                adapter.setTasks(taskList);
            }
        });

        // Setup Task Type Dropdown
        String[] types = {"Hard", "Elastic", "Recurring", "Dependent", "Large"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, types);
        binding.etType.setAdapter(typeAdapter);
        binding.etType.setText("Elastic", false); // Domyślna wartość

        binding.etType.setOnItemClickListener((parent, view1, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            updateExtraFieldVisibility(selected);
        });

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
                Toast.makeText(getContext(), R.string.error_required, Toast.LENGTH_SHORT).show();
                return;
            }

            String type = "";
            String finalExtra = extra;

            switch (typeSelected) {
                case "Hard": type = "H"; break;
                case "Elastic": type = "E"; break;
                case "Recurring": type = "R"; break;
                case "Large":
                    type = "L";
                    if (taskToEdit != null && "L".equals(taskToEdit.type)) {
                        // Jeśli edytujemy Large, zachowaj postęp
                        String[] oldParts = taskToEdit.extraInfo.split("/");
                        finalExtra = oldParts[0] + "/" + extra;
                    } else {
                        finalExtra = "0/" + extra;
                    }
                    break;
                case "Dependent":
                    type = "D";
                    Object tag = binding.etExtra.getTag();
                    if (tag != null) {
                        finalExtra = tag.toString();
                    } else if (taskToEdit != null && "D".equals(taskToEdit.type)) {
                        finalExtra = taskToEdit.extraInfo;
                    }
                    break;
                default: type = "E"; break; // Domyślnie Elastic
            }

            int coolness = coolnessStr.isEmpty() ? 3 : Integer.parseInt(coolnessStr);
            int time = timeStr.isEmpty() ? 0 : Integer.parseInt(timeStr);

            if (taskToEdit != null) {
                taskToEdit.description = desc;
                taskToEdit.deadline = deadline;
                taskToEdit.coolness = coolness;
                taskToEdit.estimatedTime = time;
                taskToEdit.type = type;
                taskToEdit.extraInfo = finalExtra;
                
                int index = taskList.indexOf(taskToEdit);
                if (index != -1) {
                    adapter.notifyItemChanged(index);
                }
                taskToEdit = null;
                binding.buttonAddTask.setText(R.string.add_task);
                binding.titleTasks.setText(R.string.new_task);
            } else {
                int id = getNextId();
                Task newTask = new Task(id, desc, deadline, coolness, time, type, finalExtra);
                taskList.add(newTask);
                adapter.notifyItemInserted(taskList.size() - 1);
            }
            saveTasks();
            clearInputs();
            hideKeyboard();
        });
        binding.sortTasksButton.setOnClickListener(v -> {
            hideKeyboard();
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
                            String errorMessage = getString(R.string.error_connection, e.getMessage());
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
                                    String errorMessage = getString(R.string.error_server, resp.code());
                                    binding.errorMessage.setText(errorMessage);
                                    binding.sortTasksButton.setEnabled(true);
                                });
                            }
                        }
                    } catch (Exception e) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                binding.errorMessage.setVisibility(View.VISIBLE);
                                String errorMessage = getString(R.string.error_processing, e.getMessage());
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
                String jsonContent = rawText;
                if (rawText.contains("{")) {
                    jsonContent = rawText.substring(rawText.indexOf("{"), rawText.lastIndexOf("}") + 1);
                }

                String[] idStrings = null;
                if (jsonContent.contains("\"order\"")) {
                    try {
                        JSONObject jsonResp = new JSONObject(jsonContent);
                        JSONArray orderArray = jsonResp.getJSONArray("order");
                        idStrings = new String[orderArray.length()];
                        for (int i = 0; i < orderArray.length(); i++) {
                            idStrings[i] = String.valueOf(orderArray.get(i));
                        }
                    } catch (Exception e) {
                        // Fallback if JSON parsing fails
                    }
                }

                if (idStrings == null) {
                    String cleaned = jsonContent.replaceAll("[^0-9,]", " ").trim();
                    idStrings = cleaned.split("\\s*,\\s*|\\s+");
                }

                List<Task> sortedList = new ArrayList<>();
                 // 2. Dopasowujemy taski z obecnej listy do ID zwróconych przez AI
                for (String idStr : idStrings) {
                    if (idStr.trim().isEmpty()) continue;
                    try {
                        int id = Integer.parseInt(idStr.trim());
                        for (Task t : taskList) {
                            if (t.id == id) {
                                if (!sortedList.contains(t)) {
                                    sortedList.add(t);
                                }
                                break;
                            }
                        }
                    } catch (NumberFormatException ignored) {}
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
                binding.errorMessage.setText(getString(R.string.error_sorting, e.getMessage()));
            } finally {
                binding.sortTasksButton.setEnabled(true);
            }
        });
    }


    private void handleRecurringTask(Task t) {
        try {
            if (t.extraInfo == null || t.extraInfo.trim().isEmpty()) {
                return;
            }
            int days = Integer.parseInt(t.extraInfo.trim());
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            Calendar c = Calendar.getInstance();
            
            // Parsujemy obecny deadline i dodajemy X dni
            Date currentDeadlineDate = sdf.parse(t.deadline);
            if (currentDeadlineDate == null) return;
            c.setTime(currentDeadlineDate);
            c.add(Calendar.DAY_OF_YEAR, days);
            String newDeadline = sdf.format(c.getTime());

            // Szukamy najwyższego ID
            int id = getNextId();

            // Tworzymy nową instancję zadania
            Task newTask = new Task(id, t.description, newDeadline, t.coolness, t.estimatedTime, t.type, t.extraInfo);
            taskList.add(newTask);
            
            // Jeśli obecnie wyświetlamy aktywne zadania, odświeżamy adapter
            if (!showingDone) {
                adapter.notifyItemInserted(taskList.size() - 1);
            }
        } catch (NumberFormatException e) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Błąd: Nieprawidłowa liczba dni w zadaniu cyklicznym.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace(); // W razie błędu w extraInfo (brak liczby) po prostu nie odnawiamy
        }
    }

    private void updateExtraFieldVisibility(String type) {
        binding.tilExtra.setVisibility(View.GONE);
        binding.etExtra.setText("");
        binding.etExtra.setAdapter(null);

        if ("Recurring".equals(type) || "Large".equals(type)) {
            binding.tilExtra.setVisibility(View.VISIBLE);
            binding.tilExtra.setHint("Large".equals(type) ? getString(R.string.large_hint) : getString(R.string.recurring_hint));
            binding.etExtra.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            binding.tilExtra.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_NONE);
        } else if ("Dependent".equals(type)) {
            binding.tilExtra.setVisibility(View.VISIBLE);
            binding.tilExtra.setHint(getString(R.string.dependent_hint));
            binding.etExtra.setInputType(android.text.InputType.TYPE_NULL);
            binding.tilExtra.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU);

            List<String> taskDisplayList = new ArrayList<>();
            for (Task t : taskList) {
                taskDisplayList.add(t.description);
            }
            ArrayAdapter<String> extraAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, taskDisplayList);
            binding.etExtra.setAdapter(extraAdapter);
            binding.etExtra.setOnItemClickListener((parent, view1, position, id1) -> {
                // Zapisujemy ID wybranego zadania w Tagu widoku, aby go później użyć
                binding.etExtra.setTag(taskList.get(position).id);
            });
            binding.etExtra.setOnClickListener(v -> binding.etExtra.showDropDown());
        }
    }

    private int getNextId() {
        int maxId = 0;
        for (Task t : taskList) if (t.id > maxId) maxId = t.id;
        for (Task t : doneTaskList) if (t.id > maxId) maxId = t.id;
        return maxId + 1;
    }

    private void clearInputs() {
        binding.etDesc.setText("");
        binding.etDeadline.setText("");
        binding.etCoolness.setText("");
        binding.etTime.setText("");
        binding.etType.setText("Elastic", false);
        binding.etExtra.setText("");
        binding.etExtra.setTag(null);
        binding.tilExtra.setVisibility(View.GONE);
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
            
            holder.itemView.setOnClickListener(v -> {
                if (showingDone) return; // Nie edytujemy ukończonych
                
                taskToEdit = task;
                binding.etDesc.setText(task.description);
                binding.etDeadline.setText(task.deadline);
                binding.etCoolness.setText(String.valueOf(task.coolness));
                binding.etTime.setText(String.valueOf(task.estimatedTime));
                
                String typeName = "";
                switch (task.type) {
                    case "H": typeName = "Hard"; break;
                    case "E": typeName = "Elastic"; break;
                    case "R": typeName = "Recurring"; break;
                    case "D": typeName = "Dependent"; break;
                    case "L": typeName = "Large"; break;
                }
                binding.etType.setText(typeName, false);
                updateExtraFieldVisibility(typeName);
                
                if ("L".equals(task.type)) {
                    String[] parts = task.extraInfo.split("/");
                    binding.etExtra.setText(parts[1]); // Pokaż całkowitą liczbę kroków
                } else if ("D".equals(task.type)) {
                    // Dla Dependent szukamy nazwy rodzica
                    int parentId = -1;
                    try { parentId = Integer.parseInt(task.extraInfo); } catch (Exception e) {}
                    for (Task t : taskList) {
                        if (t.id == parentId) {
                            binding.etExtra.setText(t.description, false);
                            binding.etExtra.setTag(t.id);
                            break;
                        }
                    }
                } else {
                    binding.etExtra.setText(task.extraInfo);
                }
                
                binding.buttonAddTask.setText(R.string.save_task);
                binding.titleTasks.setText(R.string.edit_task);
                binding.formContainer.fullScroll(View.FOCUS_UP);
            });

            String desc = task.description;
            if ("L".equals(task.type)) {
                desc += " (" + task.extraInfo + ")";
            }
            holder.tvDesc.setText(desc);
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
                    Task t = tasks.get(pos);
                    
                    if (!showingDone && "L".equals(t.type)) {
                        // Obsługa zadania Large - zwiększamy postęp
                        try {
                            String info = t.extraInfo;
                            if (info != null && info.contains("/")) {
                                String[] parts = info.split("/");
                                if (parts.length == 2) {
                                    int current = Integer.parseInt(parts[0].trim()) + 1;
                                    int total = Integer.parseInt(parts[1].trim());

                                    if (current < total) {
                                        t.extraInfo = current + "/" + total;
                                        notifyItemChanged(pos);
                                        saveTasks();
                                        return; // Nie usuwamy zadania jeszcze
                                    }
                                }
                            }
                        } catch (NumberFormatException e) {
                            if (getContext() != null) {
                                Toast.makeText(getContext(), "Błąd formatu zadania Large.", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (!showingDone && "D".equals(t.type)) {
                        // Obsługa zadania zależnego (Dependent)
                        try {
                            int parentId = Integer.parseInt(t.extraInfo);
                            boolean parentDone = false;
                            for (Task doneTask : doneTaskList) {
                                if (doneTask.id == parentId) {
                                    parentDone = true;
                                    break;
                                }
                            }
                            if (!parentDone) {
                                Toast.makeText(getContext(), R.string.parent_not_done, Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } catch (Exception e) {
                            // W razie błędu parsowania (brak ID) pozwalamy zakończyć
                        }
                    }

                    tasks.remove(pos);
                    notifyItemRemoved(pos);
                    if (showingDone) {
                        taskList.add(t);
                    } else {
                        // Obsługa zadań cyklicznych
                        if ("R".equals(t.type)) {
                            handleRecurringTask(t);
                        }
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
            TextView tvDesc, tvDeadline;
            ImageButton deleteBtn, doneBtn;
            public TaskViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDesc = itemView.findViewById(R.id.tv_task_description);
                tvDeadline = itemView.findViewById(R.id.tv_task_deadline);
                deleteBtn = itemView.findViewById(R.id.button_delete_task);
                doneBtn = itemView.findViewById(R.id.button_done_task);
            }
        }
    }
}
