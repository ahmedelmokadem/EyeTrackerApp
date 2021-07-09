package opencv.mokadem.ahmed.eyetracker;

import android.graphics.Point;

/**
 * Created by Tofaa on 10/21/2017.
 */

public class NativeClass {

    //public native static Point findFeatures(long mGray, int x,int y) ;
    public native static String FindFeatures(long matAddrGr, long matAddrRgba);
    public native static String FindCenter(long matFace,int x , long y , long width , long height);
}
