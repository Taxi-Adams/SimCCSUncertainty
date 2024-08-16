package dataStore;

/**
 *
 * @author yaw
 */
public class Source {
    private int cellNum;
    private double openingCost;
    private double omCost;
    private double[] captureCosts;
    private double[] productionRates;
    private String label;
    private String id;
    
    private DataStorer data;
    
    private double remainingCapacity;    //Heuristic
    
    public Source(DataStorer data) {
        this.data = data;
    }
    
    public void setCellNum(int cellNum) {
        this.cellNum = cellNum;
    }
    
    public void setID(String id) {
        this.id = id;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public void setOpeningCost(double openingCost) {
        this.openingCost = openingCost;
    }
    
    public void setOMCost(double omCost) {
        this.omCost = omCost;
    }
    
    public void setCaptureCosts(double[] captureCost) {
        this.captureCosts = captureCost;
    }
    
    public void setProductionRates(double[] productionRates) {
        this.productionRates = productionRates;
    }
    
    // Heuristic
    public void setRemainingCapacity(double remaingCapacity) {
        this.remainingCapacity = remaingCapacity;
    }

    // Heuristic
    public double getRemainingCapacity() {
        return remainingCapacity;
    }
    
    public int getCellNum() {
        return cellNum;
    }
    
    public String getLabel() {
        return label;
    }
    
    public String getID() {
        return id;
    }
    
    public double getOpeningCost(double crf) {
        return crf * openingCost + omCost;
    }
    
    public double getCaptureCost() {
        return captureCosts[0];
    }
    
    public double getCaptureCost(int phase) {
        if (phase >= captureCosts.length) {
            return captureCosts[0];
        }
        return captureCosts[phase];
    }
    
    public double getProductionRate() {
        return productionRates[0];
    }
    
    public double getProductionRate(int phase) {
        if (phase >= productionRates.length) {
            return productionRates[0];
        }
        return productionRates[phase];
    }
    
    public double getMaxProductionRate() {
        double max = Double.NEGATIVE_INFINITY;
        
        for (double cur: productionRates) {
            max = Math.max(max, cur);
        }
        return max;
    }
    
    public boolean isSimplified() {
        if (openingCost == 0 && omCost == 0) {
            return true;
        }
        return false;
    }
}
