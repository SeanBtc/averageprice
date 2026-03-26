package com.example.averageprice;

public class Transaction {
    private final long id;
    private final double price;
    private final double quantity;
    private final TransactionType type;

    public Transaction(long id, double price, double quantity, TransactionType type) {
        this.id = id;
        this.price = price;
        this.quantity = quantity;
        this.type = type;
    }

    public Transaction(double price, double quantity, TransactionType type) {
        this(System.currentTimeMillis(), price, quantity, type);
    }

    public long getId() {
        return id;
    }

    public double getPrice() {
        return price;
    }

    public double getQuantity() {
        return quantity;
    }

    public TransactionType getType() {
        return type;
    }

    public Transaction copyWith(double newPrice, double newQuantity) {
        return new Transaction(id, newPrice, newQuantity, type);
    }
}
