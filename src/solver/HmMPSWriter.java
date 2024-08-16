/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package solver;
        
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloObjective;
import ilog.cplex.IloCplex;
import dataStore.Edge;
import dataStore.Source;
import dataStore.Sink;
import gui.Gui;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author danie
 */
public class HmMPSWriter {
    // Instance fields
    private boolean isEdgesOnly; // cell # has only edges connected
    private boolean isSourcesOnly; // cell # has only sources and potentially edges connected
    private boolean isSinksOnly; // cell # has only sinks and potentially edges connected
    private boolean isSourcesAndSinks; // cell # has both sources and sinks connected, and potentially edges too
    private double totalCaptured;
    private double totalStored;
    private int numbOfRealizations;
    private HashMap<Edge, Integer> hmEdges;
    private HashMap<Source, Integer> hmSources;
    private HashMap<Sink, Integer> hmSinks;
    private HashMap<Edge, Double> hmEdgesAveVol;
    private HashMap<Source, Double> hmSourcesCaptureAmnt;
    private HashMap<Sink, Double> hmSinksAnnualStored;
    private HashMap<Edge, Double> hmEdgesAveCost;
    private HashMap<Source, Double> hmSourcesCaptureCost;
    private HashMap<Sink, Double> hmSinksInjectionCost;
    private HashMap<Integer, ArrayList<Edge>> hmCellsAndEdges;
    private HashMap<Integer, ArrayList<Source>> hmCellsAndSources;
    private HashMap<Integer, ArrayList<Sink>> hmCellsAndSinks;
    private ArrayList<Integer> hmCells;
    // variables
    private HashMap<Edge, IloNumVar> fe;
    private HashMap<Source, IloNumVar> caps;
    private HashMap<Sink, IloNumVar> strk;
    // output HashMaps for use in display
    private HashMap<Edge, Double> hmEdgesFinal;
    private HashMap<Source, Double> hmSourcesFinal;
    private HashMap<Sink, Double> hmSinksFinal;
    private Gui gui;
    // More optimized hashmaps min/maxes?
    HashMap<Edge, Double> hmEdgesMax;
    HashMap<Source, Double> hmSourcesMax;
    HashMap<Sink, Double> hmSinksMin;
    // Selectable sink values to use hashmap
    HashMap<Sink, Double> hmSinksSpecified;
    
    // Constructor
    public HmMPSWriter(HashMap<Edge, Integer> hmEdges, HashMap<Source, Integer> hmSources, HashMap<Sink, Integer> hmSinks,
    HashMap<Edge, Double> hmEdgesAveVol, HashMap<Source, Double> hmSourcesCaptureAmnt, HashMap<Sink, Double> hmSinksAnnualStored, 
    HashMap<Integer, ArrayList<Edge>> hmCellsAndEdges, HashMap<Integer, ArrayList<Source>> hmCellsAndSources, 
    HashMap<Integer, ArrayList<Sink>> hmCellsAndSinks, ArrayList<Integer> hmCells, Gui gui, int numbOfRealizations,
    HashMap<Edge, Double> hmEdgesAveCost, HashMap<Source, Double> hmSourcesCaptureCost, HashMap<Sink, Double> hmSinksInjectionCost, 
    HashMap<Edge, Double> hmEdgesMax, HashMap<Source, Double> hmSourcesMax, HashMap<Sink, Double> hmSinksMin, 
    HashMap<Sink, Double> hmSinksSpecified) {
        this.hmEdges = hmEdges;
        this.hmSources = hmSources;
        this.hmSinks = hmSinks;
        this.hmEdgesAveVol = hmEdgesAveVol;
        this.hmSourcesCaptureAmnt = hmSourcesCaptureAmnt;
        this.hmSinksAnnualStored = hmSinksAnnualStored;
        this.hmEdgesMax = hmEdgesMax;
        this.hmSourcesMax = hmSourcesMax;
        this.hmSinksMin = hmSinksMin;
        this.hmSinksSpecified = hmSinksSpecified;
        this.hmCellsAndEdges = hmCellsAndEdges;
        this.hmCellsAndSources = hmCellsAndSources;
        this.hmCellsAndSinks = hmCellsAndSinks;
        this.hmEdgesAveCost = hmEdgesAveCost;
        this.hmSourcesCaptureCost = hmSourcesCaptureCost;
        this.hmSinksInjectionCost = hmSinksInjectionCost;
        isEdgesOnly = false;
        isSourcesOnly = false;
        isSinksOnly = false;
        isSourcesAndSinks = false;
        this.hmCells = hmCells;
        hmEdgesFinal = new HashMap<>();
        hmSourcesFinal = new HashMap<>();
        hmSinksFinal = new HashMap<>();
        totalCaptured = 0.0;
        totalStored = 0.0;
        this.numbOfRealizations = numbOfRealizations;
        this.gui = gui;
    }
    
