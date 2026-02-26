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
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.inject.HasComponent;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * Credits dialog fragment.
 */
public class CreditsDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(CreditsDialogFragment.class);
  @Inject Activity parentActivity;

  public interface ActivityComponent {
    void inject(CreditsDialogFragment fragment);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    ((HasComponent<ActivityComponent>) getActivity()).getComponent().inject(this);

    LayoutInflater inflater = parentActivity.getLayoutInflater();
    View view = inflater.inflate(R.layout.webview_dialog, null);
    AlertDialog alertDialog = new AlertDialog.Builder(parentActivity)
        .setTitle(R.string.credits_dialog_title)
        .setView(view).setNegativeButton(android.R.string.ok,
            (dialog, whichButton) -> {
              Log.d(TAG, "Credits Dialog closed");
              dialog.dismiss();
            }).create();

    String creditsText = String.format(parentActivity.getString(R.string.credits_text),
        parentActivity.getString(R.string.sponsors_text),
        parentActivity.getString(R.string.contributors_text));

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(parentActivity);
    String lightMode = preferences.getString(ActivityLightLevelManager.LIGHT_MODE_KEY, "DAY");
    String bodyClass = "NIGHT".equals(lightMode) ? " class=\"night-mode\"" : "";
    String html = "<!DOCTYPE html><html><head>" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
        "<link rel=\"stylesheet\" href=\"html/help.css\">" +
        "</head><body" + bodyClass + ">" + creditsText + "</body></html>";
    WebView webView = view.findViewById(R.id.webview);
    webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        parentActivity.startActivity(intent);
        return true;
      }
    });
    webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    if ("NIGHT".equals(lightMode)) {
      alertDialog.setOnShowListener(dialog -> {
        AlertDialog d = (AlertDialog) dialog;
        int titleId = d.getContext().getResources().getIdentifier("alertTitle", "id", "android");
        if (titleId != 0) {
          android.widget.TextView titleView = d.findViewById(titleId);
          if (titleView != null) titleView.setTextColor(0xFFCC4444);
        }
        int dividerId = d.getContext().getResources().getIdentifier("titleDivider", "id", "android");
        if (dividerId != 0) {
          View divider = d.findViewById(dividerId);
          if (divider != null) divider.setBackgroundColor(0xFFCC4444);
        }
        android.widget.Button btn = d.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (btn != null) btn.setTextColor(0xFFCC4444);
      });
    }
    return alertDialog;
  }
}
