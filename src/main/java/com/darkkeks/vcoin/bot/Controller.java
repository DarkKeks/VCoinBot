package com.darkkeks.vcoin.bot;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Controller {

    private static final int NO_USER = -1;
    private static final int INCOME_THRESHOLD = 25_000;
    private static final int TRANSFER_THRESHOLD = 300_000_000;
    private static final int TRANSFER_LEAVE = 50_000_000;
    private static final int SHARE_PACKET = 500_000_000;

    private static final Set<Integer> blacklist;

    static {
        blacklist = new HashSet<>();
        blacklist.add(349519176);
        blacklist.add(410103684);
        blacklist.add(244896869);
        blacklist.add(191281578);
    }

    private int sinkUser;
    private AccountStorage storage;
    private ClientMonitor monitor;

    private VCoinClient biggestAccount;

    public Controller(ScheduledExecutorService executor) {
        this(NO_USER, executor);
    }

    public Controller(int sinkUser, ScheduledExecutorService executor) {
        this.sinkUser = sinkUser;
        this.storage = new AccountStorage();
        this.monitor = new ClientMonitor();

        executor.scheduleAtFixedRate(() -> {
            monitor.print(storage);
        }, 5, 5, TimeUnit.SECONDS);
    }

    public AccountStorage getStorage() {
        return storage;
    }

    public boolean hasBiggest() {
        return biggestAccount != null && biggestAccount.isActive();
    }

    public VCoinClient getBiggestAccount() {
        return biggestAccount;
    }

    public void onStart(VCoinClient client) {
        storage.update(client);
    }

    public void onStop(VCoinClient client) {
        storage.remove(client);
    }

    public void onStatusUpdate(VCoinClient client) {
        storage.update(client);
        share(client);
        long currentIncome = client.getInventory().getIncome();
        if(currentIncome < INCOME_THRESHOLD) {
            buy(client);
        }
        onTransferTick(client);
    }

    private void buy(VCoinClient client) {
        ItemStack item = client.getInventory().getBestItem();
        if(item != null) {
            if(client.getScore() >= item.getNextPrice()) {
                client.buyItem(item.getItem());
            }
        }
    }

    private void share(VCoinClient client) {
        if(client.getId() == sinkUser) {
            biggestAccount = client;
        }

        if(hasBiggest() && client.getId() != sinkUser) {
            if(client.getInventory().getIncome() < INCOME_THRESHOLD) {
                if(client.getScore() < SHARE_PACKET) {
                    if(biggestAccount.getScore() >= SHARE_PACKET) {
                        biggestAccount.transfer(client.getId(), SHARE_PACKET);
                    }
                }
            }
        }
    }

    private void onTransferTick(VCoinClient client) {
        if(blacklist.contains(client.getId())) return;

        long currentIncome = client.getInventory().getIncome();
        if(currentIncome >= INCOME_THRESHOLD) {
            if(client.getId() != sinkUser && sinkUser != NO_USER) {
                if(client.getScore() >= TRANSFER_THRESHOLD) {
                    client.transfer(sinkUser, client.getScore() - TRANSFER_LEAVE);
                }
            }
        }
    }
}