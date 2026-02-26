package com.mentalfrostbyte.jello.gui.impl.others;

import com.mentalfrostbyte.jello.managers.util.account.microsoft.Account;
import com.mentalfrostbyte.jello.managers.util.account.microsoft.sorting.*;
import com.mentalfrostbyte.jello.managers.util.account.microsoft.sorting.impl.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AccountSorter {
    public static List<Account> sortByInputAltAccounts(List<Account> var0, AccountCompareType compareType, String serverIP, String input) {
        List<Account> sortedAccounts = sortAltAccounts(var0, compareType, serverIP);
        input = input.toLowerCase();
        if (!input.isEmpty()) {
            List<Account> matchedAccounts = new ArrayList<>();
            Iterator<Account> accountIterator = sortedAccounts.iterator();

            while (accountIterator.hasNext()) {
                Account account = accountIterator.next();
                if (account.getKnownName().toLowerCase().startsWith(input)) {
                    matchedAccounts.add(account);
                    accountIterator.remove();
                }
            }

            Iterator<Account> accountIterator2 = sortedAccounts.iterator();

            while (accountIterator2.hasNext()) {
                Account account = accountIterator2.next();
                if (account.getKnownName().toLowerCase().contains(input)) {
                    matchedAccounts.add(account);
                    accountIterator2.remove();
                }
            }

            matchedAccounts.addAll(sortedAccounts);
            return matchedAccounts;
        } else {
            return sortedAccounts;
        }
    }

    public static List<Account> sortAltAccounts(List<Account> accountList, AccountCompareType compareType, String serverIP) {
        List<Account> sortedList = new ArrayList<>(accountList);
        switch (compareType) {
            case Alphabetical:
                sortedList.sort(new AccountNameComparator());
                break;
            case Bans:
                sortedList.sort(new AccountBanDateComparator(serverIP));
                break;
            case DateAdded:
                sortedList.sort(new AccountDateAddedComparator());
                break;
            case LastUsed:
                sortedList.sort(new AccountLastUsedComparator());
                break;
            case UseCount:
                sortedList.sort(new AccountUsageCountComparator());
        }

        return sortedList;
    }
}
