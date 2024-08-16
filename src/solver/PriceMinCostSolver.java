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
import dataStore.LinearComponent;
import dataStore.Source;
import dataStore.Sink;
import gui.Gui;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author danie
 */
public class PriceMinCostSolver {
    // Instance fields
    private boolean isEdgesOnly; // cell # has only edges connected
    private boolean isSourcesOnly; // cell # has only sources and potentially edges connected
    private boolean isSinksOnly; // cell # has only sinks and potentially edges connected
    private boolean isSourcesAndSinks; // cell # has both sources and sinks connected, and potentially edges too
    private double totalCaptured; // used for debugging
    private double totalStored; // used for debugging
    private int numbOfRealizations; // number of solutions used in calculation of original heat map
    private HashMap<Edge, Integer> hmEdges; // edges and the number of times they were used
    private HashMap<Source, Integer> hmSources; // sources and the number of times they were used
    private HashMap<Sink, Integer> hmSinks; // sinks and the number of times they were used
    private HashMap<Edge, Double> hmEdgesAveVol; // edges and the average volume of CO2 transported along them in heat map
    private HashMap<Integer, ArrayList<Source>> hmCellsAndSources; // cells (map coordinates) and the sources located at them
    private HashMap<Integer, ArrayList<Sink>> hmCellsAndSinks; // cells and the sinks located at them
    private ArrayList<Integer> hmCells; // list of cells used in the map
    // variables
    private HashMap<Source, IloNumVar> caps; // source capture variable
    private HashMap<Sink, IloNumVar> strk; // sink storage variable
    // output HashMaps for use in display
    private HashMap<Edge, Double> hmEdgesFinal; // output edges and amount transported along them
    private HashMap<Source, Double> hmSourcesFinal; // output sources and amount captured from them
    private HashMap<Sink, Double> hmSinksFinal; // output sinks and amount stored in them
    private Gui gui; // reference to gui class for method calls
    HashMap<Source, Double> hmSourcesMax; // max amount that can be captured from sources
    HashMap<Sink, Double> hmSinksSpecified; // max amount that can be stored in sinks
    // File variables
    String basePath;
    String dataset;
    String scenario;
    // Edge cost calculations variables
    double crf; // double variable related to insurance
    HashMap<Edge, Double> edgeConstructionCosts; // cost to construct each edge
    LinearComponent[] linComp; // trends
    IloIntVar[][] yet; // boolean variable for each edge and trend combo - whether edge is used or not
    IloNumVar[][] pet; // variable for amount transported along each edge and trend combo
    
    // Constructor
    public PriceMinCostSolver(HashMap<Edge, Integer> hmEdges, HashMap<Source, Integer> hmSources, HashMap<Sink, Integer> hmSinks,
    HashMap<Edge, Double> hmEdgesAveVol, HashMap<Integer, ArrayList<Source>> hmCellsAndSources, HashMap<Integer, ArrayList<Sink>> hmCellsAndSinks, 
    ArrayList<Integer> hmCells, Gui gui, int numbOfRealizations, HashMap<Source, Double> hmSourcesMax, HashMap<Sink, Double> hmSinksSpecified,
    String basePath, String dataset, String scenario, double crf, HashMap<Edge, Double> edgeConstructionCosts, LinearComponent[] linComp) {
        this.hmEdges = hmEdges;
        this.hmSources = hmSources;
        this.hmSinks = hmSinks;
        this.hmEdgesAveVol = hmEdgesAveVol;
        this.hmSourcesMax = hmSourcesMax;
        this.hmSinksSpecified = hmSinksSpecified;
        this.hmCellsAndSources = hmCellsAndSources;
        this.hmCellsAndSinks = hmCellsAndSinks;
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
        this.crf = crf;
        this.edgeConstructionCosts = edgeConstructionCosts;
        this.linComp = linComp;
    }
    
