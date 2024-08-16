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
public class FailureRobustMaxFlowSolver {
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
    // Edge cost calculations variables
    double crf; // double variable related to insurance
    HashMap<Edge, Double> edgeConstructionCosts; // cost to construct each edge
    LinearComponent[] linComp; // trends
    IloIntVar[][] yet; // boolean variable for each edge and trend combo - whether edge is used or not
    IloNumVar[][] pet; // variable for amount transported along each edge and trend combo
    // max flow min cost network (mfmc) deployed elements
    HashMap<Source, Double> mfmcSources;
    HashMap<Sink, Double> mfmcSinks;
    HashMap<Edge, Double> mfmcEdges;
    HashMap<Edge, int[]> candidateNetworkEdges;
    Edge[] candidateNetworkEdgeArray;
    // Robustness variables
    private HashMap<Edge, Integer[]> hmEdgesFinalTrends;
    Sink failedSink;
    double maxAmountStorable;
    Source[] allSources;
    Sink[] allSinks;
    
    // Constructor
    public FailureRobustMaxFlowSolver(HashMap<Edge, Integer> hmEdges, HashMap<Source, Integer> hmSources, HashMap<Sink, Integer> hmSinks,
    HashMap<Edge, Double> hmEdgesAveVol, HashMap<Integer, ArrayList<Source>> hmCellsAndSources, HashMap<Integer, ArrayList<Sink>> hmCellsAndSinks, 
    ArrayList<Integer> hmCells, Gui gui, int numbOfRealizations, HashMap<Edge, Double> hmEdgesAveCost, HashMap<Source, Double> hmSourcesMax, 
    HashMap<Sink, Double> hmSinksSpecified, String basePath, String dataset, String scenario, double crf, HashMap<Edge, Double> edgeConstructionCosts, 
    LinearComponent[] linComp, HashMap<Source, Double> mfmcSources, HashMap<Sink, Double> mfmcSinks, HashMap<Edge, Double> mfmcEdges, 
    HashMap<Edge, int[]> candidateNetworkEdges, Edge[] candidateNetworkEdgeArray, HashMap<Edge, Integer[]> hmEdgesFinalTrends, Sink failedSink,
    double maxAmountStorable, Source[] allSources, Sink[] allSinks) {
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
        this.crf = crf;
        this.edgeConstructionCosts = edgeConstructionCosts;
        this.linComp = linComp;
        this.mfmcSources = mfmcSources;
        this.mfmcSinks = mfmcSinks;
        this.mfmcEdges = mfmcEdges;
        this.candidateNetworkEdges = candidateNetworkEdges;
        this.candidateNetworkEdgeArray = candidateNetworkEdgeArray;
        this.hmEdgesFinalTrends = hmEdgesFinalTrends;
        this.failedSink = failedSink;
        this.maxAmountStorable = maxAmountStorable;
        this.allSources = allSources;
        this.allSinks = allSinks;
    }
    
