package internals;
import java.io.Serializable;
import java.util.*;

public class LMSRTradingMethod implements TradingMethods, Serializable {

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
        if (request.getAction() != ActionType.BUY) {
            return new TradeResult(false, request.getOptionName(), 0, 0, 0, "Selling is not supported in LMSR");
        }

        String option = request.getOptionName();

        // 2. בדיקה שהאופציה בכלל קיימת באירוע
        if (!this.optionShares.containsKey(option)) {
            return new TradeResult(false, option, 0, 0, 0, "Option does not exist");
        }

        // 3. חישוב עלות הקנייה ה"נקייה" (באמצעות מפה מדומה)
        double poolBefore = getPoolValue();

        Map<String, Integer> simulatedMap = new HashMap<>(this.optionShares);
        simulatedMap.put(option, simulatedMap.get(option) + request.getAmount());
        double poolAfter = calculateCost(simulatedMap); // שימוש בפונקציית העזר שלך!

        double rawCost = poolAfter - poolBefore;

        // 4. חישוב העמלה (רק אם השיטה היא on-purchase)
        double commission = calculateCommission(rawCost, CommissionType.ON_CLOSE);

        double finalCost = rawCost + commission;

        // 5. עדכון המפה האמיתית
        this.optionShares.put(option, this.optionShares.get(option) + request.getAmount());

        TradeResult finalReceipt = new TradeResult(true, option, request.getAmount(), finalCost,
                commission, "Trade successful");
        this.tradeHistory.add(finalReceipt);
        return finalReceipt;
    }

    @Override
    public double calculateCommission(double baseAmount, CommissionType currentPhase) {
        // check that we have matching commissionType if so calc
        if (this.commissionType == currentPhase) {
            return baseAmount * (this.commissionPercent / 100.0);
        }
        // else return 0.0
        return 0.0;
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
        double commission = calculateCommission(totalPayout, commissionType.ON_CLOSE);

        // 4. כמה כסף נטו עובר למנצחים (אחרי ניכוי העמלה)
        double netPayout = totalPayout - commission;

        // 5. מה הערך שנשאר עכשיו בקופת האירוע? (זה הכסף שחוזר ל-MM)
        double leftoverSubsidy = getPoolValue() - totalPayout;

        return new CloseMarketResult(winningOption, totalPayout, commission, netPayout, leftoverSubsidy);
    }

    @Override
    public List<TradeResult> getTradeHistory() {
        return this.tradeHistory;
    }
}
