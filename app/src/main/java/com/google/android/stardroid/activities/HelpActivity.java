package com.google.android.stardroid.activities;

import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.widget.TextView;

import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.databinding.HelpBinding;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * Help activity.
 * Created by johntaylor on 4/9/16.
 */
public class HelpActivity extends AppCompatInjectableActivity {
  private static final String TAG = MiscUtil.getTag(HelpActivity.class);
  @Inject StardroidApplication application;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    DaggerHelpComponent.builder().applicationComponent(getApplicationComponent())
        .helpModule(new HelpModule(this)).build().inject(this);

    HelpBinding binding = HelpBinding.inflate(this.getLayoutInflater());
    setContentView(binding.getRoot());

    // TODO(johntaylor): find a way to break up the help text so we can properly format it
    // without ruining localization.
    String helpText = String.format(getString(R.string.help_text),
        application.getVersionName());
    Spanned formattedHelpText = Html.fromHtml(helpText);
    binding.helpBoxText.setText(formattedHelpText, TextView.BufferType.SPANNABLE);
  }
}
