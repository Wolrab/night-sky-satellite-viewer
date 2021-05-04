import SDP4;
import java.io.*;
import java.lang.Math;

public class Main {
    public static void main(String[] args) {
        try{
            SDP4 s = new SDP4();
            s.Init();

            FileReader f = new FileReader("gp.txt");
            BufferedReader f2 = new BufferedReader(f);

            s.NoradNext(f2);
            s.GetPosVel(59336.35625);
            // Converting to Km instead of Gm (why is it in giga meters?)
            double[] pos = s.itsR;
            pos[0] *= 1000000;
            pos[1] *= 1000000;
            pos[2] *= 1000000;

            System.out.println("Position: " + pos[0] * 1000000 + ", " + pos[1] * 1000000 + ", " + pos[2] * 1000000);
            System.out.println("Latitude: " + getLatitude(pos));
            System.out.println("Longitude: " + getLongitude(pos));
        }
        catch (Exception e) {

        }
    }

    
    // Source for the math for the following function:
    // https://en.wikipedia.org/wiki/Geographic_coordinate_conversion
    // (basically the cliffnotes extracted from a few key papers on the subject)
    public static double getLongitude(double[] pos) {
        double longitude = Math.atan(pos[1]/pos[0]);
        return Math.toDegrees(longitude);
    }

    public static double getLatitude(double[] pos) {
        final double thresh = 0.1;
        double prev = 1.0/(1-Math.exp(2)); // Initial estimate
        double latitude = iterateLatitude(pos, prev);

        System.out.println("Prev: " + prev);
        System.out.println("latitude: " + latitude);
        System.out.println();

        while (Math.abs(latitude - prev) >= thresh) {
            prev = latitude;
            latitude = iterateLatitude(pos, prev);
            
            System.out.println("Prev: " + prev);
            System.out.println("latitude: " + latitude);
            System.out.println();
        }

        return Math.toDegrees(latitude);
    }

    private static double iterateLatitude(double[] pos, double prev) {
        final double a = 6378;
        final double b = 6357;
        double p_squared = Math.pow(Math.sqrt(Math.pow(pos[0], 2.0) + Math.pow(pos[1], 2.0)), 2.0);
        double ecc_squared = 1-Math.pow(b/a, 2.0);

        double c_prev = Math.pow(p_squared + (1-ecc_squared) * Math.pow(pos[2], 2.0) * Math.pow(prev, 2.0), 3.0/2.0);

        c_prev /= a * ecc_squared;
        
        double next = 1 + (p_squared+(1-ecc_squared)*Math.pow(pos[2], 2.0)*Math.pow(prev, 3.0))/(c_prev-p_squared);
        return next;
    }
}