package dataStore;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import solver.GreedyHeuristic;
import solver.Solver;
import static utilities.Utilities.*;

/**
 *
 * @author yaw
 */
public class DataStorer {

    public String basePath;
    public String dataset;
    public String scenario;

    private Solver solver;

    private DataInOut dataInOut = new DataInOut();

    // Geospatial data.
    private int width;  // Number of columns
    private int height;
    private double lowerLeftX;
    private double lowerLeftY;
    private double cellSize;
    private int projectionVersion;  //1 - GCS (lat/lon), 2 - PCS (meters)

    // Source and sink data.
    private Source[] sources;
    private Sink[] sinks;
    private LinearComponent[] linearComponents;
    private int[] sourceSinkCellLocations;  // Cell number for each source and sink node

    // Raw network information
    private double[][] rightOfWayCosts;
    private double[][] constructionCosts;
    private double[][] routingCosts;
    private double[][] modifiedRoutingCosts;

    // Candidate network graph information
    private int[] graphVertices;    // Set of all vertices in graph (source/sink/junction)
    private HashMap<Edge, Double> graphEdgeCosts;   // Cost for each edge between vertices
    private HashMap<Edge, int[]> graphEdgeRoutes;   // Cell-to-cell route for each edge between vertices
    private HashMap<Edge, Double> graphEdgeRightOfWayCosts;   // Cost for each edge between vertices
    private HashMap<Edge, Double> graphEdgeConstructionCosts;   // Cost for each edge between vertices
    private HashSet<Edge> delaunayPairs;
    private HashMap<Edge, Double> graphEdgeLengths; // Length (km) of edge when following routing

    // Configuration data
    private double[][] timeConfiguration;
    private double[] priceConfiguration;
    
    // Max Flow Data
    private String[] maxFlowGeneralData;
    private HashMap<Source, Double[][]> maxFlowSourcesInfo;
    private HashMap<Sink, Double[][]> maxFlowSinksInfo;
    private HashMap<Edge, Double[][]> maxFlowEdgesInfo;
    
    // Max Flow Min Cost Data
    private double maxFlowMinCostAmount;
    private boolean solvingMaxFlowMinCost;
    private double modelParam;
    private double crf;
    private double interestRate;
    private int numYears;
    private String[] hmOutputData;

    public DataStorer(String basePath, String dataset, String scenario) {
        this.basePath = basePath;
        this.dataset = dataset;
        this.scenario = scenario;
    }

    // Get raw Delaunay edges.
    public HashSet<int[]> getDelaunayEdges() {
        if (delaunayPairs == null) {
            generateDelaunayPairs();
        }
        HashSet<int[]> edges = new HashSet<>();
        for (Edge e : delaunayPairs) {
            edges.add(new int[]{e.v1, e.v2});
        }
        return edges;
    }

    public HashSet<int[]> getGraphEdges() {
        if (graphEdgeRoutes == null) {
            generateCandidateGraph();
        }
        return new HashSet<>(graphEdgeRoutes.values());
    }

    public void generateDelaunayPairs() {
        delaunayPairs = solver.generateDelaunayPairs();
        dataInOut.saveDelaunayPairs();
    }

    public String generateCandidateGraph() {
        loadNetworkCosts();
        String[] outliers = outliers();
        if (outliers.length == 0) {
            generateDelaunayPairs();

            Object[] graphComponents = solver.generateDelaunayCandidateGraph();
            if (graphComponents != null) {
                graphVertices = (int[]) graphComponents[0];
                graphEdgeCosts = (HashMap<Edge, Double>) graphComponents[1];
                graphEdgeRoutes = (HashMap<Edge, int[]>) graphComponents[2];

                // Make right of way and construction costs
                Object[] costComponents = solver.makeComponentCosts();
                graphEdgeRightOfWayCosts = (HashMap<Edge, Double>) costComponents[0];
                graphEdgeConstructionCosts = (HashMap<Edge, Double>) costComponents[1];

                String networkName = "CandidateNetwork";
                saveCandidateGraph(networkName);
                return networkName;
            }
        } else {
            String text = "";
            for (String outlier : outliers) {
                text += outlier + ", ";
            }
            solver.getMessenger().setText("Outliers: " + text);
            
            // Offshore routing
            generateDelaunayPairs();

            Object[] graphComponents = solver.generateDelaunayCandidateGraph();
            if (graphComponents != null) {
                graphVertices = (int[]) graphComponents[0];
                graphEdgeCosts = (HashMap<Edge, Double>) graphComponents[1];
                graphEdgeRoutes = (HashMap<Edge, int[]>) graphComponents[2];

                // Make right of way and construction costs
                Object[] costComponents = solver.makeComponentCosts();
                graphEdgeRightOfWayCosts = (HashMap<Edge, Double>) costComponents[0];
                graphEdgeConstructionCosts = (HashMap<Edge, Double>) costComponents[1];

                String networkName = "CandidateNetwork";
                saveCandidateGraph(networkName);
                return networkName;
            }
        }
        return "";
    }

