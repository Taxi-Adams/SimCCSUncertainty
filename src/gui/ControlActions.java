package gui;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.dataAccess.shape.EsriShapeExport;
import com.bbn.openmap.omGraphics.OMGraphic;
import dataStore.DataStorer;
import dataStore.Edge;
import dataStore.LinearComponent;
import dataStore.Sink;
import dataStore.Solution;
import dataStore.Source;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javax.imageio.ImageIO;
import solver.FailureRobustMaxFlowSolver;
import solver.MPSWriter;
import solver.Solver;

import static utilities.Utilities.*;

import solver.GreedyHeuristic;
import solver.PriceMinCostSolver;
import solver.SlopeScalingKim;
import solver.MaxFlowSolverRevised;
import solver.MaxFlowMinCostSolverRevised;
//import solver.RobustMaxFlowMinCostSolver;

/**
 *
 * @author yaw
 */
public class ControlActions {

    private String basePath = "";
    private String dataset = "";
    private String scenario = "";

    private DataStorer data;
    private Solver solver;
    private ImageView map;
    private Pane sourceLocationsLayer;
    private Pane sinkLocationsLayer;
    private Pane sourceLabelsLayer;
    private Pane sinkLabelsLayer;
    private Pane shortestPathsLayer;
    private Pane candidateNetworkLayer;
    private Pane rawDelaunayLayer;
    private Pane solutionLayer;
    private TextArea messenger;
    private Gui gui;

    private Shape selectedEntity;
    private int selectedCell;
    private int newEdgeV1 = 0;
    
    private Solution[] baseSolutions;
    private String currentPath;
    private String run;
    private int numberOfBaseSolutions;
    
    private HashMap<Edge, Integer> hmEdges;
    private HashMap<Edge, Double> hmEdgesAveVol;
    private HashMap<Edge, Integer> hmEdgesAveTrend;
    private HashMap<Edge, Double> hmEdgesAveCost;
    private HashMap<Source, Integer> hmSources;
    private HashMap<Source, Double> hmSourcesPercentCap;
    private HashMap<Source, Double> hmSourcesCaptureAmnt;
    private HashMap<Source, Double> hmSourcesCaptureCost;
    private HashMap<Source, Double> hmSourcesAnnualCap;
    private HashMap<Sink, Integer> hmSinks;
    private HashMap<Sink, Double> hmSinksPercentStored;
    private HashMap<Sink, Double> hmSinksTotalCap;
    private HashMap<Sink, Double> hmSinksAnnualStored;
    private HashMap<Sink, Double> hmSinksInjectionCost;
    private boolean hmDataIsCalculated;
    
    private HashMap<Integer, ArrayList<Edge>> hmCellsAndEdges;
    private HashMap<Integer, ArrayList<Source>> hmCellsAndSources;
    private HashMap<Integer, ArrayList<Sink>> hmCellsAndSinks;
    private ArrayList<Integer> hmCells;
    
    // Max Flow Instance Fields
    HashMap<Edge, Double> hmEdgesFinal;
    HashMap<Source, Double> hmSourcesFinal;
    HashMap<Sink, Double> hmSinksFinal;
    
    // Revised parameters for Max Flow
    HashMap<Edge, Double> hmEdgesMax;
    HashMap<Source, Double> hmSourcesMax;
    HashMap<Sink, Double> hmSinksMin;
    
    // Selectable parameters for Max Flow
    HashMap<Sink, Double> hmSinksAmountLimit;
    HashMap<Sink, ArrayList<Double>> hmSinksStorageList;
    HashMap<Sink, ArrayList<Integer>> hmSinksSolutionNumber;
    
    // text values displayed to the left of network map in gui
    Label[] solutionValues;
    
    // temporary model version String for differentiating between cap and price
    String tempModelVersion;
    
    // Robust Network variables
    double mfmcInjectionAmount;
    HashMap<Edge, Integer[]> mfmcFinalTrends;
    HashMap<Integer, ArrayList<Source>> fRCellsAndSources;
    HashMap<Integer, ArrayList<Sink>> fRCellsAndSinks;
    ArrayList<Integer> fRCells;

    public ControlActions(ImageView map, Gui gui) {
        this.map = map;
        this.gui = gui;
    }

    public void toggleCostSurface(Boolean show, Rectangle background) {
        if (show && data != null) {
            Image img = null;
            File costSurfaceFile = new File(data.getCostSurfacePath());
            if (costSurfaceFile.exists()) {
                // Load existing image
                img = new Image("file:" + data.getCostSurfacePath());
            } else {
                // Create image from routing costs
                double[][] routingCosts = data.getRoutingCosts();
                Double[] costSurface = new Double[data.getWidth() * data.getHeight() + 1];
                for (int i = 0; i < routingCosts.length; i++) {
                    costSurface[i] = 0.0;
                    for (int j = 0; j < routingCosts[i].length; j++) {
                        if (routingCosts[i][j] != Double.MAX_VALUE) {
                            costSurface[i] += routingCosts[i][j];
                        }
                    }
                }
                img = getImageFromArray(costSurface, data.getWidth(), data.getHeight());
            }

            map.setImage(img);

            // Adjust background.
            background.setWidth(map.getFitWidth());
            background.setHeight(map.getFitHeight());
        } else {
            map.setImage(null);
        }
    }

