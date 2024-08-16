/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package solver;

import dataStore.DataStorer;
import dataStore.Edge;
import dataStore.LinearComponent;
import dataStore.Sink;
import dataStore.Solution;
import dataStore.Source;
import dataStore.UnidirEdge;
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloObjective;
import ilog.cplex.IloCplex;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author yaw
 */
public class SlopeScalingKim {

    private static double bestCost = Double.MAX_VALUE;
    private static double[][][] mostRecentCost;
    private static double[][][] maxCost;
    private static double[][][] lastFlow;
    private static double[][][] currentCost;
    private static double[][][] lastCost;

    public void run(String modelVersion, DataStorer data, String basePath, String dataset, String scenario) {
        // create directory
        DateFormat dateFormat = new SimpleDateFormat("ddMMyyy-HHmmssss");
        Date date = new Date();
        String run = "";
        if (modelVersion.equals("ct")) {
            run = "timeCapLP" + dateFormat.format(date);
        } else if (modelVersion.equals("pt")) {
            run = "timePriceLP" + dateFormat.format(date);
        }
        File solutionDirectory = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + run + "/");
        solutionDirectory.mkdir();

        // Run algorithm
        for (int divisor = 1; divisor < data.getMaxAnnualTransferable(); divisor++) {
            int iterationNum = 0;
            boolean finished = false;

            while (!finished) {
                finished = makeSolveLP(data, modelVersion, solutionDirectory.toString(), iterationNum++, divisor);
            }

            double cost = 0;
            for (int t = 0; t < data.getTimeConfiguration().length; t++) {
                Solution soln = data.loadSolution(solutionDirectory.toString(), t, "BESTsolution.sol");
                cost += soln.getTotalCost() * soln.getProjectLength();
            }

            System.out.println("Divisor = " + divisor + ", Cost" + cost);
            //System.out.println("Best Solution Cost = " + cost);
        }
    }

    private boolean makeSolveLP(DataStorer data, String modelVersion, String solutionDirectory, int iterationNum, double divisor) {
        boolean finished = true;

        Source[] sources = data.getSources();
        Sink[] sinks = data.getSinks();
        LinearComponent[] linearComponents = data.getLinearComponents();
        int[] graphVertices = data.getGraphVertices();
        HashMap<Integer, HashSet<Integer>> neighbors = data.getGraphNeighbors();
        HashMap<Edge, Double> edgeConstructionCosts = data.getGraphEdgeConstructionCosts();
        double[][] timeConfiguration = data.getTimeConfiguration();

        // Collect model parameters
        double interestRate = data.getInterestRate();

        //HashMap<Edge, Double> edgeRightOfWayCosts = data.getGraphEdgeRightOfWayCosts();
        HashMap<Source, Integer> sourceCellToIndex = new HashMap<>();
        HashMap<Integer, Source> sourceIndexToCell = new HashMap<>();
        HashMap<Sink, Integer> sinkCellToIndex = new HashMap<>();
        HashMap<Integer, Sink> sinkIndexToCell = new HashMap<>();
        HashMap<Integer, Integer> vertexCellToIndex = new HashMap<>();
        HashMap<Integer, Integer> vertexIndexToCell = new HashMap<>();
        HashMap<UnidirEdge, Integer> edgeToIndex = new HashMap<>();
        HashMap<Integer, UnidirEdge> edgeIndexToEdge = new HashMap<>();
        HashSet<Integer> sourceCells = new HashSet<>();
        HashSet<Integer> sinkCells = new HashSet<>();

        // Initialize cell/index maps
        for (int i = 0; i < sources.length; i++) {
            sourceCellToIndex.put(sources[i], i);
            sourceIndexToCell.put(i, sources[i]);
            sourceCells.add(sources[i].getCellNum());
        }
        for (int i = 0; i < sinks.length; i++) {
            sinkCellToIndex.put(sinks[i], i);
            sinkIndexToCell.put(i, sinks[i]);
            sinkCells.add(sinks[i].getCellNum());
        }
        for (int i = 0; i < graphVertices.length; i++) {
            vertexCellToIndex.put(graphVertices[i], i);
            vertexIndexToCell.put(i, graphVertices[i]);
        }
        int index = 0;
        for (Edge e : edgeConstructionCosts.keySet()) {
            UnidirEdge e1 = new UnidirEdge(e.v1, e.v2);
            edgeToIndex.put(e1, index);
            edgeIndexToEdge.put(index, e1);
            index++;

            UnidirEdge e2 = new UnidirEdge(e.v2, e.v1);
            edgeToIndex.put(e2, index);
            edgeIndexToEdge.put(index, e2);
            index++;
        }

        try {
            IloCplex cplex = new IloCplex();

            // variable: a
            IloNumVar[][] a = new IloNumVar[sources.length][timeConfiguration.length];
            for (int s = 0; s < sources.length; s++) {
                for (int t = 0; t < timeConfiguration.length; t++) {
                    a[s][t] = cplex.numVar(0, Double.MAX_VALUE, "a[" + s + "][" + t + "]");
                }
            }

            // variable: b
            IloNumVar[][] b = new IloNumVar[sinks.length][timeConfiguration.length];
            for (int s = 0; s < sinks.length; s++) {
                for (int t = 0; t < timeConfiguration.length; t++) {
                    b[s][t] = cplex.numVar(0, Double.MAX_VALUE, "b[" + s + "][" + t + "]");
                }
            }

            // variable: p
            IloNumVar[][][] p = new IloNumVar[edgeToIndex.size()][linearComponents.length][timeConfiguration.length];
            for (int e = 0; e < edgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    for (int t = 0; t < timeConfiguration.length; t++) {
                        p[e][c][t] = cplex.numVar(0, Double.MAX_VALUE, "p[" + e + "][" + c + "][" + t + "]");
                    }
                }
            }

            // variable: x
            IloNumVar[][][] x = new IloNumVar[edgeToIndex.size()][linearComponents.length][timeConfiguration.length];
            for (int e = 0; e < edgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    for (int t = 0; t < timeConfiguration.length; t++) {
                        x[e][c][t] = cplex.numVar(0, Double.MAX_VALUE, "x[" + e + "][" + c + "][" + t + "]");
                    }
                }
            }

            // constraint A: pipe capacity
            for (int e = 0; e < edgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    for (int t = 0; t < timeConfiguration.length; t++) {
                        IloLinearNumExpr expr = cplex.linearNumExpr();
                        expr.addTerm(p[e][c][t], 1.0);
                        cplex.addLe(expr, linearComponents[c].getMaxCapacity());
                    }
                }
            }

            // constraint B: capacity for flow
            for (int e = 0; e < edgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    for (int t = 0; t < timeConfiguration.length; t++) {
                        IloLinearNumExpr expr = cplex.linearNumExpr();
                        expr.addTerm(x[e][c][t], 1.0);
                        for (int tau = 0; tau <= t; tau++) {
                            expr.addTerm(p[e][c][tau], -1.0);
                        }
                        cplex.addLe(expr, 0.0);
                    }
                }
            }

            // constraint C: conservation of flow
            for (int src : graphVertices) {
                for (int t = 0; t < timeConfiguration.length; t++) {
                    IloLinearNumExpr expr = cplex.linearNumExpr();
                    for (int dest : neighbors.get(src)) {
                        UnidirEdge edge = new UnidirEdge(src, dest);
                        for (int c = 0; c < linearComponents.length; c++) {
                            expr.addTerm(x[edgeToIndex.get(edge)][c][t], 1.0);
                        }
                    }

                    for (int dest : neighbors.get(src)) {
                        UnidirEdge edge = new UnidirEdge(dest, src);
                        for (int c = 0; c < linearComponents.length; c++) {
                            expr.addTerm(x[edgeToIndex.get(edge)][c][t], -1.0);
                        }
                    }

                    // Set right hand side
                    if (sourceCells.contains(src)) {
                        for (Source source : sources) {
                            if (source.getCellNum() == src) {
                                expr.addTerm(a[sourceCellToIndex.get(source)][t], -1.0);
                            }
                        }
                    }
                    if (sinkCells.contains(src)) {
                        for (Sink sink : sinks) {
                            if (sink.getCellNum() == src) {
                                expr.addTerm(b[sinkCellToIndex.get(sink)][t], 1.0);
                            }
                        }
                    }
                    cplex.addEq(expr, 0.0);
                }
            }

            // constraint D: capture limit
            for (int s = 0; s < sources.length; s++) {
                for (int t = 0; t < timeConfiguration.length; t++) {
                    IloLinearNumExpr expr = cplex.linearNumExpr();
                    expr.addTerm(a[s][t], 1.0);
                    cplex.addLe(expr, sources[s].getProductionRate(t));
                }
            }

            // constraint E: non-decreasing capture
            for (int s = 0; s < sources.length; s++) {
                for (int t = 1; t < timeConfiguration.length; t++) {
                    IloLinearNumExpr expr = cplex.linearNumExpr();
                    expr.addTerm(a[s][t - 1], 1.0);
                    expr.addTerm(a[s][t], -1.0);
                    cplex.addLe(expr, 0.0);
                }
            }

            // constraint F: lifetime storage
            for (int s = 0; s < sinks.length; s++) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                for (int t = 0; t < timeConfiguration.length; t++) {
                    expr.addTerm(b[s][t], timeConfiguration[t][1]);
                }
                cplex.addLe(expr, sinks[s].getCapacity());
            }

            // constraint G: phase storage
            for (int s = 0; s < sinks.length; s++) {
                for (int t = 0; t < timeConfiguration.length; t++) {
                    IloLinearNumExpr expr = cplex.linearNumExpr();
                    expr.addTerm(b[s][t], 1.0);
                    cplex.addLe(expr, sinks[s].getPhaseCapacity(t));
                }
            }

            // constraint H: capture target
            if (modelVersion.equals("ct")) {
                for (int t = 0; t < timeConfiguration.length; t++) {
                    IloLinearNumExpr expr = cplex.linearNumExpr();
                    for (int s = 0; s < sources.length; s++) {
                        expr.addTerm(a[s][t], 1.0);
                    }
                    cplex.addGe(expr, timeConfiguration[t][2]);
                }
            }

            // constraint Z: hardcoded values
            IloNumVar interestRateVar = cplex.numVar(interestRate, interestRate, "interestRate");
            IloLinearNumExpr h2 = cplex.linearNumExpr();
            h2.addTerm(interestRateVar, 1.0);
            cplex.addEq(h2, interestRate);

            for (int t = 0; t < timeConfiguration.length; t++) {
                IloNumVar phaseLengthVar = cplex.numVar(timeConfiguration[t][1], timeConfiguration[t][1], "N" + t);
                IloLinearNumExpr h1 = cplex.linearNumExpr();
                h1.addTerm(phaseLengthVar, 1.0);
                cplex.addEq(h1, timeConfiguration[t][1]);
            }

            // objective
            IloLinearNumExpr objExpr = cplex.linearNumExpr();
            for (int s = 0; s < sources.length; s++) {
                for (int t = 0; t < timeConfiguration.length; t++) {
                    objExpr.addTerm(a[s][t], sources[s].getCaptureCost(t) * timeConfiguration[t][1]);
                }
            }

            for (int s = 0; s < sinks.length; s++) {
                for (int t = 0; t < timeConfiguration.length; t++) {
                    objExpr.addTerm(b[s][t], sinks[s].getInjectionCost(t) * timeConfiguration[t][1]);
                }
            }

            for (int e = 0; e < edgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    for (int t = 0; t < timeConfiguration.length; t++) {
                        double remainingTime = 0;
                        for (int tau = t; tau < timeConfiguration.length; tau++) {
                            remainingTime += timeConfiguration[tau][1];
                        }
                        double crf = (interestRate * Math.pow(1 + interestRate, remainingTime)) / (Math.pow(1 + interestRate, remainingTime) - 1);

                        UnidirEdge unidirEdge = edgeIndexToEdge.get(e);
                        Edge bidirEdge = new Edge(unidirEdge.v1, unidirEdge.v2);

                        // Prep arrays to store cost values
                        if (lastFlow == null) {
                            mostRecentCost = new double[edgeToIndex.size()][linearComponents.length][timeConfiguration.length];
                            maxCost = new double[edgeToIndex.size()][linearComponents.length][timeConfiguration.length];
                            lastFlow = new double[edgeToIndex.size()][linearComponents.length][timeConfiguration.length];
                            currentCost = new double[edgeToIndex.size()][linearComponents.length][timeConfiguration.length];
                            lastCost = new double[edgeToIndex.size()][linearComponents.length][timeConfiguration.length];
                        }

                        // Set cost
                        double variableCost = (linearComponents[c].getConSlope() * edgeConstructionCosts.get(bidirEdge)) * crf * remainingTime;
                        double fixedCost = (linearComponents[c].getConIntercept() * edgeConstructionCosts.get(bidirEdge)) * crf * remainingTime;
                        double cost;
                        if (iterationNum == 0) {
                            // Type 1
                            //cost = variableCost;

                            // Type 2
                            //cost = variableCost + fixedCost / linearComponents[c].getMaxCapacity();
                            
                            // Type 3
                            cost = variableCost + fixedCost / divisor;

                            // Save initial costs
                            mostRecentCost[e][c][t] = cost;
                            maxCost[e][c][t] = cost;
                            lastCost[e][c][t] = cost;
                        } else {
                            if (lastFlow[e][c][t] > 0) {
                                cost = variableCost + fixedCost / lastFlow[e][c][t];

                                // Save cost
                                mostRecentCost[e][c][t] = cost;
                                if (cost > maxCost[e][c][t]) {
                                    maxCost[e][c][t] = cost;
                                }
                                lastCost[e][c][t] = cost;
                            } else {
                                // Scheme 1
                                cost = maxCost[e][c][t];

                                // Scheme 2
                                //cost = mostRecentCost[e][c][t];
                                // Scheme 3
                                //cost = lastCost[e][c][t];
                            }
                        }

                        objExpr.addTerm(p[e][c][t], cost);
                        currentCost[e][c][t] = cost;
                    }
                }
            }

            // objective:
            IloObjective obj = cplex.minimize(objExpr);
            cplex.add(obj);
            cplex.setOut(null);

            // solve
            if (cplex.solve()) {
                for (int e = 0; e < edgeToIndex.size(); e++) {
                    for (int c = 0; c < linearComponents.length; c++) {
                        for (int t = 0; t < timeConfiguration.length; t++) {
                            if (cplex.getValue(p[e][c][t]) > 0.0001) {
                                if (lastFlow[e][c][t] != cplex.getValue(p[e][c][t])) {
                                    finished = false;
                                }
                                lastFlow[e][c][t] = cplex.getValue(p[e][c][t]);
                            } else {
                                if (lastFlow[e][c][t] != 0) {
                                    lastFlow[e][c][t] = 0;
                                    finished = false;
                                }
                            }
                        }
                    }
                }
            } else {
                System.out.println("Not Feasible");
            }

            if (solutionDirectory != null && solutionDirectory != "") {
                String fileName = "";
                if (modelVersion.equals("ct")) {
                    fileName = "timeC_lp.mps";
                } else if (modelVersion.equals("pt")) {
                    fileName = "timeP_lp.mps";
                }

                cplex.exportModel(solutionDirectory + "/" + fileName);
                cplex.writeSolution(solutionDirectory + "/solution.sol");

                double cost = 0;
                for (int t = 0; t < timeConfiguration.length; t++) {
                    Solution soln = data.loadSolution(solutionDirectory, t, "solution.sol");
                    cost += soln.getTotalCost() * soln.getProjectLength();
                }

                //System.out.println("Solution Cost = " + cost);
                if (cost < bestCost) {
                    bestCost = cost;
                    cplex.exportModel(solutionDirectory + "/BEST" + fileName);
                    cplex.writeSolution(solutionDirectory + "/BESTsolution.sol");
                }
            }
            cplex.clearModel();
        } catch (IloException ex) {
            Logger.getLogger(FlowHeuristic.class.getName()).log(Level.SEVERE, null, ex);
        }
        return finished;
    }
}