    public void saveCandidateGraph(String networkName) {
        dataInOut.saveCandidateGraph(networkName);
    }

    public String[] outliers() {
        ArrayList<String> outliers = new ArrayList<>();
        for (Source src : sources) {
            int cell = src.getCellNum();

            if (cell >= constructionCosts.length) {
                outliers.add("SRC-" + src.getLabel());
            }
            boolean connected = false;
            for (double cost : constructionCosts[cell]) {
                if (cost < Double.MAX_VALUE) {
                    connected = true;
                }
            }
            if (!connected) {
                outliers.add("SRC-" + src.getLabel());
            }
        }
        for (Sink snk : sinks) {
            int cell = snk.getCellNum();

            if (cell >= constructionCosts.length) {
                outliers.add("SNK-" + snk.getLabel());
            }
            boolean connected = false;
            for (double cost : constructionCosts[cell]) {
                if (cost < Double.MAX_VALUE) {
                    connected = true;
                }
            }
            if (!connected) {
                outliers.add("SNK-" + snk.getLabel());
            }
        }
        return outliers.toArray(new String[outliers.size()]);
    }

    public void loadNetworkCosts() {
        if (constructionCosts == null) {
            dataInOut.loadCosts();

            // Make right of way and construction costs
            if (graphEdgeRoutes != null) {
                Object[] costComponents = solver.makeComponentCosts();
                graphEdgeRightOfWayCosts = (HashMap<Edge, Double>) costComponents[0];
                graphEdgeConstructionCosts = (HashMap<Edge, Double>) costComponents[1];
            }
        }
    }

    public void loadCandidateNetwork(String network) {
        dataInOut.loadCandidateGraph(network);
    }

    public Set<Integer> getJunctions() {
        if (graphVertices == null) {
            generateCandidateGraph();
        }

        HashSet<Integer> junctions = new HashSet<>();
        for (int vertex : graphVertices) {
            junctions.add(vertex);
        }
        for (Source source : sources) {
            junctions.remove(source.getCellNum());
        }
        for (Sink sink : sinks) {
            junctions.remove(sink.getCellNum());
        }

        return junctions;
    }

    public int[] getGraphVertices() {
        if (graphVertices == null) {
            generateCandidateGraph();
        }
        return graphVertices;
    }

    public HashMap<Edge, Double> getGraphEdgeCosts() {
        if (graphEdgeCosts == null) {
            generateCandidateGraph();
        }
        return graphEdgeCosts;
    }

    public HashMap<Edge, Double> getGraphEdgeRightOfWayCosts() {
        if (graphEdgeRightOfWayCosts == null) {
            generateCandidateGraph();
        }
        return graphEdgeRightOfWayCosts;
    }

    public HashMap<Edge, Double> getGraphEdgeConstructionCosts() {
        if (graphEdgeConstructionCosts == null) {
            generateCandidateGraph();
        }
        return graphEdgeConstructionCosts;
    }

    public HashMap<Edge, int[]> getGraphEdgeRoutes() {
        if (graphEdgeRoutes == null) {
            generateCandidateGraph();
        }
        return graphEdgeRoutes;
    }

    public HashMap<Edge, Double> getGraphEdgeLengths() {
        if (graphEdgeLengths == null) {
            graphEdgeLengths = solver.calculateGraphEdgeLengths();
        }
        return graphEdgeLengths;
    }

    public HashSet<Edge> getDelaunayPairs() {
        if (delaunayPairs == null) {
            generateDelaunayPairs();
        }
        return delaunayPairs;
    }

    // Get edge weight in one of the base cost surfaces.
    public double getEdgeWeight(int cell1, int cell2, String type) {
        if (cell1 == cell2) {
            return 0;
        } else if (getNeighborNum(cell1, cell2) >= 0 && getNeighborNum(cell1, cell2) < 8) {
            if (type.equals("r")) {
                return routingCosts[cell1][getNeighborNum(cell1, cell2)];
            } else if (type.equals("c")) {
                if (rightOfWayCosts != null) {
                    return constructionCosts[cell1][getNeighborNum(cell1, cell2)] + rightOfWayCosts[cell1][getNeighborNum(cell1, cell2)];
                } else {
                    return constructionCosts[cell1][getNeighborNum(cell1, cell2)];
                }
            }
        }
        return Double.MAX_VALUE;
    }