    public void writeHeatMapMPS() {
        // This method will be called in control actions, pass in data there
        try {
            IloCplex cplex = new IloCplex();

            // These 3 variables should handle contraints A, B, and C in their declaration
            // Variable: whether edge e with trend t is used
            yet = new IloIntVar[2*candidateNetworkEdges.keySet().size()][linComp.length];
            int ey = 0;
            
            // SOLUTION IDEA: Create Variables for both directions, only set correct edge and direction for has to deploy
            
            for(Edge edge : candidateNetworkEdges.keySet()) {
                for(int t = 0; t < linComp.length; t++) {
                    yet[ey][t] = cplex.intVar(0, 1, "edgYN [" + ey + "]");
                    // should force deployment for previously used edges
                    if(hmEdgesFinalTrends.containsKey(edge)) {
                        if(edge.v1 == hmEdgesFinalTrends.get(edge)[1] && edge.v2 == hmEdgesFinalTrends.get(edge)[2]) {
                            if(hmEdgesFinalTrends.get(edge)[0].intValue() == t) {
                                yet[ey][t] = cplex.intVar(1, 1, "edgYN [" + ey + "]");
                            }
                        }
                    }
                }
                ey++;
            }
            
            for(Edge edge : candidateNetworkEdges.keySet()) {
                for(int t = 0; t < linComp.length; t++) {
                    yet[ey][t] = cplex.intVar(0, 1, "edgYN [" + ey + "]");
                }
                ey++;
            }
            ey = 0;
            
            // Variable: how much edge e with trend t transports
            pet = new IloNumVar[2*candidateNetworkEdges.keySet().size()][linComp.length];
            for(int e = 0; e < 2*candidateNetworkEdges.keySet().size(); e++) {
                for(int t = 0; t < linComp.length; t++) {
                    pet[e][t] = cplex.numVar(0, Double.POSITIVE_INFINITY, "edgAmnt[" + e + "]");
                }
            }
            
            // Variable: amount captured from source
            caps = new HashMap<>();
            for(Source source : allSources) {
                if(!caps.containsKey(source)) {
                    caps.put(source, cplex.numVar(0, source.getProductionRate(), "src[" + source.getID() + "]")); // check?
                    // if prev deployed sources no longer deploy, then the fact that they have pipelines connected should be fine
                    // pipelines just will transport nothing instead
                }
//                if(((double)hmSources.get(source) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
//                    if(!caps.containsKey(source)) {
//                        caps.put(source, cplex.numVar(0, hmSourcesMax.get(source)));
//                    }
//                }
            }
            
            // Variable: amount stored in sink
            strk = new HashMap<>();
            for(Sink sink : allSinks) {
                if(!strk.containsKey(sink)) {
                    if(!failedSink.getLabel().equals(sink.getLabel())) {
                        strk.put(sink, cplex.numVar(0, sink.getCapacity(), "snk[" + sink.getID() + "]"));
                    }
                    
//                    if(failedSink.getLabel().equals(sink.getLabel())) {
//                        strk.put(sink, cplex.numVar(0, 1, "snk[" + sink.getID() + "]"));
//                    }

//                    if(failedSink.getLabel().equals(sink.getLabel())) {
//                        // strk.put(sink, cplex.numVar(0, sink.getCapacity() / 10000)); // allow a miniscule amount to simulate failure
////                        strk.put(sink, cplex.numVar(0, 0));
//                    } else {
//                        strk.put(sink, cplex.numVar(0, sink.getCapacity())); // for testing using capacity value
//                    }
                    // assumption is that b/c pipelines to prev built sinks must deploy, prev built sinks will deploy, which is most important bit
                }
//                if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
//                    if(!strk.containsKey(sink)) {
//                        strk.put(sink, cplex.numVar(0, hmSinksSpecified.get(sink)));
//                    }
//                }
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
                    System.out.println("Sources only"); // debug line
                } else if(isSinksOnly) {
                    // add the sink terms
                    addSinkTerms(expr, cell);
                    cplex.addEq(expr, 0);
                    System.out.println("Sinks only"); // debug line
                } else if(isSourcesAndSinks) {
                    // add both the source and sink terms
                    addSourceTerms(expr, cell);
                    addSinkTerms(expr, cell);
                    cplex.addEq(expr, 0);
                }
                // reset boolean items related to cell number
                resetCellBooleans();
            }
            
            // This doesn't force deployment, does it?
            // Constraint: For all possible pipelines, the boolean isUsed value (yet) for that pipeline cannot be > 1 (can't pick multiple trends)
            for(int e = 0; e < 2*candidateNetworkEdges.keySet().size(); e++) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                for(int t = 0; t < linComp.length; t++) {
                    expr.addTerm(1, yet[e][t]);
                }
//                cplex.addLe(expr, 1);
                cplex.addEq(expr, 1);
            }
            
            // constraint: previously deployed pipelines must deploy again
//            int ec = 0;
//            for(Edge edge : candidateNetworkEdgeArray) {
//                IloLinearNumExpr expr = cplex.linearNumExpr();
//                if(mfmcEdges.containsKey(edge)) {
//                        if(hmEdgesFinalTrends.get(edge) == t) {
//                            yet[ey][t] = cplex.intVar(1, 1);
//                        }
//                    }
//            }
            
