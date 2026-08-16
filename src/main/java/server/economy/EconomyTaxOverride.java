package server.economy;

public record EconomyTaxOverride(int buyerBasisPoints, int sellerBasisPoints) {
    public EconomyTaxOverride {
        if (buyerBasisPoints < 0 || buyerBasisPoints > 10_000
                || sellerBasisPoints < 0 || sellerBasisPoints > 10_000)
            throw new IllegalArgumentException("tax rates must be within zero and 10000 basis points");
    }

    public int buyerTax(int gross) { return tax(gross, buyerBasisPoints); }
    public int sellerTax(int gross) { return tax(gross, sellerBasisPoints); }
    private static int tax(int gross, int basisPoints) {
        return (int) Math.min(Integer.MAX_VALUE, Math.floorDiv((long) gross * basisPoints, 10_000));
    }
}
