package gui;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/**
 *
 * @author yaw
 */
public class Gui extends Application {

    private NetworkDisplay displayPane;
    private ChoiceBox scenarioChoice; // Data pane chooose scenario drop-down menu
    private ChoiceBox networkChoice; // Data pane choose network drop-down menu
    private RadioButton dispDelaunayEdges; // Data pane display delaunay edges button
    private RadioButton dispCandidateNetwork; // Data pane display candidate network button
    private RadioButton sourceLabeled; // Data pane display source's labels button
    private RadioButton sourceVisible; // Data pane display sources button
    private RadioButton sinkLabeled; // Data pane display sink's labels button
    private RadioButton sinkVisible; // Data pane display sinks button
    private RadioButton dispCostSurface; // Data pane display cost surface button
    private RadioButton selectBaseUncertainty; // Uncertainty pane base uncertainty model selection button
    private RadioButton selectHeatMap; // Uncertainty pane heat map models uncertainty model selection button
    private RadioButton selectDeterministic; // Uncertainty pane uncertainty toggle off button
    private RadioButton selectUncertainty; // Uncertainty pane uncertainty toggle on button
    public RadioButton addPoint; // Data pane add point to map button
    public RadioButton capVersion; // Model pane cap mode selection button
    public RadioButton priceVersion; // Model pane price mode selection button
    private ChoiceBox runChoice; 
    private ChoiceBox solutionChoice;
    private ChoiceBox uncertaintySolutionChoice;
    private ChoiceBox realizationCategoryChoice;
    private AnchorPane solutionPane;
    private TextArea messenger; // Text box present in all panes that displays supplementary text-based information
    private Slider hmSlider; // Results pane slider to control map elements to display according to confidence level by %
    private boolean underUncertainty;
    private boolean underBaseUncertainty;
    private String parentPath;
    private String hmPath;
    private double hmPercentToDisp;
    public RadioButton maxFlowHM; // Results pane toggle max flow heat map
    // max flow min cost variables
    double interestRate;
    int numYears;
    double modelParamValue;
    String selectedSolverText;

    @Override
    public void start(Stage stage) {
        Scene scene = buildGUI(stage);
        stage.setScene(scene);
        stage.setTitle("SimCCS");
        stage.setHeight(699);
        stage.show();
    }

