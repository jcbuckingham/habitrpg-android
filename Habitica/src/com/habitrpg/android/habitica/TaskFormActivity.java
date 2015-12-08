package com.habitrpg.android.habitica;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;

import com.habitrpg.android.habitica.events.TaskSaveEvent;
import com.habitrpg.android.habitica.events.commands.DeleteTaskCommand;
import com.habitrpg.android.habitica.ui.WrapContentRecyclerViewLayoutManager;
import com.habitrpg.android.habitica.ui.adapter.CheckListAdapter;
import com.habitrpg.android.habitica.ui.helpers.SimpleItemTouchHelperCallback;
import com.habitrpg.android.habitica.ui.helpers.ViewHelper;
import com.magicmicky.habitrpgwrapper.lib.models.Tag;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.ChecklistItem;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.Days;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.Task;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.TaskTag;
import com.raizlabs.android.dbflow.runtime.transaction.BaseTransaction;
import com.raizlabs.android.dbflow.runtime.transaction.TransactionListener;
import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.Select;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.greenrobot.event.EventBus;


// TODO refactor all to use butterknife
public class TaskFormActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private String taskType;
    private String taskId;
    private Task task;

    private EditText taskText, taskNotes;
    private Spinner taskDifficultySpinner, dailyFrequencySpinner;
    private CheckBox positiveCheckBox, negativeCheckBox;

    private List<CheckBox> weekdayCheckboxes = new ArrayList<CheckBox>();
    private NumberPicker frequencyPicker;
    private LinearLayout frequencyContainer;
    private List<String> tags;
    private CheckListAdapter checklistAdapter;
    private Button btnDelete;

    @InjectView(R.id.task_value_edittext)
    EditText taskValue;

    @InjectView(R.id.task_value_layout)
    TextInputLayout taskValueLayout;

    @InjectView(R.id.task_checklist_wrapper)
    LinearLayout checklistWrapper;

    @InjectView(R.id.task_startdate_layout)
    LinearLayout startDateWrapper;

    @InjectView(R.id.task_difficulty_wrapper)
    LinearLayout difficultyWrapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_form);

        ButterKnife.inject(this);

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        taskType = bundle.getString("type");
        taskId = bundle.getString("taskId");
        tags = bundle.getStringArrayList("tagsId");
        if (taskType == null) {
            return;
        }

        LinearLayout mainWrapper = (LinearLayout) findViewById(R.id.task_main_wrapper);
        taskText = (EditText) findViewById(R.id.task_text_edittext);
        taskNotes = (EditText) findViewById(R.id.task_notes_edittext);
        taskDifficultySpinner = (Spinner) findViewById(R.id.task_difficulty_spinner);
        btnDelete = (Button) findViewById(R.id.btn_delete_task);
        btnDelete.setEnabled(false);
        ViewHelper.SetBackgroundTint(btnDelete, getResources().getColor(R.color.worse_10));
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                new AlertDialog.Builder(view.getContext())
                .setTitle("Are you sure?")
                .setMessage("Do you really want to delete?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        task.delete();

                        finish();
                        dismissKeyboard();

                        EventBus.getDefault().post(new DeleteTaskCommand(taskId));
                    }
                }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).show();
            }
        });

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.task_difficulties, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        taskDifficultySpinner.setAdapter(adapter);

        if (taskType.equals("habit")) {
            LinearLayout startDateLayout = (LinearLayout) findViewById(R.id.task_startdate_layout);
            LinearLayout taskWrapper = (LinearLayout) findViewById(R.id.task_task_wrapper);
            taskWrapper.removeView(startDateLayout);

            LinearLayout checklistLayout = (LinearLayout) findViewById(R.id.task_checklist_wrapper);
            mainWrapper.removeView(checklistLayout);

            positiveCheckBox = (CheckBox) findViewById(R.id.task_positive_checkbox);
            positiveCheckBox.setChecked(true);
            negativeCheckBox = (CheckBox) findViewById(R.id.task_negative_checkbox);
            negativeCheckBox.setChecked(true);
        } else {
            LinearLayout actionsLayout = (LinearLayout) findViewById(R.id.task_actions_wrapper);
            mainWrapper.removeView(actionsLayout);
        }

        LinearLayout weekdayWrapper = (LinearLayout) findViewById(R.id.task_weekdays_wrapper);
        if (taskType.equals("daily")) {
            this.dailyFrequencySpinner = (Spinner) weekdayWrapper.findViewById(R.id.task_frequency_spinner);

            ArrayAdapter<CharSequence> frequencyAdapter = ArrayAdapter.createFromResource(this,
                    R.array.daily_frequencies, android.R.layout.simple_spinner_item);
            frequencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            this.dailyFrequencySpinner.setAdapter(frequencyAdapter);
            this.dailyFrequencySpinner.setOnItemSelectedListener(this);


            this.frequencyContainer = (LinearLayout) weekdayWrapper.findViewById(R.id.task_frequency_container);
        } else {
            mainWrapper.removeView(weekdayWrapper);
        }

        if (!taskType.equals("reward")) {
            taskValueLayout.setVisibility(View.GONE);
        } else {

            mainWrapper.removeView(checklistWrapper);
            mainWrapper.removeView(startDateWrapper);

            difficultyWrapper.setVisibility(View.GONE);
        }

        if (taskId != null) {
            Task task = new Select().from(Task.class).byIds(taskId).querySingle();
            this.task = task;
            populate(task);
            setTitle(task);

            btnDelete.setEnabled(true);
        } else {
            setTitle((Task) null);
        }

        if (taskType.equals("todo") || taskType.equals("daily")) {
            createCheckListRecyclerView();
        }
    }

    private void createCheckListRecyclerView() {
        List<ChecklistItem> checklistItems = new ArrayList<>();
        if (task != null && task.getChecklist() != null) {
            checklistItems = task.getChecklist();
        }
        checklistAdapter = new CheckListAdapter(checklistItems);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.checklist_recycler_view);

        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);

        recyclerView.setLayoutManager(llm);
        recyclerView.setAdapter(checklistAdapter);
        int i = checklistAdapter.getItemCount();

        recyclerView.setLayoutManager(new WrapContentRecyclerViewLayoutManager(this));

        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(checklistAdapter);
        ItemTouchHelper mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(recyclerView);

        final EditText newCheckListEditText = (EditText) findViewById(R.id.new_checklist);

        Button button = (Button) findViewById(R.id.add_checklist_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String checklist = newCheckListEditText.getText().toString();
                ChecklistItem item = new ChecklistItem(checklist);
                checklistAdapter.addItem(item);
                newCheckListEditText.setText("");
            }
        });
    }

    private void setTitle(Task task) {
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {

            String title = "";

            if (task != null) {
                title = getResources().getString(R.string.action_edit) + " " + task.getText();
            } else {
                switch (taskType) {
                    case "todo":
                        title = getResources().getString(R.string.new_todo);
                        break;
                    case "daily":
                        title = getResources().getString(R.string.new_daily);
                        break;
                    case "habit":
                        title = getResources().getString(R.string.new_habit);
                        break;
                    case "reward":
                        title = getResources().getString(R.string.new_reward);
                        break;
                }
            }

            actionBar.setTitle(title);
        }
    }

    private void setDailyFrequencyViews() {
        this.frequencyContainer.removeAllViews();
        if (this.dailyFrequencySpinner.getSelectedItemPosition() == 0) {
            String[] weekdays = getResources().getStringArray(R.array.weekdays);
            for (int i = 0; i < 7; i++) {
                View weekdayRow = getLayoutInflater().inflate(R.layout.row_checklist, null);
                TextView tv = (TextView) weekdayRow.findViewById(R.id.label);
                CheckBox checkbox = (CheckBox) weekdayRow.findViewById(R.id.checkbox);
                checkbox.setChecked(true);
                this.weekdayCheckboxes.add(checkbox);
                tv.setText(weekdays[i]);
                this.frequencyContainer.addView(weekdayRow);
            }
        } else {
            View dayRow = getLayoutInflater().inflate(R.layout.row_number_picker, null);
            this.frequencyPicker = (NumberPicker) dayRow.findViewById(R.id.numberPicker);
            this.frequencyPicker.setMinValue(1);
            this.frequencyPicker.setMaxValue(366);
            TextView tv = (TextView) dayRow.findViewById(R.id.label);
            tv.setText(getResources().getString(R.string.frequency_daily));
            this.frequencyContainer.addView(dayRow);
        }

        if (this.task != null) {

            if (this.dailyFrequencySpinner.getSelectedItemPosition() == 0) {
                this.weekdayCheckboxes.get(0).setChecked(this.task.getRepeat().getM());
                this.weekdayCheckboxes.get(1).setChecked(this.task.getRepeat().getT());
                this.weekdayCheckboxes.get(2).setChecked(this.task.getRepeat().getW());
                this.weekdayCheckboxes.get(3).setChecked(this.task.getRepeat().getTh());
                this.weekdayCheckboxes.get(4).setChecked(this.task.getRepeat().getF());
                this.weekdayCheckboxes.get(5).setChecked(this.task.getRepeat().getS());
                this.weekdayCheckboxes.get(6).setChecked(this.task.getRepeat().getSu());
            } else {
                this.frequencyPicker.setValue(this.task.getEveryX());
            }
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_task_form, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_save_changes) {
            finishActivitySuccessfuly();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void populate(Task task) {
        taskText.setText(task.text);
        taskNotes.setText(task.notes);
        taskValue.setText(String.format("%.0f", task.value));

        float priority = task.getPriority();
        if (priority == 0.1) {
            this.taskDifficultySpinner.setSelection(0);
        } else if (priority == 1.0) {
            this.taskDifficultySpinner.setSelection(1);
        } else if (priority == 1.5) {
            this.taskDifficultySpinner.setSelection(2);
        } else if (priority == 2.0) {
            this.taskDifficultySpinner.setSelection(3);
        }

        if (task.type.equals("habit")) {
            positiveCheckBox.setChecked(task.getUp());
            negativeCheckBox.setChecked(task.getDown());
        }

        if (task.type.equals("daily")) {
            if (task.getFrequency().equals("weekly")) {
                this.dailyFrequencySpinner.setSelection(0);
                if (weekdayCheckboxes.size() == 7) {
                    this.weekdayCheckboxes.get(0).setChecked(task.getRepeat().getM());
                    this.weekdayCheckboxes.get(1).setChecked(task.getRepeat().getT());
                    this.weekdayCheckboxes.get(2).setChecked(task.getRepeat().getW());
                    this.weekdayCheckboxes.get(3).setChecked(task.getRepeat().getTh());
                    this.weekdayCheckboxes.get(4).setChecked(task.getRepeat().getF());
                    this.weekdayCheckboxes.get(5).setChecked(task.getRepeat().getS());
                    this.weekdayCheckboxes.get(6).setChecked(task.getRepeat().getSu());
                }
            } else {
                this.dailyFrequencySpinner.setSelection(1);
                if (this.frequencyPicker != null) {
                    this.frequencyPicker.setValue(task.getEveryX());
                }
            }
        }

    }

    private boolean saveTask(Task task) {
        task.text = taskText.getText().toString();

        if (checklistAdapter != null) {
            if (checklistAdapter.getCheckListItems() != null) {
                task.setChecklist(checklistAdapter.getCheckListItems());
            }
        }

        if (task.text.isEmpty())
            return false;

        task.notes = taskNotes.getText().toString();

        if (this.taskDifficultySpinner.getSelectedItemPosition() == 0) {
            task.setPriority((float) 0.1);
        } else if (this.taskDifficultySpinner.getSelectedItemPosition() == 1) {
            task.setPriority((float) 1.0);
        } else if (this.taskDifficultySpinner.getSelectedItemPosition() == 2) {
            task.setPriority((float) 1.5);
        } else if (this.taskDifficultySpinner.getSelectedItemPosition() == 3) {
            task.setPriority((float) 2.0);
        }

        switch (task.type) {
            case "habit": {
                task.setUp(positiveCheckBox.isChecked());
                task.setDown(negativeCheckBox.isChecked());
            }
            break;

            case "daily": {
                if (this.dailyFrequencySpinner.getSelectedItemPosition() == 0) {
                    task.setFrequency("weekly");
                    Days repeat = task.getRepeat();
                    if (repeat == null) {
                        repeat = new Days();
                        task.setRepeat(repeat);
                    }

                    repeat.setM(this.weekdayCheckboxes.get(0).isChecked());
                    repeat.setT(this.weekdayCheckboxes.get(1).isChecked());
                    repeat.setW(this.weekdayCheckboxes.get(2).isChecked());
                    repeat.setTh(this.weekdayCheckboxes.get(3).isChecked());
                    repeat.setF(this.weekdayCheckboxes.get(4).isChecked());
                    repeat.setS(this.weekdayCheckboxes.get(5).isChecked());
                    repeat.setSu(this.weekdayCheckboxes.get(6).isChecked());
                } else {
                    task.setFrequency("daily");
                    task.setEveryX(this.frequencyPicker.getValue());
                }
            }
            break;

            case "reward": {
                task.setValue(Double.parseDouble(taskValue.getText().toString()));
            }
            break;
        }

        return true;
    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        this.setDailyFrequencyViews();
    }

    public void onNothingSelected(AdapterView<?> parent) {
        this.setDailyFrequencyViews();
    }

    private void prepareSave() {
        if (this.task == null) {
            this.task = new Task();
            this.task.setType(taskType);
        }
        if (this.saveTask(this.task)) {
            this.task.save();
            new Select()
                    .from(Tag.class)
                    .where(Condition.column("id").in("", tags.toArray())).async().queryList(tagsSearchingListener);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        dismissKeyboard();
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
        dismissKeyboard();
    }

    private void finishActivitySuccessfuly() {
        this.prepareSave();
        finish();
        dismissKeyboard();
    }

    private void dismissKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    private TransactionListener<List<Tag>> tagsSearchingListener = new TransactionListener<List<Tag>>() {
        @Override
        public void onResultReceived(List<Tag> tags) {
            //UI thread.
            List<TaskTag> taskTags = new ArrayList<>();
            for (Tag tag : tags) {
                TaskTag tt = new TaskTag();
                tt.setTag(tag);
                tt.setTask(task);
                taskTags.add(tt);
            }
            //save
            TaskFormActivity.this.task.setTags(taskTags);
            TaskFormActivity.this.task.update();
            //send back to other elements.
            TaskSaveEvent event = new TaskSaveEvent();
            if (TaskFormActivity.this.task.getId() == null) {
                event.created = true;
            }

            event.task = TaskFormActivity.this.task;
            EventBus.getDefault().post(event);

        }

        @Override
        public boolean onReady(BaseTransaction<List<Tag>> baseTransaction) {
            return true;
        }

        @Override
        public boolean hasResult(BaseTransaction<List<Tag>> baseTransaction, List<Tag> tags) {
            return true;
        }
    };
}
