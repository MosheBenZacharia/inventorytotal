package com.ericversteeg;

public class InventoryTotalLedgerItem {
    private final String description;
    private float qty;
    private final float price;
    private final int itemId;

    public InventoryTotalLedgerItem(String description, float qty, float price, int itemId)
    {
        this.description = description;
        this.qty = qty;
        this.price = price;
        this.itemId = itemId;
    }

    public String getDescription()
    {
        return description;
    }

    public float getQty()
    {
        return qty;
    }

    public float getPrice()
    {
        return price;
    }

    public int getItemId()
    {
        return itemId;
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
