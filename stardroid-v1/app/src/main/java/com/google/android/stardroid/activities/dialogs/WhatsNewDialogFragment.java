package com.google.android.stardroid.activities.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.activities.util.NightModeHelper;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Created by johntaylor on 6/10/16.
 */
@AndroidEntryPoint
public class WhatsNewDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(WhatsNewDialogFragment.class);

  public static WhatsNewDialogFragment newInstance() {
    return new WhatsNewDialogFragment();
  }

  @Inject StardroidApplication application;
  @Inject SharedPreferences preferences;
  private CloseListener closeListener;

  public interface CloseListener {
    void dialogClosed();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (context instanceof CloseListener) {
      closeListener = (CloseListener) context;
    }
  }

  @Override
  public void onDetach() {
    super.onDetach();
    closeListener = null;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    final Activity parentActivity = requireActivity();
    LayoutInflater inflater = parentActivity.getLayoutInflater();
    View view = inflater.inflate(R.layout.whatsnew_view, null);

    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(parentActivity, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
        .setTitle(getString(R.string.whats_new_dialog_title))
        .setView(view)
        .setNegativeButton(R.string.dialog_ok_button,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {
                endItNow(dialog);
              }
            });

    String whatsNewContent = parentActivity.getString(R.string.whats_new_content);
    String betaUserHelpText = parentActivity.getString(R.string.beta_user_help_text);
    String whatsNewText = String.format(parentActivity.getString(R.string.whats_new_text),
        application.getVersionName(), whatsNewContent, betaUserHelpText);
    boolean isNight = ActivityLightLevelManager.isNightMode(preferences);
    String bodyClass = isNight ? " class=\"night-mode\"" : "";
    String html = "<!DOCTYPE html><html><head>" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
        "<link rel=\"stylesheet\" href=\"html/help.css\">" +
        "</head><body" + bodyClass + ">" + whatsNewText + "</body></html>";
    WebView webView = view.findViewById(R.id.whatsnew_webview);
    webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        // Open links in external browser
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        parentActivity.startActivity(intent);
        return true;
      }
    });
    webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);

    AlertDialog alertDialog = dialogBuilder.create();
    alertDialog.setOnShowListener(
        dialog -> NightModeHelper.applyAlertDialogNightMode((AlertDialog) dialog, isNight));
    return alertDialog;
  }

  private void endItNow(DialogInterface dialog) {
    if (closeListener != null) {
      closeListener.dialogClosed();
    }
    dialog.dismiss();
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    endItNow(dialog);
  }
}
