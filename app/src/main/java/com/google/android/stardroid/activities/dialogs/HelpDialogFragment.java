package com.google.android.stardroid.activities.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.inject.HasComponent;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * Help dialog fragment.
 * Created by johntaylor on 4/9/16.
 */
public class HelpDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(HelpDialogFragment.class);
  @Inject StardroidApplication application;
  @Inject Activity parentActivity;

  public interface ActivityComponent {
    void inject(HelpDialogFragment fragment);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    // Activities using this dialog MUST implement this interface.  Obviously.
    ((HasComponent<ActivityComponent>) getActivity()).getComponent().inject(this);

    LayoutInflater inflater = parentActivity.getLayoutInflater();
    View view = inflater.inflate(R.layout.help, null);
    AlertDialog alertDialog = new AlertDialog.Builder(parentActivity)
        .setTitle(R.string.help_dialog_title)
        .setView(view).setNegativeButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {
                Log.d(TAG, "Help Dialog closed");
                dialog.dismiss();
              }
            }).create();
    String helpText = String.format(parentActivity.getString(R.string.help_text),
        application.getVersionName());
    Spanned formattedHelpText = Html.fromHtml(helpText);
    TextView helpTextView = (TextView) view.findViewById(R.id.help_box_text);
    helpTextView.setText(formattedHelpText, TextView.BufferType.SPANNABLE);
    return alertDialog;
  }
}
