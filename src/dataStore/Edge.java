package dataStore;

/**
 *
 * @author yaw
 */
public class Edge {

    public int v1;
    public int v2;
    private boolean isConnected;
    private boolean inCluster;
    private boolean assignedToCell;
    private boolean assignedToClusterRoute;

    public Edge(int v1, int v2) {
        this.v1 = v1;
        this.v2 = v2;
        /* Assumes upon declaration that the edge is not connected to another 
        edge and the edge is not yet declared to be in a cluster */
        isConnected = false;
        inCluster = false;
        assignedToCell = false;
        assignedToClusterRoute = false;
    }
    
    public boolean getIsConnected() {
        return isConnected;
    }
    
    public boolean getInCluster() {
        return inCluster;
    }
    
    public boolean getAssignedToCell() {
        return assignedToCell;
    }
    
    public boolean getAssignedToClusterRoute() {
        return assignedToClusterRoute;
    }
    
    public void setIsConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }
    
    public void setInCluster(boolean inCluster) {
        this.inCluster = inCluster;
    }
    
    public void setAssignedToCell(boolean assigned) {
        assignedToCell = assigned;
    }
    
    public void setAssignedToClusterRoute(boolean assigned) {
        assignedToClusterRoute = assigned;
    }
    
    public int getV1() {
        return v1;
    }
    
    public int getV2() {
        return v2;
    }

    @Override
    public int hashCode() {
        return v1 + v2;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Edge other = (Edge) obj;
        return (v1 == other.v1 && v2 == other.v2) || (v1 == other.v2 && v2 == other.v1);
    }
    
    public String toString() {
        return v1 + " <-> " + v2;
    }
    
    public boolean equalsWithDirection(Object obj) {
        final Edge other = (Edge) obj;
        return (v1 == other.v1 && v2 == other.v2);
    }
}