    public Scene buildGUI(Stage stage) {
        Group group = new Group();

        // Build display pane.
        displayPane = new NetworkDisplay();
        // Offset Network Display to account for controlPane.
        displayPane.setTranslateX(232); // original value = 220
        // Associate scroll/navigation actions.
        SceneGestures sceneGestures = new SceneGestures(displayPane);
        displayPane.addEventFilter(MouseEvent.ANY, sceneGestures.getMouseEventHandler());
        displayPane.addEventFilter(ScrollEvent.ANY, sceneGestures.getOnScrollEventHandler());

        // Make background.
        Rectangle background = new Rectangle();
        background.setStroke(Color.WHITE);
        background.setFill(Color.WHITE);
        displayPane.getChildren().add(background);

        // Add base cost surface display.
        PixelatedImageView map = new PixelatedImageView();
        map.setPreserveRatio(true);
        map.setFitWidth(830);
        map.setFitHeight(660);
        map.setSmooth(false);
        displayPane.getChildren().add(map);

        // Action handler.
        ControlActions controlActions = new ControlActions(map, this);
        displayPane.setControlActions(controlActions);

        // Build tab background with messenger.
        AnchorPane messengerPane = new AnchorPane();
        messengerPane.setStyle("-fx-background-color: white; -fx-border-color: lightgrey;");
        messengerPane.setPrefSize(232, 122);
        messengerPane.setLayoutX(0);
        messengerPane.setLayoutY(537); // permanent = 638
        messenger = new TextArea();
        messenger.setEditable(false);
        messenger.setWrapText(true);
        messenger.setPrefSize(203, 112); // og x = 193, y = 112
        messenger.setLayoutX(14);
        messenger.setLayoutY(5);
        messengerPane.getChildren().add(messenger);
        controlActions.addMessenger(messenger);

        // Build tab pane and tabs.
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab dataTab = new Tab();
        dataTab.setText("Data");
        tabPane.getTabs().add(dataTab);
        Tab modelTab = new Tab();
        modelTab.setText("Model");
        tabPane.getTabs().add(modelTab);
        Tab resultsTab = new Tab();
        resultsTab.setText("Results");
        tabPane.getTabs().add(resultsTab);
        Tab uncertaintyTab = new Tab();
        uncertaintyTab.setText("Uncertainty");
        tabPane.getTabs().add(uncertaintyTab);

        // Build data pane.
        AnchorPane dataPane = new AnchorPane();
        dataPane.setStyle("-fx-background-color: white; -fx-border-color: lightgrey;");
        dataPane.setPrefSize(220, 580);
        dataTab.setContent(dataPane);
        
        // Build network pane.
        AnchorPane networkPane = new AnchorPane();
        networkPane.setStyle("-fx-background-color: white; -fx-border-color: lightgrey;");
        networkPane.setPrefSize(220, 580);

        // Build model pane.
        AnchorPane modelPane = new AnchorPane();
        modelPane.setStyle("-fx-background-color: white; -fx-border-color: lightgrey;");
        modelPane.setPrefSize(220, 580);
        modelTab.setContent(modelPane);

        // Build results pane.
        AnchorPane resultsPane = new AnchorPane();
        resultsPane.setStyle("-fx-background-color: white; -fx-border-color: lightgrey;");
        resultsPane.setPrefSize(220, 680);
        resultsTab.setContent(resultsPane);
        
        // Build uncertainty pane.
        AnchorPane uncertaintyPane = new AnchorPane();
        uncertaintyPane.setStyle("-fx-background-color: white; -fx-border-color: lightgrey;");
        uncertaintyPane.setPrefSize(220, 580);
        uncertaintyTab.setContent(uncertaintyPane);

        // Populate data pane.
        // Build scenario selection control and add to control pane.
        scenarioChoice = new ChoiceBox();
        scenarioChoice.setPrefSize(150, 27);
        TitledPane scenarioContainer = new TitledPane("Scenario", scenarioChoice);
        scenarioContainer.setCollapsible(false);
        scenarioContainer.setPrefSize(203, 63);
        scenarioContainer.setLayoutX(14);
        scenarioContainer.setLayoutY(73);
        dataPane.getChildren().add(scenarioContainer);
        runChoice = new ChoiceBox();
        solutionChoice = new ChoiceBox();
        uncertaintySolutionChoice = new ChoiceBox();
        realizationCategoryChoice = new ChoiceBox();
        scenarioChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> selected, String oldScenario, String newScenario) {
                controlActions.selectScenario(newScenario, background, runChoice, networkChoice);
            }
        });

        // Build dataset selection control and add to control pane.
        Button openDataset = new Button("[Open Dataset]");
        openDataset.setMnemonicParsing(false);
        openDataset.setPrefSize(150, 27);
        openDataset.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                DirectoryChooser directoryChooser = new DirectoryChooser();
                directoryChooser.setTitle("Open Dataset");
                File selectedDataset = directoryChooser.showDialog(stage);
                if (selectedDataset != null) {
                    openDataset.setText(selectedDataset.getName());
                    controlActions.selectDataset(selectedDataset, scenarioChoice);
                    /* Displays a message saying dataset loaded successfully to 
                    help avoid confusion */
                    messenger.setText("Dataset loaded successfully");
                }
            }
        });
        TitledPane datasetContainer = new TitledPane("Dataset", openDataset);
        datasetContainer.setCollapsible(false);
        datasetContainer.setPrefSize(203, 63);
        datasetContainer.setLayoutX(14);
        datasetContainer.setLayoutY(5);
        dataPane.getChildren().add(datasetContainer);
        
        // Build Candidate Network selection.
        networkChoice = new ChoiceBox();
        networkChoice.setPrefSize(150, 27);
        TitledPane networkContainer = new TitledPane("Candidate Network", networkChoice);
        networkContainer.setCollapsible(false);
        networkContainer.setPrefSize(203, 63);
        networkContainer.setLayoutX(14);
        networkContainer.setLayoutY(141);
        dataPane.getChildren().add(networkContainer);
        networkChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> selected, String oldNetwork, String newNetwork) {
                controlActions.selectCandidateNetwork(newNetwork);
            }
        });

        // Build network buttons and add to control pane.
        Button generateNetwork = new Button("Generate Network");
        generateNetwork.setLayoutX(35);
        generateNetwork.setLayoutY(4);
        generateNetwork.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                controlActions.generateCandidateGraph(networkChoice);
            }
        });
        
        Line line = new Line(15, 39, 180, 39);
        line.setStroke(Color.GREY);
        
        addPoint = new RadioButton("Add Point");
        addPoint.setLayoutX(55);
        addPoint.setLayoutY(45);
        
        Button saveNetwork = new Button("Save Network");
        saveNetwork.setLayoutX(47);
        saveNetwork.setLayoutY(67);
        saveNetwork.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                controlActions.saveCandidateGraph(networkChoice);
            }
        });
        
        AnchorPane networkConstructionPane = new AnchorPane();
        networkConstructionPane.setPrefSize(190, 30);
        networkConstructionPane.setMinSize(0, 0);
        networkConstructionPane.getChildren().addAll(generateNetwork);
        networkConstructionPane.getChildren().addAll(saveNetwork);
        networkConstructionPane.getChildren().add(addPoint);
        networkConstructionPane.getChildren().add(line);
        TitledPane networkChangeContainer = new TitledPane("Network Construction", networkConstructionPane);
        networkChangeContainer.setCollapsible(false);
        networkChangeContainer.setPrefSize(203, 127);
        networkChangeContainer.setLayoutX(14);
        networkChangeContainer.setLayoutY(209);
        dataPane.getChildren().add(networkChangeContainer);

        //Build display selection legend and add to control pane.
        AnchorPane selectionPane = new AnchorPane();
        selectionPane.setPrefSize(206, 237);
        selectionPane.setMinSize(0, 0);

        dispDelaunayEdges = new RadioButton("Raw Delaunay Edges");
        dispDelaunayEdges.setLayoutX(4);
        dispDelaunayEdges.setLayoutY(83);
        selectionPane.getChildren().add(dispDelaunayEdges);
        Pane rawDelaunayLayer = new Pane();
        sceneGestures.addEntityToResize(rawDelaunayLayer);
        displayPane.getChildren().add(rawDelaunayLayer);
        controlActions.addRawDelaunayLayer(rawDelaunayLayer);
        rawDelaunayLayer.setPickOnBounds(false);
        dispDelaunayEdges.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean show) {
                controlActions.toggleRawDelaunayDisplay(show);
            }
        });

        dispCandidateNetwork = new RadioButton("Candidate Network");
        dispCandidateNetwork.setLayoutX(4);
        dispCandidateNetwork.setLayoutY(106);
        selectionPane.getChildren().add(dispCandidateNetwork);
        Pane candidateNetworkLayer = new Pane();
        sceneGestures.addEntityToResize(candidateNetworkLayer);
        displayPane.getChildren().add(candidateNetworkLayer);
        controlActions.addCandidateNetworkLayer(candidateNetworkLayer);
        candidateNetworkLayer.setPickOnBounds(false);
        dispCandidateNetwork.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean show) {
                controlActions.toggleCandidateNetworkDisplay(show);
            }
        });

        Label sourceLabel = new Label("Sources:");
        sourceLabel.setLayoutX(2);
        sourceLabel.setLayoutY(5);
        selectionPane.getChildren().add(sourceLabel);

        // Toggle source locations display button.
        sourceLabeled = new RadioButton("Label");  // Need reference before definition.
        sourceVisible = new RadioButton("Visible");
        sourceVisible.setLayoutX(62);
        sourceVisible.setLayoutY(4);
        selectionPane.getChildren().add(sourceVisible);
        Pane sourcesLayer = new Pane();
        displayPane.getChildren().add(sourcesLayer);
        controlActions.addSourceLocationsLayer(sourcesLayer);
        sceneGestures.addEntityToResize(sourcesLayer);
        sourcesLayer.setPickOnBounds(false);
        sourceVisible.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean show) {
                if (!show) {
                    sourceLabeled.setSelected(false);
                }
                controlActions.toggleSourceDisplay(show);
            }
        });

        // Toggle source labels display button.
        sourceLabeled.setLayoutX(131);
        sourceLabeled.setLayoutY(4);
        selectionPane.getChildren().add(sourceLabeled);
        Pane sourceLabelsLayer = new Pane();
        displayPane.getChildren().add(sourceLabelsLayer);
        controlActions.addSourceLabelsLayer(sourceLabelsLayer);
        sceneGestures.addEntityToResize(sourceLabelsLayer);
        sourceLabelsLayer.setPickOnBounds(false);
        sourceLabeled.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean show) {
                if (!sourceVisible.isSelected()) {
                    show = false;
                    sourceLabeled.setSelected(false);
                }
                controlActions.toggleSourceLabels(show);
            }
        });

        Label sinkLabel = new Label("Sinks:");
        sinkLabel.setLayoutX(19);
        sinkLabel.setLayoutY(30);
        selectionPane.getChildren().add(sinkLabel);

        // Toggle sink locations display button.
        sinkLabeled = new RadioButton("Label");  // Need reference before definition.
        sinkVisible = new RadioButton("Visible");
        sinkVisible.setLayoutX(62);
        sinkVisible.setLayoutY(29);
        selectionPane.getChildren().add(sinkVisible);
        Pane sinksLayer = new Pane();
        displayPane.getChildren().add(sinksLayer);
        controlActions.addSinkLocationsLayer(sinksLayer);
        sceneGestures.addEntityToResize(sinksLayer);
        sinksLayer.setPickOnBounds(false);
        sinkVisible.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean show) {
                if (!show) {
                    sinkLabeled.setSelected(false);
                }
                controlActions.toggleSinkDisplay(show);
            }
        });

        // Toggle sink labels.
        sinkLabeled.setLayoutX(131);
        sinkLabeled.setLayoutY(29);
        selectionPane.getChildren().add(sinkLabeled);
        Pane sinkLabelsLayer = new Pane();
        displayPane.getChildren().add(sinkLabelsLayer);
        controlActions.addSinkLabelsLayer(sinkLabelsLayer);
        sceneGestures.addEntityToResize(sinkLabelsLayer);
        sinkLabelsLayer.setPickOnBounds(false);
        sinkLabeled.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean show) {
                if (!sinkVisible.isSelected()) {
                    show = false;
                    sinkLabeled.setSelected(false);
                }
                controlActions.toggleSinkLabels(show);
            }
        });

        // Toggle cost surface button.
        dispCostSurface = new RadioButton("Cost Surface");
        dispCostSurface.setLayoutX(4);
        dispCostSurface.setLayoutY(60);
        selectionPane.getChildren().add(dispCostSurface);
        dispCostSurface.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean show) {
                controlActions.toggleCostSurface(show, background);
            }
        });

        TitledPane selectionContainer = new TitledPane("Legend", selectionPane);
        selectionContainer.setCollapsible(false);
        selectionContainer.setPrefSize(203, 156);
        selectionContainer.setLayoutX(14);
        selectionContainer.setLayoutY(342);
        dataPane.getChildren().add(selectionContainer);
        
        // Solution area
        AnchorPane formulationPane = new AnchorPane();
        formulationPane.setPrefSize(206, 237);
        formulationPane.setMinSize(0, 0);

        Label rateLabel = new Label("Interest Rate");
        rateLabel.setLayoutX(4);
        rateLabel.setLayoutY(8);
        formulationPane.getChildren().add(rateLabel);
        TextField rateValue = new TextField(".09");
        rateValue.setEditable(true);
        rateValue.setPrefColumnCount(2);
        rateValue.setLayoutX(152); // og x = 143 for editable text fields
        rateValue.setLayoutY(4);
        formulationPane.getChildren().add(rateValue);

        Label yearLabel = new Label("Number of Years");
        yearLabel.setLayoutX(4);
        yearLabel.setLayoutY(38);
        formulationPane.getChildren().add(yearLabel);
        TextField yearValue = new TextField("30");
        yearValue.setEditable(true);
        yearValue.setPrefColumnCount(2);
        yearValue.setLayoutX(152);
        yearValue.setLayoutY(34);
        formulationPane.getChildren().add(yearValue);

        Label paramLabel = new Label("Capture Target (MT/y)");
        paramLabel.setLayoutX(4);
        paramLabel.setLayoutY(68);
        formulationPane.getChildren().add(paramLabel);
        TextField paramValue = new TextField("15");
        paramValue.setEditable(true);
        paramValue.setPrefColumnCount(2);
        paramValue.setLayoutX(152);
        paramValue.setLayoutY(64);
        formulationPane.getChildren().add(paramValue);

        capVersion = new RadioButton("Cap");
        priceVersion = new RadioButton("Price");
        RadioButton timeVersion = new RadioButton("Time");
        ToggleGroup capVsPrice = new ToggleGroup();
        capVersion.setToggleGroup(capVsPrice);
        priceVersion.setToggleGroup(capVsPrice);

        capVersion.setLayoutX(5);
        capVersion.setLayoutY(94);
        formulationPane.getChildren().add(capVersion);
        capVersion.setSelected(true);
        capVersion.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean show) {
                if (!oldVal) {
                    priceVersion.setSelected(false);
                    if (!timeVersion.isSelected()) {
                        paramLabel.setText("Capture Target (MT/y)");
                        paramValue.setText("15");
                    }
                }
            }
        });

        priceVersion.setLayoutX(69); // og x = 60
        priceVersion.setLayoutY(94);
        formulationPane.getChildren().add(priceVersion);
        priceVersion.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean show) {
                if (!oldVal) {
                    capVersion.setSelected(false);
                    if (!timeVersion.isSelected()) {
                        paramLabel.setText("Tax/Credit ($/t)");
                        paramValue.setText("0");
                    }
                }
            }
        });

        timeVersion.setLayoutX(138); // og x = 120
        timeVersion.setLayoutY(94);
        formulationPane.getChildren().add(timeVersion);
        timeVersion.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean show) {
                if (!oldVal) {
                    paramLabel.setText("");
                    paramValue.setText("");
                    paramValue.setEditable(false);
                } else {
                    if (capVersion.isSelected()) {
                        paramLabel.setText("Capture Target (MT/y)");
                        paramValue.setText("15");
                        paramValue.setEditable(true);
                    } else {
                        paramLabel.setText("Tax/Credit ($/t)");
                        paramValue.setText("0");
                        paramValue.setEditable(true);
                    }
                }
            }
        });

        // Populate model pane.
        TitledPane modelContainer = new TitledPane("Problem Formulation", formulationPane);
        modelContainer.setCollapsible(false);
        modelContainer.setPrefSize(203, 147);
        modelContainer.setLayoutX(14);
        modelContainer.setLayoutY(5);
        modelPane.getChildren().add(modelContainer);

        // Solution pane.
        AnchorPane solverPane = new AnchorPane();
        solverPane.setPrefSize(192, 100);
        solverPane.setMinSize(0, 0);

        RadioButton cplexSelect = new RadioButton("CPLEX");
        RadioButton greedySelect = new RadioButton("Greedy");
        RadioButton lpSelect = new RadioButton("LP");
        ToggleGroup solver = new ToggleGroup();
        cplexSelect.setToggleGroup(solver);
        greedySelect.setToggleGroup(solver);
        lpSelect.setToggleGroup(solver);

        cplexSelect.setLayoutX(2);
        cplexSelect.setLayoutY(7);
        solverPane.getChildren().add(cplexSelect);
        cplexSelect.setSelected(true);

        greedySelect.setLayoutX(77); // og x = 73
        greedySelect.setLayoutY(7);
        solverPane.getChildren().add(greedySelect);

        lpSelect.setLayoutX(152); // og x = 147
        lpSelect.setLayoutY(7);
        solverPane.getChildren().add(lpSelect);

        Button cplexSolve = new Button("Solve");
        cplexSolve.setLayoutX(77); // og x = 68
        cplexSolve.setLayoutY(35);
        solverPane.getChildren().add(cplexSolve);
        cplexSolve.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                String modelVersion = "";
                if (capVersion.isSelected()) {
                    modelVersion = "c";
                } else if (priceVersion.isSelected()) {
                    modelVersion = "p";
                }

                if (timeVersion.isSelected()) {
                    modelVersion += "t";
                }

                RadioButton selectedSolver = (RadioButton) solver.getSelectedToggle();
                selectedSolverText = selectedSolver.getText();

                double interestRateInner = Double.parseDouble(rateValue.getText());
                int numYearsInner = Integer.parseInt(yearValue.getText());
                double modelParamValue = 0.0;
                if (!paramValue.getText().equals("")) {
                    modelParamValue = Double.parseDouble(paramValue.getText());
                }

                controlActions.solveInstance(interestRateInner, numYearsInner, modelParamValue, modelVersion, selectedSolver.getText());
            }
        });

        // Populate MIP solution method pane.
        TitledPane solverContainer = new TitledPane("Solver", solverPane);
        solverContainer.setCollapsible(false);
        solverContainer.setPrefSize(203, 95);    // Heuristic
        solverContainer.setLayoutX(14);
        solverContainer.setLayoutY(158);
        modelPane.getChildren().add(solverContainer);

        // Populate results pane.
        // Build solution selection control.
        solutionPane = new AnchorPane();
        solutionPane.setPrefSize(190, 30);
        solutionPane.setMinSize(0, 0);
        
        TitledPane resultsContainer = new TitledPane("Load Solution", solutionPane);
        resultsContainer.setCollapsible(false);
        resultsContainer.setPrefSize(203, 139); // og x = 192, y = 94, recently y = 236, permanent 301
        resultsContainer.setLayoutX(14);
        resultsContainer.setLayoutY(5);
        
        // runChoice and solutionChoice handle regular and time based use cases
        runChoice.setPrefSize(150, 27);
        runChoice.setLayoutX(26); // og x = 20
        runChoice.setLayoutY(4);
        solutionChoice.setPrefSize(150, 27);
        solutionChoice.setLayoutX(26); // og x = 20
        solutionChoice.setLayoutY(35);
        // uncertaintySolutionChoice and realizationCategoryChoice handle selecting heat map solutions
        uncertaintySolutionChoice.setPrefSize(150, 27);
        uncertaintySolutionChoice.setLayoutX(26);
        uncertaintySolutionChoice.setLayoutY(4);
        realizationCategoryChoice.setPrefSize(150, 27);
        realizationCategoryChoice.setLayoutX(26);
        realizationCategoryChoice.setLayoutY(35);

        solutionPane.getChildren().add(runChoice);
        solutionPane.getChildren().add(uncertaintySolutionChoice);
        
        // Sets heat map choiceboxes to disabled at start
        uncertaintySolutionChoice.setDisable(true);
        realizationCategoryChoice.setDisable(true);
        
        // displayRecalculatedHM toggles display of the hm recalculated for actual optimized amounts w/in hm itself
        maxFlowHM = new RadioButton("Max Flow Heat Map");
        maxFlowHM.setLayoutX(25);
        maxFlowHM.setLayoutY(92); // recently 187, old permanent version was 252
        solutionPane.getChildren().add(maxFlowHM);
        
        /* The max flow solution uses the capture amount, injection amount, and 
        transport amount of elements (sources, sinks, and edges) from the 
        version of the elements used in a certaint percentage of base solutions. 
        elementVerPercentSlider selects this percentage to be used */
        Label elementVerPercentSliderLabel = new Label("Max Flow Input Values Cap %");
        elementVerPercentSliderLabel.setLayoutX(23);
        elementVerPercentSliderLabel.setLayoutY(93);
        
        /* hmSlider controls what sources, sinks, and edges are displayed 
        according to >= the % of base solutions they appear in */
        Label confidenceSliderLabel = new Label("Confidence %");
        confidenceSliderLabel.setLayoutX(61);
        confidenceSliderLabel.setLayoutY(35);
        solutionPane.getChildren().add(confidenceSliderLabel);
        
        hmSlider = new Slider(0, 100, 0);
        hmSlider.setMajorTickUnit(5.0);
        hmSlider.setMinorTickCount(1);
        hmSlider.setLayoutX(26);
        hmSlider.setLayoutY(50); // recently 97
        hmSlider.setSnapToTicks(true);
        hmSlider.setShowTickLabels(true);
        hmSlider.setShowTickMarks(true);
        
        resultsPane.getChildren().add(resultsContainer);

        // Build solution display area. 
        AnchorPane solutionDisplayPane = new AnchorPane();
        solutionDisplayPane.setPrefSize(190, 30);
        solutionDisplayPane.setMinSize(0, 0);
        solutionDisplayPane.setLayoutX(0);
        solutionDisplayPane.setLayoutY(150); // og y = 140, recently 245, permanent 309
        resultsPane.getChildren().add(solutionDisplayPane);

        // Solution labels.
        Label sources = new Label("Sources:");
        sources.setLayoutX(72);
        sources.setLayoutY(0);
        Label sourcesValue = new Label("-");
        sourcesValue.setLayoutX(135);
        sourcesValue.setLayoutY(0);
        solutionDisplayPane.getChildren().addAll(sources, sourcesValue);

        Label sinks = new Label("Sinks:");
        sinks.setLayoutX(86);
        sinks.setLayoutY(20);
        Label sinksValue = new Label("-");
        sinksValue.setLayoutX(135);
        sinksValue.setLayoutY(20);
        solutionDisplayPane.getChildren().addAll(sinks, sinksValue);

        Label stored = new Label("Annual CO2 Stored:");
        stored.setLayoutX(12);
        stored.setLayoutY(40);
        Label storedValue = new Label("-");
        storedValue.setLayoutX(135);
        storedValue.setLayoutY(40);
        solutionDisplayPane.getChildren().addAll(stored, storedValue);

        Label edges = new Label("Edges:");
        edges.setLayoutX(82);
        edges.setLayoutY(60);
        Label edgesValue = new Label("-");
        edgesValue.setLayoutX(135);
        edgesValue.setLayoutY(60);
        solutionDisplayPane.getChildren().addAll(edges, edgesValue);

        Label length = new Label("Project Length:");
        length.setLayoutX(37);
        length.setLayoutY(80);
        Label lengthValue = new Label("-");
        lengthValue.setLayoutX(135);
        lengthValue.setLayoutY(80);
        solutionDisplayPane.getChildren().addAll(length, lengthValue);

        Label total = new Label("Total Cost\n   ($)"); // Originally: "Total Cost\n   ($m/yr)"
        total.setLayoutX(65);
        total.setLayoutY(120);
        Label unit = new Label("Unit Cost\n ($/tCO2)"); 
        unit.setLayoutX(150);
        unit.setLayoutY(120);
        solutionDisplayPane.getChildren().addAll(total, unit);

        Label cap = new Label("Capture:");
        cap.setLayoutX(4);
        cap.setLayoutY(150);
        Label capT = new Label("-");
        capT.setLayoutX(75);
        capT.setLayoutY(150);
        Label capU = new Label("-");
        capU.setLayoutX(160);
        capU.setLayoutY(150);
        solutionDisplayPane.getChildren().addAll(cap, capT, capU);

        Label trans = new Label("Transport:");
        trans.setLayoutX(4);
        trans.setLayoutY(170);
        Label transT = new Label("-");
        transT.setLayoutX(75);
        transT.setLayoutY(170);
        Label transU = new Label("-");
        transU.setLayoutX(160);
        transU.setLayoutY(170);
        solutionDisplayPane.getChildren().addAll(trans, transT, transU);

        Label stor = new Label("Storage:");
        stor.setLayoutX(4);
        stor.setLayoutY(190);
        Label storT = new Label("-");
        storT.setLayoutX(75);
        storT.setLayoutY(190);
        Label storU = new Label("-");
        storU.setLayoutX(160);
        storU.setLayoutY(190);
        solutionDisplayPane.getChildren().addAll(stor, storT, storU);

        Label tot = new Label("Total:");
        tot.setLayoutX(4);
        tot.setLayoutY(210);
        Label totT = new Label("-");
        totT.setLayoutX(75);
        totT.setLayoutY(210);
        Label totU = new Label("-");
        totU.setLayoutX(160);
        totU.setLayoutY(210);
        solutionDisplayPane.getChildren().addAll(tot, totT, totU);
        
        // Populate uncertainty pane
        /* toggleUncertaintyPane contains toggles for whether working with 
        deterministic model or one of the uncertainty models */
        AnchorPane toggleUncertaintyPane = new AnchorPane();
        toggleUncertaintyPane.setPrefSize(192, 30);
        toggleUncertaintyPane.setMinSize(0, 0);
        
        TitledPane uncToggleContainer = new TitledPane("Toggle Uncertainty", toggleUncertaintyPane);
        uncToggleContainer.setCollapsible(false);
        uncToggleContainer.setPrefSize(203, 52);
        uncToggleContainer.setLayoutX(14);
        uncToggleContainer.setLayoutY(5);
        
        selectDeterministic = new RadioButton("Deterministic");
        selectUncertainty = new RadioButton("Uncertainty");
        ToggleGroup uncertaintyToggleGroup = new ToggleGroup();
        selectDeterministic.setToggleGroup(uncertaintyToggleGroup);
        selectUncertainty.setToggleGroup(uncertaintyToggleGroup);
        
        selectDeterministic.setLayoutX(4);
        selectDeterministic.setLayoutY(4);
        toggleUncertaintyPane.getChildren().add(selectDeterministic);
        selectDeterministic.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldval, Boolean newVal) {
                if(!oldval) {
                    underUncertainty = false;
                    uncertaintySolutionChoice.setDisable(true);
                    runChoice.setDisable(false);
                } else {
                    underUncertainty = true;
                    runChoice.setDisable(true);
                    uncertaintySolutionChoice.setDisable(false);
                }
            }
        });
        
        selectUncertainty.setLayoutX(103);
        selectUncertainty.setLayoutY(4);
        toggleUncertaintyPane.getChildren().add(selectUncertainty);
        
        // selectUncertaintyModel contains toggels for base uncertainty and heat map options
        AnchorPane selectUncertaintyModel = new AnchorPane();
        selectUncertaintyModel.setPrefSize(192, 30);
        selectUncertaintyModel.setMinSize(0, 0);
        
        TitledPane uncertaintyContainer = new TitledPane("Select Uncertainty Model", selectUncertaintyModel);
        uncertaintyContainer.setCollapsible(false);
        uncertaintyContainer.setPrefSize(203, 52);
        uncertaintyContainer.setLayoutX(14);
        uncertaintyContainer.setLayoutY(67);
        
        selectBaseUncertainty = new RadioButton("Base Model");
        selectHeatMap = new RadioButton("Heat Map");
        ToggleGroup uncertaintySelector = new ToggleGroup();
        selectBaseUncertainty.setToggleGroup(uncertaintySelector);
        selectHeatMap.setToggleGroup(uncertaintySelector);
        
        selectBaseUncertainty.setLayoutX(4);
        selectBaseUncertainty.setLayoutY(4);
        selectUncertaintyModel.getChildren().add(selectBaseUncertainty);
        selectBaseUncertainty.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean newVal) {
                if(!oldVal) {
                    underBaseUncertainty = true;
                    uncertaintySolutionChoice.setDisable(true);
                    runChoice.setDisable(false);
                    maxFlowHM.setDisable(true);
                } else {
                    underBaseUncertainty = false; 
                    runChoice.setDisable(false);
                    uncertaintySolutionChoice.setDisable(true);
                    maxFlowHM.setDisable(false);
                }
                // set interest rate and numYears to avoid errors
                interestRate = 0;
                numYears = 0;
            }
        });
        
        selectHeatMap.setLayoutX(100);
        selectHeatMap.setLayoutY(4);
        selectUncertaintyModel.getChildren().add(selectHeatMap);
        
        // calculateBaseSltnsPane contains button that calculates the base solutions for a dataset
        AnchorPane calculateBaseSltnsPane = new AnchorPane();
        calculateBaseSltnsPane.setPrefSize(192, 30);
        calculateBaseSltnsPane.setMinSize(0,0);
        
        TitledPane baseSltnContainer = new TitledPane("Base Solutions", calculateBaseSltnsPane);
        baseSltnContainer.setCollapsible(false);
        baseSltnContainer.setPrefSize(203, 61);
        baseSltnContainer.setLayoutX(14);
        baseSltnContainer.setLayoutY(128);
        
        Button baseSltnSolve = new Button("Solve for Base Solutions");
        baseSltnSolve.setLayoutX(29);
        baseSltnSolve.setLayoutY(5);
        calculateBaseSltnsPane.getChildren().add(baseSltnSolve);
        baseSltnSolve.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                String modelVersion = "";
                if (capVersion.isSelected()) {
                    modelVersion = "c";
                } else if (priceVersion.isSelected()) {
                    modelVersion = "p";
                }

                if (timeVersion.isSelected()) {
                    modelVersion += "t";
                }

                RadioButton selectedSolver = (RadioButton) solver.getSelectedToggle();

                double interestRateInner = Double.parseDouble(rateValue.getText());
                int numYearsInner = Integer.parseInt(yearValue.getText());
                double modelParamValue = 0.0;
                if (!paramValue.getText().equals("")) {
                    modelParamValue = Double.parseDouble(paramValue.getText());
                }
                if(underUncertainty) {
                    controlActions.calculateBaseSolutions(interestRateInner, numYearsInner, modelParamValue, modelVersion, selectedSolver.getText());
                }
                messenger.setText("Finished calculating base solutions");
            }
        });
        
        uncertaintyPane.getChildren().add(uncertaintyContainer);
        uncertaintyPane.getChildren().add(baseSltnContainer);
        uncertaintyPane.getChildren().add(uncToggleContainer);            

        Label[] solutionValues = new Label[]{sourcesValue, sinksValue, storedValue, edgesValue, lengthValue, capT, capU, transT, transU, storT, storU, totT, totU};

        // hmSlider listener located here to make use of solutionValues Label array
        hmSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue <? extends Number> observable, Number oldVal, Number newVal) {
                hmPercentToDisp = (double)newVal / 100.0; // Value divided by 100 to make ControlActions operations easier
                messenger.setText("HM Slider Value: " + (hmPercentToDisp * 100.0) + "%\nVisible Elements Appear in >= The % Displayed of Solutions");
                if(hmPath != null) {
                    if(maxFlowHM.isSelected()) {
                        // Displays Max Flow solution for permutation of elements at slider value
                        controlActions.displayMaxFlowMinCostHeatMap(solutionValues);
                    } else if(!maxFlowHM.isSelected()) {
                        // Changes HM display to new slider value
                        controlActions.selectHeatMapSolutions(hmPath, solutionValues);
                    }
                }
            }
        });
        solutionPane.getChildren().add(hmSlider);
        
        maxFlowHM.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> selected, Boolean oldVal, Boolean newVal) {
                // call a controlActions method to redisplay heat map 
                if(maxFlowHM.isSelected()) {
                    controlActions.displayMaxFlowMinCostHeatMap(solutionValues);
                } else if(!maxFlowHM.isSelected()) {
                    controlActions.selectHeatMapSolutions(hmPath, solutionValues);
                }
            }
        });
        
        // Run selection action. 
        /* Solution selection listener selects solution to display from file 
        directory according to conditions set */
        runChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> selected, String oldSolution, String newSolution) {
                if(underUncertainty && !underBaseUncertainty) {
                    controlActions.selectHeatMapSolutions(newSolution, solutionValues);
                    controlActions.outputHeatMapData(newSolution);
                    hmPath = newSolution;
                    // calculates and saves to file all the max flow solutions
                    messenger.setText("Writing Max Flow Files");
                    messenger.setText("Finished Writing Max Flow Files");
                } else {
                    controlActions.selectSolution(newSolution, solutionValues); // displays deterministic solution
                }
                
            }
        });
        runChoice.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                controlActions.initializeSolutionSelection(runChoice);
            }
        });
        runChoice.setOnScroll(new EventHandler<ScrollEvent>() {
            @Override
            public void handle(ScrollEvent s) {
                double direction = s.getDeltaY();
                String currentChoice = (String) runChoice.getValue();
                ObservableList<String> choices = runChoice.getItems();
                int index = choices.indexOf(currentChoice);
                if (direction < 0 && index < choices.size() - 1) {
                    runChoice.setValue(choices.get(index + 1));
                } else if (direction > 0 && index > 0) {
                    runChoice.setValue(choices.get(index - 1));
                }
            }
        });

        // Solution sub directory selection action. (for time options selected - essentially unused)
        solutionChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> selected, String oldSolution, String newSolution) {
                controlActions.selectSubSolution((String) runChoice.getValue(), newSolution, solutionValues);
            }
        });
        
        // Controls folder selected when selecting heat map solutions
        uncertaintySolutionChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> selected, String oldSolution, String newSolution) {
                controlActions.selectParentFolder(newSolution, solutionValues);
            }
        });
        uncertaintySolutionChoice.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if(isShowing) {
                controlActions.initializeSelectedParentFolder(uncertaintySolutionChoice);
            }
        });
        
        // Also controls folder selection for different base splutions within solution choice
        realizationCategoryChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> selected, String oldSolution, String newSolution) {
                controlActions.selectCategoryFolder(newSolution, parentPath, solutionValues);
            }
        });
        realizationCategoryChoice.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if(isShowing) {
                controlActions.initializeSelectedCategoryFolder(realizationCategoryChoice, parentPath);
            }
        });

        Pane solutionLayer = new Pane();
        displayPane.getChildren().add(solutionLayer);
        controlActions.addSolutionLayer(solutionLayer);
        sceneGestures.addEntityToResize(solutionLayer);
        solutionLayer.setPickOnBounds(false);

        // Add everything to group and display.
        group.getChildren().addAll(displayPane, tabPane, messengerPane);
        return new Scene(group, 1062, 660); // og x = 1050
    }
    
    // Below are methods for transferring and resetting info, along with get set methods
    public void enableUncertaintySolutionChoiceMenu() {
        runChoice.setDisable(true);
        uncertaintySolutionChoice.setDisable(false);
    }
    
    public void disableUncertaintySolutionChoiceMenu() {
        runChoice.setDisable(false);
        uncertaintySolutionChoice.setDisable(true);
    }
    
    public void enableRealizationCategoryChoiceMenu(String savedPath) {
        realizationCategoryChoice.setDisable(false);
        parentPath = savedPath;
    }
    
    public void disableRealizationCategoryChoiceMenu() {
        realizationCategoryChoice.setDisable(true);
    }

    public void showSubSolutionMenu() {
        solutionPane.getChildren().add(solutionChoice);
    }

    public void hideSubSolutionMenu() {
        solutionPane.getChildren().remove(solutionChoice);
    }

    public ChoiceBox getSolutionChoice() {
        return solutionChoice;
    }

    public void displayCostSurface() {
        dispCostSurface.setSelected(true);
    }
    
    public void setStartingUncertaintyCondition() {
        selectDeterministic.setSelected(true);
        selectBaseUncertainty.setSelected(true);
    }
    
    public boolean getUnderUncertainty() {
        return underUncertainty;
    }
    
    public boolean getUnderBaseUncertainty() {
        return underBaseUncertainty;
    }
    
    public boolean getInCapMode() {
        return capVersion.isSelected();
    }
    
    public boolean getInPriceMode() {
        return priceVersion.isSelected();
    }
    
    public void setHMSliderValue(double sliderVal) {
        hmSlider.setValue(sliderVal);
    }

    public void fullReset() {
        //scenarioChoice;
        dispDelaunayEdges.setSelected(false);
        dispCandidateNetwork.setSelected(false);
        sourceLabeled.setSelected(false);
        sourceVisible.setSelected(false);
        sinkLabeled.setSelected(false);
        sinkVisible.setSelected(false);
        dispCostSurface.setSelected(false);
        messenger.setText("");
    }

    public void softReset() {
        dispDelaunayEdges.setSelected(false);
        dispCandidateNetwork.setSelected(false);
        sourceLabeled.setSelected(false);
        sourceVisible.setSelected(false);
        sinkLabeled.setSelected(false);
        sinkVisible.setSelected(false);
        dispCostSurface.setSelected(false);
        messenger.setText("");
    }

    public double getScale() {
        return displayPane.getScale();
    }
    
    public double getHmPercentToDisp() {
        return hmPercentToDisp;
    }
    
    // mfmc get methods
    public double getInterestRate() {
        return interestRate;
    }
            
    public double getNumYears() {
        return numYears;
    }
    
    public double getModelParamValue() {
        return modelParamValue;
    }
    
    public String getSelectedSolverText() {
        return selectedSolverText;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
