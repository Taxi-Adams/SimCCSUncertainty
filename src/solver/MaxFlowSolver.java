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
public class MaxFlowSolver {
    // Instance fields
    private boolean isEdgesOnly; // cell # has only edges connected
    private boolean isSourcesOnly; // cell # has only sources and potentially edges connected
    private boolean isSinksOnly; // cell # has only sinks and potentially edges connected
    private boolean isSourcesAndSinks; // cell # has both sources and sinks connected, and potentially edges too
    private double totalCaptured;
    private double totalStored;
    private int numbOfRealizations;
    private HashMap<Edge, Integer> hmEdges; // edges and number of base solutions used in
    private HashMap<Source, Integer> hmSources; // sources and number of base solutions used in
    private HashMap<Sink, Integer> hmSinks; // sinks and number of base solutions used in
    private HashMap<Edge, Double> hmEdgesAveVol; // edges and average amount of CO2 transported along them
    private HashMap<Edge, Double> hmEdgesAveCost; // edges and average cost of CO2 transported along them, per ton
    private HashMap<Integer, ArrayList<Source>> hmCellsAndSources; // cells (coordinates on map) and the sources located at them
    private HashMap<Integer, ArrayList<Sink>> hmCellsAndSinks; // cells and the sinks located at them
    private ArrayList<Integer> hmCells; // a list of cells used in the network
    // variables
    private HashMap<Edge, IloNumVar> fe; // edge transport variable
    private HashMap<Source, IloNumVar> caps; // source capture variable
    private HashMap<Sink, IloNumVar> strk; // sink storage variable
    // output HashMaps for use in display
    private HashMap<Edge, Double> hmEdgesFinal; // output edges and amount transported along them
    private HashMap<Source, Double> hmSourcesFinal; // output sources and amount captured from them
    private HashMap<Sink, Double> hmSinksFinal; // output sinks and amount stored in them
    private Gui gui; // a reference to the gui class for method calls
    // More optimized hashmaps min/maxes?
    HashMap<Source, Double> hmSourcesMax; // sources and the max amount that's allowed to be captured from them
    HashMap<Sink, Double> hmSinksSpecified; // sinks and the max amount that's allowed to be stored in them
    // File variables
    String basePath;
    String dataset;
    String scenario;
    
    // Constructor
    public MaxFlowSolver(HashMap<Edge, Integer> hmEdges, HashMap<Source, Integer> hmSources, HashMap<Sink, Integer> hmSinks,
    HashMap<Edge, Double> hmEdgesAveVol, HashMap<Integer, ArrayList<Source>> hmCellsAndSources, HashMap<Integer, ArrayList<Sink>> hmCellsAndSinks, 
    ArrayList<Integer> hmCells, Gui gui, int numbOfRealizations, HashMap<Edge, Double> hmEdgesAveCost, HashMap<Source, Double> hmSourcesMax, 
    HashMap<Sink, Double> hmSinksSpecified, String basePath, String dataset, String scenario) {
        this.hmEdges = hmEdges;
        this.hmSources = hmSources;
        this.hmSinks = hmSinks;
        this.hmEdgesAveVol = hmEdgesAveVol;
        this.hmSourcesMax = hmSourcesMax;
        this.hmSinksSpecified = hmSinksSpecified;
        this.hmCellsAndSources = hmCellsAndSources;
        this.hmCellsAndSinks = hmCellsAndSinks;
        this.hmEdgesAveCost = hmEdgesAveCost;
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
        this.basePath = basePath;
        this.dataset = dataset;
        this.scenario = scenario;
    }
    
    public void writeHeatMapMPS() {
        // This method will be called in control actions, pass in data there
        try {
            IloCplex cplex = new IloCplex();

            // These 3 variables should handle contraints A, B, and C in their declaration
            // Variable: amount transported by edge
            fe = new HashMap<>();
            for(Edge edge : hmEdgesAveVol.keySet()) {
                if(((double)hmEdges.get(edge) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    if(!fe.containsKey(edge)) {
                        fe.put(edge, cplex.numVar(0, Double.POSITIVE_INFINITY, "edgAmnt [" + edge + "]"));
                    }
                }
            }
            
            // Variable: amount captured from source
            caps = new HashMap<>();
            for(Source source : hmSources.keySet()) {
                if(((double)hmSources.get(source) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    if(!caps.containsKey(source)) {
                        caps.put(source, cplex.numVar(0, hmSourcesMax.get(source), "src[" + source.getID() + "]"));
                    }
                }
            }
            
            // Variable: amount stored in sink
            strk = new HashMap<>();
            for(Sink sink : hmSinks.keySet()) {
                if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    if(!strk.containsKey(sink)) {
                        strk.put(sink, cplex.numVar(0, hmSinksSpecified.get(sink), "snk[" + sink.getID() + "]"));
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
            for(Sink sink : hmSinks.keySet()) {
                if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    objExpr.addTerm(1, strk.get(sink));
                }
            }
            
            // We want to maximize the sum of the amount stored in each sink over the set of all sources
            // Objective:
            IloObjective obj = cplex.maximize(objExpr);
            cplex.add(obj);
            cplex.setOut(null);
            
            // output text file containing problem formulation
            cplex.exportModel(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + "MaxFlowFile.lp");
            
            // Solve: 
            if(cplex.solve()) {
                // stores values for edges, sources, and sinks to HashMaps to output data
                for(Edge edge : hmEdgesAveVol.keySet()) {
                    if(((double)hmEdges.get(edge) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                        hmEdgesFinal.put(edge, cplex.getValue(fe.get(edge)));
                    }
                }
                
                for(Source source : hmSources.keySet()) {
                    if(((double)hmSources.get(source) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                        hmSourcesFinal.put(source, cplex.getValue(caps.get(source)));
                    }
                }
                
                for(Sink sink : hmSinks.keySet()) {
                    if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
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
                        expr.addTerm(-1, fe.get(edge));
                    } else if(edge.v1 == cellNumb) {
                        // Subtracting the overall sum term
                        expr.addTerm(1, fe.get(edge));
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
            for(Source source : hmSources.keySet()) {
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
            for(Sink sink : hmSinks.keySet()) {
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
    
    // Resets boolean values used for conservation of flow calculations
    private void resetCellBooleans() {
        isSourcesAndSinks = false;
        isEdgesOnly = false;
        isSourcesOnly = false;
        isSinksOnly = false;
    }
    
    // For debugging flow conservation and price: Displays captured, stored, and price amounts
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
        
        for(Edge edge : hmEdgesFinal.keySet()) {
            transportCost += hmEdgesAveCost.get(edge)*Math.pow(10, 6)/(double)hmEdges.get(edge);
        }
        
        for(Source source : hmSourcesFinal.keySet()) {
            capCost += source.getCaptureCost()*hmSourcesFinal.get(source)*Math.pow(10, 6);
        }
        
        for(Sink sink : hmSinksFinal.keySet()) {
            storeCost += sink.getInjectionCost()*hmSinksFinal.get(sink)*Math.pow(10, 6);
        }

        System.out.println("\nTotal Cost: " + (transportCost + capCost + storeCost));
    }
    
    public double getTotalStored() {
        return totalStored;
    }
}