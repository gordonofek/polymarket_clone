package dto;

// why a user's balance changed (one row in their balance history)
public enum BalanceChangeType {
    DEPOSIT,
    WITHDRAW,
    BUY,          // paid for shares (including on-purchase commission)
    SELL,         // got paid for shares sold
    EVENT_START,  // market maker paid to open an event
    COMMISSION,   // market maker received a commission
    PAYOUT,       // paid for winning shares when an event closed
    POOL_RETURN   // market maker got back what was left in a closed event's pool
}