    public double getEdgeRightOfWayCost(int cell1, int cell2) {
        // Catch if right of way costs are not used.
        if (rightOfWayCosts == null) {
            return 0;
        }

        if (cell1 == cell2) {
            return 0;
        } else if (getNeighborNum(cell1, cell2) >= 0 && getNeighborNum(cell1, cell2) < 8) {
            return rightOfWayCosts[cell1][getNeighborNum(cell1, cell2)];
        }
        return Double.MAX_VALUE;
    }

    public double getEdgeConstructionCost(int cell1, int cell2) {
        if (cell1 == cell2) {
            return 0;
        } else if (getNeighborNum(cell1, cell2) >= 0 && getNeighborNum(cell1, cell2) < 8) {
            return constructionCosts[cell1][getNeighborNum(cell1, cell2)];
        }
        return Double.MAX_VALUE;
    }

    // Cell number to column number, row number. (column and row numbering start at 1)
    public double[] cellLocationToRawXY(int cell) {
        // NOTE: Cell counting starts at 1, not 0.
        int y = (cell - 1) / width + 1;
        int x = cell - (y - 1) * width;
        return new double[]{x, y};
    }

    // Lat/lon to raw X,Y in Lambert Azimuthal Equal Area
    public double[] latLonToLAEAXY(double lat, double lon) {
        double phi_1 = degreesToRadians(45.0);
        double lambda_0 = degreesToRadians(-100);
        double phi = degreesToRadians(lat);
        double lambda = degreesToRadians(lon);
        double a = 6378137.0;
        double f = 298.257223563;
        double e = Math.sqrt(2 * (1 / f) - (1 / f) * (1 / f));

        double q_p = (1 - e * e) * (Math.sin(degreesToRadians(90)) / (1 - e * e * Math.sin(degreesToRadians(90)) * Math.sin(degreesToRadians(90))) - (1 / (2 * e)) * Math.log((1 - e * Math.sin(degreesToRadians(90))) / (1 + e * Math.sin(degreesToRadians(90)))));
        double q_1 = (1 - e * e) * (Math.sin(phi_1) / (1 - e * e * Math.sin(phi_1) * Math.sin(phi_1)) - (1 / (2 * e)) * Math.log((1 - e * Math.sin(phi_1)) / (1 + e * Math.sin(phi_1))));
        double q = (1 - e * e) * (Math.sin(phi) / (1 - e * e * Math.sin(phi) * Math.sin(phi)) - (1 / (2 * e)) * Math.log((1 - e * Math.sin(phi)) / (1 + e * Math.sin(phi))));;
        double R_q = a * Math.sqrt(q_p / 2);
        double m_1 = Math.cos(phi_1) / Math.sqrt(1 - e * e * Math.sin(phi_1) * Math.sin(phi_1));

        double beta_1 = Math.asin(q_1 / q_p);
        double beta = Math.asin(q / q_p);
        double B = R_q * Math.sqrt(2 / (1 + Math.sin(beta_1) * Math.sin(beta) + Math.cos(beta_1) * Math.cos(beta) * Math.cos(lambda - lambda_0)));
        double D = a * m_1 / (R_q * Math.cos(beta_1));

        double x = B * D * Math.cos(beta) * Math.sin(lambda - lambda_0);
        double y = (B / D) * (Math.cos(beta_1) * Math.sin(beta) - Math.sin(beta_1) * Math.cos(beta) * Math.cos(lambda - lambda_0));

        return new double[]{y, x};
    }
    
