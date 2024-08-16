package solver;

import dataStore.DataStorer;
import dataStore.Edge;
import dataStore.LinearComponent;
import dataStore.Sink;
import dataStore.Solution;
import dataStore.Source;
import dataStore.directedEdge;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloObjective;
import ilog.cplex.IloCplex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author yaw
 */
public class MaxFlowSolverRevised {

    public static Solution solve(DataStorer data, double crf, int numYears, double captureTarget, double numbRealizations, double toDisplayPercent, 
            HashMap<Edge, Integer> hmEdges, HashMap<Source, Integer> hmSources, HashMap<Sink, Integer> hmSinks, HashMap<Sink, Double> hmSinksAmountLimit, 
            ArrayList<Integer> hmCells) {
        // extra data for use in failed sink solver
        HashMap<Edge, Integer> edgeTrendsEdgeVersion = new HashMap<>();
        // instance data
        int sourcesInRange = 0;
        for(Source source : hmSources.keySet()) {
            if((double)hmSources.get(source) / numbRealizations >= toDisplayPercent) {
                sourcesInRange++;
            }
        }
        Source[] sources = new Source[sourcesInRange];
        int g = 0;
        for(Source source : hmSources.keySet()) {
            if((double)hmSources.get(source) / numbRealizations >= toDisplayPercent) {
                sources[g] = source;
                g++;
            }
        }
        g = 0;
        int sinksInRange = 0;
        for(Sink sink : hmSinks.keySet()) {
            if((double)hmSinks.get(sink) / numbRealizations >= toDisplayPercent) {
                sinksInRange++;
            }
        }
        Sink[] sinks = new Sink[sinksInRange];
        //Sink[] sinks = new Sink[hmSinks.size()];
        for(Sink sink : hmSinks.keySet()) {
            if((double)hmSinks.get(sink) / numbRealizations >= toDisplayPercent) {
                sinks[g] = sink;
                g++;
            }
        }
        g = 0;
        
        LinearComponent[] linearComponents = data.getLinearComponents();
        int[] graphVertices = new int[hmCells.size()];
        // ensures we only use vertices present in the heat map
        for(int i = 0; i < hmCells.size(); i++) {
            graphVertices[i] = hmCells.get(i);
        }
        
//        if(graphVertices.length < 10) {
//            System.out.println("Error filling out the graph vertices");
//        }
        //int[] graphVertices = data.getGraphVertices();
        
        // object <-> index maps - two way street(s)
        HashMap<Integer, HashSet<Integer>> neighbors = data.getGraphNeighborsHeatMap(hmEdges, numbRealizations, toDisplayPercent);
        HashMap<Edge, Double> edgeConstructionCosts = data.getGraphEdgeConstructionCosts();

        HashMap<Source, Integer> sourceToIndex = new HashMap<>();
        HashMap<Integer, Source> indexToSource = new HashMap<>();
        
        HashMap<Sink, Integer> sinkToIndex = new HashMap<>();
        HashMap<Integer, Sink> indexToSink = new HashMap<>();
        
        HashMap<directedEdge, Integer> directdEdgeToIndex = new HashMap<>();
        HashMap<Integer, directedEdge> indexToDirectedEdge = new HashMap<>();
        
        // set to indicate if a cell (vertex) is a source/sink or not
        HashSet<Integer> sourceCells = new HashSet<>();
        HashSet<Integer> sinkCells = new HashSet<>();

        // output solution
        Solution solution = null;

        // initialize object maps
        // assign index values to source objects
        for (int i = 0; i < sources.length; i++) {
            sourceToIndex.put(sources[i], i);
            indexToSource.put(i, sources[i]);
            sourceCells.add(sources[i].getCellNum());
        }
        
        // assign index values to sink objects
        for (int i = 0; i < sinks.length; i++) {
            sinkToIndex.put(sinks[i], i);
            indexToSink.put(i, sinks[i]);
            sinkCells.add(sinks[i].getCellNum());
        }
        
        // create two directed edges for each graph edge and assign index value
        int index = 0;
        for (Edge e : edgeConstructionCosts.keySet()) {
            if(hmEdges.containsKey(e) && (double)hmEdges.get(e) / numbRealizations >= toDisplayPercent) {
                directedEdge e1 = new directedEdge(e.v1, e.v2);
                directdEdgeToIndex.put(e1, index);
                indexToDirectedEdge.put(index, e1);
                index++;

                directedEdge e2 = new directedEdge(e.v2, e.v1);
                directdEdgeToIndex.put(e2, index);
                indexToDirectedEdge.put(index, e2);
                index++;
            }
        }

        try {
            IloCplex cplex = new IloCplex();
            IloCplex cplex2 = new IloCplex();

            // variable a: source capture amount
            IloNumVar[] a = new IloNumVar[sources.length];
            IloNumVar[] a2 = new IloNumVar[sources.length];
            for (int s = 0; s < sources.length; s++) {
                //a[s] = cplex.numVar(0, sources[s].getProductionRate(), "a[" + s + "]");
                a[s] = cplex.numVar(0, Double.MAX_VALUE, "a[" + s + "]");
                a2[s] = cplex2.numVar(0, Double.MAX_VALUE, "a[" + s + "]");
            }

            // variable b: sink storage amount
            IloNumVar[] b = new IloNumVar[sinks.length];
            IloNumVar[] b2 = new IloNumVar[sinks.length];
            for (int s = 0; s < sinks.length; s++) {
                //b[s] = cplex.numVar(0, hmSinksAmountLimit.get(sinks[s]) / (double)numYears, "b[" + s + "]");
                b[s] = cplex.numVar(0, Double.MAX_VALUE, "b[" + s + "]");
                b2[s] = cplex2.numVar(0, Double.MAX_VALUE, "b[" + s + "]");
            }

            // variable p: edge transport amount for directed edges
            IloNumVar[][] p = new IloNumVar[directdEdgeToIndex.size()][linearComponents.length];
            IloNumVar[][] p2 = new IloNumVar[directdEdgeToIndex.size()][linearComponents.length];
            for (int e = 0; e < directdEdgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    p[e][c] = cplex.numVar(0, Double.MAX_VALUE, "p[" + e + "][" + c + "]");
                    p2[e][c] = cplex2.numVar(0, Double.MAX_VALUE, "p[" + e + "][" + c + "]");
                }
            }

            // variable y: edge use indicator for directed edges
            IloIntVar[][] y = new IloIntVar[directdEdgeToIndex.size()][linearComponents.length];
            IloIntVar[][] y2 = new IloIntVar[directdEdgeToIndex.size()][linearComponents.length];
            for (int e = 0; e < directdEdgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    y[e][c] = cplex.intVar(0, 1, "y[" + e + "][" + c + "]");
                    y2[e][c] = cplex2.intVar(0, 1, "y[" + e + "][" + c + "]");
                }
            }

