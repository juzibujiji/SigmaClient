package com.mentalfrostbyte.jello.managers.util.account.microsoft.sorting.impl;

import com.mentalfrostbyte.jello.managers.util.account.microsoft.Account;
import com.mentalfrostbyte.jello.managers.util.account.microsoft.Ban;

import java.util.Comparator;
import java.util.Date;

public record AccountBanDateComparator(String serverIP) implements Comparator<Account> {
    public int compare(Account account1, Account account2) {
        Ban ban = account1.getBanInfo(this.serverIP);
        Ban ban2 = account2.getBanInfo(this.serverIP);
        Date date;
        if (ban != null) {
            date = ban.getDate();
        } else {
            date = new Date();
        }

        Date date2;
        if (ban2 != null) {
            date2 = ban2.getDate();
        } else {
            date2 = new Date();
        }

        if (ban != null && ban2 != null) {
            long var9 = date.getTime() - new Date().getTime();
            long var11 = date2.getTime() - new Date().getTime();
            if (var9 < 0L && var11 < 0L) {
                return date2.compareTo(date);
            }
        }

        return date.compareTo(date2);
    }
}
