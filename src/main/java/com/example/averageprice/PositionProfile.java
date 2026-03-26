package com.example.averageprice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PositionProfile {
    private final String name;
    private final boolean shortMode;
    private final List<Transaction> transactions;

    public PositionProfile(String name) {
        this(name, false, new ArrayList<>());
    }

    public PositionProfile(String name, boolean shortMode, List<Transaction> transactions) {
        this.name = name;
        this.shortMode = shortMode;
        this.transactions = new ArrayList<>(transactions);
    }

    public String getName() {
        return name;
    }

    public boolean isShortMode() {
        return shortMode;
    }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }
}
