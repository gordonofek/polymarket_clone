package internals;
import java.util.*;

public class LMSRTradingMethod implements TradingMethods {

    // the b var from the equations
    private int liquidity;
    // instead of only having yes or no options, thinking
    // ahead of part 3 and making LMSR support multi choice bets
    private Map<String, Integer> optionShares;
    private int commissionPercent;
    private CommissionType commissionType;
    private List<TradeResult> tradeHistory;


    public LMSRTradingMethod(int liquidity, Map<String, Integer> initialOptions, int commissionPercent, CommissionType commissionType) {
        this.liquidity = liquidity;
        this.optionShares = initialOptions;
        this.commissionPercent = commissionPercent;
        this.commissionType = commissionType;
        this.tradeHistory = new ArrayList<>();
    }

    /**
     * HELPER 1: The Sigma Sum (Denominator)
     * Calculates the sum of e^(q_i / b) for all options currently in the map.
     */
    private double calculateSigmaSum(Map<String, Integer> stateToCalculate) {
        double sum = 0.0;
        for (int q : stateToCalculate.values()) {
            sum += Math.exp((double) q / this.liquidity);
        }
        return sum;
    }

    /**
     * HELPER 2: The Cost Function C(q)
     * Calculates the total value of the pool for a given state of shares.
     */
    private double calculateCost(Map<String, Integer> stateToCalculate) {
        return this.liquidity * Math.log(calculateSigmaSum(stateToCalculate));
    }

    @Override
    public double getPoolValue() {
        // The current pool value uses the actual, current state of the market
        return calculateCost(this.optionShares);
    }

    @Override
    public double getPrice(String optionName) {
        // If the option doesn't exist, default to 0 shares (or throw an exception)
        int qTarget = this.optionShares.getOrDefault(optionName, 0);

        // Numerator: e^(q_target / b)
        double numerator = Math.exp((double) qTarget / this.liquidity);

        // Denominator: sum of e^(q_i / b) for all options
        double denominator = calculateSigmaSum(this.optionShares);

        return numerator / denominator;
    }

    @Override
    public TradeResult executeOrder(TradeRequest request) {
        // 1. חסימת מכירה
        if (request.action() != ActionType.BUY) {
            return new TradeResult(false, request.optionName(), 0, 0, 0, "Selling is not supported in LMSR");
        }

        String option = request.optionName();

        // 2. בדיקה שהאופציה בכלל קיימת באירוע
        if (!this.optionShares.containsKey(option)) {
            return new TradeResult(false, option, 0, 0, 0, "Option does not exist");
        }

        // 3. חישוב עלות הקנייה ה"נקייה" (באמצעות מפה מדומה)
        double poolBefore = getPoolValue();

        Map<String, Integer> simulatedMap = new HashMap<>(this.optionShares);
        simulatedMap.put(option, simulatedMap.get(option) + request.amount());
        double poolAfter = calculateCost(simulatedMap); // שימוש בפונקציית העזר שלך!

        double rawCost = poolAfter - poolBefore;

        // 4. חישוב העמלה (רק אם השיטה היא on-purchase)
        double commission = calculateCommission(rawCost, "on-purchase");

        double finalCost = rawCost + commission;

        // 5. עדכון המפה האמיתית
        this.optionShares.put(option, this.optionShares.get(option) + request.amount());

        TradeResult finalReceipt = new TradeResult(true, option, request.amount(), finalCost,
                commission, "Trade successful");
        this.tradeHistory.add(finalReceipt);
        return finalReceipt;
    }

    @Override
    public double calculateCommission(double baseAmount, String currentPhase) {
        // בודקים האם שלבה הגבייה הנוכחי תואם לשיטת העמלה שהוגדר ב-XML לאירוע הזה
        if (this.commissionType.equals(currentPhase)) {
            return baseAmount * (this.commissionPercent / 100.0);
        }
        return 0.0; // אם אנחנו בשלב רכישה אבל העמלה היא on-close, נחזיר 0
    }

    @Override
    public int getOptionShares(String optionName)
    {
        return this.optionShares.getOrDefault(optionName, 0);
    }

    @Override
    public double openMarket() {
        return getPoolValue();
    }

    @Override
    public CloseMarketResult closeMarket(String winningOption) {
        // 1. כמה מניות מנצחות יש בסך הכל?
        int winningShares = this.optionShares.getOrDefault(winningOption, 0);

        // 2. חישוב התשלום הכולל (דולר 1 על כל מניה מנצחת)
        double totalPayout = winningShares * 1.0;

        // 3. חישוב העמלה באמצעות הפונקציה הכללית שיצרנו!
        double commission = calculateCommission(totalPayout, "on-close");

        // 4. כמה כסף נטו עובר למנצחים (אחרי ניכוי העמלה)
        double netPayout = totalPayout - commission;

        // 5. מה הערך שנשאר עכשיו בקופת האירוע? (זה הכסף שחוזר ל-MM)
        double leftoverSubsidy = getPoolValue();

        return new CloseMarketResult(winningOption, totalPayout, commission, netPayout, leftoverSubsidy);
    }

    @Override
    public List<TradeResult> getTradeHistory() {
        return this.tradeHistory;
    }
}