    // Lat/lon to raw X,Y in Albers Equal-Area Conic Projection
    /*PROJCS["Albers_Conical_Equal_Area",GEOGCS["GCS_WGS_1984",
    DATUM["D_WGS_1984",SPHEROID["WGS_1984",6378137.0,298.257223563]],
    PRIMEM["Greenwich",0.0],UNIT["Degree",0.0174532925199433]],PROJECTION["Albers"],
    PARAMETER["false_easting",0.0],PARAMETER["false_northing",0.0],
    PARAMETER["central_meridian",-96.0],PARAMETER["standard_parallel_1",29.5],
    PARAMETER["standard_parallel_2",45.5],PARAMETER["latitude_of_origin",23.0],UNIT["Meter",1.0]]*/
    public double[] latLonToAEACPXY(double lat, double lon) {
        double phi = degreesToRadians(lat);
        double phi_0 = degreesToRadians(23.0);
        double phi_1 = degreesToRadians(29.5);
        double phi_2 = degreesToRadians(45.5);
        double lambda = degreesToRadians(lon);
        double lambda_0 = degreesToRadians(-96.0);
        double a = 6378137.0;
        double f = 1 / 298.257223563;
        double b = a * (1 - f);
        double e = Math.sqrt(1 - (b * b) / (a * a));
        
        double q = (1 - e * e) * (Math.sin(phi) / (1 - e * e * Math.sin(phi) * Math.sin(phi)) - (1 / (2 * e)) * Math.log((1 - e * Math.sin(phi)) / (1 + e * Math.sin(phi))));
        double q_0 = (1 - e * e) * (Math.sin(phi_0) / (1 - e * e * Math.sin(phi_0) * Math.sin(phi_0)) - (1 / (2 * e)) * Math.log((1 - e * Math.sin(phi_0)) / (1 + e * Math.sin(phi_0))));
        double q_1 = (1 - e * e) * (Math.sin(phi_1) / (1 - e * e * Math.sin(phi_1) * Math.sin(phi_1)) - (1 / (2 * e)) * Math.log((1 - e * Math.sin(phi_1)) / (1 + e * Math.sin(phi_1))));
        double q_2 = (1 - e * e) * (Math.sin(phi_2) / (1 - e * e * Math.sin(phi_2) * Math.sin(phi_2)) - (1 / (2 * e)) * Math.log((1 - e * Math.sin(phi_2)) / (1 + e * Math.sin(phi_2))));
        double m_1 = Math.cos(phi_1) / Math.sqrt(1 - e * e * Math.sin(phi_1) * Math.sin(phi_1));
        double m_2 = Math.cos(phi_2) / Math.sqrt(1 - e * e * Math.sin(phi_2) * Math.sin(phi_2));
        double n = (m_1 * m_1 - m_2 * m_2) / (q_2 - q_1);
        double theta = n * (lambda - lambda_0);
        double c = m_1 * m_1 + n * q_1;
        double rho = a * Math.sqrt(c - n * q) / n;
        double rho_0 = a * Math.sqrt(c - n * q_0) / n;

        double x = rho * Math.sin(theta);
        double y = rho_0 - (rho * Math.cos(theta));

        return new double[]{y, x};
    }
    
    public double[] AEACPXYtoLatLon(double y, double x) {
        double phi_0 = degreesToRadians(23.0);
        double phi_1 = degreesToRadians(29.5);
        double phi_2 = degreesToRadians(45.5);
        double lambda_0 = degreesToRadians(-96.0);
        double a = 6378137.0;
        double f = 1 / 298.257223563;
        double b = a * (1 - f);
        double e = Math.sqrt(1 - (b * b) / (a * a));
        
        double q_0 = (1 - e * e) * (Math.sin(phi_0) / (1 - e * e * Math.sin(phi_0) * Math.sin(phi_0)) - (1 / (2 * e)) * Math.log((1 - e * Math.sin(phi_0)) / (1 + e * Math.sin(phi_0))));
        double q_1 = (1 - e * e) * (Math.sin(phi_1) / (1 - e * e * Math.sin(phi_1) * Math.sin(phi_1)) - (1 / (2 * e)) * Math.log((1 - e * Math.sin(phi_1)) / (1 + e * Math.sin(phi_1))));
        double q_2 = (1 - e * e) * (Math.sin(phi_2) / (1 - e * e * Math.sin(phi_2) * Math.sin(phi_2)) - (1 / (2 * e)) * Math.log((1 - e * Math.sin(phi_2)) / (1 + e * Math.sin(phi_2))));
        double m_1 = Math.cos(phi_1) / Math.sqrt(1 - e * e * Math.sin(phi_1) * Math.sin(phi_1));
        double m_2 = Math.cos(phi_2) / Math.sqrt(1 - e * e * Math.sin(phi_2) * Math.sin(phi_2));
        double n = (m_1 * m_1 - m_2 * m_2) / (q_2 - q_1);
        double c = m_1 * m_1 + n * q_1;
        double rho_0 = a * Math.sqrt(c - n * q_0) / n;
        
        double theta = Math.atan(x / (rho_0 - y));
        double rho = Math.sqrt(x * x + ((rho_0 - y) * (rho_0 - y)));
        double q = (c - rho * rho * n * n / (a * a)) / n;
        
        double lon = radiansToDegrees(lambda_0 + theta / n);
        
        double lastPhi = Math.asin(q / 2);
        double newPhi = lastPhi + (1 - e * e * Math.sin(lastPhi) * Math.sin(lastPhi)) * (1 - e * e * Math.sin(lastPhi) * Math.sin(lastPhi)) / (2 * Math.cos(lastPhi)) * (q / (1 - e * e) - Math.sin(lastPhi) / (1 - e * e * Math.sin(lastPhi) * Math.sin(lastPhi)) + 1 / (2 * e) * Math.log((1 - e * Math.sin(lastPhi)) / (1 + e * Math.sin(lastPhi))));
        while (Math.abs(lastPhi - newPhi) > 0.000001) {
            lastPhi = newPhi;
            newPhi = lastPhi + (1 - e * e * Math.sin(lastPhi) * Math.sin(lastPhi)) * (1 - e * e * Math.sin(lastPhi) * Math.sin(lastPhi)) / (2 * Math.cos(lastPhi)) * (q / (1 - e * e) - Math.sin(lastPhi) / (1 - e * e * Math.sin(lastPhi) * Math.sin(lastPhi)) + 1 / (2 * e) * Math.log((1 - e * Math.sin(lastPhi)) / (1 + e * Math.sin(lastPhi))));
        }
        
        double lat = radiansToDegrees(newPhi);

        return new double[]{lat, lon};
    }

