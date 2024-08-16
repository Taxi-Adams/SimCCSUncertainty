package dataStore;

/**
 *
 * @author yaw
 */
public class Sink {
    private int cellNum;
    private double openingCost;
    private double omCost;
    private double wellOpeningCost;
    private double wellOMCost;
    private double[] injectionCosts;
    private double wellCapacity;
    private double[] phaseCapacities;
    private double capacity;
    private String label;
    private String id;
    
    private DataStorer data;
    
    private double remainingCapacity;    //Heuristic
    private int numWells;   //Heuristic
    
    public Sink(DataStorer data) {
        this.data = data;
    }
    
    public void setCellNum(int cellNum) {
        this.cellNum = cellNum;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public void setID(String id) {
        this.id = id;
    }
    
    public void setOpeningCost(double openingCost) {
        this.openingCost = openingCost;
    }
    
    public void setOMCost(double omCost) {
        this.omCost = omCost;
    }
    
    public void setWellOpeningCost(double wellOpeningCost) {
        this.wellOpeningCost = wellOpeningCost;
    }
    
    public void setWellOMCost(double wellOMCost) {
        this.wellOMCost = wellOMCost;
    }
    
    public void setInjectionCosts(double[] injectionCost) {
        this.injectionCosts = injectionCost;
    }
    
    public void setWellCapacity(double wellCapacity) {
        this.wellCapacity = wellCapacity;
    }
    
    public void setPhaseCapacities(double[] phaseCapacities) {
        this.phaseCapacities = phaseCapacities;
    }
    
    // Under Uncertainty
    public void setCapacity(double capacity) {
        this.capacity = capacity;
    }
    
    // Heuristic
    public void setRemainingCapacity(double remaingCapacity) {
        this.remainingCapacity = remaingCapacity;
    }
    
    // Heuristic
    public void setNumWells(int numWells) {
        this.numWells = numWells;
    }
    
    //Heuristic
    public int getNumWells() {
        return numWells;
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
    
    public double getWellOpeningCost(double crf) {
        return crf * wellOpeningCost + wellOMCost;
    }
    
    public double getInjectionCost() {
        return injectionCosts[0];
    }
    
    public double getInjectionCost(int phase) {
        if (phase >= injectionCosts.length) {
            return injectionCosts[0];
        }
        return injectionCosts[phase];
    }
    
    public double getWellCapacity() {
        return wellCapacity;
    }
    
    public double getCapacity() {
        return capacity;
    }
    
    public double getPhaseCapacity(int phase) {
        if (phaseCapacities == null || phase >= phaseCapacities.length) {
            return capacity;
        }
        return phaseCapacities[phase];
    }
    
    //public int getNumCapacities() {
    //    return capacities.length;
    //}
    
    public boolean isSimplified() {
        if (openingCost == 0 && omCost == 0 && wellOpeningCost == 0 && wellOMCost == 0) {
            return true;
        }
        return false;
    }
}
