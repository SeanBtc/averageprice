package com.example.averageprice;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PositionViewModel extends AndroidViewModel {
    private final File dataFile;

    private final Map<String, PositionProfile> profiles = new LinkedHashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();

    private String currentProfileName = "";
    private boolean shortMode = false;
    private String currentPrice = "";

    private double totalQuantity = 0.0;
    private double averagePrice = 0.0;
    private double totalCost = 0.0;

    public PositionViewModel(@NonNull Application application) {
        super(application);
        dataFile = new File(application.getExternalFilesDir(null), "position_data.json");
        loadAllDataFromDisk();

        if (!profiles.isEmpty()) {
            loadProfileState(profiles.keySet().iterator().next());
        } else {
            createNewProfile("默认仓位");
        }
    }

    public Map<String, PositionProfile> getProfiles() {
        return Collections.unmodifiableMap(profiles);
    }

    public List<String> getProfileNames() {
        return new ArrayList<>(profiles.keySet());
    }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public String getCurrentProfileName() {
        return currentProfileName;
    }

    public boolean isShortMode() {
        return shortMode;
    }

    public String getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(String currentPrice) {
        this.currentPrice = currentPrice == null ? "" : currentPrice;
    }

    public double getTotalQuantity() {
        return totalQuantity;
    }

    public double getAveragePrice() {
        return averagePrice;
    }

    public double getTotalCost() {
        return totalCost;
    }

    private void loadProfileState(String name) {
        PositionProfile target = profiles.get(name);
        if (target == null) {
            return;
        }
        currentProfileName = name;
        shortMode = target.isShortMode();
        transactions.clear();
        transactions.addAll(target.getTransactions());
        currentPrice = "";
        calculatePosition();
    }

    public boolean createNewProfile(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || profiles.containsKey(trimmed)) {
            return false;
        }

        saveCurrentToProfileMap();
        profiles.put(trimmed, new PositionProfile(trimmed));
        saveAllDataToDisk();
        loadProfileState(trimmed);
        return true;
    }

    public void switchProfile(String name) {
        if (name == null || name.equals(currentProfileName) || !profiles.containsKey(name)) {
            return;
        }

        saveCurrentToProfileMap();
        saveAllDataToDisk();
        loadProfileState(name);
    }

    public boolean deleteCurrentProfile() {
        if (profiles.size() <= 1) {
            return false;
        }

        profiles.remove(currentProfileName);
        String nextProfile = profiles.keySet().iterator().next();
        loadProfileState(nextProfile);
        saveAllDataToDisk();
        return true;
    }

    private void saveCurrentToProfileMap() {
        if (!currentProfileName.isEmpty()) {
            profiles.put(
                    currentProfileName,
                    new PositionProfile(currentProfileName, shortMode, new ArrayList<>(transactions))
            );
        }
    }

    public boolean addTransaction(double price, double quantity, TransactionType type) {
        boolean isReducing = shortMode ? type == TransactionType.BUY : type == TransactionType.SELL;
        if (isReducing && quantity > totalQuantity) {
            return false;
        }

        transactions.add(new Transaction(price, quantity, type));
        calculatePosition();
        saveCurrentToProfileMap();
        saveAllDataToDisk();
        return true;
    }

    public void removeTransaction(Transaction transaction) {
        transactions.remove(transaction);
        calculatePosition();
        saveCurrentToProfileMap();
        saveAllDataToDisk();
    }

    public void clearAllTransactions() {
        transactions.clear();
        calculatePosition();
        saveCurrentToProfileMap();
        saveAllDataToDisk();
    }

    public void toggleShortMode(boolean enabled) {
        shortMode = enabled;
        calculatePosition();
        saveCurrentToProfileMap();
        saveAllDataToDisk();
    }

    public boolean updateTransaction(Transaction transaction, double newPrice, double newQuantity) {
        int index = -1;
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).getId() == transaction.getId()) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            return false;
        }

        boolean isReducing = shortMode
                ? transaction.getType() == TransactionType.BUY
                : transaction.getType() == TransactionType.SELL;
        if (isReducing && newQuantity > totalQuantity) {
            return false;
        }

        transactions.set(index, transaction.copyWith(newPrice, newQuantity));
        calculatePosition();
        saveCurrentToProfileMap();
        saveAllDataToDisk();
        return true;
    }

    private void calculatePosition() {
        double runningQty = 0.0;
        double runningCost = 0.0;

        for (Transaction tx : transactions) {
            if (!shortMode) {
                if (tx.getType() == TransactionType.BUY) {
                    runningCost += tx.getPrice() * tx.getQuantity();
                    runningQty += tx.getQuantity();
                } else {
                    runningCost -= tx.getPrice() * tx.getQuantity();
                    runningQty -= tx.getQuantity();
                }
            } else {
                if (tx.getType() == TransactionType.SELL) {
                    runningCost += tx.getPrice() * tx.getQuantity();
                    runningQty += tx.getQuantity();
                } else {
                    runningCost -= tx.getPrice() * tx.getQuantity();
                    runningQty -= tx.getQuantity();
                }
            }
        }

        totalQuantity = runningQty;
        totalCost = runningCost;
        averagePrice = runningQty > 0 ? runningCost / runningQty : 0.0;
    }

    public double getCurrentProfit() {
        double curPrice;
        try {
            curPrice = Double.parseDouble(currentPrice);
        } catch (Exception ignored) {
            curPrice = 0.0;
        }

        if (totalQuantity == 0.0) {
            return 0.0;
        }

        return !shortMode
                ? (curPrice - averagePrice) * totalQuantity
                : (averagePrice - curPrice) * totalQuantity;
    }

    public double getCurrentProfitPercent() {
        if (averagePrice == 0.0) {
            return 0.0;
        }

        double curPrice;
        try {
            curPrice = Double.parseDouble(currentPrice);
        } catch (Exception ignored) {
            curPrice = 0.0;
        }

        return !shortMode
                ? (curPrice - averagePrice) / averagePrice * 100.0
                : (averagePrice - curPrice) / averagePrice * 100.0;
    }

    private void saveAllDataToDisk() {
        JSONObject jsonRoot = new JSONObject();
        JSONArray profilesArray = new JSONArray();

        try {
            for (PositionProfile profile : profiles.values()) {
                JSONObject pObj = new JSONObject();
                pObj.put("name", profile.getName());
                pObj.put("isShort", profile.isShortMode());

                JSONArray txArray = new JSONArray();
                for (Transaction tx : profile.getTransactions()) {
                    JSONObject tObj = new JSONObject();
                    tObj.put("p", tx.getPrice());
                    tObj.put("q", tx.getQuantity());
                    tObj.put("t", tx.getType().name());
                    tObj.put("id", tx.getId());
                    txArray.put(tObj);
                }

                pObj.put("txs", txArray);
                profilesArray.put(pObj);
            }

            jsonRoot.put("profiles", profilesArray);
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            java.nio.file.Files.write(dataFile.toPath(), jsonRoot.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadAllDataFromDisk() {
        if (!dataFile.exists()) {
            return;
        }

        try {
            String dataStr = new String(java.nio.file.Files.readAllBytes(dataFile.toPath()), StandardCharsets.UTF_8);
            JSONObject jsonRoot = new JSONObject(dataStr);
            JSONArray profilesArray = jsonRoot.getJSONArray("profiles");

            for (int i = 0; i < profilesArray.length(); i++) {
                JSONObject pObj = profilesArray.getJSONObject(i);
                String name = pObj.getString("name");
                boolean isShort = pObj.getBoolean("isShort");

                List<Transaction> txs = new ArrayList<>();
                JSONArray txArray = pObj.getJSONArray("txs");
                for (int j = 0; j < txArray.length(); j++) {
                    JSONObject tObj = txArray.getJSONObject(j);
                    txs.add(new Transaction(
                            tObj.getLong("id"),
                            tObj.getDouble("p"),
                            tObj.getDouble("q"),
                            TransactionType.valueOf(tObj.getString("t"))
                    ));
                }

                profiles.put(name, new PositionProfile(name, isShort, txs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
