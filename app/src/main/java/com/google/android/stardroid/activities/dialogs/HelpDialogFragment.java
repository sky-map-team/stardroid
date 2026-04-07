package com.google.android.stardroid.activities.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.preference.PreferenceManager;

import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.activities.util.NightModeHelper;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Help dialog fragment.
 * Created by johntaylor on 4/9/16.
 */
@AndroidEntryPoint
public class HelpDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(HelpDialogFragment.class);
  @Inject StardroidApplication application;
  @Inject Activity parentActivity;

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    LayoutInflater inflater = parentActivity.getLayoutInflater();
    View view = inflater.inflate(R.layout.webview_dialog, null);
    AlertDialog alertDialog = new AlertDialog.Builder(parentActivity)
        .setTitle(R.string.help_dialog_title)
        .setView(view).setNegativeButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {
                Log.d(TAG, "Help Dialog closed");
                dialog.dismiss();
              }
            }).create();
    String creditsText = String.format(parentActivity.getString(R.string.credits_text),
        parentActivity.getString(R.string.sponsors_text),
        parentActivity.getString(R.string.contributors_text));

    String whatsNewContentText = parentActivity.getString(R.string.whats_new_content);
    String betaTesterHelp = parentActivity.getString(R.string.beta_user_help_text);

    String helpText = String.format(parentActivity.getString(R.string.help_text),
        application.getVersionName(), creditsText, whatsNewContentText, betaTesterHelp);
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(parentActivity);
    boolean isNight = ActivityLightLevelManager.isNightMode(preferences);
    String bodyClass = isNight ? " class=\"night-mode\"" : "";
    String html = "<!DOCTYPE html><html><head>" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
        "<link rel=\"stylesheet\" href=\"html/help.css\">" +
        "</head><body" + bodyClass + ">" + helpText + "</body></html>";
    WebView webView = view.findViewById(R.id.webview);
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
    alertDialog.setOnShowListener(
        dialog -> NightModeHelper.applyAlertDialogNightMode((AlertDialog) dialog, isNight));
    return alertDialog;
  }
}