    public void writeHeatMapMPS() {
        // This method will be called in control actions, pass in data there
        try {
            IloCplex cplex = new IloCplex();

            // These 3 variables should handle contraints A, B, and C in their declaration
            // Variable: amount transported by edge
            fe = new HashMap<>();
//            IloNumVar[] fe = new IloNumVar[hmEdgesAveVol.size()];
            for(Edge edge : hmEdgesAveVol.keySet()) {
                if(((double)hmEdges.get(edge) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    if(!fe.containsKey(edge)) {
                        // max transport version, also used for variable injection version
                        fe.put(edge, cplex.numVar(0, hmEdgesMax.get(edge)));
                        // old version
//                        fe.put(edge, cplex.numVar(0, hmEdgesAveVol.get(edge) / (double)hmEdges.get(edge))); // Might want to revert back to no division for these 3, but amount fed in now is sum without divided amount
                    }
                }
            }
            
            // Variable: amount captured from source
            caps = new HashMap<>();
            for(Source source : hmSourcesCaptureAmnt.keySet()) {
                if(((double)hmSources.get(source) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    if(!caps.containsKey(source)) {
                        // max capture version, also used for variable injection version
                        caps.put(source, cplex.numVar(0, hmSourcesMax.get(source)));
                        // old version
//                        caps.put(source, cplex.numVar(0, hmSourcesCaptureAmnt.get(source) / (double)hmSources.get(source)));
                    }
                }
            }
            
            // Variable: amount stored in sink
            strk = new HashMap<>();
            for(Sink sink : hmSinksAnnualStored.keySet()) {
                if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    if(!strk.containsKey(sink)) {
                        // variable injection version
                        strk.put(sink, cplex.numVar(0, hmSinksSpecified.get(sink)));
                        // min injection version
//                        strk.put(sink, cplex.numVar(0, hmSinksMin.get(sink)));
                        // old version
//                        strk.put(sink, cplex.numVar(0, hmSinksAnnualStored.get(sink) / (double)hmSinks.get(sink)));
                    }
                }
            }        
            
            // Constraint D
            for(int cell : hmCells) {
                // create an IloLinearNumExpr
                IloLinearNumExpr expr = cplex.linearNumExpr();
                // add the edge terms (has to be done no matter what)
                addEdgeTerms(expr, cell);
                // determine boolean items related to cell number
                determineCellItems(cell);   
                // determine boolean scenario for cell # and perform necessary steps
                if(isEdgesOnly) {
                    // Ensures sum of flow going in equals sum of flow going out and adds eqn
                    cplex.addEq(expr, 0);
                } else if(isSourcesOnly) {
                    // add the source terms
                    addSourceTerms(expr, cell);
                    cplex.addEq(expr, 0);
                } else if(isSinksOnly) {
                    // add the sink terms
                    addSinkTerms(expr, cell);
                    cplex.addEq(expr, 0);
                } else if(isSourcesAndSinks) {
                    // add both the source and sink terms
                    addSourceTerms(expr, cell);
                    addSinkTerms(expr, cell);
                    cplex.addEq(expr, 0);
                }
                // reset boolean items related to cell number
                resetCellBooleans();
            }
            
            // Creates an expression of the sum of all sink terms -  to be maximized
            IloLinearNumExpr objExpr = cplex.linearNumExpr();
            for(Sink sink : hmSinksAnnualStored.keySet()) {
                if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    objExpr.addTerm(1, strk.get(sink));
                }
            }
            
            // We want to maximize the sum of the amount stored in each sink over the set of all sources
            // Objective:
            IloObjective obj = cplex.maximize(objExpr);
            cplex.add(obj);
            cplex.setOut(null);
            
            // Solve: 
            if(cplex.solve()) {
                // prints out edges, sources, and sinks and their corresponding CO2 amounts for debugging purposes
                // also stores values for edges, sources, and sinks to HashMaps to output data
//                System.out.println("Edges: ");
                for(Edge edge : hmEdgesAveVol.keySet()) {
                    if(((double)hmEdges.get(edge) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                        boolean followsCon = true;
                        if(cplex.getValue(fe.get(edge)) > hmEdgesAveVol.get(edge)) {
                            followsCon = false;
                        }
                        // Debugging Line
//                        System.out.println(edge + ", Old Value: " + hmEdgesAveVol.get(edge)/(double)hmEdges.get(edge) + ", New Value: " + cplex.getValue(fe.get(edge)) + ", Follows <=: " + followsCon);
                        hmEdgesFinal.put(edge, cplex.getValue(fe.get(edge)));
                    }
                }
                
//                System.out.println(); // spacing between debug outputs
//                System.out.println("Sources: ");
                for(Source source : hmSourcesCaptureAmnt.keySet()) {
                    if(((double)hmSources.get(source) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                        boolean followsCon = true;
                        if(cplex.getValue(caps.get(source)) > hmSourcesCaptureAmnt.get(source)) {
                            followsCon = false;
                        }
                        // Debugging Line
//                        System.out.println(source.getID() + ", Old Value: " + hmSourcesCaptureAmnt.get(source)/(double)hmSources.get(source) + ", New Value: " + cplex.getValue(caps.get(source)) + ", Follows <=: " + followsCon);
                        hmSourcesFinal.put(source, cplex.getValue(caps.get(source)));
                    }
                }
                
//                System.out.println(); // spacing between debug outputs
//                System.out.println("Sinks: ");
                for(Sink sink : hmSinksAnnualStored.keySet()) {
                    if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                        boolean followsCon = true;
                        if(cplex.getValue(strk.get(sink)) > hmSinksAnnualStored.get(sink)) {
                            followsCon = false;
                        } 
                        // Debugging Line
//                        System.out.println(sink.getID() + ", Old Value: " + hmSinksAnnualStored.get(sink)/(double)hmSinks.get(sink) + ", New Value: " + cplex.getValue(strk.get(sink)) + ", Follows <=: " + followsCon);
                        hmSinksFinal.put(sink, cplex.getValue(strk.get(sink)));
                    }
                }
            } else {
                System.out.println("Not feasible");
            }      
        } catch(IloException ex) {
            ex.printStackTrace();
        }  
    }
    
    // Adds edge CO2 terms into passed in IloLinearNumExpr
    private void addEdgeTerms(IloLinearNumExpr expr, int cellNumb) {
        try {
            for(Edge edge : hmEdgesAveVol.keySet()) {
                if(((double)hmEdges.get(edge) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    // Add terms for edges flowing into cell #
                    if(edge.v2 == cellNumb) {
                        // Adding the overall sum term
                        expr.addTerm(1, fe.get(edge));
                    } else if(edge.v1 == cellNumb) {
                        // Subtracting the overall sum term
                        expr.addTerm(-1, fe.get(edge));
                    }
                }
            }
        } catch(IloException ex) {
            ex.printStackTrace();
        }
    }
    
    // Adds source CO2 terms into passed in IloLinearNumExpr
    private void addSourceTerms(IloLinearNumExpr expr, int cellNumb) {
        try {
            for(Source source : hmSourcesCaptureAmnt.keySet()) {
                if(((double)hmSources.get(source) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    if(source.getCellNum() == cellNumb) {
                        expr.addTerm(1, caps.get(source));
                    }
                }
            }
        } catch(IloException ex) {
            ex.printStackTrace();
        }
    }
    
    // Adds sink CO2 terms into passed in IloLinearNumExpr
    private void addSinkTerms(IloLinearNumExpr expr, int cellNumb) {
        try {
            for(Sink sink : hmSinksAnnualStored.keySet()) {
                if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    if(sink.getCellNum() == cellNumb) {
                        expr.addTerm(-1, strk.get(sink));
                    }
                }   
            }
        } catch(IloException ex) {
            ex.printStackTrace();
        }
    }
    
    // returns the calculated hmEdgesFinal, if extant
    public HashMap<Edge, Double> gethmEdgesFinal() {
        return hmEdgesFinal;
    }
    
    // returns the calculated hmSourcesFinal, if extant
    public HashMap<Source, Double> gethmSourcesFinal() {
        return hmSourcesFinal;
    }
    
    // returns the calculated hmSinksFinal, if extant
    public HashMap<Sink, Double> gethmSinksFinal() {
        return hmSinksFinal;
    }
    
    // This method sets boolean variables tracking tbe scenrio type depending on cell #
    private void determineCellItems(int cellNumb) {
        if(hmCellsAndSources.containsKey(cellNumb) && hmCellsAndSinks.containsKey(cellNumb)) {
            isSourcesAndSinks = true;
        } else if(hmCellsAndSources.containsKey(cellNumb)) {
            isSourcesOnly = true;
        } else if(hmCellsAndSinks.containsKey(cellNumb)) {
            isSinksOnly = true;
        } else {
            isEdgesOnly = true;
        }
    }
    
    private void resetCellBooleans() {
        isSourcesAndSinks = false;
        isEdgesOnly = false;
        isSourcesOnly = false;
        isSinksOnly = false;
    }
    
    public void checkCapStrTotaled() {
        double transportCost = 0.0;
        double capCost = 0.0;
        double storeCost = 0.0;
        for(Source source : hmSourcesFinal.keySet()) {
            totalCaptured += hmSourcesFinal.get(source);
        }
        
        for(Sink sink : hmSinksFinal.keySet()) {
            totalStored += hmSinksFinal.get(sink);
        }
        System.out.println("Max Flow Amounts");
        System.out.println("Total Captured: " + totalCaptured);
        System.out.println("Total Stored: " + totalStored);
        
        // excel output for quick copying
        System.out.println("\n\nExcel Text");
        
        
        for(Edge edge : hmEdgesFinal.keySet()) {
            transportCost += hmEdgesAveCost.get(edge)/(double)hmEdges.get(edge);
        }
        
        for(Source source : hmSourcesFinal.keySet()) {
            capCost += hmSourcesCaptureCost.get(source)/(double)hmSources.get(source);
        }
        
        for(Sink sink : hmSinksFinal.keySet()) {
            storeCost += hmSinksInjectionCost.get(sink)/(double)hmSinks.get(sink);
        }
//        System.out.println("Edge Cost: " + transportCost);
//        System.out.println("Capture Cost: " + capCost);
//        System.out.println("Storage Cost: " + storeCost);
        System.out.println("Total Cost: " + (transportCost + capCost + storeCost));
    }
    
    public double getTotalStored() {
        return totalStored;
    }
}
