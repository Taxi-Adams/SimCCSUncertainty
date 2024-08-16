package utilities;

/**
 *
 * @author yaw
 */
public class Utilities {
    public static int[] convertIntegerArray(Integer[] a) {
        int[] returnA = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            returnA[i] = a[i];
        }
        return returnA;
    }

    public static double[] convertDoubleArray(Double[] a) {
        double[] returnA = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            returnA[i] = a[i];
        }
        return returnA;
    }
    
    public static double round(double val, int numAfterDecimal) {
        double div = Math.pow(10, numAfterDecimal);
        return Math.round(val * div) / div;
    }
    
    public static double[] csvToDoubleArray(String s) {
        if (s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1,s.length() - 1);
        }
        String[] components = s.split(",");
        double[] returnArray = new double[components.length];
        for (int i = 0; i < returnArray.length; i++) {
            returnArray[i] = Double.parseDouble(components[i]);
        }
        return returnArray;
    }
    
    public static boolean isDouble(String value) {  
     try {  
         Double.parseDouble(value);  
         return true;  
      } catch (NumberFormatException e) {  
         return false;  
      }  
    }   
    
    public static double degreesToRadians(double degree) {
        return degree * Math.PI / 180.0;
    }
    
    public static double radiansToDegrees(double radians) {
        return radians * 180.0 / Math.PI;
    }
}
