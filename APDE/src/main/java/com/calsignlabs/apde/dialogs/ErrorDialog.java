package com.calsignlabs.apde.dialogs;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.calsignlabs.apde.R;

public class ErrorDialog {
    public static AlertDialog show(Context context, Throwable err) {
        MaterialAlertDialogBuilder madb = new MaterialAlertDialogBuilder(context);
        madb.setTitle(R.string.runtime_exception);
        madb.setNegativeButton(R.string.ok, (i, i2)->{i.dismiss();});
        madb.setMessage(err.getMessage());
        AlertDialog d = madb.create();
        d.show();
        return d;
    }
}
