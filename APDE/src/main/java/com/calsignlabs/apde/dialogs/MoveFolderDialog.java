package com.calsignlabs.apde.dialogs;

import androidx.appcompat.app.AlertDialog;
import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MoveFolderDialog {
    // Moving from ../APDE.java
    public static AlertDialog show(APDE apde, boolean isSketch, boolean isSelected) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(apde);

        builder.setTitle(
                isSketch
                        ? R.string.rename_sketch_failure_title
                        : R.string.rename_move_folder_failure_title);
        builder.setMessage(
                isSketch
                        ? R.string.rename_sketch_failure_message
                        : R.string.rename_move_folder_failure_message);

        builder.setNeutralButton(R.string.ok, (i, w) -> {});

        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }
}
