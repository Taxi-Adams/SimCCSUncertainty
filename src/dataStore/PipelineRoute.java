/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dataStore;

import java.util.HashMap;
import java.util.Stack;

/**
 *
 * @author danie
 */
public class PipelineRoute {
    // Instance fields
    private Stack<Edge> pipelineEdges;// Stores edges in stack
    private HashMap<Edge, Double> hmEdgesAveVol;
    private boolean v1Tov2;
    private boolean v2Tov1;
    
    public PipelineRoute(HashMap<Edge, Double> hmEdgesAveVol) {
        pipelineEdges = new Stack<>();
        v1Tov2 = false;
        v2Tov1 = false;
        this.hmEdgesAveVol = hmEdgesAveVol;
    }
    
    public void addEdgeToRoute(Edge edge) {
        pipelineEdges.add(edge);
    }
    
    public Stack<Edge> getPipelineEdges() {
        return pipelineEdges;
    }
    
    public boolean getv1Tov2() {
        return v1Tov2;
    }
    
    public boolean getv2Tov1() {
        return v2Tov1;
    }
    
    public void setv1Tov2(boolean isv1Tov2) {
        v1Tov2 = isv1Tov2;
    }
    
    public void setv2Tov1(boolean isv2Tov1) {
        v2Tov1 = isv2Tov1;
    }
    
    public void printPipelineRoute() {
        // for debugging
//        System.out.println("v1 to v2: " + v1Tov2);
//        System.out.println("v2 to v1: " + v2Tov1);
        for(Edge edge : pipelineEdges) {
            System.out.println(edge);
        }
    }
    
    public double getRouteAverageVolume(int numbOfRealizations) {
        double routeAverageVolume = 0.0;
        // Amount should be the same for any edge in stack when rounded
        if(!pipelineEdges.isEmpty()) {
            routeAverageVolume = Math.round(hmEdgesAveVol.get(pipelineEdges.peek()) / numbOfRealizations * 100.0) / 100.0;
        }
//        System.out.println("Route Average Volume: " + routeAverageVolume);
        return routeAverageVolume;
    }
}