    public Image getImageFromArray(Double[] pixels, int width, int height) {
        double minV = Collections.min(Arrays.asList(pixels));
        double maxV = Collections.max(Arrays.asList(pixels));
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] -= minV;
        }
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 1.0 - pixels[i] / ((maxV - minV) * 0.64);
            if(pixels[i] < 0.0) {
                pixels[i] = 0.0;
            }
        }
        BufferedImage image = new BufferedImage(width, height, 3);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float c = (float) pixels[x + y * width].doubleValue();
                int rgb = new java.awt.Color(c, c, c).getRGB();
                image.setRGB(x, y, rgb);
            }
        }

        try {
            ImageIO.write(image, "PNG", new File(data.getCostSurfacePath()));
        } catch (IOException ex) {
            System.out.println(ex.toString());
        }
        return SwingFXUtils.toFXImage(image, null);
    }

    public void selectDataset(File datasetPath, ChoiceBox scenarioChoice) {
        // Clear GUI
        gui.fullReset();

        this.dataset = datasetPath.getName();
        this.basePath = datasetPath.getParent();

        // Populate scenarios ChoiceBox.
        File f = new File(datasetPath, "Scenarios");
        ArrayList<String> dirs = new ArrayList<>();
        for (File file : f.listFiles()) {
            if (file.isDirectory() && file.getName().charAt(0) != '.' && file.getName().charAt(0) != '_') {
                dirs.add(file.getName());
            }
        }
        scenarioChoice.setItems(FXCollections.observableArrayList(dirs));
    }

    public void selectScenario(String scenario, Rectangle background, ChoiceBox solutionChoice, ChoiceBox networkChoice) {
        if (scenario != null) {
            gui.softReset();
            this.scenario = scenario;

            //enable selection menu
            //do initial drawing
            data = new DataStorer(basePath, dataset, scenario);
            solver = new Solver(data);
            data.setSolver(solver);
            //dataStorer.loadData();
            solver.setMessenger(messenger);
            gui.displayCostSurface();
            gui.setStartingUncertaintyCondition();

            // Load solutions.
            initializeSolutionSelection(solutionChoice);

            // Populate network ChoiceBox.
            File f = new File(basePath + "/" + dataset + "/Scenarios/" + this.scenario + "/Network/CandidateNetwork/");
            ArrayList<String> dirs = new ArrayList<>();
            for (File file : f.listFiles()) {
                dirs.add(file.getName().substring(0, file.getName().lastIndexOf('.')));
            }
            networkChoice.setItems(FXCollections.observableArrayList(dirs));
            if (dirs.contains("CandidateNetwork")) {
                networkChoice.getSelectionModel().select("CandidateNetwork");
            } else {
                networkChoice.getSelectionModel().selectFirst();
            }
        }
    }

    public void selectCandidateNetwork(String network) {
        if (network != null) {
            data.loadCandidateNetwork(network);

            if (candidateNetworkLayer.getChildren().size() > 0) {
                toggleCandidateNetworkDisplay(false);
                toggleCandidateNetworkDisplay(true);
            }
        }
    }

    public void toggleSourceDisplay(boolean show) {
        if (show && data != null) {
            for (Source source : data.getSources()) {
                double[] rawXYLocation = data.cellLocationToRawXY(source.getCellNum());
                Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), 5 / gui.getScale());
                c.setStroke(Color.SALMON);
                c.setFill(Color.SALMON);
                sourceLocationsLayer.getChildren().add(c);

                ContextMenu nodeMenu = new ContextMenu();
                MenuItem viewData = new MenuItem("View Data");
                viewData.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        String text = "Source - " + source.getID() + ": " + source.getLabel() + "\n";
                        text += "  Capture Cost: " + source.getCaptureCost() + "\n";
                        text += "  Annual Capacity: " + source.getMaxProductionRate() + "\n";
                        text += "  Cell Number: " + source.getCellNum() + "\n";
                        messenger.setText(text);
                    }
                });
                nodeMenu.getItems().add(viewData);
                MenuItem connectTo = new MenuItem("Connect To...");
                connectTo.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        if (newEdgeV1 == 0) {
                            newEdgeV1 = selectedCell;
                        } else {
                            addEdge(newEdgeV1, selectedCell);
                        }
                    }
                });
                nodeMenu.getItems().add(connectTo);
                c.setOnMousePressed(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent e) {
                        if (e.isSecondaryButtonDown()) {
                            if (selectedEntity != null) {
                                if (selectedEntity.getClass() == new Circle().getClass()) {
                                    ((Circle) selectedEntity).setRadius(5.0 / gui.getScale());
                                } else if (selectedEntity.getClass() == new javafx.scene.shape.Path().getClass()) {
                                    ((javafx.scene.shape.Path) selectedEntity).setStrokeWidth(3.0 / gui.getScale());
                                }
                            }
                            c.setRadius(10.0 / gui.getScale());
                            nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                            selectedEntity = c;
                            selectedCell = displayXYToVectorized(c.getCenterX(), c.getCenterY());
                        }
                    }
                });
            }
        } else {
            sourceLocationsLayer.getChildren().clear();
            sourceLabelsLayer.getChildren().clear();
        }
    }

    public void toggleSourceLabels(boolean show) {
        if (show && data != null) {
            for (Source source : data.getSources()) {
                Label l = new Label(source.getID() + "");
                double[] rawXYLocation = data.cellLocationToRawXY(source.getCellNum());
                l.setTranslateX(rawXtoDisplayX(rawXYLocation[0]) + 0.25);
                l.setTranslateY(rawXtoDisplayX(rawXYLocation[1]) + 0.25);
                sourceLabelsLayer.getChildren().add(l);
            }
        } else {
            sourceLabelsLayer.getChildren().clear();
        }
    }

    public void toggleSinkDisplay(boolean show) {
        if (show && data != null) {
            for (Sink sink : data.getSinks()) {
                double[] rawXYLocation = data.cellLocationToRawXY(sink.getCellNum());
                Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), 5 / gui.getScale());
                c.setStroke(Color.CORNFLOWERBLUE);
                c.setFill(Color.CORNFLOWERBLUE);
                sinkLocationsLayer.getChildren().add(c);

                ContextMenu nodeMenu = new ContextMenu();
                MenuItem viewData = new MenuItem("View Data");
                viewData.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        String text = "Sink - " + sink.getID() + ": " + sink.getLabel() + "\n";
                        text += "  Injection Cost: " + sink.getInjectionCost() + "\n";
                        text += "  Total Capacity: " + sink.getCapacity() + "\n";
                        text += "  Cell Number: " + sink.getCellNum() + "\n";
                        messenger.setText(text);
                    }
                });
                nodeMenu.getItems().add(viewData);
                MenuItem connectTo = new MenuItem("Connect To...");
                connectTo.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        if (newEdgeV1 == 0) {
                            newEdgeV1 = selectedCell;
                        } else {
                            addEdge(newEdgeV1, selectedCell);
                        }
                    }
                });
                nodeMenu.getItems().add(connectTo);
                c.setOnMousePressed(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent e) {
                        if (e.isSecondaryButtonDown()) {
                            if (selectedEntity != null) {
                                if (selectedEntity.getClass() == new Circle().getClass()) {
                                    ((Circle) selectedEntity).setRadius(5.0 / gui.getScale());
                                } else if (selectedEntity.getClass() == new javafx.scene.shape.Path().getClass()) {
                                    ((javafx.scene.shape.Path) selectedEntity).setStrokeWidth(3.0 / gui.getScale());
                                }
                            }
                            c.setRadius(10.0 / gui.getScale());
                            nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                            selectedEntity = c;
                            selectedCell = displayXYToVectorized(c.getCenterX(), c.getCenterY());
                        }
                    }
                });
            }
        } else {
            sinkLocationsLayer.getChildren().clear();
            sinkLabelsLayer.getChildren().clear();
        }
    }

    public void toggleSinkLabels(boolean show) {
        if (show && data != null) {
            for (Sink sink : data.getSinks()) {
                Label l = new Label(sink.getID() + "");
                double[] rawXYLocation = data.cellLocationToRawXY(sink.getCellNum());
                l.setTranslateX(rawXtoDisplayX(rawXYLocation[0]) + 0.25);
                l.setTranslateY(rawXtoDisplayX(rawXYLocation[1]) + 0.25);
                sinkLabelsLayer.getChildren().add(l);
            }
        } else {
            sinkLabelsLayer.getChildren().clear();
        }
    }

    public void processLeftClick(int cellNum) {
        getMessenger().clear();
        newEdgeV1 = 0;

        if (selectedEntity != null) {
            if (selectedEntity.getClass() == new Circle().getClass()) {
                ((Circle) selectedEntity).setRadius(5.0 / gui.getScale());
            } else if (selectedEntity.getClass() == new javafx.scene.shape.Path().getClass()) {
                ((javafx.scene.shape.Path) selectedEntity).setStrokeWidth(3.0 / gui.getScale());
            }
        }

        if (gui.addPoint.isSelected()) {
            addNode(cellNum);
        }
    }

    public void processRightClick(int cellNum) {
        String text = "Cell number: " + cellNum + "\n";
        text += "Lat/Long: " + (Math.round(data.cellToLocation(cellNum)[0] * 10000.0) / 10000.0) + ", " + (Math.round(data.cellToLocation(cellNum)[1] * 10000.0) / 10000.0);
        messenger.setText(text);
    }

    public void addNode(int cellNum) {
        double[] rawXYLocation = data.cellLocationToRawXY(cellNum);
        Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), 5 / gui.getScale());
        c.setStroke(Color.GREY);
        c.setFill(Color.GREY);

        // Temporarily add to set of sinks. Not saved if sink display is toggled.
        sinkLocationsLayer.getChildren().add(c);

        ContextMenu nodeMenu = new ContextMenu();
        MenuItem connectTo = new MenuItem("Connect To...");
        connectTo.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                if (newEdgeV1 == 0) {
                    newEdgeV1 = selectedCell;
                } else {
                    addEdge(newEdgeV1, selectedCell);
                }
            }
        });
        nodeMenu.getItems().add(connectTo);
        c.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent e) {
                if (e.isSecondaryButtonDown()) {
                    if (selectedEntity != null) {
                        if (selectedEntity.getClass() == new Circle().getClass()) {
                            ((Circle) selectedEntity).setRadius(5.0 / gui.getScale());
                        } else if (selectedEntity.getClass() == new javafx.scene.shape.Path().getClass()) {
                            ((javafx.scene.shape.Path) selectedEntity).setStrokeWidth(3.0 / gui.getScale());
                        }
                    }
                    c.setRadius(10.0 / gui.getScale());
                    nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                    selectedEntity = c;
                    selectedCell = displayXYToVectorized(c.getCenterX(), c.getCenterY());
                }
            }
        });
        gui.addPoint.setSelected(false);
    }

    public void addEdge(int cellNum1, int cellNum2) {
        newEdgeV1 = 0;
        int[] route = ((ArrayList<int[]>) (solver.dijkstra(cellNum1, new int[]{cellNum2}, .9999999)[0])).get(0);

        // Make new edge.
        Edge newEdge = new Edge(route[0], route[route.length - 1]);

        // Collect graph data.
        HashMap<Edge, Double> graphEdgeCosts = data.getGraphEdgeCosts();
        HashMap<Edge, Double> graphEdgeConstructionCosts = data.getGraphEdgeConstructionCosts();
        HashMap<Edge, Double> graphEdgeRightOfWayCosts = data.getGraphEdgeRightOfWayCosts();
        HashMap<Edge, int[]> graphEdgeRoutes = data.getGraphEdgeRoutes();

        // Find intersecting cells
        HashSet<Integer> cellsInRoute = new HashSet<>();
        for (int i = 0; i < route.length; i++) {
            cellsInRoute.add(route[i]);
        }
        HashSet<Integer> intersectingCells = new HashSet<>();
        HashSet<Edge> intersectingEdges = new HashSet<>();
        intersectingCells.add(route[0]);
        intersectingCells.add(route[route.length - 1]);
        for (int cell : route) {
            if (data.isSourceSink(cell)) {
                intersectingCells.add(cell);
            }
        }
        for (Edge edge : graphEdgeRoutes.keySet()) {
            int[] path = graphEdgeRoutes.get(edge);
            for (int i = 0; i < path.length; i++) {
                if (cellsInRoute.contains(path[i])) {
                    if (i > 0 && !cellsInRoute.contains(path[i - 1])) {
                        intersectingCells.add(path[i]);
                        intersectingEdges.add(edge);
                    }
                    if (i < path.length - 1 && !cellsInRoute.contains(path[i + 1])) {
                        intersectingCells.add(path[i]);
                        intersectingEdges.add(edge);
                    }
                    if (data.isSourceSink(path[i])) {
                        intersectingCells.add(path[i]);
                        intersectingEdges.add(edge);
                    }
                }
            }
        }
        intersectingEdges.add(newEdge);

        // Insert Costs.
        double conCost = 0;
        double rowCost = 0;
        for (int i = 1; i < route.length; i++) {
            conCost += data.getEdgeConstructionCost(route[i - 1], route[i]);
            rowCost += data.getEdgeRightOfWayCost(route[i - 1], route[i]);
        }
        graphEdgeCosts.put(newEdge, conCost + rowCost);
        graphEdgeConstructionCosts.put(newEdge, conCost);
        graphEdgeRightOfWayCosts.put(newEdge, rowCost);

        // Insert Route.
        graphEdgeRoutes.put(newEdge, route);

        // Insert junction vertex if applicable.
        for (int cell : new int[]{route[0], route[route.length - 1]}) {
            int[] graphVertices = data.getGraphVertices();
            boolean insert = true;
            int[] newGraphVertices = new int[graphVertices.length + 1];
            for (int j = 0; j < graphVertices.length; j++) {
                newGraphVertices[j] = graphVertices[j];
                if (graphVertices[j] == cell) {
                    insert = false;
                }
            }
            if (insert) {
                newGraphVertices[newGraphVertices.length - 1] = cell;
                Arrays.sort(newGraphVertices);
                data.setGraphVertices(newGraphVertices);
            }
        }

        for (int cell : intersectingCells) {
            HashSet<Edge> addedEdges = new HashSet<>();
            HashSet<Edge> removedEdges = new HashSet<>();
            for (Edge edge : intersectingEdges) {
                int[] edgeRoute = data.getGraphEdgeRoutes().get(edge);
                for (int i = 1; i < edgeRoute.length - 1; i++) {
                    if (edgeRoute[i] == cell) {
                        addedEdges.addAll(insertJunction(edge, cell));
                        removedEdges.add(edge);
                    }
                }
            }
            intersectingEdges.addAll(addedEdges);
            intersectingEdges.removeAll(removedEdges);
        }

        // Remove degree 2 vertices as applicable
        if (!data.isSourceSink(cellNum1) && data.getGraphNeighbors().get(cellNum1).size() == 2) {
            removeDegree2Vertex(cellNum1);
        }
        if (!data.isSourceSink(cellNum2) && data.getGraphNeighbors().get(cellNum2).size() == 2) {
            removeDegree2Vertex(cellNum2);
        }

        // Update display
        toggleCandidateNetworkDisplay(false);
        toggleCandidateNetworkDisplay(true);
    }

    private HashSet<Edge> insertJunction(Edge edge, int cell) {
        // Collect graph information.
        HashMap<Edge, Double> graphEdgeCosts = data.getGraphEdgeCosts();
        HashMap<Edge, Double> graphEdgeConstructionCosts = data.getGraphEdgeConstructionCosts();
        HashMap<Edge, Double> graphEdgeRightOfWayCosts = data.getGraphEdgeRightOfWayCosts();
        HashMap<Edge, int[]> graphEdgeRoutes = data.getGraphEdgeRoutes();
        int[] graphVertices = data.getGraphVertices();

        int[] oldRoute = graphEdgeRoutes.get(edge);
        int cellIndexInRoute = -1;
        for (int i = 0; i < oldRoute.length; i++) {
            if (oldRoute[i] == cell) {
                cellIndexInRoute = i;
            }
        }

        Edge newEdge1 = new Edge(oldRoute[0], cell);
        Edge newEdge2 = new Edge(cell, oldRoute[oldRoute.length - 1]);

        // Insert costs.
        double conCost = 0;
        double rowCost = 0;
        for (int i = 1; i <= cellIndexInRoute; i++) {
            conCost += data.getEdgeConstructionCost(oldRoute[i - 1], oldRoute[i]);
            rowCost += data.getEdgeRightOfWayCost(oldRoute[i - 1], oldRoute[i]);
        }
        graphEdgeCosts.put(newEdge1, conCost + rowCost);
        graphEdgeConstructionCosts.put(newEdge1, conCost);
        graphEdgeRightOfWayCosts.put(newEdge1, rowCost);

        conCost = 0;
        rowCost = 0;
        for (int i = cellIndexInRoute + 1; i < oldRoute.length - 1; i++) {
            conCost += data.getEdgeConstructionCost(oldRoute[i - 1], oldRoute[i]);
            rowCost += data.getEdgeRightOfWayCost(oldRoute[i - 1], oldRoute[i]);
        }
        graphEdgeCosts.put(newEdge2, conCost + rowCost);
        graphEdgeConstructionCosts.put(newEdge2, conCost);
        graphEdgeRightOfWayCosts.put(newEdge2, rowCost);

        // Insert route.
        int[] newRoute1 = new int[cellIndexInRoute + 1];
        for (int i = 0; i < newRoute1.length; i++) {
            newRoute1[i] = oldRoute[i];
        }
        graphEdgeRoutes.put(newEdge1, newRoute1);

        int[] newRoute2 = new int[oldRoute.length - cellIndexInRoute];
        for (int i = 0; i < newRoute2.length; i++) {
            newRoute2[i] = oldRoute[cellIndexInRoute + i];
        }
        graphEdgeRoutes.put(newEdge2, newRoute2);

        // Insert junction vertex if applicable.
        boolean insert = true;
        int[] newGraphVertices = new int[graphVertices.length + 1];
        for (int j = 0; j < graphVertices.length; j++) {
            newGraphVertices[j] = graphVertices[j];
            if (graphVertices[j] == cell) {
                insert = false;
            }
        }
        if (insert) {
            newGraphVertices[newGraphVertices.length - 1] = cell;
            Arrays.sort(newGraphVertices);
            data.setGraphVertices(newGraphVertices);
        }

        // Remove old edge
        graphEdgeCosts.remove(edge);
        graphEdgeConstructionCosts.remove(edge);
        graphEdgeRightOfWayCosts.remove(edge);
        graphEdgeRoutes.remove(edge);

        return new HashSet<>(Arrays.asList(newEdge1, newEdge2));
    }

    public void generateCandidateGraph(ChoiceBox networkChoice) {
        if (scenario != "") {
            String network = data.generateCandidateGraph();
            networkChoice.getSelectionModel().select(network);
        }
    }

    public void saveCandidateGraph(ChoiceBox networkChoice) {
        DateFormat dateFormat = new SimpleDateFormat("ddMMyyy-HHmmssss");
        Date date = new Date();
        String networkName = "Network-" + dateFormat.format(date);
        if(data != null) {
            data.saveCandidateGraph(networkName);
            networkChoice.getItems().add(networkName);
            networkChoice.getSelectionModel().select(networkName);
        }
    }
    
    public HashMap<Sink, Double> getRealizationStorageCapacities(int realizationNumber, Sink inputSink) {
        HashMap<Sink, Double> realizationStorageCapacities = new HashMap<>();
        String sinkPath = basePath + "/" + dataset + "/Scenarios/" + scenario + "/Sinks/Sinks.csv";
        Sink[] sinks = data.getSinks();
        
        try (BufferedReader br = new BufferedReader(new FileReader(sinkPath))) {
            br.readLine();
            br.readLine();
            br.readLine();
            String line = br.readLine();
            int i = 0;
            while(line != null) {
                String[] splitLine = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
//                if(splitLine.length < 50) {
//                    System.out.println("The getRealizationStorageCapacities buffered reader isn't working. POS");
//                }
                if(gui.getUnderUncertainty() && sinks[i].equals(inputSink)) {
                    //sinks[i].setCapacity(Double.parseDouble(splitLine[19+realizationNumber])); // 11 for Florida, 19 for California 
                    //System.out.println("splitline[19+" + realizationNumber + "] is " + splitLine[19+realizationNumber]);
//                    System.out.println("Sink " + sinks[i].getID() + " has splitline of lengh " + splitLine.length + " and elements: ");
//                    if(splitLine.length == 0) {
//                        System.out.println("Crappy splitline isn't working for sink " + sinks[i].getID());
//                    } else {
//                        for(int h = 0; h < splitLine.length; h++) {
//                            System.out.print(splitLine[h] + ", and ");
//                        }
//                        System.out.println("");
//                    }
                    realizationStorageCapacities.put(sinks[i], Double.parseDouble(splitLine[(20+realizationNumber)]));
                } 
                line = br.readLine();
                i++;
            }
            br.close();
        } catch(IOException e) {
            System.out.println("Couldn't get sink capacities for realization " + realizationNumber);
        }
        return realizationStorageCapacities;
    }
    
    public ArrayList<Double> getSinkStorageCapacities(Sink inputSink, int numbRealizations) {
        ArrayList<Double> storageCapacities = new ArrayList<>();
        String sinkPath = basePath + "/" + dataset + "/Scenarios/" + scenario + "/Sinks/Sinks.csv";
        Sink[] sinks = data.getSinks();
        
        try (BufferedReader br = new BufferedReader(new FileReader(sinkPath))) {
            br.readLine();
            br.readLine();
            br.readLine();
            String line = br.readLine();
            int i = 0;
            while(line != null) {
                String[] splitLine = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
                if(gui.getUnderUncertainty() && sinks[i].equals(inputSink)) {
                    for(int j = 0; j < numbRealizations; j++) {
                        storageCapacities.add(Double.parseDouble(splitLine[(20+j)]));
                    }
                } 
                line = br.readLine();
                i++;
            }
            if(storageCapacities.size() > numbRealizations) {
                System.out.println("The sink list is too long.");
            }
            br.close();
        } catch(IOException e) {
            System.out.println("Couldn't get sink capacities for realization ");
        }
        storageCapacities.sort(Comparator.naturalOrder());
        return storageCapacities;
    }
    
    public void setStorageCapacity(int baseNumb) {
        String sinkPath = basePath + "/" + dataset + "/Scenarios/" + scenario + "/Sinks/Sinks.csv";
        Sink[] sinks = data.getSinks();
        
        try (BufferedReader br = new BufferedReader(new FileReader(sinkPath))) {
            br.readLine();
            br.readLine();
            br.readLine();
            String line = br.readLine();
            int i = 0;
            while(line != null) {
                String[] splitLine = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
                if(gui.getUnderUncertainty()) {
                    sinks[i].setCapacity(Double.parseDouble(splitLine[19+baseNumb])); // 11 for Florida, 19 for California 
//                    System.out.println(sinks[i].getCapacity()); For testing
                } else {
                    sinks[i].setCapacity(Double.parseDouble(splitLine[2]));
                }
                line = br.readLine();
                i++;
            }
            br.close();
        } catch(IOException e) {
            
        }
    }

    // Calculates base solutions for an uncertainty problem
    public void calculateBaseSolutions(double interestRate, int numYears, double modelParamValue, String modelVersion, String solver) {
        if(scenario != "") {
            double crf = (interestRate * Math.pow(1 + interestRate, numYears)) / (Math.pow(1 + interestRate, numYears) - 1);
            data.setCrf(crf);
            data.setNumYears(numYears);
            data.setPipelineCapacities();
            data.setModelParam(modelParamValue);
            data.setInterestRate(interestRate);
            tempModelVersion = modelVersion;
            
            // Calculates the number or realizations/base solutions based on amount of sinks
            int numbOfRealizations = data.getSinks().length;
            baseSolutions = new Solution[numbOfRealizations];
            
            // Create the folders to hold base solutions
            run = "";
            DateFormat dateFormat = new SimpleDateFormat("ddMMyyy-HHmmssss");
            Date date = new Date();
            run += dateFormat.format(date);
            File umbrellaDirectory = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + modelVersion + run);
            umbrellaDirectory.mkdir();
            
            /* loops numbOfRealizations times and calculates a base solution 
            w/differences in pipeline routes, amounts stored, captured, and 
            transported on edges */
            for(int i = 1; i <= numbOfRealizations; i++) {
                File realizationDirectory = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + modelVersion + run + "/Realization-" + i);
                realizationDirectory.mkdir();
                currentPath = basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + modelVersion + run + "/Realization-" + i + "/BaseSolution";
                
                // Set data's sink total capacities according to uncertainty
                setStorageCapacity(i);
                
                if(solver.equals("CPLEX")) {
                    // Generate MPS file
                    if (modelVersion.equals("c") || modelVersion.equals("p")) {
                        MPSWriter.writeCapPriceMPS(data, modelVersion, null);
                    } else if (modelVersion.equals("ct") || modelVersion.equals("pt")) {
                        if (data.isSimplified()) {
                            MPSWriter.writeTimeMPS(data, modelVersion);
                        } else {
                            messenger.setText("Source/sink data not compatible with temporal model.");
                        }
                    }

                    // Run CPLEX
                    runCPLEX();
                    
                    /* Thread.sleep in use to avoid too many instances of cplex 
                    running and bogging system down at once, should implement
                    cleaner method of doing this*/
                    try {
                        Thread.sleep(570000); // sleep time should be modified on a per dataset basis - CPLEX can be fast or slow dep on size
                    } catch(InterruptedException e) {
                        System.out.println("An error occurred while computing solutions");
                    }
                }
            }
            
            System.out.println("Finished with base solutions");
        }
    }
    
    // Loads calculated base solutions into memory from file when solutions were calculated in same usage of SimCCS
    public void saveBaseSolutions() {
        if(scenario != null) {
            int numbOfRealizations = data.getSinks().length;
            if(baseSolutions == null) {
                baseSolutions = new Solution[numbOfRealizations];
            }
            int q = 0;
            for(int i = 1; i <= numbOfRealizations; i++) {
                currentPath = basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + tempModelVersion + run + "/Realization-" + i + "/BaseSolution";
                File checkFile = new File(currentPath);
                if(checkFile.exists()) {
                    baseSolutions[i-1] = data.loadSolution(currentPath, -1);
                    q = i;
                } else {
                    baseSolutions[i-1] = baseSolutions[q];
                }
            }
        }
    }
    
    // loads base solutions into memory for seperate uses of SimCCS
    public void saveBaseSolutions(String file) {        
        if(file != null && !file.equals("None")) {
            // sets the temporary model version
            if(file.startsWith("ct")) {
                tempModelVersion = "ct";
            } else if(file.startsWith("pt")) {
                tempModelVersion = "pt";
            } else if(file.startsWith("c")) {
                tempModelVersion = "c";
            } else if(file.startsWith("p")) {
                tempModelVersion = "p";
            } else {
                tempModelVersion = "c";
            }
            String solPath = basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file;
            if(basePath != "" && dataset != "" && scenario != "") {
                int g = 0;
                int m = 1;
                File f = new File(solPath + "/");
                for(File loopFile : f.listFiles()) {
                    if(loopFile.getName().contains("Realization")) {
                        g++;
//                        System.out.println("g: " + g);
                    }
                }
                
                numberOfBaseSolutions = g;
                
                if(baseSolutions == null) {
                    baseSolutions = new Solution[g];
                }
                
                for(File loopFile : f.listFiles()) {
                    if(loopFile.getName().contains("Realization")) {
                        currentPath = basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file + "/Realization-" + m + "/BaseSolution";
                        baseSolutions[m-1] = data.loadSolution(currentPath, -1);
                        m++;
                    }
                }
                
            }
        }
    }
    
    // Uses CPLEX to calculate solution for normal scenario not under uncertainty
    public void solveInstance(double interestRate, int numYears, double modelParamValue, String modelVersion, String solver) {
        // Error check
        if (scenario != "") {
            double crf = (interestRate * Math.pow(1 + interestRate, numYears)) / (Math.pow(1 + interestRate, numYears) - 1);
            data.setCrf(crf);
            data.setNumYears(numYears);
            data.setPipelineCapacities();
            data.setModelParam(modelParamValue);
            data.setInterestRate(interestRate);
            
            // Ensures the right capacities are set for the sinks
            setStorageCapacity(1);

            if (solver.equals("CPLEX")) {
                // Generate MPS file
                if (modelVersion.equals("c") || modelVersion.equals("p")) {
                    MPSWriter.writeCapPriceMPS(data, modelVersion, null);
                } else if (modelVersion.equals("ct") || modelVersion.equals("pt")) {
                    if (data.isSimplified()) {
                        MPSWriter.writeTimeMPS(data, modelVersion);
                    } else {
                        messenger.setText("Source/sink data not compatible with temporal model.");
                    }
                }

                // Run CPLEX
                runCPLEX();
            } else if (solver.equals("Greedy")) {
                if (modelVersion.equals("c")) {
                    greedyHeuristicSolve("g", crf, numYears, modelParamValue, 1, modelVersion);
                } else if (modelVersion.equals("p")) {
                    //controlActions.heuristicSolve(crfValue.getText(), yearValue.getText(), paramValue.getText(),numPairsValue.getText(), modelVersion);
                    runPriceSimulation(crf, numYears, modelParamValue, 1, modelVersion);
                } else if (modelVersion.equals("ct") || modelVersion.equals("pt")) {
                    messenger.setText("Heuristic does not support temporal models.");
                }

            } else if (solver.equals("LP")) {
                // Generate MPS file
                if (modelVersion.equals("c")) {
                    MPSWriter.writeFlowHeuristicMPS("flowCap.mps", data, crf, numYears, modelParamValue, basePath, dataset, scenario, modelVersion);
                } else if (modelVersion.equals("p")) {
                    //TODO
                } else if (modelVersion.equals("ct")) {
                    temporalHeuristicSolve("ct");
                } else if (modelVersion.equals("pt")) {
                    temporalHeuristicSolve("pt");
                }
            }
        } else {
            messenger.setText("Load dataset before attempting to solve.");
        }
    }
    
    // 45Q Simulation
    public void run45QSimulation() {
        
    }

    // Price simulation
    public void runPriceSimulation(double crf, int numYears, double inputPrice, int numPairs, String modelVersion) {
        // Load simulation parmeters.
        data.loadPriceConfiguration();
        double prices[] = data.getPriceConfiguration();
        if (prices == null) {
            prices = new double[]{inputPrice};
        }

        // Save original injection costs.
        Sink[] sinks = data.getSinks();
        double[] originalInjectionCosts = new double[sinks.length];
        for (int i = 0; i < sinks.length; i++) {
            originalInjectionCosts[i] = sinks[i].getInjectionCost();
        }

        // Create aggregation file.
        StringBuilder aggregateResults = new StringBuilder("");
        aggregateResults.append("CO2 Price,Captured Amount,# Opened Sources,# Opened Sinks,Network Length,");
        aggregateResults.append("Total Cost ($M/yr),Total Capture Cost ($M/yr),Total Transport Cost ($M/yr),Total Storage Cost ($M/yr),");
        aggregateResults.append("Total Unit Cost($/tCO2),Unit Capture Cost($/tCO2),Unit Transport Cost($/tCO2),Unit Storage Cost($/tCO2)\n");

        for (double price : prices) {
            // Set new injection costs.
            for (int i = 0; i < sinks.length; i++) {
                sinks[i].setInjectionCosts(new double[]{originalInjectionCosts[i] - price});
            }

            // Run solver.
            File solutionPriceDirectory = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + "price-" + price + "h");
            solutionPriceDirectory.mkdir();
            runGreedyHeuristic(crf, numYears, inputPrice, numPairs, modelVersion, solutionPriceDirectory);

            // Create shapefiles.
            Solution soln = data.loadGreedyHeuristicSolution(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + "price-" + price + "h");
            data.makeShapeFiles(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + "price-" + price + "h", soln);
            data.makeCandidateShapeFiles(basePath + "/" + dataset + "/Scenarios/" + scenario);
            data.makeSolutionFile(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + "price-" + price + "h", soln);

            // Update aggregation file.
            aggregateResults.append(price + "," + soln.getAnnualCaptureAmount() + "," + soln.getNumOpenedSources() + "," + soln.getNumOpenedSinks() + ",TBD,");
            aggregateResults.append(soln.getTotalCost() + "," + soln.getTotalAnnualCaptureCost() + "," + soln.getTotalAnnualTransportCost() + "," + soln.getTotalAnnualStorageCost() + ",");
            aggregateResults.append(soln.getUnitTotalCost() + "," + soln.getUnitCaptureCost() + "," + soln.getUnitTransportCost() + "," + soln.getUnitStorageCost() + "\n");
        }

        // Write aggregation file.
        data.makePriceAggregationFile(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/aggregateResults.csv", aggregateResults.toString());
    }

    public void temporalHeuristicSolve(String modelVersion) {
        //String modelVersion, DataStorer data, String basePath, String dataset, String scenario
        // Run heuristic
        SlopeScalingKim heuristic = new SlopeScalingKim();
        heuristic.run(modelVersion, data, basePath, dataset, scenario);
    }

    public void greedyHeuristicSolve(String heuristicVersion, double crf, int numYears, double capacityTarget, int numPairs, String modelVersion) {
        DateFormat dateFormat = new SimpleDateFormat("ddMMyyy-HHmmssss");
        Date date = new Date();
        String run = "";
        if (heuristicVersion.equals("g")) {
            run = "greedy" + dateFormat.format(date);
            File solutionDirectory = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + run);
            solutionDirectory.mkdir();
            runGreedyHeuristic(crf, numYears, capacityTarget, numPairs, modelVersion, solutionDirectory);
        } else if (heuristicVersion.equals("f")) {
        }
    }

    private void runGreedyHeuristic(double crf, int numYears, double capacityTarget, int numPairs, String modelVersion, File directory) {
        // Get model data
        data.setModelParam(capacityTarget);
        data.setCrf(crf);
        data.setNumYears(numYears);

        // Run heuristic
        GreedyHeuristic heuristic = new GreedyHeuristic(data);
        heuristic.solve(numPairs, modelVersion);

        // Save solution
        data.saveHeuristicSolution(directory, heuristic);
    }

    public void runCPLEX() {
        // Check if CPLEX exists.
        try {
            Runtime r = Runtime.getRuntime();
            r.exec("cplex");

            // Determine model version
            String mipPath = basePath + "/" + dataset + "/Scenarios/" + scenario + "/MIP/";
            String modelVersion = "";
            String run = "";
            String mpsFileName = "";
            for (File f : new File(mipPath).listFiles()) {
                if (f.getName().endsWith(".mps")) {
                    if (f.getName().startsWith("cap")) {
                        modelVersion = "c";
                        run = "cap";
                        mpsFileName = "cap.mps";
                    } else if (f.getName().startsWith("price")) {
                        modelVersion = "p";
                        run = "price";
                        mpsFileName = "price.mps";
                    } else if (f.getName().startsWith("timeC")) {
                        modelVersion = "ct";
                        run = "timeCap";
                        mpsFileName = "timeC.mps";
                    } else if (f.getName().startsWith("timeP")) {
                        modelVersion = "pt";
                        run = "timePrice";
                        mpsFileName = "timeP.mps";
                    } else if (f.getName().startsWith("flowCap")) {
                        modelVersion = "cf";
                        run = "flowCap";
                        mpsFileName = "flowCap.mps";
                    }
                }
            }

            // Copy mps file and make command files.
            DateFormat dateFormat = new SimpleDateFormat("ddMMyyy-HHmmssss");
            Date date = new Date();
            run += dateFormat.format(date);
            File solutionDirectory = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + modelVersion + run); // old version didn't have + modelVersion
            if(gui.getUnderUncertainty()) {
                solutionDirectory = new File(currentPath);
            }

            String os = System.getProperty("os.name");
            try {
                solutionDirectory.mkdir();

                // Copy MPS file into results file.
                mipPath += mpsFileName;

                Path from = Paths.get(mipPath);
                Path to = Paths.get(solutionDirectory + "/" + mpsFileName);
                Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);

                // Make OS script file and cplex commands file.
                if (os.toLowerCase().contains("mac")) {
                    PrintWriter cplexCommands = new PrintWriter(solutionDirectory + "/cplexCommands.txt");
                    cplexCommands.println("set logfile *");
                    cplexCommands.println("read " + solutionDirectory.getAbsolutePath() + "/" + mpsFileName);
                    cplexCommands.println("opt");
                    cplexCommands.println("write " + solutionDirectory.getAbsolutePath() + "/solution.sol");
                    cplexCommands.println("quit");
                    cplexCommands.close();

                    File osCommandsFile = new File(solutionDirectory + "/osCommands.sh");
                    PrintWriter osCommands = new PrintWriter(osCommandsFile);
                    osCommands.println("#!/bin/sh");
                    osCommands.println("cplex < " + solutionDirectory.getAbsolutePath() + "/cplexCommands.txt");
                    osCommands.close();
                    osCommandsFile.setExecutable(true);
                } else if (os.toLowerCase().contains("windows")) {
                    PrintWriter cplexCommands = new PrintWriter(solutionDirectory + "/cplexCommands.txt");
                    cplexCommands.println("read " + mpsFileName);
                    cplexCommands.println("opt");
                    cplexCommands.println("write solution.sol");
                    cplexCommands.println("quit");


                    cplexCommands.close();

                    File osCommandsFile = new File(solutionDirectory + "/osCommands.bat");
                    PrintWriter osCommands = new PrintWriter(osCommandsFile);
                    osCommands.println("@echo off");
                    osCommands.println("cplex < cplexCommands.txt");
                    osCommands.println("exit"); 
                    /* exit command only executes 
                    after cplex is done running and the solution file is 
                    written, then command prompt is closed */
                    osCommands.close();
                    osCommandsFile.setExecutable(true);
                }

                // Make solution sub directories.
                if (modelVersion.equals("ct") || modelVersion.equals("pt")) {
                    // Determine number of phases from MPS file.
                    int numPhases = data.determineNumPhases(mipPath);

                    for (int phase = 1; phase <= numPhases; phase++) {
                        File solutionSubDirectory = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + run + "/phase-" + phase);
                        solutionSubDirectory.mkdir();
                    }
                }
            } catch (FileNotFoundException e) {
            } catch (IOException e) {
            }

            try {
                if (os.toLowerCase().contains("mac")) {
                    String[] args = new String[]{"/usr/bin/open", "-a", "Terminal", solutionDirectory.getAbsolutePath() + "/osCommands.sh"};
                    ProcessBuilder pb = new ProcessBuilder(args);
                    pb.directory(solutionDirectory);
                    Process p = pb.start();
                } else if (os.toLowerCase().contains("windows")) {
                    String[] args = new String[]{"cmd.exe", "/C", "start", solutionDirectory.getAbsolutePath() + "/osCommands.bat"};
                    ProcessBuilder pb = new ProcessBuilder(args);
                    pb.directory(solutionDirectory);
                    Process p = pb.start();
                }
            } catch (IOException e) {
            }
        } catch (IOException e) {
            messenger.setText("Error: Make sure CPLEX is installed and in System PATH.");
        }
    }

    public void toggleRawDelaunayDisplay(boolean show) {
        if (show & data != null) {
            HashSet<int[]> delaunayEdges = data.getDelaunayEdges();
            for (int[] path : delaunayEdges) {
                for (int src = 0; src < path.length - 1; src++) {
                    int dest = src + 1;
                    double[] rawSrc = data.cellLocationToRawXY(path[src]);
                    double[] rawDest = data.cellLocationToRawXY(path[dest]);
                    double sX = rawXtoDisplayX(rawSrc[0]);
                    double sY = rawYtoDisplayY(rawSrc[1]);
                    double dX = rawXtoDisplayX(rawDest[0]);
                    double dY = rawYtoDisplayY(rawDest[1]);
                    Line edge = new Line(sX, sY, dX, dY);
                    edge.setStroke(Color.BROWN);
                    edge.setStrokeWidth(1.0 / gui.getScale());
                    edge.setStrokeLineCap(StrokeLineCap.ROUND);
                    rawDelaunayLayer.getChildren().add(edge);
                }
            }
        } else {
            rawDelaunayLayer.getChildren().clear();
        }
    }

    public void toggleCandidateNetworkDisplay(boolean show) {
        if (show && data != null) {
            HashSet<int[]> selectedRoutes = data.getGraphEdges();
            for (int[] route : selectedRoutes) {
                double[] rawSrc = data.cellLocationToRawXY(route[0]);
                double sX = rawXtoDisplayX(rawSrc[0]);
                double sY = rawYtoDisplayY(rawSrc[1]);
                MoveTo moveTo = new MoveTo(sX, sY);
                javafx.scene.shape.Path path = new javafx.scene.shape.Path(moveTo);
                path.setStrokeWidth(3.0 / gui.getScale());
                path.setStroke(Color.PURPLE);
                path.setStrokeLineCap(StrokeLineCap.ROUND);
                candidateNetworkLayer.getChildren().add(path);
                for (int src = 1; src < route.length; src++) {
                    double[] rawDest = data.cellLocationToRawXY(route[src]);
                    double dX = rawXtoDisplayX(rawDest[0]);
                    double dY = rawYtoDisplayY(rawDest[1]);
                    LineTo line = new LineTo(dX, dY);
                    path.getElements().add(line);
                }

                ContextMenu edgeMenu = new ContextMenu();
                MenuItem removeEdge = new MenuItem("Remove Edge");
                removeEdge.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        removeEdge(route);
                    }
                });
                edgeMenu.getItems().add(removeEdge);
                MenuItem connectTo = new MenuItem("Connect To...");
                connectTo.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        if (newEdgeV1 == 0) {
                            newEdgeV1 = selectedCell;
                        } else {
                            addEdge(newEdgeV1, selectedCell);
                        }
                    }
                });
                edgeMenu.getItems().add(connectTo);
                MenuItem edgeInfo = new MenuItem("Edge Info");
                edgeInfo.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        messenger.setText(route[0] + "<-->" + route[route.length-1]);
                    }
                });
                edgeMenu.getItems().add(edgeInfo);
                path.setOnMousePressed(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent e) {
                        if (e.isSecondaryButtonDown()) {
                            if (selectedEntity != null) {
                                if (selectedEntity.getClass() == new Circle().getClass()) {
                                    ((Circle) selectedEntity).setRadius(5.0 / gui.getScale());
                                } else if (selectedEntity.getClass() == new javafx.scene.shape.Path().getClass()) {
                                    ((javafx.scene.shape.Path) selectedEntity).setStrokeWidth(3.0 / gui.getScale());
                                }
                            }
                            path.setStrokeWidth(10.0 / gui.getScale());
                            edgeMenu.show(path, e.getScreenX(), e.getScreenY());
                            selectedEntity = path;

                            // Set selectedCell to be on path
                            int clickedCell = displayXYToVectorized(e.getX(), e.getY());
                            double[] clickedRowCol = data.cellLocationToRawXY(clickedCell);
                            int closestCell = 0;
                            double closestDistance = Double.MAX_VALUE;
                            for (int cell : route) {
                                double[] cellRowCol = data.cellLocationToRawXY(cell);
                                double deltaX = Math.abs(cellRowCol[0] - clickedRowCol[0]);
                                double deltaY = Math.abs(cellRowCol[1] - clickedRowCol[1]);
                                double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                                if (distance < closestDistance) {
                                    closestCell = cell;
                                    closestDistance = distance;
                                }
                            }
                            selectedCell = closestCell;
                        }
                    }
                });
            }
        } else {
            candidateNetworkLayer.getChildren().clear();
        }
    }

    public void removeEdge(int[] route) {
        //Collect pointers to graph data
        HashMap<Edge, Double> graphEdgeCosts = data.getGraphEdgeCosts();
        HashMap<Edge, Double> graphEdgeConstructionCosts = data.getGraphEdgeConstructionCosts();
        HashMap<Edge, Double> graphEdgeRightOfWayCosts = data.getGraphEdgeRightOfWayCosts();
        HashMap<Edge, int[]> graphEdgeRoutes = data.getGraphEdgeRoutes();

        int[] vertices = new int[]{route[0], route[route.length - 1]};

        // Remove edge
        Edge edge = new Edge(vertices[0], vertices[1]);
        graphEdgeCosts.remove(edge);
        if (graphEdgeConstructionCosts != null) {
            graphEdgeConstructionCosts.remove(edge);
        }
        if (graphEdgeRightOfWayCosts != null) {
            graphEdgeRightOfWayCosts.remove(edge);
        }
        graphEdgeRoutes.remove(edge);

        // Remove end point vertices if not src/snk and remaining degree would be 2
        for (int vertexCell : vertices) {
            HashMap<Integer, HashSet<Integer>> graphNeighbors = data.getGraphNeighbors();
            if (!data.isSourceSink(vertexCell) && graphNeighbors.get(vertexCell) != null && graphNeighbors.get(vertexCell).size() == 2) {
                removeDegree2Vertex(vertexCell);
            }
        }

        // Update display
        toggleCandidateNetworkDisplay(false);
        toggleCandidateNetworkDisplay(true);
    }

    public void removeDegree2Vertex(int cellNum) {
        //Collect pointers to graph data
        int[] graphVertices = data.getGraphVertices();
        HashMap<Edge, Double> graphEdgeCosts = data.getGraphEdgeCosts();
        HashMap<Edge, Double> graphEdgeConstructionCosts = data.getGraphEdgeConstructionCosts();
        HashMap<Edge, Double> graphEdgeRightOfWayCosts = data.getGraphEdgeRightOfWayCosts();
        HashMap<Edge, int[]> graphEdgeRoutes = data.getGraphEdgeRoutes();

        HashMap<Integer, HashSet<Integer>> graphNeighbors = data.getGraphNeighbors();
        ArrayList<Integer> neighbors = new ArrayList<Integer>(graphNeighbors.get(cellNum));

        // Other possible degree 2 vertices to remove
        ArrayList<Integer> otherVertices = new ArrayList<>();

        Edge edge1 = new Edge(cellNum, neighbors.get(0));
        Edge edge2 = new Edge(cellNum, neighbors.get(1));
        Edge newEdge = new Edge(neighbors.get(0), neighbors.get(1));

        // Calculate new edge cost
        double cost1 = graphEdgeCosts.get(edge1);
        double cost2 = graphEdgeCosts.get(edge2);
        double newCost = cost1 + cost2;

        // Only add new edge if there is not already one or existing is more expensive
        if (!graphEdgeCosts.containsKey(newEdge) || graphEdgeCosts.get(newEdge) >= newCost) {
            graphEdgeCosts.put(newEdge, newCost);

            // Construction cost
            if (graphEdgeConstructionCosts != null) {
                cost1 = graphEdgeConstructionCosts.get(edge1);
                cost2 = graphEdgeConstructionCosts.get(edge2);
                newCost = cost1 + cost2;
                graphEdgeConstructionCosts.put(newEdge, newCost);
            }

            // Right of way cost
            if (graphEdgeRightOfWayCosts != null) {
                cost1 = graphEdgeRightOfWayCosts.get(edge1);
                cost2 = graphEdgeRightOfWayCosts.get(edge2);
                newCost = cost1 + cost2;
                graphEdgeRightOfWayCosts.put(newEdge, newCost);
            }

            // Route
            int[] route1 = graphEdgeRoutes.get(edge1);
            int[] route2 = graphEdgeRoutes.get(edge2);
            int index1 = 0;
            int index2 = 0;
            if (route1[0] != cellNum) {
                index1 = route1.length - 1;
            }
            if (route2[0] != cellNum) {
                index2 = route2.length - 1;
            }
            int[] newRoute = new int[route1.length + route2.length - 1];
            for (int i = 0; i < route1.length; i++) {
                int index = i;
                if (index1 == 0) {
                    index = route1.length - 1 - i;
                }
                newRoute[i] = route1[index];
            }
            for (int i = 0; i < route2.length - 1; i++) {
                int index = i + 1;
                if (index2 != 0) {
                    index = route2.length - 2 - i;
                }
                newRoute[route1.length + i] = route2[index];
            }
            graphEdgeRoutes.put(newEdge, newRoute);
        } else {
            // If edge not added, check if remaining vertices are degree 2
            if (!data.isSourceSink(neighbors.get(0)) && graphNeighbors.get(neighbors.get(0)).size() == 3) {
                otherVertices.add(neighbors.get(0));
            }
            if (!data.isSourceSink(neighbors.get(1)) && graphNeighbors.get(neighbors.get(1)).size() == 3) {
                otherVertices.add(neighbors.get(1));
            }
        }

        // Remove old edges
        graphEdgeCosts.remove(edge1);
        graphEdgeCosts.remove(edge2);
        if (graphEdgeConstructionCosts != null) {
            graphEdgeConstructionCosts.remove(edge1);
            graphEdgeConstructionCosts.remove(edge2);
        }
        if (graphEdgeRightOfWayCosts != null) {
            graphEdgeRightOfWayCosts.remove(edge1);
            graphEdgeRightOfWayCosts.remove(edge2);
        }
        graphEdgeRoutes.remove(edge1);
        graphEdgeRoutes.remove(edge2);

        // Remove vertex from graph
        int[] newGraphVertices = new int[graphVertices.length - 1];
        int newIndex = 0;
        for (int i = 0; i < graphVertices.length; i++) {
            if (graphVertices[i] != cellNum) {
                newGraphVertices[newIndex] = graphVertices[i];
                newIndex++;
            }
        }
        data.setGraphVertices(newGraphVertices);

        for (int otherVertex : otherVertices) {
            removeDegree2Vertex(otherVertex);
        }
    }

    // initializes solution selection for normal scenario not under uncertainty
    public void initializeSolutionSelection(ChoiceBox runChoice) {
        if (basePath != "" && dataset != "" && scenario != "") {
            // Set initial datasets.
            File f = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results");
            ArrayList<String> solns = new ArrayList<>();
            solns.add("None");
            for (File file : f.listFiles()) {
                if(gui.getUnderUncertainty() && !gui.getUnderBaseUncertainty()) {
                    solns.add(file.getName());
                } else if (file.getName().endsWith("Agg")) {
                    solns.add(file.getName());
                } else if (file.isDirectory() && file.getName().charAt(0) != '.') {
                    boolean sol = false;
                    boolean mps = false;
                    for (File subFile : file.listFiles()) {
                        if (subFile.getName().endsWith(".sol")) {
                            sol = true;
                        } else if (subFile.getName().endsWith(".mps")) {
                            mps = true;
                        }
                        // Heuristic
                        if (subFile.getName().endsWith("solution.txt")) {
                            sol = true;
                            mps = true;
                        }
                    }
                    if (sol && mps) {
                        solns.add(file.getName());
                    }
                }
            }
            runChoice.setItems(FXCollections.observableArrayList(solns));
        }
    }
    
    // initializes folder to be used as parent folder for uncertainty solutions to display
    public void initializeSelectedParentFolder(ChoiceBox firstChoice) {
        if (basePath != "" && dataset != "" && scenario != "") { 
            // Set initial datasets.
            File f = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results");
            ArrayList<String> solns = new ArrayList<>();
            solns.add("None");
            for(File file : f.listFiles()) {
                solns.add(file.getName());
            }
            
            firstChoice.setItems(FXCollections.observableArrayList(solns));
        }
    }
    
    // initializes next folder down to use for uncertainty solutions to display
    public void initializeSelectedCategoryFolder(ChoiceBox secondChoice, String parentFolder) {
        if (basePath != "" && dataset != "" && scenario != "") {
            File f = new File(parentFolder + "/");
            ArrayList<String> solns = new ArrayList<>();
            solns.add("None");
            for(File file : f.listFiles()) {
                solns.add(file.getName());
            }
            
            secondChoice.setItems(FXCollections.observableArrayList(solns));
        }
    }
    
    // Selects folder containing different solutions under uncertainty
    public void selectParentFolder(String file, Label[] solutionValues) {
        gui.enableUncertaintySolutionChoiceMenu();
        solutionLayer.getChildren().clear();
        for (Label l : solutionValues) {
            l.setText("-");
        }

        if (file != null && !file.equals("None")) {
            String solutionPath = basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file;
            gui.enableRealizationCategoryChoiceMenu(solutionPath);
        }
    }
    
    // Selects folder containing realization (or base) solution
    public void selectCategoryFolder(String file, String parentPath, Label[] solutionValues) {
        for (Label l : solutionValues) {
            l.setText("-");
        }

        if (file != null && !file.equals("None")) {
            String solutionPath = parentPath + "/" + file;
        }
    }
    
    // selects solution for deterministic options
    public void selectSolution(String file, Label[] solutionValues) {
        gui.hideSubSolutionMenu();
        solutionLayer.getChildren().clear();
        for (Label l : solutionValues) {
            l.setText("-");
        }

        if (file != null && !file.equals("None")) {
            String solutionPath = basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file;

            // Determine solution loading method
            String solutionType = "";
            for (File f : new File(solutionPath).listFiles()) {
                String fileName = f.getName();
                if (fileName.endsWith(".mps")) {
                    solutionType = fileName.substring(0, fileName.length() - 4);
                } else if (fileName.equals("solution.txt")) {
                    solutionType = "greedy";
                }
            }

            if (solutionType.equals("greedy")) {
                Solution soln = data.loadGreedyHeuristicSolution(solutionPath);
                displaySolution(file, soln, solutionValues);
            } else if (solutionType.equals("flowCap")) {
                Solution soln = data.loadSolution(solutionPath, -1);
                displaySolution(file, soln, solutionValues);
            } else if (solutionType.equals("cap")) {
                Solution soln = data.loadSolution(solutionPath, -1);
                System.out.println(solutionPath);
                displaySolution(file, soln, solutionValues);
            } else if (solutionType.equals("price")) {
                Solution soln = data.loadSolution(solutionPath, -1);
                displaySolution(file, soln, solutionValues);
            } else if (solutionType.startsWith("time") || solutionType.contains("lp")) {
                gui.showSubSolutionMenu();

                // Populate sub directory
                int numPhases = 0;
                if (solutionType.startsWith("timeC_lp")) {
                    numPhases = data.determineNumPhases(solutionPath + "/timeC_lp.mps");
                } else if (solutionType.startsWith("timeP_lp")) {
                    numPhases = data.determineNumPhases(solutionPath + "/timeP_lp.mps");
                } else if (solutionType.startsWith("timeC")) {
                    numPhases = data.determineNumPhases(solutionPath + "/timeC.mps");
                } else if (solutionType.startsWith("timeP")) {
                    numPhases = data.determineNumPhases(solutionPath + "/timeP.mps");
                }

                ArrayList<String> solns = new ArrayList<>();
                for (int phase = 1; phase <= numPhases; phase++) {
                    solns.add("phase-" + phase);
                }
                gui.getSolutionChoice().setItems(FXCollections.observableArrayList(solns));
            } else {
                Solution soln = data.loadSolution(solutionPath, -1);
                displaySolution(file, soln, solutionValues);
            }
        }
    }

    // selects the subsolution for deterministic setup with time factored in
    public void selectSubSolution(String parent, String solutionName, Label[] solutionValues) {
        if (solutionName != null && solutionName.contains("phase-")) {
            int phase = Integer.parseInt(solutionName.substring(6)) - 1;
            String solutionPath = basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + parent;
            
            Solution soln;
            if (parent.toLowerCase().contains("lp")) {
                soln = data.loadSolution(solutionPath, phase, "BESTsolution.sol");
            } else {
                soln = data.loadSolution(solutionPath, phase);
            }
            
            displaySolution(parent + "/" + solutionName, soln, solutionValues);
        }
    }
    
    // Selects solution for base uncertainty model
    public void selectBaseUncertaintySolution(String file, String parentFolder, Label[] solutionValues) {
        if (file != null && !file.equals("None")) {
            String solutionPath = parentFolder + "/" + file;
            
            // Determine solution loading method
            String solutionType = "";
            for (File f : new File(solutionPath).listFiles()) {
                String fileName = f.getName();
                if (fileName.endsWith(".mps")) {
                    solutionType = fileName.substring(0, fileName.length() - 4);
                } else if (fileName.equals("solution.txt")) {
                    solutionType = "greedy";
                }
            }

            if (solutionType.equals("greedy")) {
                Solution soln = data.loadGreedyHeuristicSolution(solutionPath);
                displaySolution(file, soln, solutionValues);
            } else if (solutionType.equals("flowCap")) {
                Solution soln = data.loadSolution(solutionPath, -1);
                displaySolution(file, soln, solutionValues);
            } else if (solutionType.equals("cap")) {
                Solution soln = data.loadSolution(solutionPath, -1);
                displaySolution(file, soln, solutionValues);
            } else if (solutionType.equals("price")) {
                Solution soln = data.loadSolution(solutionPath, -1);
                displaySolution(file, soln, solutionValues);
            } else {
                Solution soln = data.loadSolution(solutionPath, -1);
                displaySolution(file, soln, solutionValues);
            }
        }
    }
    
    // Called in Gui class, ensures necessary data is loaded, then calls method to display vanilla heat map
    public void selectHeatMapSolutions(String file, Label[] solutionValues) {
        // Loads base solutions
        saveBaseSolutions(file);
        
        if(baseSolutions != null) {
            // Ensures heat map data is calculated
            if(!hmDataIsCalculated) {
                calculateHeatMapData(); 
            }
            
            displayHeatMapSolution(file, baseSolutions[0], solutionValues);
        }
    }
    
    // Calculates hashmaps needed to store data about edges for use in displaying heat map solutions
    public void calculateHeatMapData() {
        // need to properly comment and explain these
        hmEdges = new HashMap<>();
        hmEdgesAveVol = new HashMap<>();
        hmEdgesAveTrend = new HashMap<>();
        hmEdgesAveCost = new HashMap<>();
        hmEdgesMax = new HashMap<>();
        hmSources = new HashMap<>();
        hmSourcesPercentCap = new HashMap<>();
        hmSourcesCaptureAmnt = new HashMap<>();
        hmSourcesCaptureCost = new HashMap<>();
        hmSourcesAnnualCap = new HashMap<>();
        hmSourcesMax = new HashMap<>();
        hmSinks = new HashMap<>();
        hmSinksPercentStored = new HashMap<>();
        hmSinksTotalCap = new HashMap<>();
        hmSinksAnnualStored = new HashMap<>();
        hmSinksInjectionCost = new HashMap<>();
        hmSinksMin = new HashMap<>();
        hmSinksAmountLimit = new HashMap<>();
        hmSinksStorageList = new HashMap<>();
        hmSinksSolutionNumber = new HashMap<>();
        
        
        if(gui.getUnderUncertainty() && !gui.getUnderBaseUncertainty()) {
//            System.out.println("Base Solutions Length: " + baseSolutions.length);
            int solutionNumber = 0;
            for(Solution solution : baseSolutions) {
                for (Edge edge : solution.getOpenedEdges()) {
                    /* Go through all edges, add them to an arraylist or hashset
                    or something, paired along with how many times an edge is
                    included in the list. Then do the below loop using that
                    list and use those numbers as the scalar for edge, source, 
                    and sink intensity. */ 
                    int g = 1;
                    double f = 0.0;
                    int q = 0;
                    double m = 0.0;
                    
                    if(hmEdges.containsKey(edge)) {
                        // increase int value
                        g += hmEdges.get(edge);
                        f = hmEdgesAveVol.get(edge) + solution.getEdgeTransportAmounts().get(edge);
                        q = hmEdgesAveTrend.get(edge) + solution.getEdgeTrends().get(edge);
                        m = hmEdgesAveCost.get(edge) + solution.getEdgeCosts().get(edge);
                        hmEdges.put(edge, g);
                        hmEdgesAveVol.put(edge, f);
                        hmEdgesAveTrend.put(edge, q);
                        // repurposing hmEdgesAveCost to correspond with max idea
//                        hmEdgesAveCost.put(edge, m); // old version  
                        if(solution.getEdgeTransportAmounts().get(edge) > hmEdgesMax.get(edge)) {
                            hmEdgesMax.put(edge, solution.getEdgeTransportAmounts().get(edge));
                            hmEdgesAveCost.put(edge, solution.getEdgeCosts().get(edge));
                        }
                    } else {
                        // create entry
                        hmEdges.put(edge, g);
                        hmEdgesAveVol.put(edge, solution.getEdgeTransportAmounts().get(edge));
                        hmEdgesAveTrend.put(edge, solution.getEdgeTrends().get(edge));
                        hmEdgesAveCost.put(edge, solution.getEdgeCosts().get(edge));
                        hmEdgesMax.put(edge, solution.getEdgeTransportAmounts().get(edge));
                    }
                }
                
                // may need to add an overrided equals method to sources and sinks
                for(Source source : solution.getOpenedSources()) {
                    int g = 1;
                    double f = 0.0;
                    double m = 0.0;
                    double h = 0.0;
                    double q = 0.0;
                    if(hmSources.containsKey(source)) {
                        g += hmSources.get(source);
                        f = hmSourcesPercentCap.get(source) + solution.getPercentCaptured(source);
                        m = hmSourcesCaptureAmnt.get(source) + solution.getSourceCaptureAmounts().get(source);
                        h = hmSourcesCaptureCost.get(source) + source.getCaptureCost();
                        q = hmSourcesAnnualCap.get(source) + source.getMaxProductionRate();
                        //System.out.println("Source " + source.getID() + " calculate heat map data is " + source.getMaxProductionRate());
                        hmSources.put(source, g);
                        hmSourcesPercentCap.put(source, f);
                        hmSourcesCaptureAmnt.put(source, m);
                        // repurposing hmSourcesCaptureCost to test max
//                        hmSourcesCaptureCost.put(source, h); // old version
                        hmSourcesAnnualCap.put(source, q);
                        if(solution.getSourceCaptureAmounts().get(source) > hmSourcesMax.get(source)) {
                            hmSourcesMax.put(source, solution.getSourceCaptureAmounts().get(source));
                            hmSourcesCaptureCost.put(source, source.getCaptureCost());
                        }
                    } else {
                        hmSources.put(source, g);
                        hmSourcesPercentCap.put(source, solution.getPercentCaptured(source));
                        hmSourcesCaptureAmnt.put(source, solution.getSourceCaptureAmounts().get(source));
                        hmSourcesCaptureCost.put(source, source.getCaptureCost());
                        hmSourcesAnnualCap.put(source, source.getMaxProductionRate());
                        hmSourcesMax.put(source, solution.getSourceCaptureAmounts().get(source));
                    }
                }

                for(Sink sink : solution.getOpenedSinks()) {
                    int g = 1; 
                    double q, h, m, j;
                    q = 0.0;
                    h = 0.0;
                    m = 0.0;
                    if(hmSinks.containsKey(sink)) {
                        g += hmSinks.get(sink);
                        q = hmSinksPercentStored.get(sink) + solution.getPercentStored(sink);
                        h = hmSinksTotalCap.get(sink) + sink.getCapacity();
                        m = hmSinksAnnualStored.get(sink) + solution.getSinkStorageAmounts().get(sink);
                        j = hmSinksInjectionCost.get(sink) + solution.getSinkCosts().get(sink);
                        hmSinks.put(sink, g);
                        hmSinksPercentStored.put(sink, q);
                        hmSinksTotalCap.put(sink, h);
                        hmSinksAnnualStored.put(sink, m);
                        // repurposing hmSinksInjectionCost to test min
//                        hmSinksInjectionCost.put(sink, j); // old version
                        if(solution.getSinkStorageAmounts().get(sink) < hmSinksMin.get(sink)) {
                            hmSinksMin.put(sink, solution.getSinkStorageAmounts().get(sink));
                            hmSinksInjectionCost.put(sink, sink.getInjectionCost());
                        }
                        hmSinksStorageList.get(sink).add(solution.getSinkStorageAmounts().get(sink));
                        hmSinksStorageList.put(sink, hmSinksStorageList.get(sink));
                        hmSinksSolutionNumber.get(sink).add(solutionNumber);
                        hmSinksSolutionNumber.put(sink, hmSinksSolutionNumber.get(sink));
                    } else {
                        hmSinks.put(sink, g);
                        hmSinksPercentStored.put(sink, solution.getPercentStored(sink));
                        hmSinksTotalCap.put(sink, sink.getCapacity());
                        hmSinksAnnualStored.put(sink, solution.getSinkStorageAmounts().get(sink));
                        hmSinksInjectionCost.put(sink, sink.getInjectionCost());
                        hmSinksMin.put(sink, solution.getSinkStorageAmounts().get(sink));
                        hmSinksAmountLimit.put(sink, solution.getSinkStorageAmounts().get(sink));
                        ArrayList<Double> sinkStorageList = new ArrayList<>();
                        sinkStorageList.add(solution.getSinkStorageAmounts().get(sink));
                        ArrayList<Integer> sinkSolutionNumberList = new ArrayList<>();
                        sinkSolutionNumberList.add(solutionNumber);
                        hmSinksStorageList.put(sink, sinkStorageList);
                        hmSinksSolutionNumber.put(sink, sinkSolutionNumberList);
                    }
                }
                solutionNumber++;
            }
            hmDataIsCalculated = true;
            createhmSinksAmountLimit();
        }
    }
    
    public void createhmSinksAmountLimit() {
        for(Sink sink : hmSinks.keySet()) {
            ArrayList<Double> sinkUncertaintyStorageVals = getSinkStorageCapacities(sink, numberOfBaseSolutions);
            double highestValTest = sinkUncertaintyStorageVals.get((int)Math.round((1 - gui.getHmPercentToDisp())*(sinkUncertaintyStorageVals.size()-1)));
            hmSinksAmountLimit.put(sink, highestValTest);
        }
    }
    
    private String determineSolutionSourceText(HashMap<Source, Double> hmSourcesFinal, Source source, Solution soln, boolean isBaseSolText, boolean isHeatMapSolText, boolean isMaxFlowText) {
        String sourceText = "Source: " + source.getLabel() + "\n";
        
        if(isBaseSolText) {
            sourceText += "  Capture Cost: " + source.getCaptureCost() + "\n";
//            sourceText += "  Opening Cost" + source.getOpeningCost(crf == ???) + "\n";
            sourceText += "  Annual Capacity: " + source.getMaxProductionRate() + "\n";
            sourceText += "  Cell Number: " + source.getCellNum() + "\n";
            sourceText += "  Annual Captured: " + (Math.round(soln.getSourceCaptureAmounts().get(source) * 1000.0) / 1000.0);
        } else if(isHeatMapSolText) {
            sourceText += "  Times Used: " + hmSources.get(source) + "\n";
            sourceText += "  Average Capture Cost: " + (hmSourcesCaptureCost.get(source) / (double)hmSources.get(source)) + "\n"; // Still need HashMaps for these variables
            sourceText += "  Average Annual Capacity: " + (hmSourcesAnnualCap.get(source) / (double)hmSources.get(source)) + "\n"; // may not need them actually, but they're here for now
            sourceText += "  Cell Number: " + source.getCellNum() + "\n"; // Not this one though
            sourceText += "  Average Annual Captured: " + (Math.round((hmSourcesCaptureAmnt.get(source) / (double)hmSources.get(source)) * 1000.0) / 1000.0); // originally 100, not 1000
        } else if(isMaxFlowText) {
            if(hmSources.containsKey(source)) {
                sourceText += "  Times Used: " + hmSources.get(source) + "\n";
            } else {
                sourceText += "  Times Used: 1 \n";
            }
            // No averaging division here because cplex calculations already account for this for max flow
            sourceText += "  Average Annual Captured: " + (Math.round((hmSourcesFinal.get(source)) * 1000.0) / 1000.0);
        }
        
        return sourceText;
    }
    
    private String determineSolutionSinkText(HashMap<Sink, Double> hmSinksFinal, Sink sink, Solution soln, boolean isBaseSolText, boolean isHeatMapSolText, boolean isMaxFlowText) {
        String sinkText = "Sink: " + sink.getLabel() + "\n";
        
        if(isBaseSolText) {
            sinkText += "  Injection Cost: " + sink.getInjectionCost() + "\n";
            sinkText += "  Total Capacity: " + sink.getCapacity() + "\n";
            sinkText += "  Cell Number: " + sink.getCellNum() + "\n";
            sinkText += "  Annual Stored: " + (Math.round(soln.getSinkStorageAmounts().get(sink) * 1000.0) / 1000.0) + "\n";
            sinkText += "  Total Stored: " + (Math.round(soln.getSinkStorageAmounts().get(sink) * soln.getProjectLength() * 1000) / 1000.0);
        } else if(isHeatMapSolText) {
            sinkText += "  Times Used: " + hmSinks.get(sink) + "\n";
            sinkText += "  Injection Cost: " + sink.getInjectionCost() + "\n";
            sinkText += "  Average Total Capacity: " + (hmSinksTotalCap.get(sink) / (double)hmSinks.get(sink)) + "\n";
            sinkText += "  Cell Number: " + sink.getCellNum() + "\n";
            sinkText += "  Average Annual Stored: " + (Math.round(hmSinksAnnualStored.get(sink) * 1000.0 / (double)hmSinks.get(sink)) / 1000.0) + "\n";
            sinkText += "  Average Total Stored: " + (Math.round(hmSinksAnnualStored.get(sink) * soln.getProjectLength() * 1000 / (double)hmSinks.get(sink)) / 1000.0);
        } else if(isMaxFlowText) {
            if(hmSinks.containsKey(sink)) {
                sinkText += "  Times Used: " + hmSinks.get(sink) + "\n";
            } else {
                sinkText += "  Times Used: 1 \n";
            }
            // No averaging division here because cplex calculations already account for this for max flow
            sinkText += "  Average Annual Stored: " + (Math.round(hmSinksFinal.get(sink) * 1000.0) / 1000.0) + "\n";
        }
        
        return sinkText;
    }
    
    private String determineSolutionEdgeText(HashMap<Edge, Double> hmEdgesFinal, Edge edge, Solution soln, boolean isBaseSolText, boolean isHeatMapSolText, boolean isMaxFlowText) {
        String edgeText = "Edge: " + edge.toString() + "\n";
        
        if(isBaseSolText) {
            edgeText += "  Annual Volume: " + (Math.round(soln.getEdgeTransportAmounts().get(edge) * 1000.0) / 1000.0) + "\n";
            edgeText += "  Trend: " + soln.getEdgeTrends().get(edge) + "\n";
            edgeText += "  Annual Cost: " + (Math.round(soln.getEdgeCosts().get(edge) * 1000.0) / 1000.0);
        } else if(isHeatMapSolText) {
            edgeText += "  Number of Times Used: " + hmEdges.get(edge) + "\n";
            edgeText += "  Average Annual Volume: " + (Math.round((hmEdgesAveVol.get(edge) / (double)hmEdges.get(edge)) * 1000.0) / 1000.0) + "\n";
            edgeText += "  Average Trend: " + Math.round((double)hmEdgesAveTrend.get(edge) / (double)hmEdges.get(edge)) + "\n";
            edgeText += "  Average Annual Cost: " + ((Math.round((hmEdgesAveCost.get(edge) / (double)hmEdges.get(edge)) * 1000.0)) / 1000.0);
        } else if(isMaxFlowText) {
            edgeText += "  Number of Times Used: " + hmEdges.get(edge) + "\n";
            // No averaging division here because cplex calculations already account for this for max flow
            edgeText += "  Average Annual Volume: " + (Math.round((hmEdgesFinal.get(edge)) * 1000.0) / 1000.0) + "\n";
        }
        
        return edgeText;
    }
    
    public void calcSourceNode(Circle c, Arc arc, Source source, Solution soln, double[] rawXYLocation, int numbOfRealizations, boolean isHmDisp) {
        // New color scaling algorithm:
        int sourceTimesUsed = hmSources.get(source);
        float normalizedSourceTimesUsed = (float)((double)sourceTimesUsed / (double)numbOfRealizations);
        float redValue = Math.min(2.0f * (1.0f - normalizedSourceTimesUsed), 1.0f);
        float greenValue = Math.min(2.0f * normalizedSourceTimesUsed, 1.0f);
        Color color = new Color(redValue, greenValue, 0.0f, 1.0);
        
        // Value to scale heatmap sizes depending on number of uses
        double sizeScalarSS = 13.0;
        int colorScalarMult = 255 / numbOfRealizations;
        
        c.setStrokeWidth(0);
        c.setStroke(Color.SALMON);
        c.setFill(Color.SALMON);
        solutionLayer.getChildren().add(c);

        // Pie chart nodes.
        arc.setCenterX(rawXtoDisplayX(rawXYLocation[0]));
        arc.setCenterY(rawYtoDisplayY(rawXYLocation[1]));
        if(isHmDisp) {
            arc.setRadiusX((sizeScalarSS + 1.1) / gui.getScale()); // Originally 20 / gui.getScale()
            arc.setRadiusY((sizeScalarSS + 1.1) / gui.getScale()); // Originally 20 / gui.getScale()
        } else {
            arc.setRadiusX(20 / gui.getScale());
            arc.setRadiusY(20 / gui.getScale());
        }  
        arc.setStartAngle(0);
        if(isHmDisp) {
            if(hmSources.containsKey(source)) {
                //arc.setLength(hmSourcesPercentCap.get(source) / (double) hmSources.get(source) * 360); // may need to check in on
                arc.setLength(1 / (double) 1 * 360);
            } else {
                arc.setLength(1 / (double) 1 * 360); // may need to check in on
            }
        } else {
            arc.setLength(soln.getPercentCaptured(source) * 360);
        }
        arc.setStrokeWidth(0);
        arc.setType(ArcType.ROUND);
        if(isHmDisp) {
            if(hmSources.containsKey(source)) {
                //arc.setStroke(Color.rgb(hmSources.get(source) * colorScalarMult, 21, Math.abs(hmSources.get(source) * colorScalarMult - 15))); // Originally red
                //arc.setFill(Color.rgb(hmSources.get(source) * colorScalarMult, 21, Math.abs(hmSources.get(source) * colorScalarMult - 15))); // Originally red
                arc.setStroke(color);
                arc.setFill(color);
            } else {
                arc.setStroke(Color.rgb(1 * colorScalarMult, 21, 1 * colorScalarMult - 15)); // Originally red
                arc.setFill(Color.rgb(1 * colorScalarMult, 21, 1 * colorScalarMult - 15)); // Originally red
            }
        } else {
            arc.setStroke(Color.RED);
            arc.setFill(Color.RED);
        }
        // commented out code good for non-literature usage - better differentiate sources and sinks
//        Arc arc2 = new Arc();
//        arc2.setCenterX(rawXtoDisplayX(rawXYLocation[0]));
//        arc2.setCenterY(rawYtoDisplayY(rawXYLocation[1]));
//        arc2.setRadiusX(1.12 * (sizeScalarSS + 1.1) / gui.getScale());
//        arc2.setRadiusY(1.12 * (sizeScalarSS + 1.1) / gui.getScale());
//        arc2.setStrokeWidth(0);
//        arc2.setType(ArcType.ROUND);
//        arc2.setFill(Color.SALMON);
//        arc2.setFill(Color.SALMON);
//        arc2.setLength(1 / (double) 1 * 360);
//        solutionLayer.getChildren().add(arc2);
        solutionLayer.getChildren().add(arc);
    }
    
    public void calcSinkNode(Circle c, Arc arc, Sink sink, Solution soln, double[] rawXYLocation, int numbOfRealizations, boolean isHmDisp) {
        // New color scaling algorithm:
        int sourceTimesUsed = hmSinks.get(sink);
        float normalizedSourceTimesUsed = (float)((double)sourceTimesUsed / (double)numbOfRealizations);
        float redValue = Math.min(2.0f * (1 - normalizedSourceTimesUsed), 1.0f);
        float greenValue = Math.min(2.0f * (normalizedSourceTimesUsed), 1.0f);
        Color color = new Color(redValue, greenValue, 0.0f, 1.0);
        
        // Value to scale heatmap sizes depending on number of uses
        double sizeScalarSS = 13.0;
        int colorScalarMult = 255 / numbOfRealizations;
        
        c.setStrokeWidth(0);
        c.setStroke(Color.CORNFLOWERBLUE);
        c.setFill(Color.CORNFLOWERBLUE);
        solutionLayer.getChildren().add(c);

        // Pie chart nodes.
        arc.setCenterX(rawXtoDisplayX(rawXYLocation[0]));
        arc.setCenterY(rawYtoDisplayY(rawXYLocation[1]));
        if(isHmDisp) {
            arc.setRadiusX(sizeScalarSS / gui.getScale()); // Originally 20 / gui.getScale()
            arc.setRadiusY(sizeScalarSS / gui.getScale()); // Originally 20 / gui.getScale()
        } else {
            arc.setRadiusX(20 / gui.getScale());
            arc.setRadiusY(20 / gui.getScale());
        }
        arc.setStartAngle(0);
        if(isHmDisp) {
            if(hmSinks.containsKey(sink)) {
                //arc.setLength(hmSinksPercentStored.get(sink) / (double)hmSinks.get(sink) * 360);
                arc.setLength(1 / 1 * 360);
            } else {
                arc.setLength(1 / 1 * 360);
            }
        } else {
            arc.setLength(soln.getPercentStored(sink) * 360);
        }
        arc.setStrokeWidth(0);
        arc.setType(ArcType.ROUND);
        if(isHmDisp) {
            if(hmSinks.containsKey(sink)) {
                //arc.setStroke(Color.rgb(0, 0, Math.abs(hmSinks.get(sink) * colorScalarMult))); // Originally blue
                //arc.setFill(Color.rgb(0, 0, Math.abs(hmSinks.get(sink) * colorScalarMult))); // Originally blue
                arc.setStroke(color);
                arc.setFill(color);
            } else {
                arc.setStroke(Color.rgb(0, 0, 1 * colorScalarMult)); // Originally blue
                arc.setFill(Color.rgb(0, 0, 1 * colorScalarMult)); // Originally blue
            }
                
        } else {
            arc.setStroke(Color.BLUE);
            arc.setFill(Color.BLUE);
        }
        // commented out code good for non-literature usage - better differentiate sources and sinks
//        Arc arc2 = new Arc();
//        arc2.setCenterX(rawXtoDisplayX(rawXYLocation[0]));
//        arc2.setCenterY(rawYtoDisplayY(rawXYLocation[1]));
//        arc2.setRadiusX(1.05 * (sizeScalarSS + 1.1) / gui.getScale());
//        arc2.setRadiusY(1.05 * (sizeScalarSS + 1.1) / gui.getScale());
//        arc2.setStrokeWidth(0);
//        arc2.setType(ArcType.ROUND);
//        arc2.setFill(Color.CORNFLOWERBLUE);
//        arc2.setFill(Color.CORNFLOWERBLUE);
//        arc2.setLength(1 / (double) 1 * 360);
//        solutionLayer.getChildren().add(arc2);
        solutionLayer.getChildren().add(arc);
    }
    
    // displaySolution displays deterministic solutions
    public void displaySolution(String file, Solution soln, Label[] solutionValues) {
        solutionLayer.getChildren().clear();
        HashMap<Edge, int[]> graphEdgeRoutes = data.getGraphEdgeRoutes();
        if(numberOfBaseSolutions == 0) {
            numberOfBaseSolutions = 1;
        }
        
        for (Edge edge : soln.getOpenedEdges()) {
            int[] route = graphEdgeRoutes.get(edge);

            double[] rawSrc = data.cellLocationToRawXY(route[0]);
            double sX = rawXtoDisplayX(rawSrc[0]);
            double sY = rawYtoDisplayY(rawSrc[1]);
            MoveTo moveTo = new MoveTo(sX, sY);
            javafx.scene.shape.Path path = new javafx.scene.shape.Path(moveTo);
            path.setStrokeWidth(5.0 / gui.getScale());
            path.setStroke(Color.GREEN);
            path.setStrokeLineCap(StrokeLineCap.ROUND);
            solutionLayer.getChildren().add(path);
            for (int src = 1; src < route.length; src++) {
                double[] rawDest = data.cellLocationToRawXY(route[src]);
                double dX = rawXtoDisplayX(rawDest[0]);
                double dY = rawYtoDisplayY(rawDest[1]);
                LineTo line = new LineTo(dX, dY);
                path.getElements().add(line);
            }

            ContextMenu edgeMenu = new ContextMenu();
            MenuItem viewData = new MenuItem("View Data");
            viewData.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent e) {
                    messenger.setText(determineSolutionEdgeText(null, edge, soln, true, false, false));
                }
            });
            edgeMenu.getItems().add(viewData);
            path.setOnMousePressed(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent e) {
                    if (e.isSecondaryButtonDown()) {
                        edgeMenu.show(path, e.getScreenX(), e.getScreenY());
                    }
                }
            });
        }

        for (Source source : soln.getOpenedSources()) {
            double[] rawXYLocation = data.cellLocationToRawXY(source.getCellNum());
            Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), 20 / gui.getScale());
            Arc arc = new Arc();
            
            calcSourceNode(c, arc, source, soln, rawXYLocation, numberOfBaseSolutions, false);

            ContextMenu nodeMenu = new ContextMenu();
            MenuItem viewData = new MenuItem("View Data");
            viewData.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent e) {
                    messenger.setText(determineSolutionSourceText(null, source, soln, true, false, false));
                }
            });
            nodeMenu.getItems().add(viewData);
            c.setOnMousePressed(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent e) {
                    if (e.isSecondaryButtonDown()) {
                        nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                    }
                }
            });
            arc.setOnMousePressed(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent e) {
                    if (e.isSecondaryButtonDown()) {
                        nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                    }
                }
            });
        }

        for (Sink sink : soln.getOpenedSinks()) {
            double[] rawXYLocation = data.cellLocationToRawXY(sink.getCellNum());
            Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), 20 / gui.getScale());
            Arc arc = new Arc();
            
            calcSinkNode(c, arc, sink, soln, rawXYLocation, numberOfBaseSolutions, false);

            ContextMenu nodeMenu = new ContextMenu();
            MenuItem viewData = new MenuItem("View Data");
            viewData.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent e) {
                    messenger.setText(determineSolutionSinkText(null, sink, soln, true, false, false));
                }
            });
            nodeMenu.getItems().add(viewData);
            c.setOnMousePressed(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent e) {
                    if (e.isSecondaryButtonDown()) {
                        nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                    }
                }
            });
            arc.setOnMousePressed(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent e) {
                    if (e.isSecondaryButtonDown()) {
                        nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                    }
                }
            });
        }
        
        // Update solution values.
        solutionValues[0].setText(Integer.toString(soln.getNumOpenedSources()));
        solutionValues[1].setText(Integer.toString(soln.getNumOpenedSinks()));
        solutionValues[2].setText(Double.toString(round(soln.getAnnualCaptureAmount(), 2)));
        solutionValues[3].setText(Integer.toString(soln.getNumEdgesOpened()));
        solutionValues[4].setText(Integer.toString(soln.getProjectLength()));
        solutionValues[5].setText(Double.toString(round(soln.getTotalAnnualCaptureCost(), 2)));
        solutionValues[6].setText(Double.toString(round(soln.getUnitCaptureCost(), 2)));
        solutionValues[7].setText(Double.toString(round(soln.getTotalAnnualTransportCost(), 2)));
        solutionValues[8].setText(Double.toString(round(soln.getUnitTransportCost(), 2)));
        solutionValues[9].setText(Double.toString(round(soln.getTotalAnnualStorageCost(), 2)));
        solutionValues[10].setText(Double.toString(round(soln.getUnitStorageCost(), 2)));
        solutionValues[11].setText(Double.toString(round(soln.getTotalCost(), 2)));
        solutionValues[12].setText(Double.toString(round(soln.getUnitTotalCost(), 2)));

        // Write to shapefiles.
        File solutionSubDirectory = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file);
        solutionSubDirectory.mkdir();
        data.makeShapeFiles(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file, soln);
        data.makeCandidateShapeFiles(basePath + "/" + dataset + "/Scenarios/" + scenario);
        data.makeSolutionFile(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file, soln);
    }
    
    // Displays generic heat map comprised of base solutions
    public void displayHeatMapSolution(String file, Solution soln, Label[] solutionValues) {
        solutionLayer.getChildren().clear();
        HashMap<Edge, int[]> graphEdgeRoutes = data.getGraphEdgeRoutes();
        double sizeScalarSS = 13.0;
        double sizeScalarEdge = 4.0;
        
        // Caculate display information and display network
        for (Source source : hmSources.keySet()) {
            if(((double)hmSources.get(source) / numberOfBaseSolutions) >= gui.getHmPercentToDisp()) {
                double[] rawXYLocation = data.cellLocationToRawXY(source.getCellNum());

                Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), sizeScalarSS / gui.getScale());
                Arc arc = new Arc();
                
                calcSourceNode(c, arc, source, soln, rawXYLocation, numberOfBaseSolutions, true);

                ContextMenu nodeMenu = new ContextMenu();
                MenuItem viewData = new MenuItem("View Data");
                viewData.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        messenger.setText(determineSolutionSourceText(null, source, soln, false, true, false));
                    }
                });
                nodeMenu.getItems().add(viewData);
                c.setOnMousePressed(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent e) {
                        if (e.isSecondaryButtonDown()) {
                            nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                        }
                    }
                });
                arc.setOnMousePressed(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent e) {
                        if (e.isSecondaryButtonDown()) {
                            nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                        }
                    }
                });
            }
        }

        for (Sink sink : hmSinks.keySet()) {
            if(((double)hmSinks.get(sink) / numberOfBaseSolutions) >= gui.getHmPercentToDisp()) {
                double[] rawXYLocation = data.cellLocationToRawXY(sink.getCellNum());
                // Originally 20 / gui.getScale()
                Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), sizeScalarSS / gui.getScale());
                Arc arc = new Arc();
                
                calcSinkNode(c, arc, sink, soln, rawXYLocation, numberOfBaseSolutions, true);

                ContextMenu nodeMenu = new ContextMenu();
                MenuItem viewData = new MenuItem("View Data");
                viewData.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        messenger.setText(determineSolutionSinkText(null, sink, soln, false, true, false));
                    }
                });

                nodeMenu.getItems().add(viewData);
                c.setOnMousePressed(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent e) {
                        if (e.isSecondaryButtonDown()) {
                            nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                        }
                    }
                });
                arc.setOnMousePressed(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent e) {
                        if (e.isSecondaryButtonDown()) {
                            nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                        }
                    }
                });
            }
        }

        for(Edge edge : hmEdges.keySet()) {
            if(((double)hmEdges.get(edge) / numberOfBaseSolutions) >= gui.getHmPercentToDisp()) {
                int[] route = graphEdgeRoutes.get(edge);

                double[] rawSrc = data.cellLocationToRawXY(route[0]);
                double sX = rawXtoDisplayX(rawSrc[0]);
                double sY = rawYtoDisplayY(rawSrc[1]);
                MoveTo moveTo = new MoveTo(sX, sY);
                javafx.scene.shape.Path path = new javafx.scene.shape.Path(moveTo);
                path.setStrokeWidth(sizeScalarEdge / gui.getScale());
                
                // New color scaling algorithm:
                int edgeTimesUsed = hmEdges.get(edge);
                float normalizedEdgeTimesUsed = (float)((double)edgeTimesUsed / (double)numberOfBaseSolutions);
                float redValue = Math.min(2.0f * (1.0f - normalizedEdgeTimesUsed), 1.0f);
                float greenValue = Math.min(2.0f * normalizedEdgeTimesUsed, 1.0f);
                Color color = new Color(redValue, greenValue, 0.0, 1.0);

                double intensScalCap = (100.0 / hmEdges.get(edge)) / 2;
                double colorVal = (double)hmEdges.get(edge) / numberOfBaseSolutions * 44;
                double intensVal = 0.5 + (double)hmEdges.get(edge) * intensScalCap / 100.0;
                double saturationVal = 1.0;
                if((double)hmEdges.get(edge) / 100.0 > 0.9) {
                    saturationVal = 0.04;
                    intensVal = 1.0;
                } else if((double)hmEdges.get(edge) / 100.0 > 0.82) {
                    saturationVal = 0.15;
                }else if((double)hmEdges.get(edge) / 100.0 > 0.75) {
                    saturationVal = 0.3;
                } else if((double)hmEdges.get(edge) / 100.0 > 0.6) {
                    saturationVal = 0.6;
                } else if((double)hmEdges.get(edge) / 100.0 > 0.5) {
                    saturationVal = 0.7;
                } else if((double)hmEdges.get(edge) / 100.0 > 0.25) {
                    saturationVal = 0.9;
                } else {
                    saturationVal = 1.0;
                }
                path.setStroke(color); 

                path.setStrokeLineCap(StrokeLineCap.ROUND);
                solutionLayer.getChildren().add(path);
                for (int src = 1; src < route.length; src++) {
                    double[] rawDest = data.cellLocationToRawXY(route[src]);
                    double dX = rawXtoDisplayX(rawDest[0]);
                    double dY = rawYtoDisplayY(rawDest[1]);
                    LineTo line = new LineTo(dX, dY);
                    path.getElements().add(line);
                }

                ContextMenu edgeMenu = new ContextMenu();
                MenuItem viewData = new MenuItem("View Data");
                viewData.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        messenger.setText(determineSolutionEdgeText(null, edge, soln, false, true, false));
                    }
                });
                edgeMenu.getItems().add(viewData);
                path.setOnMousePressed(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent e) {
                        if (e.isSecondaryButtonDown()) {
                            edgeMenu.show(path, e.getScreenX(), e.getScreenY());
                        }
                    }
                });
            }
        }       

        // Update solution values.
        solutionValues[0].setText(Integer.toString(soln.getNumOpenedSources()));
        solutionValues[1].setText(Integer.toString(soln.getNumOpenedSinks()));
        solutionValues[2].setText(Double.toString(round(soln.getAnnualCaptureAmount(), 2)));
        solutionValues[3].setText(Integer.toString(soln.getNumEdgesOpened()));
        solutionValues[4].setText(Integer.toString(soln.getProjectLength()));
        solutionValues[5].setText(Double.toString(round(soln.getTotalAnnualCaptureCost(), 2)));
        solutionValues[6].setText(Double.toString(round(soln.getUnitCaptureCost(), 2)));
        solutionValues[7].setText(Double.toString(round(soln.getTotalAnnualTransportCost(), 2)));
        solutionValues[8].setText(Double.toString(round(soln.getUnitTransportCost(), 2)));
        solutionValues[9].setText(Double.toString(round(soln.getTotalAnnualStorageCost(), 2)));
        solutionValues[10].setText(Double.toString(round(soln.getUnitStorageCost(), 2)));
        solutionValues[11].setText(Double.toString(round(soln.getTotalCost(), 2)));
        solutionValues[12].setText(Double.toString(round(soln.getUnitTotalCost(), 2)));

        // Write to shapefiles.
        File solutionSubDirectory = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file);
        solutionSubDirectory.mkdir();
        data.makeShapeFiles(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file, soln);
        data.makeCandidateShapeFiles(basePath + "/" + dataset + "/Scenarios/" + scenario);
        data.makeSolutionFile(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file, soln);
    }
    
    public void displayMaxFlowMinCostHeatMap(Label[] solutionValues) {
        System.out.println("Model Version: " + tempModelVersion); // test model version identification is working upon loading in data from file
        // Following 3 lines for display calculations
        solutionLayer.getChildren().clear();
        HashMap<Edge, int[]> graphEdgeRoutes = data.getGraphEdgeRoutes();
        int colorScalarMult = 255 / numberOfBaseSolutions; // scalar, controls network display color scaling
        // Value to scale heatmap sizes depending on number of uses
        double sizeScalarSS = 13.0;
        double sizeScalarEdge = 4.0;
        // initializes solution values
        this.solutionValues = solutionValues;
        // mfmc network edge trends
        mfmcFinalTrends = new HashMap<>();
        Edge[] candNetworkEdgesArray = new Edge[data.getGraphEdgeRoutes().size()];
        int candIter = 0;
        for(Edge edge : data.getGraphEdgeRoutes().keySet()) {
            candNetworkEdgesArray[candIter] = edge;
            candIter++;
        }
        
        if(gui.getUnderUncertainty() && !gui.getUnderBaseUncertainty()) {
            // calls populateCellNumbHMaps to ensure data is ready for HmMPSWriter class operations
            populateCellNumbHMaps();
            // Calculates the new values to for sink injectivity in hmFinal
            createhmSinksAmountLimit();
            
            // if cap mode, calculate max flow network, then max flow min cost network
            // if price mode, calculate min cost network
            if(tempModelVersion.equals("c")) {
                // New final version of max flow solver
                double interestRate = baseSolutions[0].getInterestRate();
                if(interestRate == 0) {
                    interestRate = 0.09;
                }
                double crf = (interestRate * Math.pow(1 + interestRate, baseSolutions[0].getProjectLength())) / (Math.pow(1 + interestRate, baseSolutions[0].getProjectLength()) - 1);
                int projectLength = baseSolutions[0].getProjectLength();
                double capAmnt = baseSolutions[0].getAnnualCaptureAmount();
                System.out.println("The number of hmSources and hmSinks are " + hmSources.keySet().size() + " and " + hmSinks.keySet().size() + ". ");
                Solution maxFlowSltn = MaxFlowSolverRevised.solve(data, crf, projectLength, capAmnt, numberOfBaseSolutions, gui.getHmPercentToDisp(), hmEdges, hmSources, hmSinks, hmSinksAmountLimit, hmCells);
                
                System.out.println("crf: " + crf); // printing to console forces calculations to finish before moving on
                
                double maxFlowInjectionAmount = maxFlowSltn.getAnnualCaptureAmount(); // for the revision
                mfmcInjectionAmount = maxFlowInjectionAmount;
                HashMap<Edge, Double> edgeConstructionCosts = data.getGraphEdgeConstructionCosts(); // create a hashmap with the edge construction cost for each edge
                LinearComponent[] linComp = data.getLinearComponents();
                Solution maxFlowMinCostSltn = MaxFlowMinCostSolverRevised.solve(data, crf, projectLength, maxFlowInjectionAmount, numberOfBaseSolutions, gui.getHmPercentToDisp(), hmEdges, hmSources, hmSinks, hmSinksAmountLimit, hmCells);

                hmEdgesFinal = maxFlowMinCostSltn.getEdgeTransportAmounts();
                hmSourcesFinal = maxFlowMinCostSltn.getSourceCaptureAmounts();
                hmSinksFinal = maxFlowMinCostSltn.getSinkStorageAmounts();

                double totalStored = 0.0;
                for(Sink sink : hmSinksFinal.keySet()) {
                    totalStored += hmSinksFinal.get(sink);
                }
                //totalStored /= projectLength;
                double totalVariableCost = 0.0;
                double sourceVariableCost = 0.0;
                double sinkVariableCost = 0.0;
                double edgeVariableCost = 0.0;
                for(Source source : hmSourcesFinal.keySet()) {
                    totalVariableCost += hmSourcesFinal.get(source) * source.getCaptureCost() * Math.pow(10.0, 6);
                    sourceVariableCost += hmSourcesFinal.get(source) * source.getCaptureCost() * Math.pow(10.0, 6);
                }
                for(Sink sink : hmSinksFinal.keySet()) {
                    totalVariableCost += hmSinksFinal.get(sink) * sink.getInjectionCost() * Math.pow(10.0, 6);
                    sinkVariableCost += hmSinksFinal.get(sink) * sink.getInjectionCost() * Math.pow(10.0, 6);
                }
                for(Edge edge : hmEdgesFinal.keySet()) {
                    double cost1 = linComp[0].getConSlope()*hmEdgesFinal.get(edge) + linComp[0].getConIntercept();
                    double cost2 = linComp[1].getConSlope()*hmEdgesFinal.get(edge) + linComp[1].getConIntercept();
                    if(cost1 <= cost2) {
                        totalVariableCost += cost1;
                        edgeVariableCost += cost1;
                    } else {
                        totalVariableCost += cost2;
                        edgeVariableCost += cost2;
                    }
                }
                
                solutionValues[0].setText(Integer.toString(hmSourcesFinal.size()));
                solutionValues[1].setText(Integer.toString(hmSinksFinal.size()));
                solutionValues[2].setText(Double.toString(Math.round(totalStored * 100.0) / 100.0));
                solutionValues[3].setText(Integer.toString(hmEdgesFinal.size()));
                solutionValues[4].setText("-");
                solutionValues[5].setText(Double.toString(Math.round(sourceVariableCost * 100.0) / 100.0));
                solutionValues[6].setText("-");
                solutionValues[7].setText(Double.toString(Math.round(edgeVariableCost * 100.0) / 100.0));
                solutionValues[8].setText("-");
                solutionValues[9].setText(Double.toString(Math.round(sinkVariableCost * 100.0) / 100.0));
                solutionValues[10].setText("-");
                solutionValues[11].setText(Double.toString(Math.round(totalVariableCost * 100.0) / 100.0));
                solutionValues[12].setText("-");
//                solutionValues[0].setText(Integer.toString(hmSourcesFinal.size()));
//                solutionValues[1].setText(Integer.toString(hmSinksFinal.size()));
//                solutionValues[2].setText(Double.toString(Math.round(maxFlowFinal.getTotalStored()*100.0)/100.0));
//                solutionValues[3].setText(Integer.toString(hmEdgesFinal.size())); 
//                solutionValues[5].setText(captureCost.substring(0, 5) + captureCost.substring(captureCost.length() - 2, captureCost.length()));
//                solutionValues[6].setText("-");
//                solutionValues[7].setText(Double.toString(Math.round(maxFlowFinal.getTransportCost()*100.0)/100.0));
//                solutionValues[8].setText("-");
//                solutionValues[9].setText(storageCost.substring(0, 5) + storageCost.substring(storageCost.length() - 2, storageCost.length()));
//                solutionValues[10].setText("-");
//                solutionValues[11].setText(totalCost.substring(0, 5) + totalCost.substring(totalCost.length() - 2, totalCost.length()));
//                solutionValues[12].setText("-");
                
//                System.out.println("Size of data.getGraphEdgeRoutes(): " + data.getGraphEdgeRoutes().size()); // debugging 
//                System.out.println("Size of hmEdges: " + hmEdges.size()); // debugging


                // maxFlowFinal.checkCapStrTotaled(); // Debug line   
            } else if(tempModelVersion.equals("p")) {
                // calculating variables to pass in for calculating edge costs
                double interestRate = baseSolutions[0].getInterestRate();
                // Ensures that if interest rate stored is 0, that it equals default amount
                // ^- Ensures 0 is not used as a divisor
                if(interestRate == 0) {
                    interestRate = 0.09;
                }
                double crf = (interestRate * Math.pow(1 + interestRate, baseSolutions[0].getProjectLength())) / (Math.pow(1 + interestRate, baseSolutions[0].getProjectLength()) - 1);
                HashMap<Edge, Double> edgeConstructionCosts = data.getGraphEdgeConstructionCosts(); // create a hashmap with the edge construction cost for each edge
                LinearComponent[] linComp = data.getLinearComponents();

                PriceMinCostSolver minCostPrice = new PriceMinCostSolver(hmEdges, hmSources, hmSinks,
                hmEdgesAveVol, hmCellsAndSources, hmCellsAndSinks, hmCells, gui, numberOfBaseSolutions,
                hmSourcesMax, hmSinksAmountLimit, basePath, dataset, scenario, crf, edgeConstructionCosts, linComp);
                minCostPrice.writeHeatMapMPS();

                hmEdgesFinal = minCostPrice.gethmEdgesFinal();
                hmSourcesFinal = minCostPrice.gethmSourcesFinal();
                hmSinksFinal = minCostPrice.gethmSinksFinal();

                // use max flow min cost network to update solution values
                String captureCost = Double.toString(minCostPrice.getCaptureCost());
                String storageCost = Double.toString(minCostPrice.getStorageCost());
                String totalCost = Double.toString(minCostPrice.getCaptureCost() + minCostPrice.getTransportCost() + minCostPrice.getStorageCost());
                solutionValues[0].setText(Integer.toString(hmSourcesFinal.size()));
                solutionValues[1].setText(Integer.toString(hmSinksFinal.size()));
                solutionValues[2].setText(Double.toString(Math.round(minCostPrice.getTotalStored()*100.0)/100.0));
                solutionValues[3].setText(Integer.toString(hmEdgesFinal.size())); 
                solutionValues[5].setText(captureCost.substring(0, 5) + captureCost.substring(captureCost.length() - 2, captureCost.length()));
                solutionValues[6].setText("-");
                solutionValues[7].setText(Double.toString(Math.round(minCostPrice.getTransportCost()*100.0)/100.0));
                solutionValues[8].setText("-");
                solutionValues[9].setText(storageCost.substring(0, 5) + storageCost.substring(storageCost.length() - 2, storageCost.length()));
                solutionValues[10].setText("-");
                solutionValues[11].setText(totalCost.substring(0, 5) + totalCost.substring(totalCost.length() - 2, totalCost.length()));
                solutionValues[12].setText("-");
            }
            
                  
            
            // Below code determines visual design of network display and displays said network
            // additional > 0 check in each loop overall if statement is to ensure items no longer in use do not display
            for (Source source : hmSourcesFinal.keySet()) {
                if(((double)hmSources.get(source) / numberOfBaseSolutions) >= gui.getHmPercentToDisp() && hmSourcesFinal.get(source) > 0.0) {
                    double[] rawXYLocation = data.cellLocationToRawXY(source.getCellNum());

                    Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), sizeScalarSS / gui.getScale());
                    Arc arc = new Arc();

                    calcSourceNode(c, arc, source, null, rawXYLocation, numberOfBaseSolutions, true);

                    ContextMenu nodeMenu = new ContextMenu();
                    MenuItem viewData = new MenuItem("View Data");
                    viewData.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent e) {
                            messenger.setText(determineSolutionSourceText(hmSourcesFinal, source, null, false, false, true));
                        }
                    });
                    nodeMenu.getItems().add(viewData);
                    c.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            if (e.isSecondaryButtonDown()) {
                                nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                            }
                        }
                    });
                    arc.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            if (e.isSecondaryButtonDown()) {
                                nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                            }
                        }
                    });
                }
            }
            
            for (Sink sink : hmSinksFinal.keySet()) {
                if(((double)hmSinks.get(sink) / numberOfBaseSolutions) >= gui.getHmPercentToDisp() && hmSinksFinal.get(sink) > 0.0) {
                    double[] rawXYLocation = data.cellLocationToRawXY(sink.getCellNum());
                    
                    Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), sizeScalarSS / gui.getScale());
                    Arc arc = new Arc();
                    
                    calcSinkNode(c, arc, sink, null, rawXYLocation, numberOfBaseSolutions, true);

                    ContextMenu nodeMenu = new ContextMenu();
                    MenuItem viewData = new MenuItem("View Data");
                    viewData.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent e) {
                            messenger.setText(determineSolutionSinkText(hmSinksFinal, sink, null, false, false, true));
                        }
                    });

                    nodeMenu.getItems().add(viewData);
                    c.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            if (e.isSecondaryButtonDown()) {
                                nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                            }
                        }
                    });
                    arc.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            if (e.isSecondaryButtonDown()) {
                                nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                            }
                        }
                    });
                    
                    c.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                        }
                    });
                    arc.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                        }
                    });
                }
            }
            
            for(Edge edge : hmEdgesFinal.keySet()) {
                if(((double)hmEdges.get(edge) / numberOfBaseSolutions) >= gui.getHmPercentToDisp() && hmEdgesFinal.get(edge) > 0.0) {
                    int[] route = graphEdgeRoutes.get(edge);

                    double[] rawSrc = data.cellLocationToRawXY(route[0]);
                    double sX = rawXtoDisplayX(rawSrc[0]);
                    double sY = rawYtoDisplayY(rawSrc[1]);
                    MoveTo moveTo = new MoveTo(sX, sY);
                    javafx.scene.shape.Path path = new javafx.scene.shape.Path(moveTo);
                    path.setStrokeWidth(sizeScalarEdge / gui.getScale());
                    
                    
                    // New color scaling algorithm:
                    int edgeTimesUsed = hmEdges.get(edge);
                    float normalizedEdgeTimesUsed = (float)((double)edgeTimesUsed / (double)numberOfBaseSolutions);
                    float redValue = Math.min(2.0f * (1.0f - normalizedEdgeTimesUsed), 1.0f);
                    float greenValue = Math.min(2.0f * normalizedEdgeTimesUsed, 1.0f);
                    Color color = new Color(redValue, greenValue, 0.0, 1.0);
                    
                    double intensScalCap = (100.0 / hmEdges.get(edge)) / 2;
                    double colorVal = (double)hmEdges.get(edge) / numberOfBaseSolutions * 44;
                    double intensVal = 0.5 + (double)hmEdges.get(edge) * intensScalCap / 100.0;
                    double saturVal = 1.0;
                    if((double)hmEdges.get(edge) / 100.0 > 0.9) {
                        saturVal = 0.04;
                        intensVal = 1.0;
                    } else if((double)hmEdges.get(edge) / 100.0 > 0.82) {
                        saturVal = 0.15;
                    } else if((double)hmEdges.get(edge) / 100.0 > 0.75) {
                        saturVal = 0.3;
                    } else if((double)hmEdges.get(edge) / 100.0 > 0.6) {
                        saturVal = 0.6;
                    } else if((double)hmEdges.get(edge) / 100.0 > 0.5) {
                        saturVal = 0.7;
                    } else if((double)hmEdges.get(edge) / 100.0 > 0.25) {
                        saturVal = 0.9;
                    } else {
                        saturVal = 1.0;
                    }
                    path.setStroke(color); 
                    
                    path.setStrokeLineCap(StrokeLineCap.ROUND);
                    solutionLayer.getChildren().add(path);
                    for (int src = 1; src < route.length; src++) {
                        double[] rawDest = data.cellLocationToRawXY(route[src]);
                        double dX = rawXtoDisplayX(rawDest[0]);
                        double dY = rawYtoDisplayY(rawDest[1]);
                        LineTo line = new LineTo(dX, dY);
                        path.getElements().add(line);
                    }

                    ContextMenu edgeMenu = new ContextMenu();
                    MenuItem viewData = new MenuItem("View Data");
                    viewData.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent e) {
                            messenger.setText(determineSolutionEdgeText(hmEdgesFinal, edge, null, false, false, true));
                        }
                    });
                    edgeMenu.getItems().add(viewData);
                    path.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            if (e.isSecondaryButtonDown()) {
                                edgeMenu.show(path, e.getScreenX(), e.getScreenY());
                            }
                        }
                    });
                }
            }  
        }
    }
    
    public void displayRobustToSinkFailureMap(Label[] solutionValues, double mfmcInjectionAmount, HashMap<Source, Double> mfmcSources, 
            HashMap<Sink, Double> mfmcSinks, HashMap<Edge, Double> mfmcEdges, HashMap<Edge, Integer[]> mfmcEdgeTrends, 
            HashMap<Edge, int[]> candidateNetworkEdges, Edge[] candidateNetworkEdgesArray, Sink failedSink) {
        
        System.out.println("Model Version: " + tempModelVersion); // test model version identification is working upon loading in data from file
        // Following 3 lines for display calculations
        solutionLayer.getChildren().clear();
        HashMap<Edge, int[]> graphEdgeRoutes = data.getGraphEdgeRoutes();
        int colorScalarMult = 255 / numberOfBaseSolutions; // scalar, controls network display color scaling
        // Value to scale heatmap sizes depending on number of uses
        double sizeScalarSS = 13.0;
        double sizeScalarEdge = 4.0;
        // initializes solution values
        this.solutionValues = solutionValues;
        // new cell hmaps
        populateFailureRobustCellHmaps();
        
        if(gui.getUnderUncertainty() && !gui.getUnderBaseUncertainty()) {
            // calls populateCellNumbHMaps to ensure data is ready for HmMPSWriter class operations
            populateCellNumbHMaps();
            // Calculates the new values to for sink injectivity in hmFinal
            createhmSinksAmountLimit();
            
            // if cap mode, calculate max flow network, then max flow min cost network
            // if price mode, calculate min cost network
            if(tempModelVersion.equals("c")) {
                // Uses max flow solution to calculate a min cost formulation
                // calculating variables to pass in for calculating edge costs
                double interestRate = baseSolutions[0].getInterestRate();
                // Ensures that if interest rate stored is 0, that it equals default amount
                // ^- Ensures 0 is not used as a divisor
                if(interestRate == 0) {
                    interestRate = 0.09;
                }
                double crf = (interestRate * Math.pow(1 + interestRate, baseSolutions[0].getProjectLength())) / (Math.pow(1 + interestRate, baseSolutions[0].getProjectLength()) - 1);
                HashMap<Edge, Double> edgeConstructionCosts = data.getGraphEdgeConstructionCosts(); // create a hashmap with the edge construction cost for each edge
                LinearComponent[] linComp = data.getLinearComponents();

                FailureRobustMaxFlowSolver maxFlowFinal = new FailureRobustMaxFlowSolver(hmEdges, hmSources, hmSinks, hmEdgesAveVol,
                fRCellsAndSources, fRCellsAndSinks, fRCells, gui, numberOfBaseSolutions, hmEdgesAveCost, hmSourcesMax, hmSinksAmountLimit, 
                basePath, dataset, scenario, crf, edgeConstructionCosts, linComp, mfmcSources, mfmcSinks, mfmcEdges, candidateNetworkEdges, 
                candidateNetworkEdgesArray, mfmcEdgeTrends, failedSink, mfmcInjectionAmount, data.getSources(), data.getSinks());
                maxFlowFinal.writeHeatMapMPS();
                
                maxFlowFinal.checkCapStrTotaled();

                hmEdgesFinal = maxFlowFinal.gethmEdgesFinal();
                hmSourcesFinal = maxFlowFinal.gethmSourcesFinal();
                hmSinksFinal = maxFlowFinal.gethmSinksFinal();

                // use max flow min cost network to update solution values
//                String captureCost = Double.toString(maxFlowFinal.getCaptureCost());
//                String storageCost = Double.toString(maxFlowFinal.getStorageCost());
//                String totalCost = Double.toString(maxFlowFinal.getCaptureCost() + maxFlowFinal.getTransportCost() + maxFlowFinal.getStorageCost());
//                solutionValues[0].setText(Integer.toString(hmSourcesFinal.size()));
//                solutionValues[1].setText(Integer.toString(hmSinksFinal.size()));
//                solutionValues[2].setText(Double.toString(Math.round(maxFlowFinal.getTotalStored()*100.0)/100.0));
//                solutionValues[3].setText(Integer.toString(hmEdgesFinal.size())); 
//                solutionValues[5].setText(captureCost.substring(0, 5) + captureCost.substring(captureCost.length() - 2, captureCost.length()));
//                solutionValues[6].setText("-");
//                solutionValues[7].setText(Double.toString(Math.round(maxFlowFinal.getTransportCost()*100.0)/100.0));
//                solutionValues[8].setText("-");
//                solutionValues[9].setText(storageCost.substring(0, 5) + storageCost.substring(storageCost.length() - 2, storageCost.length()));
//                solutionValues[10].setText("-");
//                solutionValues[11].setText(totalCost.substring(0, 5) + totalCost.substring(totalCost.length() - 2, totalCost.length()));
//                solutionValues[12].setText("-");
                
//                System.out.println("Size of data.getGraphEdgeRoutes(): " + data.getGraphEdgeRoutes().size()); // debugging 
//                System.out.println("Size of hmEdges: " + hmEdges.size()); // debugging


                // maxFlowFinal.checkCapStrTotaled(); // Debug line   
            } else if(tempModelVersion.equals("p")) {
                // calculating variables to pass in for calculating edge costs
//                double interestRate = baseSolutions[0].getInterestRate();
//                // Ensures that if interest rate stored is 0, that it equals default amount
//                // ^- Ensures 0 is not used as a divisor
//                if(interestRate == 0) {
//                    interestRate = 0.09;
//                }
//                double crf = (interestRate * Math.pow(1 + interestRate, baseSolutions[0].getProjectLength())) / (Math.pow(1 + interestRate, baseSolutions[0].getProjectLength()) - 1);
//                HashMap<Edge, Double> edgeConstructionCosts = data.getGraphEdgeConstructionCosts(); // create a hashmap with the edge construction cost for each edge
//                LinearComponent[] linComp = data.getLinearComponents();
//
//                PriceMinCostSolver minCostPrice = new PriceMinCostSolver(hmEdges, hmSources, hmSinks,
//                hmEdgesAveVol, hmCellsAndSources, hmCellsAndSinks, hmCells, gui, numberOfBaseSolutions,
//                hmSourcesMax, hmSinksAmountLimit, basePath, dataset, scenario, crf, edgeConstructionCosts, linComp);
//                minCostPrice.writeHeatMapMPS();
//
//                hmEdgesFinal = minCostPrice.gethmEdgesFinal();
//                hmSourcesFinal = minCostPrice.gethmSourcesFinal();
//                hmSinksFinal = minCostPrice.gethmSinksFinal();
//
//                // use max flow min cost network to update solution values
//                String captureCost = Double.toString(minCostPrice.getCaptureCost());
//                String storageCost = Double.toString(minCostPrice.getStorageCost());
//                String totalCost = Double.toString(minCostPrice.getCaptureCost() + minCostPrice.getTransportCost() + minCostPrice.getStorageCost());
//                solutionValues[0].setText(Integer.toString(hmSourcesFinal.size()));
//                solutionValues[1].setText(Integer.toString(hmSinksFinal.size()));
//                solutionValues[2].setText(Double.toString(Math.round(minCostPrice.getTotalStored()*100.0)/100.0));
//                solutionValues[3].setText(Integer.toString(hmEdgesFinal.size())); 
//                solutionValues[5].setText(captureCost.substring(0, 5) + captureCost.substring(captureCost.length() - 2, captureCost.length()));
//                solutionValues[6].setText("-");
//                solutionValues[7].setText(Double.toString(Math.round(minCostPrice.getTransportCost()*100.0)/100.0));
//                solutionValues[8].setText("-");
//                solutionValues[9].setText(storageCost.substring(0, 5) + storageCost.substring(storageCost.length() - 2, storageCost.length()));
//                solutionValues[10].setText("-");
//                solutionValues[11].setText(totalCost.substring(0, 5) + totalCost.substring(totalCost.length() - 2, totalCost.length()));
//                solutionValues[12].setText("-");
            }
            
                  
            
            // Below code determines visual design of network display and displays said network
            // additional > 0 check in each loop overall if statement is to ensure items no longer in use do not display
            for (Source source : hmSourcesFinal.keySet()) {
                if(hmSourcesFinal.get(source) >= 0.0) {
                    double[] rawXYLocation = data.cellLocationToRawXY(source.getCellNum());

                    Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), sizeScalarSS / gui.getScale());
                    Arc arc = new Arc();

                    calcSourceNode(c, arc, source, null, rawXYLocation, numberOfBaseSolutions, true);

                    ContextMenu nodeMenu = new ContextMenu();
                    MenuItem viewData = new MenuItem("View Data");
                    viewData.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent e) {
                            messenger.setText(determineSolutionSourceText(hmSourcesFinal, source, null, false, false, true));
                        }
                    });
                    nodeMenu.getItems().add(viewData);
                    c.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            if (e.isSecondaryButtonDown()) {
                                nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                            }
                        }
                    });
                    arc.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            if (e.isSecondaryButtonDown()) {
                                nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                            }
                        }
                    });
                }
            }
            
            for (Sink sink : hmSinksFinal.keySet()) {
                if(hmSinksFinal.get(sink) >= 0.0) {
                    double[] rawXYLocation = data.cellLocationToRawXY(sink.getCellNum());
                    
                    Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), sizeScalarSS / gui.getScale());
                    Arc arc = new Arc();
                    
                    calcSinkNode(c, arc, sink, null, rawXYLocation, numberOfBaseSolutions, true);

                    ContextMenu nodeMenu = new ContextMenu();
                    MenuItem viewData = new MenuItem("View Data");
                    viewData.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent e) {
                            messenger.setText(determineSolutionSinkText(hmSinksFinal, sink, null, false, false, true));
                        }
                    });

                    nodeMenu.getItems().add(viewData);
                    c.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            if (e.isSecondaryButtonDown()) {
                                nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                            }
                        }
                    });
                    arc.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            if (e.isSecondaryButtonDown()) {
                                nodeMenu.show(c, e.getScreenX(), e.getScreenY());
                            }
                        }
                    });
                }
            }
            
            for(Edge edge : hmEdgesFinal.keySet()) {
                if(hmEdgesFinal.get(edge) >= 0.0) {
                    int[] route = graphEdgeRoutes.get(edge);

                    double[] rawSrc = data.cellLocationToRawXY(route[0]);
                    double sX = rawXtoDisplayX(rawSrc[0]);
                    double sY = rawYtoDisplayY(rawSrc[1]);
                    MoveTo moveTo = new MoveTo(sX, sY);
                    javafx.scene.shape.Path path = new javafx.scene.shape.Path(moveTo);
                    path.setStrokeWidth(sizeScalarEdge / gui.getScale());
                    
                    double intensScalCap = 50.0;
                    
                    double colorVal = 1 / numberOfBaseSolutions * 44;
                    double intensVal = 0.5 + 1 * intensScalCap / 100.0;
                    double saturVal = 1.0;
                        if(1 / 100.0 > 0.9) {
                            saturVal = 0.04;
                            intensVal = 1.0;
                        } else if(1 / 100.0 > 0.82) {
                            saturVal = 0.15;
                        } else if(1 / 100.0 > 0.75) {
                            saturVal = 0.3;
                        } else if(1 / 100.0 > 0.6) {
                            saturVal = 0.6;
                        } else if(1 / 100.0 > 0.5) {
                            saturVal = 0.7;
                        } else if(1 / 100.0 > 0.25) {
                            saturVal = 0.9;
                        } else {
                            saturVal = 1.0;
                        }
                    if(hmEdges.containsKey(edge)) {
                        intensScalCap = (100.0 / hmEdges.get(edge)) / 2;
                        colorVal = (double)hmEdges.get(edge) / numberOfBaseSolutions * 44;
                        intensVal = 0.5 + (double)hmEdges.get(edge) * intensScalCap / 100.0;
                        if((double)hmEdges.get(edge) / 100.0 > 0.9) {
                            saturVal = 0.04;
                            intensVal = 1.0;
                        } else if((double)hmEdges.get(edge) / 100.0 > 0.82) {
                            saturVal = 0.15;
                        } else if((double)hmEdges.get(edge) / 100.0 > 0.75) {
                            saturVal = 0.3;
                        } else if((double)hmEdges.get(edge) / 100.0 > 0.6) {
                            saturVal = 0.6;
                        } else if((double)hmEdges.get(edge) / 100.0 > 0.5) {
                            saturVal = 0.7;
                        } else if((double)hmEdges.get(edge) / 100.0 > 0.25) {
                            saturVal = 0.9;
                        } else {
                            saturVal = 1.0;
                        }
                    }
                    path.setStroke(Color.hsb(colorVal, saturVal, intensVal)); 
                    
                    path.setStrokeLineCap(StrokeLineCap.ROUND);
                    solutionLayer.getChildren().add(path);
                    for (int src = 1; src < route.length; src++) {
                        double[] rawDest = data.cellLocationToRawXY(route[src]);
                        double dX = rawXtoDisplayX(rawDest[0]);
                        double dY = rawYtoDisplayY(rawDest[1]);
                        LineTo line = new LineTo(dX, dY);
                        path.getElements().add(line);
                    }

                    ContextMenu edgeMenu = new ContextMenu();
                    MenuItem viewData = new MenuItem("View Data");
                    viewData.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent e) {
                            messenger.setText(determineSolutionEdgeText(hmEdgesFinal, edge, null, false, false, true));
                        }
                    });
                    edgeMenu.getItems().add(viewData);
                    path.setOnMousePressed(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            if (e.isSecondaryButtonDown()) {
                                edgeMenu.show(path, e.getScreenX(), e.getScreenY());
                            }
                        }
                    });
                }
            }  
        }
    }
    
    // Self explanatory
    public double calculateTotalCO2Stored() {
        double co2Stored = 0.0;
        for(Sink sink : hmSinks.keySet()) {
            co2Stored += hmSinksAnnualStored.get(sink);
        }
        return co2Stored;
    }
    
    /* Calculates % of CO2 stored at base solutions appearing percentile 
    (think hmSlider) for usage in output files containing useful percentile info*/
    public double calculatePercentOfTotalCO2Stored(int percentile) {
        double percentStored = 0.0;
        double totalStored = calculateTotalCO2Stored();
        for(Sink sink : hmSinks.keySet()) {
            if(hmSinks.get(sink) * 100.0 / numberOfBaseSolutions >= percentile) {
                percentStored += hmSinksAnnualStored.get(sink);
            }
        }
        percentStored = percentStored * 100.0 / totalStored;
        return percentStored;
    }
    
    // Using quicksort pseudocode from gfg for time being - need to look over best sorting options later
    // Sorts a list of sources according to amount captured (largest to smallest)
    public void quickSortSourceList(Source[] sourceList, int low, int high) {
        Source[] srcList = sourceList;
        if(low < high) {
            int parIndx = sourcePartition(sourceList, low, high);
            
            quickSortSourceList(sourceList, low, parIndx - 1);
            quickSortSourceList(sourceList, parIndx + 1, high);
        }
    }
    
    public int sourcePartition(Source[] sourceList, int low, int high) {
        Source sourcePivot = sourceList[high];
        
        int i = low - 1;
        
        for(int j = low; j <= high - 1; j++) {
            if(hmSourcesCaptureAmnt.get(sourceList[j]) < hmSourcesCaptureAmnt.get(sourcePivot)) {
                i++;
                // swap sourceList[i] and sourceList[j]
                Source tempVal = sourceList[i];
                sourceList[i] = sourceList[j];
                sourceList[j] = tempVal;
            }
        }
        
        // swap sourceList[i+1] and sourceList[high]
        Source tempVal = sourceList[i+1];
        sourceList[i+1] = sourceList[high];
        sourceList[high] = tempVal;
        return i + 1;
    }
    
    // Sorts a list of sinks according to amount stored (largest to smallest)
    public void quickSortSinkList(Sink[] sinkList, int low, int high) {
        if(low < high) {
            int parIndx = sinkPartition(sinkList, low, high);
            
            quickSortSinkList(sinkList, low, parIndx - 1);
            quickSortSinkList(sinkList, parIndx + 1, high);
        }
    }
    
    public int sinkPartition(Sink[] sinkList, int low, int high) {
        Sink sinkPivot = sinkList[high];
        
        int i = low - 1;
        
        for(int j = low; j <= high - 1; j++) {
            if(hmSinksAnnualStored.get(sinkList[j]) < hmSinksAnnualStored.get(sinkPivot)) {
                i++;
                // swap sourceList[i] and sourceList[j]
                Sink tempVal = sinkList[i];
                sinkList[i] = sinkList[j];
                sinkList[j] = tempVal;
            }
        }
        
        // swap sourceList[i+1] and sourceList[high]
        Sink tempVal = sinkList[i+1];
        sinkList[i+1] = sinkList[high];
        sinkList[high] = tempVal;
        return i + 1;
    }
    
    public String getSortedSourcesList(int percentile) {
        String sourcesListString = "";
        Source[] sourcesList = new Source[hmSources.size()];
        int i = 0;
        // Copies sources hashmap to array
        for(Source source : hmSources.keySet()) {
            sourcesList[i] = source;
            i++;
        }
        
        // Sorts the array according to amount captured (smallest to largest)
        quickSortSourceList(sourcesList, 0, sourcesList.length - 1);
        
        // Creates a string containing the sorted list of sources
        // remember - want to display largest to smallest, so iterate backwards
        for(int q = sourcesList.length - 1; q >= 0; q--) {
            if((double)hmSources.get(sourcesList[q]) / numberOfBaseSolutions * 100.0 >= percentile) {
                sourcesListString += sourcesList[q].getLabel() + "; ";
            }
        }
        
        return sourcesListString;
    }
    
    public String getSortedSinksList(int percentile) {
        String sinksListString = "";
        Sink[] sinksList = new Sink[hmSinks.size()]; 
        int i = 0;
        // Copies sinks hashmap to array
        for(Sink sink : hmSinks.keySet()) {
            sinksList[i] = sink;
            i++;
        }
        
        // Sorts the array according to amount stored (largest to smallest)
        quickSortSinkList(sinksList, 0, sinksList.length - 1);
        
        // Creates a string containing the sorted list of sinks
        for(int q = sinksList.length - 1; q >= 0; q--) {
            // if amount in or above percentile list
            if((double)hmSinks.get(sinksList[q]) / numberOfBaseSolutions * 100.0 >= percentile) {
                sinksListString += sinksList[q].getLabel() + "; ";
            }
        }
        
        return sinksListString;
    }
    
    public String getEdgesList(int percentile) {
        String edgesList = "";
        for(Edge edge : hmEdges.keySet()) {
            if((double)hmEdges.get(edge) / numberOfBaseSolutions * 100.0 >= percentile) {
                edgesList += edge.toString() + "; ";
            }
        }
        return edgesList;
    }
    
    public void outputHeatMapData(String path) {
        String[] outputDocData = new String[6];
        
        for(int i = 0; i < outputDocData.length; i++) {
            outputDocData[i] = "";
            outputDocData[i] += calculateTotalCO2Stored() + ", ";
            outputDocData[i] += calculatePercentOfTotalCO2Stored(i * 20) + ", ";
            outputDocData[i] += "[" + getSortedSourcesList(i * 20) + "], ";
            outputDocData[i] += "[" + getSortedSinksList(i * 20) + "], ";
            outputDocData[i] += "[" + getEdgesList(i * 20) + "]";
        }
        data.setHeatMapOutputData(outputDocData);
        data.makeHeatMapInformationFile(path);
    }
    
    private double calculateMaxFlowCO2Stored() {
        double mFCO2Stored = 0.0;
        for(Sink sink : hmSinksFinal.keySet()) {
            mFCO2Stored += hmSinksFinal.get(sink);
        }
        return mFCO2Stored;
    }
    
    private double calculateMaxFlowCO2Cost() {
        double mFCO2Cost = 0.0;
        for(Source source : hmSourcesFinal.keySet()) {
            mFCO2Cost += hmSourcesCaptureCost.get(source)/(double)hmSources.get(source);
        }
        for(Sink sink : hmSinksFinal.keySet()) {
            mFCO2Cost += hmSinksInjectionCost.get(sink)/(double)hmSinks.get(sink);
        }
        for(Edge edge : hmEdgesFinal.keySet()) {
            mFCO2Cost += hmEdgesAveCost.get(edge)/(double)hmEdges.get(edge);
        }
        return mFCO2Cost;
    }
    
    private String getSourcesFinalList() {
        String sourcesFinalList = "";
        for(Source source : hmSourcesFinal.keySet()) {
            sourcesFinalList += source.getLabel() + "; ";
        }
        return sourcesFinalList;
    }
    
    private String getSinksFinalList() {
        String sinksFinalList = "";
        for(Sink sink : hmSinksFinal.keySet()) {
            sinksFinalList += sink.getLabel() + "; ";
        }
        return sinksFinalList;
    }
    
    private String getEdgesFinalList() {
        String edgesFinalList = "";
        for(Edge edge : hmEdgesFinal.keySet()) {
            edgesFinalList += edge.toString() + "; ";
        }
        return edgesFinalList;
    }
    
    // Problem: hmSourcesFinal at 50% won't contain all the sources from 25%
    private void insertMFSourceElements(HashMap<Source, Double[][]> maxFlowSourcesInfo, int percent) {
        for(Source source : hmSources.keySet()) {
            if(!maxFlowSourcesInfo.containsKey(source)) {
                Double[][] sourceInfoArray = new Double[41][2];
                maxFlowSourcesInfo.put(source, sourceInfoArray);
                maxFlowSourcesInfo.get(source)[percent][0] = hmSourcesFinal.get(source);
                maxFlowSourcesInfo.get(source)[percent][1] = hmSourcesCaptureCost.get(source)/(double)hmSources.get(source);
            } else { 
                maxFlowSourcesInfo.get(source)[percent][0] = hmSourcesFinal.get(source);
                maxFlowSourcesInfo.get(source)[percent][1] = hmSourcesCaptureCost.get(source)/(double)hmSources.get(source);
            }
        }
    }
    
    private void insertMFSinkElements(HashMap<Sink, Double[][]> maxFlowSinksInfo, int percent) {
        for(Sink sink : hmSinks.keySet()) {
            if(!maxFlowSinksInfo.containsKey(sink)) {
                Double[][] sinkInfoArray = new Double[41][2];
                maxFlowSinksInfo.put(sink, sinkInfoArray);
                maxFlowSinksInfo.get(sink)[percent][0] = hmSinksFinal.get(sink);
                maxFlowSinksInfo.get(sink)[percent][1] = hmSinksInjectionCost.get(sink)/(double)hmSinks.get(sink);
            } else {
                maxFlowSinksInfo.get(sink)[percent][0] = hmSinksFinal.get(sink);
                maxFlowSinksInfo.get(sink)[percent][1] = hmSinksInjectionCost.get(sink)/(double)hmSinks.get(sink);
            }
        }
    }
    
    public void insertMFEdgeElements(HashMap<Edge, Double[][]> maxFlowEdgesInfo, int percent) {
        for(Edge edge : hmEdges.keySet()) {
            if(!maxFlowEdgesInfo.containsKey(edge)) {
                Double[][] edgeInfoArray = new Double[41][2];
                maxFlowEdgesInfo.put(edge, edgeInfoArray);
                maxFlowEdgesInfo.get(edge)[percent][0] = hmEdgesFinal.get(edge);
                maxFlowEdgesInfo.get(edge)[percent][1] = hmEdgesAveCost.get(edge)/(double)hmEdges.get(edge);
            } else {
                maxFlowEdgesInfo.get(edge)[percent][0] = hmEdgesFinal.get(edge);
                maxFlowEdgesInfo.get(edge)[percent][1] = hmEdgesAveCost.get(edge)/(double)hmEdges.get(edge);
            }
        }
    }
    
    // currently not in use
