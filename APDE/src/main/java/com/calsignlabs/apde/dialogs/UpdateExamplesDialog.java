package com.calsignlabs.apde.dialogs;

import android.view.View;
import android.widget.CompoundButton;
import androidx.appcompat.app.AlertDialog;
import com.calsignlabs.apde.EditorActivity;
import com.calsignlabs.apde.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.widget.LinearLayout;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.TextView;
import com.calsignlabs.apde.APDE;
import androidx.preference.PreferenceManager;
import android.widget.Button;
import android.content.DialogInterface;

public class UpdateExamplesDialog {
    // Moving from ../APDE.java
    public static AlertDialog show(APDE apde) {
        var builder = new MaterialAlertDialogBuilder(apde);

        builder.setTitle(R.string.examples_update_dialog_title);

        LinearLayout layout =
                (LinearLayout)
                        LayoutInflater.from(apde)
                                .inflate(R.layout.examples_update_dialog, null, false);
        CheckBox dontShowAgain = layout.findViewById(R.id.examples_update_dialog_dont_show_again);
        TextView disableWarning = layout.findViewById(R.id.examples_update_dialog_disable_warning);

        builder.setView(layout);

        builder.setPositiveButton(
                R.string.examples_update_dialog_update_button,
                (dialog, which) -> {
                    if (dontShowAgain.isChecked()) {
                        PreferenceManager.getDefaultSharedPreferences(apde)
                                .edit()
                                .putBoolean("update_examples", false)
                                .apply();
                    } else {
                        apde.updateExamplesRepo();
                    }
                });

        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        Button updateButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        Button cancelButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);

        dontShowAgain.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        disableWarning.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                        // Change the behavior if
                        // the user wants to get rid
                        // of this dialog...
                        updateButton.setText(
                                isChecked
                                        ? R.string.tool_find_replace_close
                                        : R.string.examples_update_dialog_update_button);
                        // Hide the cancel button so
                        // that it's unambiguous
                        cancelButton.setEnabled(!isChecked);
                    }
                });
        return dialog;
    }
}
