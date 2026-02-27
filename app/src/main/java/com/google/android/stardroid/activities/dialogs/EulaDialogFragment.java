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
import com.google.android.stardroid.inject.HasComponent;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * End User License agreement dialog.
 * Created by johntaylor on 4/3/16.
 */
public class EulaDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(EulaDialogFragment.class);
  @Inject Activity parentActivity;
  @Inject Analytics analytics;
  private EulaAcceptanceListener resultListener;

  public interface EulaAcceptanceListener {
    void eulaAccepted();
    void eulaRejected();
  }

  public interface ActivityComponent {
    void inject(EulaDialogFragment fragment);
  }

  public void setEulaAcceptanceListener(EulaAcceptanceListener resultListener) {
    this.resultListener = resultListener;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    Log.d(TAG, "onCreateDialog");
    // Activities using this dialog MUST implement this interface.  Obviously.
    ((HasComponent<ActivityComponent>) getActivity()).getComponent().inject(this);

    LayoutInflater inflater = parentActivity.getLayoutInflater();
    View view = inflater.inflate(R.layout.tos_view, null);

    AlertDialog.Builder tosDialogBuilder = new AlertDialog.Builder(parentActivity)
        .setTitle(R.string.menu_tos)
        .setView(view);
    if (resultListener != null) {
      tosDialogBuilder
          .setPositiveButton(R.string.dialog_accept,
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                  acceptEula(dialog);
                }
              })
          .setNegativeButton(R.string.dialog_decline,
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                  rejectEula(dialog);
                }
              });
    } else {
      tosDialogBuilder.setNeutralButton(android.R.string.ok,
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
              dialog.dismiss();
            }
          });
    }

    // Build the HTML content
    String apologyText = parentActivity.getString(R.string.language_apology_text);
    String eulaText = parentActivity.getString(R.string.eula_text);

    StringBuilder contentBuilder = new StringBuilder();
    // Add apology text as a callout if it's not empty
    if (apologyText != null && !apologyText.trim().isEmpty()) {
      contentBuilder.append("<p class=\"callout\">").append(apologyText).append("</p>");
    }
    contentBuilder.append(eulaText);

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(parentActivity);
    boolean isNight = ActivityLightLevelManager.isNightMode(preferences);
    String bodyClass = isNight ? " class=\"night-mode\"" : "";
    String html = "<!DOCTYPE html><html><head>" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
        "<link rel=\"stylesheet\" href=\"html/help.css\">" +
        "</head><body" + bodyClass + ">" + contentBuilder.toString() + "</body></html>";

    WebView webView = view.findViewById(R.id.eula_webview);
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

    AlertDialog tosDialog = tosDialogBuilder.create();
    tosDialog.setOnShowListener(
        dialog -> NightModeHelper.applyAlertDialogNightMode((AlertDialog) dialog, isNight));
    return tosDialog;
  }

  private void acceptEula(DialogInterface dialog) {
    Log.d(TAG, "TOS Dialog closed.  User accepts.");
    dialog.dismiss();
    analytics.trackEvent(Analytics.TOS_ACCEPTED_EVENT, new Bundle());
    if (resultListener != null) {
      resultListener.eulaAccepted();
    }
  }

  private void rejectEula(DialogInterface dialog) {
    Log.d(TAG, "TOS Dialog closed.  User declines.");
    dialog.dismiss();
    analytics.trackEvent(Analytics.TOS_REJECTED_EVENT, new Bundle());
    if (resultListener != null) {
      resultListener.eulaRejected();
    }
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    if (resultListener != null) {
      rejectEula(dialog);
    }
  }
}
