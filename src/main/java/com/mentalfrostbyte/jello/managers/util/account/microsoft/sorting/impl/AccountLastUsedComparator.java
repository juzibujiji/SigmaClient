package com.mentalfrostbyte.jello.managers.util.account.microsoft.sorting.impl;

import com.mentalfrostbyte.jello.managers.util.account.microsoft.Account;

import java.util.Comparator;

public final class AccountLastUsedComparator implements Comparator<Account> {
    public int compare(Account account1, Account account2) {
        return account1.getLastUsed() >= account2.getLastUsed() ? (account1.getLastUsed() <= account2.getLastUsed() ? 0 : -1) : 1;
    }
}
