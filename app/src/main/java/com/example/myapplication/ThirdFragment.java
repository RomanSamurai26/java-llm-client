package com.example.myapplication;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.databinding.FragmentThirdBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ThirdFragment extends Fragment {

    private FragmentThirdBinding binding;
    private Calendar calendar;
    private DaysAdapter daysAdapter;
    private List<String> daysList = new ArrayList<>();
    private List<SecondFragment.Task> allTasks = new ArrayList<>();
    private List<SecondFragment.Task> filteredTasks = new ArrayList<>();
    private CalendarTaskAdapter taskAdapter;
    private int selectedDay = -1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentThirdBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        calendar = Calendar.getInstance();
        selectedDay = calendar.get(Calendar.DAY_OF_MONTH);

        setupRecyclerViews();
        updateCalendar();
        loadTasks();

        binding.btnPrevMonth.setOnClickListener(v -> {
            calendar.add(Calendar.MONTH, -1);
            updateCalendar();
        });

        binding.btnNextMonth.setOnClickListener(v -> {
            calendar.add(Calendar.MONTH, 1);
            updateCalendar();
        });
    }

    private void setupRecyclerViews() {
        daysAdapter = new DaysAdapter(daysList);
        binding.rvCalendarDays.setLayoutManager(new GridLayoutManager(getContext(), 7));
        binding.rvCalendarDays.setAdapter(daysAdapter);

        taskAdapter = new CalendarTaskAdapter(filteredTasks);
        binding.rvCalendarTasks.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvCalendarTasks.setAdapter(taskAdapter);
    }

    private void updateCalendar() {
        daysList.clear();
        Calendar tempCal = (Calendar) calendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        
        String monthYear = tempCal.getDisplayName(Calendar.MONTH, Calendar.LONG, new Locale("pl")) + " " + tempCal.get(Calendar.YEAR);
        binding.tvMonthYear.setText(monthYear);

        int firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) - 2; // Adjust for Mon-Sun
        if (firstDayOfWeek < 0) firstDayOfWeek = 6;

        int daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < firstDayOfWeek; i++) {
            daysList.add("");
        }

        for (int i = 1; i <= daysInMonth; i++) {
            daysList.add(String.valueOf(i));
        }

        daysAdapter.notifyDataSetChanged();
        filterTasks();
    }

    private void loadTasks() {
        allTasks.clear();
        try (FileInputStream fis = requireContext().openFileInput("tasks.json");
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                allTasks.add(SecondFragment.Task.fromJSON(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        daysAdapter.notifyDataSetChanged(); // Re-render dots
        filterTasks();
    }

    private void filterTasks() {
        filteredTasks.clear();
        String dateSuffix = String.format(Locale.getDefault(), "-%02d-%d", calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR));
        String selectedDate = String.format(Locale.getDefault(), "%02d", selectedDay) + dateSuffix;
        
        binding.tvSelectedDayLabel.setText(selectedDay == Calendar.getInstance().get(Calendar.DAY_OF_MONTH) ? "Dzisiaj" : selectedDate);

        for (SecondFragment.Task task : allTasks) {
            if (task.deadline.equals(selectedDate)) {
                filteredTasks.add(task);
            }
        }
        taskAdapter.notifyDataSetChanged();
    }

    private class DaysAdapter extends RecyclerView.Adapter<DaysAdapter.DayViewHolder> {
        private List<String> days;

        public DaysAdapter(List<String> days) { this.days = days; }

        @NonNull
        @Override
        public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
            return new DayViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
            String day = days.get(position);
            holder.tvDay.setText(day);
            holder.tvDay.setTextColor(Color.BLACK);
            holder.layoutDots.removeAllViews();

            if (!day.isEmpty()) {
                int dayInt = Integer.parseInt(day);
                String dateStr = String.format(Locale.getDefault(), "%02d-%02d-%d", 
                        dayInt, calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR));

                // Liczenie zadań dla tego dnia
                int taskCount = 0;
                for (SecondFragment.Task t : allTasks) {
                    if (t.deadline.equals(dateStr)) {
                        taskCount++;
                    }
                }

                // Dodawanie kropek (max np. 5 żeby się zmieściły)
                for (int i = 0; i < Math.min(taskCount, 5); i++) {
                    View dot = new View(getContext());
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(12, 12);
                    params.setMargins(2, 0, 2, 0);
                    dot.setLayoutParams(params);
                    dot.setBackgroundResource(R.drawable.dot_task);
                    holder.layoutDots.addView(dot);
                }

                if (dayInt == selectedDay) {
                    holder.itemView.setBackgroundColor(Color.parseColor("#E1D5F9"));
                } else {
                    holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                }
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            holder.itemView.setOnClickListener(v -> {
                if (!day.isEmpty()) {
                    selectedDay = Integer.parseInt(day);
                    notifyDataSetChanged();
                    filterTasks();
                }
            });
        }

        @Override
        public int getItemCount() { return days.size(); }

        class DayViewHolder extends RecyclerView.ViewHolder {
            TextView tvDay;
            LinearLayout layoutDots;
            public DayViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDay = itemView.findViewById(R.id.tv_day);
                layoutDots = itemView.findViewById(R.id.layout_dots);
            }
        }
    }

    private class CalendarTaskAdapter extends RecyclerView.Adapter<CalendarTaskAdapter.ViewHolder> {
        private List<SecondFragment.Task> tasks;
        public CalendarTaskAdapter(List<SecondFragment.Task> tasks) { this.tasks = tasks; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_task, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SecondFragment.Task task = tasks.get(position);
            holder.tvId.setText("zad " + task.id);
            holder.tvId.setTextColor(Color.BLACK);
            holder.tvDesc.setText(task.description);
            holder.tvDesc.setTextColor(Color.BLACK);
        }

        @Override
        public int getItemCount() { return tasks.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvId, tvDesc;
            public ViewHolder(@NonNull View v) {
                super(v);
                tvId = v.findViewById(R.id.tv_cal_task_id);
                tvDesc = v.findViewById(R.id.tv_cal_task_desc);
            }
        }
    }
}
