package com.mentalfrostbyte.jello.managers.util.account.microsoft.sorting.impl;

import com.mentalfrostbyte.jello.managers.util.account.microsoft.Account;

import java.util.Comparator;

public final class AccountNameComparator implements Comparator<Account> {
    public int compare(Account account1, Account account2) {
        return account1.getName().toLowerCase().compareTo(account2.getName().toLowerCase());
    }
}