    public int getCellNum(double lat, double lon) {
        if (projectionVersion == 1) {
            return locationToCell(lat, lon);
        } else if (projectionVersion == 2) {
            double[] xy = latLonToAEACPXY(lat, lon);
            return locationToCell(xy[0], xy[1]);
        }
        return 0;
    }

    /*public double[] latLonToXY(double lat, double lon) {
        double y = height - (((lat - lowerLeftY) / cellSize) + 1) + 1;
        double x = (lon - lowerLeftX) / cellSize;
        return new double[]{x, y};
    }*/
    
    // /location to cell number. (x = lat, y = lon)
    private int locationToCell(double x, double y) {
        // NOTE: Cell counting starts at 1, not 0.
        int row = height - ((int) ((x - lowerLeftY) / cellSize) + 1) + 1;
        int col = (int) ((y - lowerLeftX) / cellSize) + 1;
        return colRowToVectorized(col, row);
    }

    // Row/column to cell number.
    public int colRowToVectorized(int col, int row) {
        // NOTE: Cell counting starts at 1, not 0.
        return (row - 1) * width + col;
    }

    // Cell to location.
    public double[] cellToLocation(int cell) {
        double[] xy = cellLocationToRawXY(cell);
        xy[0] -= .5;
        xy[1] -= .5;
        // X = lat, Y = lon
        double X = (height - xy[1]) * cellSize + lowerLeftY;
        double Y = xy[0] * cellSize + lowerLeftX;
        return new double[]{X, Y};
    }

    public int[] getNeighborCells(int cellNum) {
        // NOTE: Neighbor numbering starts in upper left as 0 and goes in clockwise direction.
        int[] neighbors = {cellNum - width - 1, cellNum - width, cellNum - width + 1, cellNum + 1, cellNum + width + 1, cellNum + width, cellNum + width - 1, cellNum - 1};
        for (int i = 0; i < neighbors.length; i++) {
            if (neighbors[i] < 1 || neighbors[i] > height * width) {
                neighbors[i] = 0;
            }
        }
        return neighbors;
    }

    public int getNeighborNum(int centerCell, int neighborCell) {
        // NOTE: Neighbor numbering starts in upper left as 0 and goes in clockwise direction.
        if (neighborCell == centerCell - width - 1) {
            return 0;
        } else if (neighborCell == centerCell - width) {
            return 1;
        } else if (neighborCell == centerCell - width + 1) {
            return 2;
        } else if (neighborCell == centerCell + 1) {
            return 3;
        } else if (neighborCell == centerCell + width + 1) {
            return 4;
        } else if (neighborCell == centerCell + width) {
            return 5;
        } else if (neighborCell == centerCell + width - 1) {
            return 6;
        } else if (neighborCell == centerCell - 1) {
            return 7;
        }
        return -1;
    }

    // Get array of all source and sink node cell locations
    public int[] getSourceSinkCells() {
        if (sourceSinkCellLocations == null) {
            sourceSinkCellLocations = new int[sources.length + sinks.length];
            for (int i = 0; i < sources.length; i++) {
                sourceSinkCellLocations[i] = sources[i].getCellNum();
            }
            for (int i = 0; i < sinks.length; i++) {
                sourceSinkCellLocations[sources.length + i] = sinks[i].getCellNum();
            }
        }
        return sourceSinkCellLocations;
    }

