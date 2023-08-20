package com.ericversteeg;

public class InventoryTotalLedgerItem {
    private final String description;
    private float qty;
    private final int price;

    public InventoryTotalLedgerItem(String description, float qty, int price)
    {
        this.description = description;
        this.qty = qty;
        this.price = price;
    }

    public String getDescription()
    {
        return description;
    }

    public float getQty()
    {
        return qty;
    }

    public int getPrice()
    {
        return price;
    }

    public void addQuantityDifference(float qtyDifference)
    {
        qty += qtyDifference;
    }

    public long getCombinedValue()
    {
        return (long) (this.qty * this.price);
    }
}