    public void writeHeatMapMPS() {
        // This method will be called in control actions, pass in data there
        try {
            IloCplex cplex = new IloCplex();
            
            // Variable: whether edge e with trend t is used
            yet = new IloIntVar[hmEdgesAveVol.keySet().size()][linComp.length];
            for(int e = 0; e < hmEdgesAveVol.keySet().size(); e++) {
                for(int t = 0; t < linComp.length; t++) {
                    yet[e][t] = cplex.intVar(0, 1);
                }
            }
            
            // Variable: how much edge e with trend t transports
            pet = new IloNumVar[hmEdgesAveVol.keySet().size()][linComp.length];
            for(int e = 0; e < hmEdgesAveVol.keySet().size(); e++) {
                for(int t = 0; t < linComp.length; t++) {
                    pet[e][t] = cplex.numVar(0, Double.POSITIVE_INFINITY);
                }
            }
            
            // Variable: amount captured from source
            // Contains constraint: sources must capture between 0 and max amount capturable from source
            caps = new HashMap<>();
            for(Source source : hmSources.keySet()) {
                if(((double)hmSources.get(source) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    if(!caps.containsKey(source)) {
                        caps.put(source, cplex.numVar(0, hmSourcesMax.get(source)));
                    }
                }
            }
            
            // Variable: amount stored in sink
            // Contains constraint: sinks must store between 0 and max amount capturable from sink
            strk = new HashMap<>();
            for(Sink sink : hmSinks.keySet()) {
                if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    if(!strk.containsKey(sink)) {
                        strk.put(sink, cplex.numVar(0, hmSinksSpecified.get(sink)));
                    }
                }
            }        
            
            // Constraint: conservation of flow
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
            
            
            // Constraint: For all possible pipelines, the boolean isUsed value (yet) for that pipeline cannot be > 1 (can't pick multiple trends)
            for(int e = 0; e < hmEdgesAveVol.keySet().size(); e++) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                for(int t = 0; t < linComp.length; t++) {
                    expr.addTerm(1, yet[e][t]);
                }
//                cplex.addLe(expr, 1);
                cplex.addEq(expr, 1);
            }
            
            // Constraint: For all possible pipelines and trend combinations, the amount of flow in pipeline k w/trend c must
            // be >= the min amount possible to transport times y_kc and <= the max amount possible to transport times y_kc
            for(int e = 0; e < hmEdgesAveVol.keySet().size(); e++) {
                for(int t = 0; t < linComp.length; t++) {
                    IloLinearNumExpr expr1 = cplex.linearNumExpr();
                    IloLinearNumExpr expr2 = cplex.linearNumExpr();
                    IloLinearNumExpr expr3 = cplex.linearNumExpr();
                    expr1.addTerm(0, yet[e][t]);
                    expr2.addTerm(1, pet[e][t]);
                    expr3.addTerm(Double.POSITIVE_INFINITY, yet[e][t]);
                    // first expression must be less than equal to second
                    cplex.addLe(expr1, expr2);
                    cplex.addLe(expr2, expr3);
                }
            }

            /* Want to minimize the sum the sum of all sources captured from times that sources capture cost,
            the sum of all sinks stored in times the injection cost, and the sum of all edges transported on 
            times the transport cost (the edges bit requires a bit more work since it will be binary:
            either the edge did transport or didn't) */
            IloLinearNumExpr objExpr = cplex.linearNumExpr();
            for(Sink sink : hmSinks.keySet()) {
                if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    objExpr.addTerm(sink.getInjectionCost(), strk.get(sink));
                }
            }
            