    // Check if cell is a source or sink
    public boolean isSourceSink(int cellNum) {
        if (sourceSinkCellLocations == null) {
            getSourceSinkCells();
        }

        for (int sourceSinkCell : sourceSinkCellLocations) {
            if (sourceSinkCell == cellNum) {
                return true;
            }
        }
        return false;
    }

    public double getModifiedEdgeRoutingCost(int cell1, int cell2) {
        if (cell1 == cell2) {
            return 0;
        } else if (getNeighborNum(cell1, cell2) >= 0 && getNeighborNum(cell1, cell2) < 8) {
            if (modifiedRoutingCosts == null) {
                loadNetworkCosts();
            }
            return modifiedRoutingCosts[cell1][getNeighborNum(cell1, cell2)];
        }
        return Double.MAX_VALUE;
    }

    public void updateModifiedEdgeRoutingCost(int cell1, int cell2, double edgeCostModification) {
        if (cell1 == cell2) {

        } else if (getNeighborNum(cell1, cell2) >= 0 && getNeighborNum(cell1, cell2) < 8) {
            if (modifiedRoutingCosts == null) {
                loadNetworkCosts();
            }
            modifiedRoutingCosts[cell1][getNeighborNum(cell1, cell2)] = edgeCostModification * routingCosts[cell1][getNeighborNum(cell1, cell2)];
        }
    }

    public int sourceNum(int vertex) {
        for (int i = 0; i < sources.length; i++) {
            if (vertex == sources[i].getCellNum()) {
                return i;
            }
        }
        return -1;
    }

    public int sinkNum(int vertex) {
        for (int i = 0; i < sinks.length; i++) {
            if (vertex == sinks[i].getCellNum()) {
                return i;
            }
        }
        return -1;
    }

    public boolean isSimplified() {
        for (Source src : sources) {
            if (!src.isSimplified()) {
                return false;
            }
        }

        for (Sink snk : sinks) {
            if (!snk.isSimplified()) {
                return false;
            }
        }

        return true;
    }

    public HashMap<Integer, HashSet<Integer>> getGraphNeighbors() {
        HashMap<Integer, HashSet<Integer>> graphNeighbors = new HashMap<>();
        for (Edge e : graphEdgeCosts.keySet()) {
            int v1 = e.v1;
            int v2 = e.v2;
            if (!graphNeighbors.containsKey(v1)) {
                graphNeighbors.put(v1, new HashSet<>());
            }
            if (!graphNeighbors.containsKey(v2)) {
                graphNeighbors.put(v2, new HashSet<>());
            }
            graphNeighbors.get(v1).add(v2);
            graphNeighbors.get(v2).add(v1);
        }
        return graphNeighbors;
    }
    
    public HashMap<Integer, HashSet<Integer>> getGraphNeighborsHeatMap(HashMap<Edge, Integer> hmEdges, double numbRealizations, double toDisplayPercent) {
        HashMap<Integer, HashSet<Integer>> graphNeighbors = new HashMap<>();
        for(Edge e : hmEdges.keySet()) {
            if((double)hmEdges.get(e) / numbRealizations >= toDisplayPercent) {
                int v1 = e.v1;
                int v2 = e.v2;
                if(!graphNeighbors.containsKey(v1)) {
                    graphNeighbors.put(v1, new HashSet<>());
                }
                if(!graphNeighbors.containsKey(v2)) {
                    graphNeighbors.put(v2, new HashSet<>());
                }
                graphNeighbors.get(v1).add(v2);
                graphNeighbors.get(v2).add(v1);
            }
        }
        return graphNeighbors;
    }

    // Get cell location of neighbor neighborNum of centerCell.
    public int getNeighbor(int centerCell, int neighborNum) {
        // NOTE: Neighbor numbering starts in upper left as 0 and goes in clockwise direction.
        if (neighborNum == 0 && centerCell > width && centerCell % width != 1) {
            return centerCell - width - 1;
        }
        if (neighborNum == 1 && centerCell > width) {
            return centerCell - width;
        }
        if (neighborNum == 2 && centerCell > width && centerCell % width != 0) {
            return centerCell - width + 1;
        }
        if (neighborNum == 3 && centerCell % width != 0) {
            return centerCell + 1;
        }
        if (neighborNum == 4 && centerCell % width != 0 && centerCell / width + 1 < height) {
            return centerCell + width + 1;
        }
        if (neighborNum == 5 && centerCell / width + 1 < height) {
            return centerCell + width;
        }
        if (neighborNum == 6 && centerCell / width + 1 < height && centerCell % width != 1) {
            return centerCell + width - 1;
        }
        if (neighborNum == 7 && centerCell % width != 1) {
            return centerCell - 1;
        }
        return -1;
    }

