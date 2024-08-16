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
public class MaxFlowMinCostSolverRevised {

    public static Solution solve(DataStorer data, double crf, int numYears, double captureTarget, double numbRealizations, 
            double toDisplayPercent, HashMap<Edge, Integer> hmEdges, HashMap<Source, Integer> hmSources, HashMap<Sink, Integer> hmSinks, 
            HashMap<Sink, Double> hmSinksAmountLimit, ArrayList<Integer> hmCells) {
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
        //Source[] sources = new Source[hmSources.size()];
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

            // variable a: source capture amount
            IloNumVar[] a = new IloNumVar[sources.length];
            for (int s = 0; s < sources.length; s++) {
                //a[s] = cplex.numVar(0, sources[s].getProductionRate(), "a[" + s + "]");
                a[s] = cplex.numVar(0, Double.MAX_VALUE, "a[" + s + "]");
            }

            // variable b: sink storage amount
            IloNumVar[] b = new IloNumVar[sinks.length];
            for (int s = 0; s < sinks.length; s++) {
                //b[s] = cplex.numVar(0, hmSinksAmountLimit.get(sinks[s]) / (double)numYears, "b[" + s + "]");
                b[s] = cplex.numVar(0, Double.MAX_VALUE, "b[" + s + "]");
            }

            // variable p: edge transport amount for directed edges
            IloNumVar[][] p = new IloNumVar[directdEdgeToIndex.size()][linearComponents.length];
            for (int e = 0; e < directdEdgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    p[e][c] = cplex.numVar(0, Double.MAX_VALUE, "p[" + e + "][" + c + "]");
                }
            }

            // variable y: edge use indicator for directed edges
            IloIntVar[][] y = new IloIntVar[directdEdgeToIndex.size()][linearComponents.length];
            for (int e = 0; e < directdEdgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    y[e][c] = cplex.intVar(0, 1, "y[" + e + "][" + c + "]");
                }
            }

            // constraint A: pipe capacity and connect y to p
            for (int e = 0; e < directdEdgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    IloLinearNumExpr expr = cplex.linearNumExpr();
                    expr.addTerm(p[e][c], 1.0);
                    expr.addTerm(y[e][c], -linearComponents[c].getMaxCapacity());
                    cplex.addLe(expr, 0);
                }
            }

            // constraint B: single-trend edges
            for (int e = 0; e < directdEdgeToIndex.size(); e++) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                for (int c = 0; c < linearComponents.length; c++) {
                    expr.addTerm(y[e][c], 1.0);
                }
                cplex.addLe(expr, 1);
            }

            // constraint C: conservation of flow
            // loop through every vertex in the graph
            for (int vertex : graphVertices) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                
                if(neighbors.containsKey(vertex)) {
                    // sum up all flow (p-values) flowing out of the vertex
                    for (int neighbor : neighbors.get(vertex)) {
                        // directed edge from vertex to neighbor
                        directedEdge edge = new directedEdge(vertex, neighbor);
                        for (int c = 0; c < linearComponents.length; c++) {
                            expr.addTerm(p[directdEdgeToIndex.get(edge)][c], 1.0);
                        }
                    }

                    // deduct all flow flowing into the vertex
                    for (int neighbor : neighbors.get(vertex)) {
                        // directed edge from neighbor to vertex
                        directedEdge edge = new directedEdge(neighbor, vertex);
                        for (int c = 0; c < linearComponents.length; c++) {
                            expr.addTerm(p[directdEdgeToIndex.get(edge)][c], -1.0);
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
                        }
                    }
                }
                // a net decrease of the storage amount if the vertex is a sink
                if (sinkCells.contains(vertex)) {
                    for (Sink sink : sinks) {
                        if (sink.getCellNum() == vertex) {
                            expr.addTerm(b[sinkToIndex.get(sink)], -1.0);
                        }
                    }
                }
                // nothing if the vertex is neither
                cplex.addEq(expr, 0.0);
            }

            // constraint D: limit capture amount by source emission rate
            for (int s = 0; s < sources.length; s++) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                expr.addTerm(a[s], 1.0);
                cplex.addLe(expr, sources[s].getMaxProductionRate());
            }

            // constraint E: limit storage amount by annualized storage capacity
            for (int s = 0; s < sinks.length; s++) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                expr.addTerm(b[s], 1.0);
                cplex.addLe(expr, hmSinksAmountLimit.get(sinks[s]) / (double)numYears);
                //cplex.addLe(expr, sinks[s].getCapacity() / (double) numYears);
            }

            // constraint F: enforce capture target is met - maybe change to a leq?
            IloLinearNumExpr expr = cplex.linearNumExpr();
            for (int s = 0; s < sources.length; s++) {
                expr.addTerm(a[s], 1.0);
            }
            cplex.addGe(expr, captureTarget);
//            IloLinearNumExpr expr = cplex.linearNumExpr();
//            for (int s = 0; s < sinks.length; s++) {
//                expr.addTerm(b[s], 1.0);
//            }
//            cplex.addLe(expr, captureTarget);

            // objective
            IloLinearNumExpr objExpr = cplex.linearNumExpr();
            
            // capture cost
            for (int s = 0; s < sources.length; s++) {
                objExpr.addTerm(a[s], sources[s].getCaptureCost());
            }

            // storage cost
            for (int s = 0; s < sinks.length; s++) {
                objExpr.addTerm(b[s], sinks[s].getInjectionCost());
            }

            // transport cost
            for (int e = 0; e < directdEdgeToIndex.size(); e++) {
                for (int c = 0; c < linearComponents.length; c++) {
                    directedEdge directedEdge = indexToDirectedEdge.get(e);
                    
                    // make undirected edge to look up costs (costs are the same for each opposing directed edge)
                    Edge undirectedEdge = new Edge(directedEdge.v1, directedEdge.v2);

                    // calculate "fixed" and "variable" costs
                    double fixedCost = (linearComponents[c].getConIntercept() * edgeConstructionCosts.get(undirectedEdge)) * crf;
                    double variableCost = (linearComponents[c].getConSlope() * edgeConstructionCosts.get(undirectedEdge)) * crf;

                    objExpr.addTerm(y[e][c], fixedCost);
                    objExpr.addTerm(p[e][c], variableCost);
                }
            }

            // objective:
            //IloObjective obj = cplex.minimize(objExpr);
            IloObjective obj = cplex.minimize(objExpr);
            cplex.add(obj);
            cplex.setOut(null);

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
            } else {
                System.out.println("Not Feasible - Revised Max Flow Solver");
            }
            
            cplex.clearModel();
        } catch (IloException ex) {
            ex.printStackTrace();
        }
        return solution;
    }
}