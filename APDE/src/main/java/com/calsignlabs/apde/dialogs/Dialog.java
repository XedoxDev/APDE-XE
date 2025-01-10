package com.calsignlabs.apde.dialogs;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.appcompat.widget.AppCompatEditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class Dialog {
    public static EditText createAlertDialogEditText(
            Activity context,
            MaterialAlertDialogBuilder builder,
            String content,
            boolean selectAll) {
        AppCompatEditText input = new AppCompatEditText(context);
        input.setSingleLine();
        input.setText(content);
        if (selectAll) {
            input.selectAll();
        }

        // http://stackoverflow.com/a/27776276/
        FrameLayout frameLayout = new FrameLayout(context);
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        // http://stackoverflow.com/a/35211225/
        float dpi = context.getResources().getDisplayMetrics().density;
        params.leftMargin = (int) (19 * dpi);
        params.topMargin = (int) (5 * dpi);
        params.rightMargin = (int) (14 * dpi);
        params.bottomMargin = (int) (5 * dpi);

        frameLayout.addView(input, params);

        builder.setView(frameLayout);

        return input;
    }
}
