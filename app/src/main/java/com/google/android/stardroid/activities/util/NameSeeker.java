package com.google.android.stardroid.activities.util;

import android.accounts.Account;
import android.accounts.AccountManager;

import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * Created by johntaylor on 6/10/16.
 */
public class NameSeeker {
  private final String TAG = MiscUtil.getTag(NameSeeker.class);
  private final AccountManager accountManager;

  @Inject
  NameSeeker(AccountManager accountManager) {
    this.accountManager = accountManager;
  }

  public String getFirstEmailOrEmpty() {
    Account[] accounts = accountManager.getAccountsByType("com.google");
    return accounts.length == 0 ? "" : accounts[0].name;
  }

  public String getFirstNameOrEmpty() {
    String firstEmail = getFirstEmailOrEmpty();
    int atSign = firstEmail.indexOf("@");
    return atSign == -1 ? firstEmail : firstEmail.substring(0, atSign);
  }

  public boolean check() {
    return true;
  }
}
