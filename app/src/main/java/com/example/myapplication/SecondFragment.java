package com.example.myapplication;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.ArrayList;
import java.util.List;

public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;
    private TaskAdapter adapter;
    private final List<Task> taskList = new ArrayList<>();
    private static final String TASKS_FILE = "tasks.json";

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

        adapter = new TaskAdapter(taskList);
        binding.recyclerViewTasks.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewTasks.setAdapter(adapter);

        binding.buttonAddTask.setOnClickListener(v -> {
            String desc = binding.etDesc.getText().toString().trim();
            String deadline = binding.etDeadline.getText().toString().trim();
            String coolnessStr = binding.etCoolness.getText().toString().trim();
            String timeStr = binding.etTime.getText().toString().trim();
            String type = binding.etType.getText().toString().trim();
            String extra = binding.etExtra.getText().toString().trim();

            if (desc.isEmpty() || deadline.isEmpty()) {
                Toast.makeText(getContext(), "Opis i Deadline są wymagane!", Toast.LENGTH_SHORT).show();
                return;
            }

            int id = taskList.isEmpty() ? 1 : taskList.get(taskList.size() - 1).id + 1;
            int coolness = coolnessStr.isEmpty() ? 3 : Integer.parseInt(coolnessStr);
            int time = timeStr.isEmpty() ? 0 : Integer.parseInt(timeStr);

            Task newTask = new Task(id, desc, deadline, coolness, time, type, extra);
            taskList.add(newTask);
            adapter.notifyItemInserted(taskList.size() - 1);
            saveTasks();
            clearInputs();
        });

        binding.buttonSecond.setOnClickListener(v ->
                NavHostFragment.findNavController(SecondFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment)
        );
    }

    private void clearInputs() {
        binding.etDesc.setText("");
        binding.etDeadline.setText("");
        binding.etCoolness.setText("");
        binding.etTime.setText("");
        binding.etType.setText("");
        binding.etExtra.setText("");
    }

    private void saveTasks() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Task t : taskList) {
                jsonArray.put(t.toJSON());
            }
            try (FileOutputStream fos = requireContext().openFileOutput(TASKS_FILE, Context.MODE_PRIVATE)) {
                fos.write(jsonArray.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadTasks() {
        taskList.clear();
        try (FileInputStream fis = requireContext().openFileInput(TASKS_FILE);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray jsonArray = new JSONArray(sb.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                taskList.add(Task.fromJSON(jsonArray.getJSONObject(i)));
            }
        } catch (IOException ignored) {} catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
        private final List<Task> tasks;
        public TaskAdapter(List<Task> tasks) { this.tasks = tasks; }

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
            holder.deleteBtn.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    tasks.remove(pos);
                    notifyItemRemoved(pos);
                    saveTasks();
                }
            });
        }

        @Override
        public int getItemCount() { return tasks.size(); }

        class TaskViewHolder extends RecyclerView.ViewHolder {
            TextView tvId, tvDesc, tvDeadline;
            ImageButton deleteBtn;
            public TaskViewHolder(@NonNull View itemView) {
                super(itemView);
                tvId = itemView.findViewById(R.id.tv_task_id);
                tvDesc = itemView.findViewById(R.id.tv_task_description);
                tvDeadline = itemView.findViewById(R.id.tv_task_deadline);
                deleteBtn = itemView.findViewById(R.id.button_delete_task);
            }
        }
    }
}