//    public void outputMaxFlowData(String path) {
//        // Saves the overall file
//        String[] outputDocData = new String[41];
//        // HashMaps that track sources and sinks and their important values at each percentage
//        HashMap<Source, Double[][]> maxFlowSourcesInfo = new HashMap<>();
//        HashMap<Sink, Double[][]> maxFlowSinksInfo = new HashMap<>();
//        HashMap<Edge, Double[][]> maxFlowEdgesInfo = new HashMap<>();
//        
//        for(int i = 0; i < outputDocData.length; i++) {
//            gui.setHMSliderValue(i * 2.5);
//            displayMaxFlowMinCostHeatMap(solutionValues);
//            insertMFSourceElements(maxFlowSourcesInfo, i);
//            insertMFSinkElements(maxFlowSinksInfo, i);
//            insertMFEdgeElements(maxFlowEdgesInfo, i);
//            outputDocData[i] = "";
//            outputDocData[i] += calculateMaxFlowCO2Stored() + ", "; // amount stored
//            outputDocData[i] += calculateMaxFlowCO2Cost() + ", "; // cost
//            outputDocData[i] += "[" + getSourcesFinalList() + "], "; // sources used list
//            outputDocData[i] += "[" + getSinksFinalList() + "], "; // sinks used list
//            outputDocData[i] += "[" + getEdgesFinalList() + "]"; // edges used list
//        }
//        
//        // Creates the Source and Sink folders - figure out how to create new subfolders
//        File sourceFolder = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + path + "/Sources");
//        sourceFolder.mkdir();
//        File sinkFolder = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + path + "/Sinks");
//        sinkFolder.mkdir();
//        File edgeFolder = new File(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + path + "/Edges");
//        edgeFolder.mkdir();
//        
//        data.setMaxFlowGeneralData(outputDocData);
//        data.setMaxFlowSourcesInfo(maxFlowSourcesInfo);
//        data.setMaxFlowSinksInfo(maxFlowSinksInfo);
//        data.setMaxFlowEdgesInfo(maxFlowEdgesInfo);
//        data.makeMaxFlowInformationFile(path);
//    }
    // end of sorting and percentile info related methods
    
    /* Creates hashmaps storing cell numbers and the edges that are on them,
    along with generic objects to be replaced in the next method that 
    must be initialized here (need better system, look over again later) */
    
    public void populateFailureRobustCellHmaps() {
        fRCellsAndSources = new HashMap<>();
        fRCellsAndSinks = new HashMap<>();
        fRCells = new ArrayList<>();
        
        for(Source source : data.getSources()) {
            if(!fRCellsAndSources.containsKey(source.getCellNum())) {
                ArrayList<Source> sourcesUsingCell = new ArrayList<>();
                sourcesUsingCell.add(source);
                fRCellsAndSources.put(source.getCellNum(), sourcesUsingCell);
                fRCells.add(source.getCellNum());
            } else if(fRCellsAndSources.containsKey(source.getCellNum())) {
                // Adds additional sources at the same cell numb
                fRCellsAndSources.get(source.getCellNum()).add(source);
            }
        }
        
        for(Sink sink : data.getSinks()) {
            if(!fRCellsAndSinks.containsKey(sink.getCellNum())) {
                ArrayList<Sink> sinksUsingCell = new ArrayList<>();
                sinksUsingCell.add(sink);
                fRCellsAndSinks.put(sink.getCellNum(), sinksUsingCell);
                fRCells.add(sink.getCellNum());
            } else if(fRCellsAndSinks.containsKey(sink.getCellNum())) {
                // Adds additional sinks at the same cell numb
                fRCellsAndSinks.get(sink.getCellNum()).add(sink);
            }
        }
        
        for(Edge edge : data.getGraphEdgeRoutes().keySet()) {
            if(!fRCells.contains(edge.v1)) {
                fRCells.add(edge.v1);
            } else if(!fRCells.contains(edge.v2)) {
                fRCells.add(edge.v2);
            }
        }
    }
    
    // Really need to rework to consider cell numbers that don't appear on edges
    public void populateCellNumbHMaps() {
        hmCellsAndEdges = new HashMap<>();
        hmCellsAndSources = new HashMap<>();
        hmCellsAndSinks = new HashMap<>();
        hmCells = new ArrayList<>();
        
        // Adds all cell numbers to the two hashmaps and creates ALs
        for(Edge edge : hmEdges.keySet()) {
            if(!hmCellsAndEdges.containsKey(edge.v1)) {
                ArrayList<Edge> edgesUsingCell = new ArrayList<>();
                                
                hmCellsAndEdges.put(edge.v1, edgesUsingCell);
            } 
            if(!hmCellsAndEdges.containsKey(edge.v2)) {
                ArrayList<Edge> edgesUsingCell = new ArrayList<>();
                                
                hmCellsAndEdges.put(edge.v2, edgesUsingCell);
            }
        }
        
        // Creates HashMap with all cells containing Sources
        for(Source source : hmSources.keySet()) {
            if(!hmCellsAndSources.containsKey(source.getCellNum())) {
                ArrayList<Source> sourcesUsingCell = new ArrayList<>();
                sourcesUsingCell.add(source);
                hmCellsAndSources.put(source.getCellNum(), sourcesUsingCell);
            } else if(hmCellsAndSources.containsKey(source.getCellNum())) {
                // Adds additional sources at the same cell numb
                hmCellsAndSources.get(source.getCellNum()).add(source);
            }
        }
        
        // Creates HashMap with all cells containing Sinks
        for(Sink sink : hmSinks.keySet()) {
            if(!hmCellsAndSinks.containsKey(sink.getCellNum())) {
                ArrayList<Sink> sinksUsingCell = new ArrayList<>();
                sinksUsingCell.add(sink);
                hmCellsAndSinks.put(sink.getCellNum(), sinksUsingCell);
            } else if(hmCellsAndSinks.containsKey(sink.getCellNum())) {
                // Adds additional sinks at the same cell numb
                hmCellsAndSinks.get(sink.getCellNum()).add(sink);
            }
        }
        
        // Creates list of all cells containing sources, sinks, or edges
        // Add cells only if sources, sinks, and edges appear in correct slider amount
        for(Edge edge : hmEdges.keySet()) {
            if(((double)hmEdges.get(edge) / numberOfBaseSolutions) >= gui.getHmPercentToDisp()) {
                if(!hmCells.contains(edge.v1)) {
                    hmCells.add(edge.v1);
                }
                if(!hmCells.contains(edge.v2)) {
                    hmCells.add(edge.v2);
                }
            }
        }
        
        for(Source source : hmSources.keySet()) {
            if(((double)hmSources.get(source) / numberOfBaseSolutions) >= gui.getHmPercentToDisp()) {
                if(!hmCells.contains(source.getCellNum())) {
                    hmCells.add(source.getCellNum());
                }
            }
        }
        
        for(Sink sink : hmSinks.keySet()) {
            if(((double)hmSinks.get(sink) / numberOfBaseSolutions) >= gui.getHmPercentToDisp()) {
                if(!hmCells.contains(sink.getCellNum())) {
                    hmCells.add(sink.getCellNum());
                }
            }
        }
        
        // Ensures arraylists for hmEdges are filled with appropriate edges
        for(Integer cellNumb : hmCellsAndEdges.keySet()) {
            // want to add edges that connect to cell number in cells AL
            for(Edge edge : hmEdges.keySet()) {
                if(edge.v1 == cellNumb) {
                    hmCellsAndEdges.get(cellNumb).add(edge);
                    edge.setAssignedToCell(true);
                } else if(edge.v2 == cellNumb) {
                    hmCellsAndEdges.get(cellNumb).add(edge);
                    edge.setAssignedToCell(true);
                }
            }
        }
    }

    public void determineROW(Solution soln, String path) {
        // read in right of way file
        String rowFileLocation = basePath + "/" + dataset + "/BaseData/CostSurface/Ascii/rows.asc";
        boolean[] rightOfWay = new boolean[data.getWidth() * data.getHeight() + 1];
        try (BufferedReader br = new BufferedReader(new FileReader(rowFileLocation))) {
            for (int i = 0; i < 6; i++) {
                br.readLine();
            }
            String line = br.readLine();
            int cellNum = 1;
            while (line != null) {
                String[] costs = line.split("\\s+");
                for (String cost : costs) {
                    int val = Integer.parseInt(cost);
                    if (val != -9999) {
                        rightOfWay[cellNum] = true;
                    }
                    cellNum++;
                }
                line = br.readLine();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        // compare solution to right of way
        HashSet<Integer> usedCells = new HashSet<>();
        HashSet<Integer> rowedCells = new HashSet<>();
        HashSet<int[]> rowedPairs = new HashSet<>();
        HashMap<Edge, int[]> graphEdgeRoutes = data.getGraphEdgeRoutes();
        ArrayList<ArrayList<Integer>> existingRowRoutes = new ArrayList<>();
        ArrayList<ArrayList<Integer>> newRowRoutes = new ArrayList<>();
        for (Edge e : soln.getOpenedEdges()) {
            int[] route = graphEdgeRoutes.get(e);
            boolean existingROW = false;
            ArrayList<Integer> newRoute = new ArrayList<>();
            for (int i = 0; i < route.length - 1; i++) {
                if (rightOfWay[route[i]] && rightOfWay[route[i + 1]]) {
                    rowedPairs.add(new int[]{route[i], route[i + 1]});
                }
            }
            for (int cell : route) {
                usedCells.add(cell);
                if (rightOfWay[cell]) {
                    if (!existingROW) {
                        // swap: new ROW -> existing ROW
                        newRowRoutes.add(newRoute);
                        newRoute = new ArrayList<>();
                        existingROW = true;
                    }
                    newRoute.add(cell);
                    rowedCells.add(cell);
                } else {
                    if (existingROW) {
                        // swap: existing ROW -> new ROW
                        existingRowRoutes.add(newRoute);
                        newRoute = new ArrayList<>();
                        existingROW = false;
                    }
                    newRoute.add(cell);
                }
            }
            if (existingROW) {
                existingRowRoutes.add(newRoute);
            } else {
                newRowRoutes.add(newRoute);
            }
        }

        // display ROWed edges
        for (int[] pair : rowedPairs) {
            double[] rawSrc = data.cellLocationToRawXY(pair[0]);
            double[] rawDest = data.cellLocationToRawXY(pair[1]);
            double sX = rawXtoDisplayX(rawSrc[0]);
            double sY = rawYtoDisplayY(rawSrc[1]);
            double dX = rawXtoDisplayX(rawDest[0]);
            double dY = rawYtoDisplayY(rawDest[1]);
            Line edge = new Line(sX, sY, dX, dY);
            edge.setStroke(Color.PURPLE);
            edge.setStrokeWidth(5.0 / gui.getScale());
            edge.setStrokeLineCap(StrokeLineCap.ROUND);
            solutionLayer.getChildren().add(edge);
        }

        double percentUsed = rowedCells.size() / (double) usedCells.size();
        messenger.setText("Percent on existing ROW: " + percentUsed);
        makeRowShapeFiles("ExistingROW", path + "/shapeFiles/", existingRowRoutes);
        makeRowShapeFiles("NewROW", path + "/shapeFiles/", newRowRoutes);
    }

    public void makeRowShapeFiles(String name, String path, ArrayList<ArrayList<Integer>> routes) {
        EsriPolylineList edgeList = new EsriPolylineList();
        String[] edgeAttributeNames = {"Id", "CapID", "CapValue", "Flow", "Cost", "LengKM", "LengROW", "LengCONS", "Variable"};
        int[] edgeAttributeDecimals = {0, 0, 0, 6, 0, 0, 0, 0, 0};
        DbfTableModel edgeAttributeTable = new DbfTableModel(edgeAttributeNames.length);   //12
        for (int colNum = 0; colNum < edgeAttributeNames.length; colNum++) {
            edgeAttributeTable.setColumnName(colNum, edgeAttributeNames[colNum]);
            edgeAttributeTable.setDecimalCount(colNum, (byte) edgeAttributeDecimals[colNum]);
            edgeAttributeTable.setLength(colNum, 10);
            if (edgeAttributeNames[colNum].equals("Id")) {
                edgeAttributeTable.setType(colNum, DbfTableModel.TYPE_CHARACTER);
            } else {
                edgeAttributeTable.setType(colNum, DbfTableModel.TYPE_NUMERIC);
            }
        }

        for (ArrayList<Integer> route : routes) {
            double[] routeLatLon = new double[route.size() * 2];    // Route cells translated into: lat, lon, lat, lon,...
            for (int i = 0; i < route.size(); i++) {
                int cell = route.get(i);
                routeLatLon[i * 2] = data.cellToLocation(cell)[0];
                routeLatLon[i * 2 + 1] = data.cellToLocation(cell)[1];
            }

            EsriPolyline edge = new EsriPolyline(routeLatLon, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT);
            edgeList.add(edge);

            // Add attributes.
            ArrayList row = new ArrayList();
            for (int i = 0; i < 3; i++) {
                row.add(0);
            }
            row.add(0);
            for (int i = 0; i < 5; i++) {
                row.add(0);
            }

            edgeAttributeTable.addRecord(row);
        }

        EsriShapeExport writeEdgeShapefiles = new EsriShapeExport(edgeList, edgeAttributeTable, path + "/" + name);
        writeEdgeShapefiles.export();
    }

    public void aggregateSolutions(String file, Label[] solutionValues) {
        Solution aggSoln = new Solution();
        HashMap<Source, Integer> sourcePopularity = new HashMap<>();
        HashMap<Sink, Integer> sinkPopularity = new HashMap<>();
        HashMap<Edge, Integer> edgePopularity = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            Solution soln = data.loadSolution(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file + "/run" + i, -1);

            HashMap<Edge, Double> edgeTransportAmounts = soln.getEdgeTransportAmounts();
            HashMap<Source, Double> sourceCaptureAmounts = soln.getSourceCaptureAmounts();
            HashMap<Sink, Double> sinkStorageAmounts = soln.getSinkStorageAmounts();

            for (Edge e : soln.getOpenedEdges()) {
                if (!edgePopularity.containsKey(e)) {
                    edgePopularity.put(e, 1);
                } else {
                    edgePopularity.put(e, edgePopularity.get(e) + 1);
                }

                aggSoln.addEdgeTransportAmount(e, edgeTransportAmounts.get(e));
            }

            for (Source source : soln.getOpenedSources()) {
                if (!sourcePopularity.containsKey(source)) {
                    sourcePopularity.put(source, 1);
                } else {
                    sourcePopularity.put(source, sourcePopularity.get(source) + 1);
                }

                aggSoln.addSourceCaptureAmount(source, sourceCaptureAmounts.get(source));
            }

            for (Sink sink : soln.getOpenedSinks()) {
                if (!sinkPopularity.containsKey(sink)) {
                    sinkPopularity.put(sink, 1);
                } else {
                    sinkPopularity.put(sink, sinkPopularity.get(sink) + 1);
                }

                aggSoln.addSinkStorageAmount(sink, sinkStorageAmounts.get(sink));
            }
        }

        HashMap<Edge, int[]> graphEdgeRoutes = data.getGraphEdgeRoutes();
        for (Edge e : edgePopularity.keySet()) {
            int[] route = graphEdgeRoutes.get(e);
            for (int src = 0; src < route.length - 1; src++) {
                int dest = src + 1;
                double[] rawSrc = data.cellLocationToRawXY(route[src]);
                double[] rawDest = data.cellLocationToRawXY(route[dest]);
                double sX = rawXtoDisplayX(rawSrc[0]);
                double sY = rawYtoDisplayY(rawSrc[1]);
                double dX = rawXtoDisplayX(rawDest[0]);
                double dY = rawYtoDisplayY(rawDest[1]);
                Line edge = new Line(sX, sY, dX, dY);
                edge.setStroke(Color.GREEN);
                edge.setStrokeWidth(Math.ceil(edgePopularity.get(e) / 10.0) / gui.getScale());
                edge.setStrokeLineCap(StrokeLineCap.ROUND);
                solutionLayer.getChildren().add(edge);
            }
        }

        for (Source source : sourcePopularity.keySet()) {
            double[] rawXYLocation = data.cellLocationToRawXY(source.getCellNum());
            Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), 5 * Math.ceil(sourcePopularity.get(source) / 35.0) / gui.getScale());
            c.setStroke(Color.RED);
            c.setFill(Color.RED);
            solutionLayer.getChildren().add(c);
        }

        for (Sink sink : sinkPopularity.keySet()) {
            double[] rawXYLocation = data.cellLocationToRawXY(sink.getCellNum());
            Circle c = new Circle(rawXtoDisplayX(rawXYLocation[0]), rawYtoDisplayY(rawXYLocation[1]), 5 * Math.ceil(sinkPopularity.get(sink) / 35.0) / gui.getScale());
            c.setStroke(Color.BLUE);
            c.setFill(Color.BLUE);
            solutionLayer.getChildren().add(c);
        }

        // Write to shapefiles.
        data.makeShapeFiles(basePath + "/" + dataset + "/Scenarios/" + scenario + "/Results/" + file, aggSoln);
    }

    public double rawXtoDisplayX(double rawX) {
        double widthRatio = map.getBoundsInParent().getWidth() / data.getWidth();
        // Need to offset to middle of pixel.
        return (rawX - .5) * widthRatio;
    }

    public double rawYtoDisplayY(double rawY) {
        double heightRatio = map.getBoundsInParent().getHeight() / data.getHeight();
        // Need to offset to middle of pixel.
        return (rawY - .5) * heightRatio;
    }

    public int displayXYToVectorized(double x, double y) {
        if (map == null || data == null) {
            return -1;
        }
        int rawX = (int) (x / (map.getBoundsInParent().getWidth() / data.getWidth())) + 1;
        int rawY = (int) (y / (map.getBoundsInParent().getHeight() / data.getHeight())) + 1;
        return data.colRowToVectorized(rawX, rawY);
    }

    /*public double[] latLonToDisplayXY(double lat, double lon) {
        double[] rawXY = data.latLonToXY(lat, lon);
        double heightRatio = map.getBoundsInParent().getHeight() / data.getHeight();
        double widthRatio = map.getBoundsInParent().getWidth() / data.getWidth();
        return new double[]{rawXY[0] * widthRatio, rawXY[1] * heightRatio};
    }*/
    
    public void addSourceLocationsLayer(Pane layer) {
        sourceLocationsLayer = layer;
    }

    public void addSinkLocationsLayer(Pane layer) {
        sinkLocationsLayer = layer;
    }

    public void addSourceLabelsLayer(Pane layer) {
        sourceLabelsLayer = layer;
    }

    public void addSinkLabelsLayer(Pane layer) {
        sinkLabelsLayer = layer;
    }

    public void addShortestPathsLayer(Pane layer) {
        shortestPathsLayer = layer;
    }

    public void addCandidateNetworkLayer(Pane layer) {
        candidateNetworkLayer = layer;
    }

    public void addSolutionLayer(Pane layer) {
        solutionLayer = layer;
    }

    public void addRawDelaunayLayer(Pane layer) {
        rawDelaunayLayer = layer;
    }

    public DataStorer getDataStorer() {
        return data;
    }

    public void addMessenger(TextArea messenger) {
        this.messenger = messenger;
    }

    public TextArea getMessenger() {
        return messenger;
    }

    public DataStorer getData() {
        return data;
    }
}
