package com.google.android.stardroid.activities.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.preference.PreferenceManager;

import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.inject.HasComponent;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * Created by johntaylor on 6/10/16.
 */
public class WhatsNewDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(WhatsNewDialogFragment.class);
  @Inject Activity parentActivity;
  @Inject StardroidApplication application;
  private CloseListener closeListener;

  public interface CloseListener {
    void dialogClosed();
  }

  public void setCloseListener(CloseListener closeListener) {
    this.closeListener = closeListener;
  }

  public interface ActivityComponent {
    void inject(WhatsNewDialogFragment fragment);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    // Activities using this dialog MUST implement this interface.  Obviously.
    ((HasComponent<ActivityComponent>) getActivity()).getComponent().inject(this);

    LayoutInflater inflater = parentActivity.getLayoutInflater();
    View view = inflater.inflate(R.layout.whatsnew_view, null);

    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(parentActivity)
        .setTitle(getString(R.string.whats_new_dialog_title))
        .setView(view)
        .setNegativeButton(R.string.dialog_ok_button,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {
                endItNow(dialog);
              }
            });

    String whatsNewText = String.format(parentActivity.getString(R.string.whats_new_text),
        application.getVersionName());
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(parentActivity);
    String lightMode = preferences.getString(ActivityLightLevelManager.LIGHT_MODE_KEY, "DAY");
    String bodyClass = "NIGHT".equals(lightMode) ? " class=\"night-mode\"" : "";
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

    return dialogBuilder.create();
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