            // Constraint: For all possible pipelines and trend combinations, the amount of flow in pipeline k w/trend c must
            // be >= the min amount possible to transport times y_kc and <= the max amount possible to transport times y_kc
            for(int e = 0; e < 2*candidateNetworkEdges.keySet().size(); e++) {
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
            
            // Constraint to capture only as much as mfmc at most. - Questionable. We're adjusting to failure, but can use cap or price approach?
//            IloLinearNumExpr maxCapConstraint = cplex.linearNumExpr();
//            for(Sink sink : allSinks) {
//                maxCapConstraint.addTerm(1, strk.get(sink));
//            }
//            cplex.addLe(maxCapConstraint, maxAmountStorable);
            
            // Creates an expression of the sum of all sink terms - to be maximized
            IloLinearNumExpr objExpr = cplex.linearNumExpr();
            for(Sink sink : allSinks) {
                if(!failedSink.getLabel().equals(sink.getLabel())) {
//                    if(!failedSink.getLabel().equals(sink.getLabel())) {
//                        objExpr.addTerm(1, strk.get(sink));
//                    }
                    objExpr.addTerm(1, strk.get(sink));
//                    if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
//                        objExpr.addTerm(1, strk.get(sink));
//                    }
                }
//                    if(!failedSink.getLabel().equals(sink.getLabel())) {
//                        objExpr.addTerm(1, strk.get(sink));
//                    }
//                    objExpr.addTerm(1, strk.get(sink));
//                    if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
//                        objExpr.addTerm(1, strk.get(sink));
//                    }
            }
//            cplex.addGe(objExpr, 4);
            
            // We want to maximize the sum of the amount stored in each sink over the set of all sources
            // Objective:
            IloObjective obj = cplex.maximize(objExpr);
            cplex.add(obj);
            cplex.setOut(null);
            
            // output text file containing problem formulation
            cplex.exportModel(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + "FailureRobustFile.lp");
            
            // Solve: 
            if(cplex.solve()) {
                System.out.println(cplex.getCplexStatus());
                // stores values for edges, sources, and sinks to HashMaps to output data
                int count = 0;
                for(Edge edge : candidateNetworkEdges.keySet()) {
                    double petVal = 0; // only one should be added, the other should be empty
                    for(int t = 0; t < linComp.length; t++) {
                        petVal += cplex.getValue(pet[count][t]);
                    }
                    hmEdgesFinal.put(edge, petVal);
                    count++;
                }
                for(Edge edge : candidateNetworkEdges.keySet()) {
                    double petVal = 0; // only one should be added, the other should be empty
                    for(int t = 0; t < linComp.length; t++) {
                        petVal += cplex.getValue(pet[count][t]);
                    }
                    hmEdgesFinal.put(edge, petVal);
                    count++;
                }
                
                for(Source source : allSources) {
                    hmSourcesFinal.put(source, cplex.getValue(caps.get(source)));
//                    if(((double)hmSources.get(source) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
//                        hmSourcesFinal.put(source, cplex.getValue(caps.get(source)));
//                    }
                }
                
                for(Sink sink : allSinks) {
                    if(!failedSink.getLabel().equals(sink.getLabel())) {
                        hmSinksFinal.put(sink, cplex.getValue(strk.get(sink)));
                    }
//                    hmSinksFinal.put(sink, cplex.getValue(strk.get(sink)));
//                    if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
//                        hmSinksFinal.put(sink, cplex.getValue(strk.get(sink)));
//                    }
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
            // pretty innefficient
            for(int cell : hmCells) {
                int e = 0;
                for(Edge edge : candidateNetworkEdges.keySet()) {
                    if(edge.v1 == cellNumb || edge.v2 == cellNumb) {
                        expr.addTerm(1, pet[e][0]);
                        expr.addTerm(1, pet[e][1]);
                        expr.addTerm(-1, pet[e][0]);
                        expr.addTerm(-1, pet[e][1]);
                    }
                    e++;
                }
            }
            
            
//            int e = 0;
//            for(Edge edge : candidateNetworkEdges.keySet()) {
//                if(edge.v2 == cellNumb) {
//                    expr.addTerm(1, pet[e][0]);
//                    expr.addTerm(1, pet[e][1]);
//                } else if(edge.v1 == cellNumb) {
//                    expr.addTerm(-1, pet[e][0]);
//                    expr.addTerm(-1, pet[e][1]);
//                }
//
//                e++;
//            }
//            for(Edge edge : candidateNetworkEdges.keySet()) {
//                if(edge.v2 == cellNumb) {
//                    expr.addTerm(1, pet[e][0]);
//                    expr.addTerm(1, pet[e][1]);
//                } else if(edge.v1 == cellNumb) {
//                    expr.addTerm(-1, pet[e][0]);
//                    expr.addTerm(-1, pet[e][1]);
//                }
//                e++;
//            }
        } catch(IloException ex) {
            ex.printStackTrace();
        }
    }
    
    // Adds source CO2 terms into passed in IloLinearNumExpr
    private void addSourceTerms(IloLinearNumExpr expr, int cellNumb) {
        try {
            for(Source source : allSources) {
                if(source.getCellNum() == cellNumb) {
                    expr.addTerm(1, caps.get(source));
                }
//                if(((double)hmSources.get(source) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
//                    if(source.getCellNum() == cellNumb) {
//                        expr.addTerm(1, caps.get(source));
//                    }
//                }
            }
        } catch(IloException ex) {
            ex.printStackTrace();
        }
    }
    
    // Adds sink CO2 terms into passed in IloLinearNumExpr
    private void addSinkTerms(IloLinearNumExpr expr, int cellNumb) {
        try {
            for(Sink sink : allSinks) {
                if(sink.getCellNum() == cellNumb) {
                    if(!failedSink.getLabel().equals(sink.getLabel())) {
                        expr.addTerm(-1, strk.get(sink));
                    }
//                    expr.addTerm(-1, strk.get(sink));
                }
                
//                if(((double)hmSinks.get(sink) / numbOfRealizations) >= gui.getHmPercentToDisp()) {
//                    if(sink.getCellNum() == cellNumb) {
//                        expr.addTerm(-1, strk.get(sink));
//                    }
//                }   
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
        
        for(Source source : allSources) {
            if(hmSourcesFinal.containsKey(source) && mfmcSources.containsKey(source)) {
                System.out.println("Source: " + source.getLabel() + ", Max Flow Min Cost Captured Amount: " + 
                        mfmcSources.get(source) + ", Robust to Failure Captured Amount: " + hmSourcesFinal.get(source));
            } else if(hmSourcesFinal.containsKey(source)) {
                System.out.println("Source: " + source.getLabel() + ", Max Flow Min Cost Captured Amount: n.a. , Robust to Failure Captured Amount: " + hmSourcesFinal.get(source));
            } else if(mfmcSources.containsKey(source)) {
                System.out.println("Source: " + source.getLabel() + ", Max Flow Min Cost Captured Amount: " + 
                        mfmcSources.get(source) + ", Robust to Failure Captured Amount: n.a.");
            }
        }
        
        for(Sink sink : allSinks) {
            if(hmSinksFinal.containsKey(sink) && mfmcSinks.containsKey(sink)) {
                System.out.println("Sink: " + sink.getLabel() + ", Max Flow Min Cost Stored Amount: " + 
                        mfmcSinks.get(sink) + ", Robust to Failure Stored Amount: " + hmSinksFinal.get(sink));
            } else if(hmSinksFinal.containsKey(sink)) {
                System.out.println("Sink: " + sink.getLabel() + ", Max Flow Min Cost Stored Amount: n.a. , Robust to Failure Stored Amount: " + 
                        hmSinksFinal.get(sink));
            } else if(mfmcSinks.containsKey(sink)) {
                System.out.println("Sink: " + sink.getLabel() + ", Max Flow Min Cost Stored Amount: " + 
                        mfmcSinks.get(sink) + ", Robust to Failure Stored Amount: n.a.");
            }
        }
        
        for(Edge edge : candidateNetworkEdgeArray) {
            if(hmEdgesFinal.containsKey(edge) && mfmcEdges.containsKey(edge)) {
                System.out.println("Edge: " + edge + ", Max Flow Min Cost Transport Amount: " + 
                        mfmcEdges.get(edge) + ", Robust to Failure Transport Amount: " + hmEdgesFinal.get(edge));
            } else if(hmEdgesFinal.containsKey(edge)) {
                System.out.println("Edge: " + edge + ", Max Flow Min Cost Transport Amount: n.a. " +
                        ", Robust to Failure Transport Amount: " + hmEdgesFinal.get(edge));
            } else if(mfmcEdges.containsKey(edge)) {
                System.out.println("Edge: " + edge + ", Max Flow Min Cost Transport Amount: " + 
                        mfmcEdges.get(edge) + ", Robust to Failure Transport Amount: n.a. ");
            }
        }
        
        System.out.println("Max Flow Amounts");
        System.out.println("Total Captured: " + totalCaptured);
        System.out.println("Total Stored: " + totalStored);
        
//        for(Edge edge : hmEdgesFinal.keySet()) {
//            transportCost += candidateNetworkEdges.get(edge)*Math.pow(10, 6)/(double)hmEdges.get(edge);
//        }
//        
//        for(Source source : hmSourcesFinal.keySet()) {
//            capCost += source.getCaptureCost()*hmSourcesFinal.get(source)*Math.pow(10, 6);
//        }
//        
//        for(Sink sink : hmSinksFinal.keySet()) {
//            storeCost += sink.getInjectionCost()*hmSinksFinal.get(sink)*Math.pow(10, 6);
//        }
//
//        System.out.println("\nTotal Cost: " + (transportCost + capCost + storeCost));
    }
    
    public double getTotalStored() {
        return totalStored;
    }
}