            for(Source source : hmSources.keySet()) {
                if(((double)hmSources.get(source) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    objExpr.addTerm(source.getCaptureCost(), caps.get(source));
                }
            }
            
            int eCounter = 0;
            for(Edge edge : hmEdgesAveVol.keySet()) {
                for(int t = 0; t < linComp.length; t++) {
                    double crfAndEdgeConstruction = crf*edgeConstructionCosts.get(edge);
                    objExpr.addTerm(linComp[t].getConSlope()*crfAndEdgeConstruction, pet[eCounter][t]);
                    objExpr.addTerm(linComp[t].getConIntercept()*crfAndEdgeConstruction, yet[eCounter][t]);
                }
                eCounter++;
            }

            
            // We want to maximize the sum of the amount stored in each sink over the set of all sources
            // Objective:
            IloObjective obj = cplex.minimize(objExpr);
            cplex.add(obj);
            cplex.setOut(null);
            
            // export model 
           cplex.exportModel(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + "mFMCFile.lp");
            
            // Solve: 
            if(cplex.solve()) {
//                System.out.println(cplex.getStatus());  // useful debugging line
                // stores values for edges, sources, and sinks to HashMaps to output data
                int count = 0;
                for(Edge edge : hmEdgesAveVol.keySet()) {
                    if(((double)hmEdges.get(edge) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                        double petVal = 0; // only one should be added, the other should be empty
                        for(int t = 0; t < linComp.length; t++) {
                            petVal += cplex.getValue(pet[count][t]);
                        }
                        hmEdgesFinal.put(edge, petVal);
                    }
                    count++;
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
            int e = 0;
            for(Edge edge : hmEdgesAveVol.keySet()) {
                if(((double)hmEdges.get(edge) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
                    if(edge.v2 == cellNumb) {
                        expr.addTerm(1, pet[e][0]);
                        expr.addTerm(1, pet[e][1]);
                    } else if(edge.v1 == cellNumb) {
                        expr.addTerm(-1, pet[e][0]);
                        expr.addTerm(-1, pet[e][1]);
                    }
                }
                e++;
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
    
    public double getTotalStored() {
        double amountStored = 0.0;
        for(Sink sink : hmSinksFinal.keySet()) {
            amountStored += hmSinksFinal.get(sink);
        }
        return amountStored;
    }
    
    public double getTransportCost() {
        double transportCost = 0.0;
        for(Edge edge : hmEdgesFinal.keySet()) {
            double cost1 = linComp[0].getConSlope()*hmEdgesFinal.get(edge) + linComp[0].getConIntercept();
            double cost2 = linComp[1].getConSlope()*hmEdgesFinal.get(edge) + linComp[1].getConIntercept();
            if(cost1 <= cost2) {
                transportCost += cost1;
            } else {
                transportCost += cost2;
            }
        }
        return transportCost;
    }
    
    public double getCaptureCost() {
        double capCost = 0.0;
        for(Source source : hmSourcesFinal.keySet()) {
            capCost += source.getCaptureCost()*hmSourcesFinal.get(source)*Math.pow(10, 6);
        }
        return capCost;
    }
    
    public double getStorageCost() {
        double storeCost = 0.0;
        for(Sink sink : hmSinksFinal.keySet()) {
            storeCost += sink.getInjectionCost()*hmSinksFinal.get(sink)*Math.pow(10, 6);
        }
        return storeCost;
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
    
    // Debugging method: Display amount captured, stored, and price
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
        System.out.println("\nMax Flow Min Cost Amounts");
        System.out.println("Total Captured: " + totalCaptured);
        System.out.println("Total Stored: " + totalStored);

        for(Edge edge : hmEdgesFinal.keySet()) {
            double cost1 = linComp[0].getConSlope()*hmEdgesFinal.get(edge) + linComp[0].getConIntercept();
            double cost2 = linComp[1].getConSlope()*hmEdgesFinal.get(edge) + linComp[1].getConIntercept();
            if(cost1 <= cost2) {
                transportCost += cost1;
            } else {
                transportCost += cost2;
            }
        }
        
        for(Source source : hmSourcesFinal.keySet()) {
            capCost += source.getCaptureCost()*hmSourcesFinal.get(source)*Math.pow(10, 6);
        }
        
        for(Sink sink : hmSinksFinal.keySet()) {
            storeCost += sink.getInjectionCost()*hmSinksFinal.get(sink)*Math.pow(10, 6);
        }
        System.out.println("Transport Cost: " + transportCost);
        System.out.println("Capture Cost: " + capCost);
        System.out.println("Storage Cost: " + storeCost);
        System.out.println("Total Cost: " + (transportCost + capCost + storeCost));
    }
}