    // Data element get methods
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public double getLowerLeftX() {
        return lowerLeftX;
    }

    public double getLowerLeftY() {
        return lowerLeftY;
    }

    public int getProjectionVersion() {
        return projectionVersion;
    }

    public Source[] getSources() {
        return sources;
    }

    public Sink[] getSinks() {
        return sinks;
    }

    public LinearComponent[] getLinearComponents() {
        return linearComponents;
    }

    public String getCostSurfacePath() {
        return basePath + "/" + dataset + "/BaseData/CostSurface/cost.bmp";
    }

    public double getModelParam() {
        return modelParam;
    }

    public double getMaxAnnualTransferable() {
        double maxCap = 0;
        for (Source src : sources) {
            maxCap += src.getMaxProductionRate();
        }

        double maxStore = 0;
        for (Sink snk : sinks) {
            maxStore += snk.getCapacity() / numYears;
        }

        return Math.min(maxCap, maxStore);
    }

    public double getCrf() {
        return crf;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public int getNumYears() {
        return numYears;
    }

    public double[][] getTimeConfiguration() {
        if (timeConfiguration == null) {
            dataInOut.loadTimeConfiguration();
        }
        return timeConfiguration;
    }

    public double[] getPriceConfiguration() {
        return priceConfiguration;
    }

    public double[][] getConstructionCosts() {
        return constructionCosts;
    }

    public double[][] getRoutingCosts() {
        if (routingCosts == null) {
            dataInOut.loadCosts();
        }
        return routingCosts;
    }
    
    public double getMaxFlowMinCostAmount() {
        return maxFlowMinCostAmount;
    }
    
    public boolean getSolvingMaxFlowMinCost() {
        return solvingMaxFlowMinCost;
    }
    
    public void setSolvingMaxFlowMinCost(boolean solving) {
        solvingMaxFlowMinCost = solving;
    }
    
    public void setMaxFlowMinCostAmount(double amount) {
        maxFlowMinCostAmount = amount;
    }

    // Data element set methods
    public void setNumYears(int numYears) {
        this.numYears = numYears;
    }

    public void setCrf(double crf) {
        this.crf = crf;
    }

    public void setInterestRate(double interestRate) {
        this.interestRate = interestRate;
    }

    public void setModelParam(double amt) {
        modelParam = amt;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setLowerLeftX(double lowerLeftX) {
        this.lowerLeftX = lowerLeftX;
    }

    public void setLowerLeftY(double lowerLeftY) {
        this.lowerLeftY = lowerLeftY;
    }

    public void setCellSize(double cellSize) {
        this.cellSize = cellSize;
    }

    public void setProjectionVersion(int projectionVersion) {
        this.projectionVersion = projectionVersion;
    }

    public void setRightOfWayCosts(double[][] rightOfWayCosts) {
        this.rightOfWayCosts = rightOfWayCosts;
    }

    public void setConstructionCosts(double[][] constructionCosts) {
        this.constructionCosts = constructionCosts;
    }

    public void setRoutingCosts(double[][] routingCosts) {
        this.routingCosts = routingCosts;

        modifiedRoutingCosts = new double[routingCosts.length][];
        for (int i = 0; i < routingCosts.length; i++) {
            double[] temp = routingCosts[i];
            int tempLength = temp.length;
            modifiedRoutingCosts[i] = new double[tempLength];
            System.arraycopy(temp, 0, modifiedRoutingCosts[i], 0, tempLength);
        }
    }

    public void setSources(Source[] sources) {
        this.sources = sources;
    }

    public void setSinks(Sink[] sinks) {
        this.sinks = sinks;
    }

    public void setLinearComponents(LinearComponent[] linearComponents) {
        this.linearComponents = linearComponents;
    }

    public void setGraphVertices(int[] vertices) {
        graphVertices = vertices;
    }

    public void setGraphEdgeCosts(HashMap<Edge, Double> edgeCosts) {
        graphEdgeCosts = edgeCosts;
    }

    public void setGraphEdgeConstructionCosts(HashMap<Edge, Double> constructionCosts) {
        graphEdgeConstructionCosts = constructionCosts;
    }

    public void setGraphEdgeRightOfWayCosts(HashMap<Edge, Double> rowCosts) {
        graphEdgeRightOfWayCosts = rowCosts;
    }

    public void setGraphEdgeRoutes(HashMap<Edge, int[]> edgeRoutes) {
        graphEdgeRoutes = edgeRoutes;
    }

    public void setDelaunayPairs(HashSet<Edge> pairs) {
        delaunayPairs = pairs;
    }

    public void setSolver(Solver s) {
        solver = s;

        // Load data from files.
        dataInOut.loadData(basePath, dataset, scenario, this);
    }

    public void setPipelineCapacities() {
        linearComponents[linearComponents.length - 1].setMaxCapacity(getMaxAnnualTransferable());  // Do not make Double.MaxValue. CPLEX does not do well with infinity here.
    }

    public void setTimeConfiguration(double[][] timeConfiguration) {
        this.timeConfiguration = timeConfiguration;
    }

    public void setPriceConfiguration(double[] priceConfiguration) {
        this.priceConfiguration = priceConfiguration;
    }
    
    public String[] getHeatMapOutputData() {
        return hmOutputData;
    }
    
    public void setHeatMapOutputData(String[] hmOutputData) {
        this.hmOutputData = hmOutputData;
    }
    
    public void setMaxFlowGeneralData(String[] maxFlowGeneralData) {
        this.maxFlowGeneralData = maxFlowGeneralData;
    }
    
    public void setMaxFlowSourcesInfo(HashMap<Source, Double[][]> maxFlowSourcesInfo) {
        this.maxFlowSourcesInfo = maxFlowSourcesInfo;
    }
    
    public void setMaxFlowSinksInfo(HashMap<Sink, Double[][]> maxFlowSinksInfo) {
        this.maxFlowSinksInfo = maxFlowSinksInfo;
    }
    
    public void setMaxFlowEdgesInfo(HashMap<Edge, Double[][]> maxFlowEdgesInfo) {
        this.maxFlowEdgesInfo = maxFlowEdgesInfo;
    }
    
    public String[] getMaxFlowGeneralData() {
        return maxFlowGeneralData;
    }
    
    public HashMap<Source, Double[][]> getMaxFlowSourcesInfo() {
        return maxFlowSourcesInfo;
    }
    
    public HashMap<Sink, Double[][]> getMaxFlowSinksInfo() {
        return maxFlowSinksInfo;
    }
    
    public HashMap<Edge, Double[][]> getMaxFlowEdgesInfo() {
        return maxFlowEdgesInfo;
    }

    public void loadTimeConfiguration() {
        dataInOut.loadTimeConfiguration();
    }

    public void loadPriceConfiguration() {
        dataInOut.loadPriceConfiguration();
    }

    public Solution loadGreedyHeuristicSolution(String solutionPath) {
        return dataInOut.loadGreedyHeuristicSolution(solutionPath);
    }

    public void makeShapeFiles(String path, Solution soln) {
        dataInOut.makeShapeFiles(path, soln);
    }

    public void makeCandidateShapeFiles(String path) {
        dataInOut.makeCandidateShapeFiles(path);
    }

    public void makeSolutionFile(String path, Solution soln) {
        dataInOut.makeSolutionFile(path, soln);
    }

    public void makePriceAggregationFile(String path, String content) {
        dataInOut.makePriceAggregationFile(path, content);
    }
    
    // Creates file detailing important general heat map info
    public void makeHeatMapInformationFile(String path) {
        dataInOut.makeHeatMapInformationFile(path);
    }
    
    public void makeMaxFlowInformationFile(String path) {
        dataInOut.makeMaxFlowInformationFile(path);
    }
    
    public void makeMaxFlowSourcesFile(String path) {
        dataInOut.makeMaxFlowSourcesFile(path);
    }
    
    public void makeMaxFlowSinksFile(String path) {
        dataInOut.makeMaxFlowSinksFile(path);
    }
    
    public void makeMaxFlowEdgesFile(String path) {
        dataInOut.makeMaxFlowEdgesFile(path);
    }

    public void downloadFile(String urlPath) {
        dataInOut.downloadFile(urlPath);
    }

    public Solution loadSolution(String solutionPath, int phase) {
        return dataInOut.loadSolution(solutionPath, phase, null);
    }

    public Solution loadSolution(String solutionPath, int phase, String solutionName) {
        return dataInOut.loadSolution(solutionPath, phase, solutionName);
    }

    public int determineNumPhases(String mpsFilePath) {
        return dataInOut.determineNumPhases(mpsFilePath);
    }

    public void saveHeuristicSolution(File solutionDirectory, GreedyHeuristic heuristic) {
        dataInOut.saveHeuristicSolution(solutionDirectory, heuristic);
    }
}
