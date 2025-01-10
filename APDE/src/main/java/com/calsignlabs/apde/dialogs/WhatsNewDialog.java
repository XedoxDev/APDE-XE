package com.calsignlabs.apde.dialogs;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import com.calsignlabs.apde.EditorActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Stack;
import com.calsignlabs.apde.R;

public class WhatsNewDialog {
    public static AlertDialog show(EditorActivity act) {
        Stack<String> releaseNotesStack = EditorActivity.getReleaseNotesStack(act);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(act);
        builder.setTitle(R.string.whats_new);

        RelativeLayout layout;

        layout = (RelativeLayout) LayoutInflater.from(act).inflate(R.layout.whats_new, null, false);

        ListView list = (ListView) layout.findViewById(R.id.whats_new_list);
        Button loadMore = (Button) layout.findViewById(R.id.whats_new_more);
        CheckBox keepShowing = (CheckBox) layout.findViewById(R.id.whats_new_keep_showing);

        ArrayAdapter<String> listAdapter =
                new ArrayAdapter<>(
                        act, R.layout.whats_new_list_item, R.id.whats_new_list_item_text);
        list.setAdapter(listAdapter);

        EditorActivity.addWhatsNewItem(list, listAdapter, releaseNotesStack, loadMore, false);

        loadMore.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        for (int i = 0; i < 5; i++) {

                            if (!EditorActivity.addWhatsNewItem(
                                    list, listAdapter, releaseNotesStack, loadMore, true)) {
                                break;
                            }
                        }

                        int w = FrameLayout.LayoutParams.MATCH_PARENT;
                        int h = FrameLayout.LayoutParams.WRAP_CONTENT;

                        layout.setLayoutParams(new FrameLayout.LayoutParams(w, h));
                    }
                });

        builder.setView(layout);
        builder.setNeutralButton(R.string.ok, (i, w) -> {});

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.setOnDismissListener(
                (d) -> {
                    SharedPreferences.Editor edit =
                            PreferenceManager.getDefaultSharedPreferences(act).edit();
                    edit.putBoolean("pref_whats_new_enable", keepShowing.isChecked());
                    edit.apply();

                    act.getGlobalState().initExamplesRepo();
                });

        layout.requestLayout();

        layout.post(
                () -> {
                    int w = FrameLayout.LayoutParams.MATCH_PARENT;
                    int h =
                            list.getChildAt(0).getHeight()
                                    + loadMore.getHeight()
                                    + keepShowing.getHeight()
                                    + Math.round(
                                            TypedValue.applyDimension(
                                                    TypedValue.COMPLEX_UNIT_DIP,
                                                    24,
                                                    act.getResources().getDisplayMetrics()));

                    layout.setLayoutParams(new FrameLayout.LayoutParams(w, h));
                });
        return dialog;
    }
}