            // constraint A: pipe capacity and connect y to p
            for (int e = 0; e < directdEdgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    IloLinearNumExpr expr = cplex.linearNumExpr();
                    expr.addTerm(p[e][c], 1.0);
                    expr.addTerm(y[e][c], -linearComponents[c].getMaxCapacity());
                    //expr.addTerm(y[e][c], -1.0 * (Double.MAX_VALUE-1));
                    cplex.addLe(expr, 0);
                    IloLinearNumExpr expr2 = cplex2.linearNumExpr();
                    expr2.addTerm(p2[e][c], 1.0);
                    expr2.addTerm(y2[e][c], -linearComponents[c].getMaxCapacity());
                    cplex2.addLe(expr2, 0);
                }
            }

            // constraint B: single-trend edges
            for (int e = 0; e < directdEdgeToIndex.size(); e++) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                for (int c = 0; c < linearComponents.length; c++) {
                    expr.addTerm(y[e][c], 1.0);
                }
                cplex.addLe(expr, 1);
                IloLinearNumExpr expr2 = cplex2.linearNumExpr();
                for (int c = 0; c < linearComponents.length; c++) {
                    expr2.addTerm(y2[e][c], 1.0);
                }
                cplex2.addLe(expr2, 1);
            }

            // constraint C: conservation of flow
            // loop through every vertex in the graph
            for (int vertex : graphVertices) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                IloLinearNumExpr expr2 = cplex2.linearNumExpr();
                
                if(neighbors.containsKey(vertex)) {
                    // sum up all flow (p-values) flowing out of the vertex
                    for (int neighbor : neighbors.get(vertex)) {
                        // directed edge from vertex to neighbor
                        directedEdge edge = new directedEdge(vertex, neighbor);
                        for (int c = 0; c < linearComponents.length; c++) {
                            expr.addTerm(p[directdEdgeToIndex.get(edge)][c], 1.0);
                            expr2.addTerm(p2[directdEdgeToIndex.get(edge)][c], 1.0);
                        }
                    }

                    // deduct all flow flowing into the vertex
                    for (int neighbor : neighbors.get(vertex)) {
                        // directed edge from neighbor to vertex
                        directedEdge edge = new directedEdge(neighbor, vertex);
                        for (int c = 0; c < linearComponents.length; c++) {
                            expr.addTerm(p[directdEdgeToIndex.get(edge)][c], -1.0);
                            expr2.addTerm(p2[directdEdgeToIndex.get(edge)][c], -1.0);
                        }
                    }
                }
                
                // determine what the net flow should be
                // this equation is formatted as: sum_out - sum_in +/- (cap/store amount) = 0
                // a net increase of the capture amount if the vertex is a source
                if (sourceCells.contains(vertex)) {
                    for (Source source : sources) {
                        if (source.getCellNum() == vertex) {
                            expr.addTerm(a[sourceToIndex.get(source)], 1.0);
                            expr2.addTerm(a2[sourceToIndex.get(source)], 1.0);
                        }
                    }
                }
                // a net decrease of the storage amount if the vertex is a sink
                if (sinkCells.contains(vertex)) {
                    for (Sink sink : sinks) {
                        if (sink.getCellNum() == vertex) {
                            expr.addTerm(b[sinkToIndex.get(sink)], -1.0);
                            expr2.addTerm(b2[sinkToIndex.get(sink)], -1.0);
                        }
                    }
                }
                // nothing if the vertex is neither
                cplex.addEq(expr, 0.0);
                cplex2.addEq(expr2, 0.0);
            }

            // constraint D: limit capture amount by source emission rate
            for (int s = 0; s < sources.length; s++) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                expr.addTerm(a[s], 1.0);
                cplex.addLe(expr, sources[s].getMaxProductionRate());
                IloLinearNumExpr expr2 = cplex2.linearNumExpr();
                expr.addTerm(a2[s], 1.0);
                cplex2.addLe(expr2, sources[s].getMaxProductionRate());
                //System.out.println("Source " + sources[s].getID() + " has a production rate of " + sources[s].getMaxProductionRate());
            }

            // constraint E: limit storage amount by annualized storage capacity
            for (int s = 0; s < sinks.length; s++) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                expr.addTerm(b[s], 1.0);
                IloLinearNumExpr expr2 = cplex2.linearNumExpr();
                expr2.addTerm(b2[s], 1.0);
                cplex.addLe(expr, hmSinksAmountLimit.get(sinks[s]) / numYears); // should actually be this one
                cplex2.addLe(expr2, hmSinksAmountLimit.get(sinks[s]) / numYears);
            }
            
            double sourceCapacityTotal = 0.0;
            double sinkCapacityTotal = 0.0;
            for(int s = 0; s < sources.length; s++) {
                sourceCapacityTotal += sources[s].getMaxProductionRate();
            }
            for(int s = 0; s < sinks.length; s++) {
                sinkCapacityTotal += hmSinksAmountLimit.get(sinks[s]) / numYears;
            }
            System.out.println("The max flow solver source capacity yearly total is " + sourceCapacityTotal + " and the sink total yearly capacity is " + sinkCapacityTotal);

            // constraint F: enforce capture target is met - maybe change to a leq?
            IloLinearNumExpr expr = cplex.linearNumExpr();
            IloLinearNumExpr expr2 = cplex2.linearNumExpr();
            for (int s = 0; s < sinks.length; s++) {
                expr.addTerm(b[s], 1.0);
                expr2.addTerm(b2[s], 1.0);
            }
            //cplex.addGe(expr, captureTarget);
            cplex.addLe(expr, captureTarget);
            cplex2.addLe(expr2, sourceCapacityTotal);
            // this constraint has the potential to render the MILP infeasible.
            // In this case, don't add the constraint, and cplex can run again
            // and maximize to whatever the best it can do is

            //cplex.addLe(expr, sourceCapacityTotal * (double)numYears);
            //cplex.addLe(expr, captureTarget / (double)numYears);
            //cplex.addGe(expr, captureTarget / 2.0);
            // Constraint G: limit storage total to be leq than capture total
