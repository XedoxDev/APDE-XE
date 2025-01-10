package com.calsignlabs.apde.dialogs;

import androidx.appcompat.app.AlertDialog;
import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ExamplesDownloadErrorDialog {
    // Moving from ../APDE.java
    public static AlertDialog show(APDE apde) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(apde);
        builder.setTitle(
                R.string.examples_update_settings_download_now_mobile_data_error_dialog_title);
        builder.setMessage(
                R.string.examples_update_settings_download_now_mobile_data_error_dialog_message);

        builder.setNeutralButton(
                R.string.ok,
                (i, w)->{});

        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }
}
