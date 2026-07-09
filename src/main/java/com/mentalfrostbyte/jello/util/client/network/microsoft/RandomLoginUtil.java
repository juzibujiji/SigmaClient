package com.mentalfrostbyte.jello.util.client.network.microsoft;

import com.mentalfrostbyte.jello.managers.AccountManager;
import com.mentalfrostbyte.jello.managers.util.account.microsoft.Account;

import java.util.concurrent.ThreadLocalRandom;

public final class RandomLoginUtil {
    private static final String USERNAME_PREFIX = "Random";
    private static final String USERNAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    private static final int RANDOM_SUFFIX_LENGTH = 8;
    private static final int UNIQUE_USERNAME_ATTEMPTS = 32;

    private RandomLoginUtil() {
    }

    public static Account createOfflineAccount() {
        return new Account(generateUsername(), "");
    }

    public static Account createOfflineAccount(AccountManager accountManager) {
        Account account = createOfflineAccount();

        for (int i = 0; accountManager != null && accountManager.containsAccount(account)
                && i < UNIQUE_USERNAME_ATTEMPTS; i++) {
            account = createOfflineAccount();
        }

        return account;
    }

    public static Account login(AccountManager accountManager) {
        Account account = createOfflineAccount(accountManager);
        return login(accountManager, account) ? account : null;
    }

    public static boolean login(AccountManager accountManager, Account account) {
        if (accountManager == null || account == null || !accountManager.login(account)) {
            return false;
        }

        accountManager.updateAccount(account);
        return true;
    }

    public static String generateUsername() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder username = new StringBuilder(USERNAME_PREFIX);

        for (int i = 0; i < RANDOM_SUFFIX_LENGTH; i++) {
            username.append(USERNAME_CHARS.charAt(random.nextInt(USERNAME_CHARS.length())));
        }

        return username.toString();
    }
}