//            IloLinearNumExpr expr2 = cplex.linearNumExpr();
//            for(int s = 0; s < sources.length; s++) {
//                expr2.addTerm(a[s], 1.0);
//            }
//            cplex.addLe(expr2, sinkCapacityTotal / 30.0);
           

            // objective
            IloLinearNumExpr objExpr = cplex.linearNumExpr();
            IloLinearNumExpr objExpr2 = cplex2.linearNumExpr();
            
            // maximizing the storage amount
            for(int s = 0; s < sinks.length; s++) {
                objExpr.addTerm(b[s], 1.0);
                objExpr2.addTerm(b2[s], 1.0);
            }

            // capture cost
//            for (int s = 0; s < sources.length; s++) {
//                objExpr.addTerm(a[s], sources[s].getCaptureCost());
//                objExpr2.addTerm(a2[s], sources[s].getCaptureCost());
//            }
//
//            // storage cost
//            for (int s = 0; s < sinks.length; s++) {
//                objExpr.addTerm(b[s], sinks[s].getInjectionCost());
//                objExpr2.addTerm(b2[s], sinks[s].getInjectionCost());
//            }
//
//            // transport cost
//            for (int e = 0; e < directdEdgeToIndex.size(); e++) {
//                for (int c = 0; c < linearComponents.length; c++) {
//                    directedEdge directedEdge = indexToDirectedEdge.get(e);
//                    
//                    // make undirected edge to look up costs (costs are the same for each opposing directed edge)
//                    Edge undirectedEdge = new Edge(directedEdge.v1, directedEdge.v2);
//
//                    // calculate "fixed" and "variable" costs
//                    double fixedCost = (linearComponents[c].getConIntercept() * edgeConstructionCosts.get(undirectedEdge)) * crf;
//                    double variableCost = (linearComponents[c].getConSlope() * edgeConstructionCosts.get(undirectedEdge)) * crf;
//
//                    objExpr.addTerm(y[e][c], fixedCost);
//                    objExpr.addTerm(p[e][c], variableCost);
//                    objExpr2.addTerm(y2[e][c], fixedCost);
//                    objExpr2.addTerm(p2[e][c], variableCost);
//                }
//            }

            // objective:
            IloObjective obj = cplex.maximize(objExpr);
            IloObjective obj2 = cplex2.maximize(objExpr2);
            //IloObjective obj = cplex.maximize(objExpr);
            cplex.add(obj);
            cplex.setOut(null);
            
            cplex2.add(obj2);
            cplex2.setOut(null);

            // solve
            if (cplex.solve()) {
                // populate new solution
                solution = new Solution();
                
                // ignore cplex solution values that are below this threshold
                double threshold = .00001;

                // add each deployed source to the solution
                for (Source source : sources) {
                    double captureAmount = cplex.getValue(a[sourceToIndex.get(source)]);
                    if (captureAmount > threshold) {
                        solution.addSourceCaptureAmount(source, captureAmount);
                        solution.addSourceCostComponent(source, captureAmount * source.getCaptureCost());
                    }
                }

                // add each deployed sink to the solution
                for (Sink sink : sinks) {
                    double storageAmount = cplex.getValue(b[sinkToIndex.get(sink)]);
                    if (storageAmount > threshold) {
                        solution.addSinkStorageAmount(sink, storageAmount);
                        solution.addSinkCostComponent(sink, storageAmount * sink.getInjectionCost());
                    }
                }

                // iterate through all of the directed edges
                for (int e = 0; e < directdEdgeToIndex.size(); e++) {
                    for (int c = 0; c < linearComponents.length; c++) {
                        // determine how much was transported though that edge
                        double transportAmount = cplex.getValue(p[e][c]);
                        if (transportAmount > threshold) {
                            // get the directed edge
                            directedEdge directedEdge = indexToDirectedEdge.get(e);
                            
                            // turn it into an undirected edge
                            Edge undirectedEdge = new Edge(directedEdge.v1, directedEdge.v2);
                            
                            // add transport amounts and trend info for that edge to the solution
                            solution.addEdgeTransportAmount(undirectedEdge, transportAmount);
                            solution.setEdgeTrend(undirectedEdge, c);
                            if(e % 2 == 0) {
                                edgeTrendsEdgeVersion.put(undirectedEdge, 0);
                            } else {
                                edgeTrendsEdgeVersion.put(undirectedEdge, 1);
                            }
                            
                            // calculate the "fixed" and "variable" costs
                            double fixedCost = (linearComponents[c].getConIntercept() * edgeConstructionCosts.get(undirectedEdge)) * crf;
                            double variableCost = (linearComponents[c].getConSlope() * edgeConstructionCosts.get(undirectedEdge)) * crf;
                            
                            // add the cost for that edge to the solution
                            solution.addEdgeCostComponent(undirectedEdge, fixedCost + variableCost * transportAmount);
                        }
                    }
                }
                
                // put other details in the solution
                solution.setProjectLength(numYears);
                solution.setCRF(crf);
                solution.setAnnualCaptureAmount(captureTarget);
                //solution.setEdgeTrendsEdgeVersion(edgeTrendsEdgeVersion);
            } else if(cplex2.solve()) {
                System.out.println("Made it to cplex2's try at it. ");
                // populate new solution
                solution = new Solution();
                
                // ignore cplex solution values that are below this threshold
                double threshold = .00001;

                // add each deployed source to the solution
                for (Source source : sources) {
                    double captureAmount = cplex2.getValue(a2[sourceToIndex.get(source)]);
                    if (captureAmount > threshold) {
                        solution.addSourceCaptureAmount(source, captureAmount);
                        solution.addSourceCostComponent(source, captureAmount * source.getCaptureCost());
                    }
                }

                // add each deployed sink to the solution
                for (Sink sink : sinks) {
                    double storageAmount = cplex2.getValue(b2[sinkToIndex.get(sink)]);
                    if (storageAmount > threshold) {
                        solution.addSinkStorageAmount(sink, storageAmount);
                        solution.addSinkCostComponent(sink, storageAmount * sink.getInjectionCost());
                    }
                }

                // iterate through all of the directed edges
                for (int e = 0; e < directdEdgeToIndex.size(); e++) {
                    for (int c = 0; c < linearComponents.length; c++) {
                        // determine how much was transported though that edge
                        double transportAmount = cplex2.getValue(p2[e][c]);
                        if (transportAmount > threshold) {
                            // get the directed edge
                            directedEdge directedEdge = indexToDirectedEdge.get(e);
                            
                            // turn it into an undirected edge
                            Edge undirectedEdge = new Edge(directedEdge.v1, directedEdge.v2);
                            
                            // add transport amounts and trend info for that edge to the solution
                            solution.addEdgeTransportAmount(undirectedEdge, transportAmount);
                            solution.setEdgeTrend(undirectedEdge, c);
                            if(e % 2 == 0) {
                                edgeTrendsEdgeVersion.put(undirectedEdge, 0);
                            } else {
                                edgeTrendsEdgeVersion.put(undirectedEdge, 1);
                            }
                            
                            // calculate the "fixed" and "variable" costs
                            double fixedCost = (linearComponents[c].getConIntercept() * edgeConstructionCosts.get(undirectedEdge)) * crf;
                            double variableCost = (linearComponents[c].getConSlope() * edgeConstructionCosts.get(undirectedEdge)) * crf;
                            
                            // add the cost for that edge to the solution
                            solution.addEdgeCostComponent(undirectedEdge, fixedCost + variableCost * transportAmount);
                        }
                    }
                }
                
                // put other details in the solution
                solution.setProjectLength(numYears);
                solution.setCRF(crf);
                solution.setAnnualCaptureAmount(captureTarget);
                //solution.setEdgeTrendsEdgeVersion(edgeTrendsEdgeVersion);
            } else {
                System.out.println("Not Feasible - Revised Max Flow Solver");
            }
            
            cplex.clearModel();
            cplex2.clearModel();
        } catch (IloException ex) {
            ex.printStackTrace();
        }
        return solution;
    }
}