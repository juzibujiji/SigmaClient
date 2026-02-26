package com.mentalfrostbyte.jello.managers.util.account.microsoft.sorting.impl;

import com.mentalfrostbyte.jello.managers.util.account.microsoft.Account;

import java.util.Comparator;

public final class AccountDateAddedComparator implements Comparator<Account> {
   public int compare(Account account1, Account account2) {
      return account1.getDateAdded() >= account2.getDateAdded() ? (account1.getDateAdded() <= account2.getDateAdded() ? 0 : -1) : 1;
   }
}
