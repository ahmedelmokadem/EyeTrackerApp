#include <opencv_mokadem_ahmed_eyetracker_NativeClass.h>
JNIEXPORT jstring JNICALL Java_opencv_mokadem_ahmed_eyetracker_NativeClass_FindFeatures
        (JNIEnv *env, jclass, jlong addrGray, jlong)
{

    Mat& mGr  = *(Mat*)addrGray;
   // Mat& mRgb = *(Mat*)addrRgba;
    vector<KeyPoint> v;
    int array[2];
    char msg[1000] = "";
    char buffer[30] = "";
    char seperator[1] = {','};
    char semicolon[1] = {';'};
    FastFeatureDetector detector(20);
    detector.detect(mGr, v);
    for( unsigned int i = 0; i < v.size(); i++ )
    {
        const KeyPoint& kp = v[i];
       // circle(mRgb, Point(kp.pt.x, kp.pt.y), 10, Scalar(255,0,0,255));
        array[0] = kp.pt.x ;
        array[1] = kp.pt.y ;
        sprintf(buffer,"%d",array[0]); // the x value of the point
        strcat(msg,buffer);
        strcat(msg,seperator);
        sprintf(buffer,"%d",array[1]);
        strcat(msg,buffer);
        strcat(msg,semicolon);




        //TODO : we need to handle each point in thaat string buy addin a , between each x and y and ; between each point
    }

    return (*env).NewStringUTF(msg);
}


JNIEXPORT jstring JNICALL Java_opencv_mokadem_ahmed_eyetracker_NativeClass_FindCenter
        (JNIEnv *env, jclass, jlong addFace, jint xeye,jlong yeye,jlong weye ,jlong heye)
{

    // trash
    char msg[1000] = "";
    char buffer[30] = "";
    char seperator[1] = {','};



    Mat& face  = *(Mat*)addFace;
    // Mat& eyeMat = *(Mat*)addEye;

    Rect eye(xeye,yeye,weye,heye)  ;

    const int kFastEyeWidth = 50;
    const int kWeightBlurSize = 5;
    const bool kEnableWeight = true;
    const float kWeightDivisor = 1.0;
    const double kGradientThreshold = 50.0;

    // Postprocessing
    const bool kEnablePostProcess = true;
    const float kPostProcessThreshold = 0.97;

    // Debugging
    const bool kPlotVectorField = false;

//    sprintf(buffer,"%d",face.cols);
//    strcat(msg,buffer);
//    strcat(msg,seperator);
//    sprintf(buffer,"%d",face.rows);
//    strcat(msg,buffer);
//    strcat(msg,seperator);
//    sprintf(buffer,"%d",eye.x);
//    strcat(msg,buffer);
//    strcat(msg,seperator);
//    sprintf(buffer,"%d",eye.y);
//    strcat(msg,buffer);
//    strcat(msg,seperator);
//    sprintf(buffer,"%d",eye.width);
//    strcat(msg,buffer);
//    strcat(msg,seperator);
//    sprintf(buffer,"%d",eye.height);
//    strcat(msg,buffer);
//    strcat(msg,seperator);
//    sprintf(buffer,"%d",xeye);
//    strcat(msg,buffer);
//    strcat(msg,seperator);
//    sprintf(buffer,"%d",yeye);
//    strcat(msg,buffer);
//    strcat(msg,seperator);
//    sprintf(buffer,"%d",weye);
//    strcat(msg,buffer);
//    strcat(msg,seperator);
//    sprintf(buffer,"%d",heye);
//    strcat(msg,buffer);
//    strcat(msg,seperator);

    cv::Mat eyeROIUnscaled = face(eye);

    cv::Mat eyeROI;
    //scaleToFastSize(eyeROIUnscaled, eyeROI);
    cv::resize(eyeROIUnscaled, eyeROI, cv::Size(kFastEyeWidth,(((float)kFastEyeWidth)/eyeROIUnscaled.cols) * eyeROIUnscaled.rows));
    // draw eye region
    //rectangle(face,eye,1234);
    //-- Find the gradient
    // cv::Mat gradientX = computeMatXGradient(eyeROI);
    cv::Mat gradientX(eyeROI.rows,eyeROI.cols,CV_64F);

    for (int y = 0; y < eyeROI.rows; ++y) {
        const uchar *Mr = eyeROI.ptr<uchar>(y);
        double *Or = gradientX.ptr<double>(y);

        Or[0] = Mr[1] - Mr[0];
        for (int x = 1; x < eyeROI.cols - 1; ++x) {
            Or[x] = (Mr[x+1] - Mr[x-1])/2.0;
        }
        Or[eyeROI.cols-1] = Mr[eyeROI.cols-1] - Mr[eyeROI.cols-2];
    }

    //cv::Mat gradientY = computeMatXGradient(eyeROI.t()).t();
    cv::Mat gradientY =eyeROI.t();
    cv::Mat tmp(gradientY.rows,gradientY.cols,CV_64F);
    for (int y = 0; y < gradientY.rows; ++y) {
        const uchar *Mr = gradientY.ptr<uchar>(y);
        double *Or = tmp.ptr<double>(y);

        Or[0] = Mr[1] - Mr[0];
        for (int x = 1; x < gradientY.cols - 1; ++x) {
            Or[x] = (Mr[x+1] - Mr[x-1])/2.0;
        }
        Or[gradientY.cols-1] = Mr[gradientY.cols-1] - Mr[gradientY.cols-2];
    }
    gradientY = tmp.t();


    //-- Normalize and threshold the gradient
    // compute all the magnitudes
    // cv::Mat mags = matrixMagnitude(gradientX, gradientY);///////////////////////

    cv::Mat mags(gradientX.rows,gradientX.cols,CV_64F);
    for (int y = 0; y < gradientX.rows; ++y) {
        const double *Xr = gradientX.ptr<double>(y), *Yr = gradientY.ptr<double>(y);
        double *Mr = mags.ptr<double>(y);
        for (int x = 0; x < gradientX.cols; ++x) {
            double gX = Xr[x], gY = Yr[x];
            double magnitude = sqrt((gX * gX) + (gY * gY));
            Mr[x] = magnitude;
        }
    }
    //  return mags;


    //compute the threshold
    // double gradientThresh = computeDynamicThreshold(mags, kGradientThreshold);//////////////////
    cv::Scalar stdMagnGrad, meanMagnGrad;
    cv::meanStdDev(mags, meanMagnGrad, stdMagnGrad);
    double stdDev = stdMagnGrad[0] / sqrt(mags.rows*mags.cols);
    double gradientThresh= kGradientThreshold * stdDev + meanMagnGrad[0];


    //double gradientThresh = kGradientThreshold;
    //double gradientThresh = 0;
    //normalize
    for (int y = 0; y < eyeROI.rows; ++y) {
        double *Xr = gradientX.ptr<double>(y), *Yr = gradientY.ptr<double>(y);
        const double *Mr = mags.ptr<double>(y);
        for (int x = 0; x < eyeROI.cols; ++x) {
            double gX = Xr[x], gY = Yr[x];
            double magnitude = Mr[x];
            if (magnitude > gradientThresh) {
                Xr[x] = gX/magnitude;
                Yr[x] = gY/magnitude;
            } else {
                Xr[x] = 0.0;
                Yr[x] = 0.0;
            }
        }
    }

    //imshow(debugWindow,gradientX);
    //-- Create a blurred and inverted image for weighting
    cv::Mat weight;
    GaussianBlur( eyeROI, weight, cv::Size( kWeightBlurSize, kWeightBlurSize ), 0, 0 );
    for (int y = 0; y < weight.rows; ++y) {
        unsigned char *row = weight.ptr<unsigned char>(y);
        for (int x = 0; x < weight.cols; ++x) {
            row[x] = (255 - row[x]);
        }
    }
    //imshow(debugWindow,weight);
    //-- Run the algorithm!
    cv::Mat outSum = cv::Mat::zeros(eyeROI.rows,eyeROI.cols,CV_64F);
    // for each possible gradient location
    // Note: these loops are reversed from the way the paper does them
    // it evaluates every possible center for each gradient location instead of
    // every possible gradient location for every center.
    //printf("Eye Size: %ix%i\n",outSum.cols,outSum.rows);

    for (int y = 0; y < weight.rows; ++y) {
        const double *Xr = gradientX.ptr<double>(y), *Yr = gradientY.ptr<double>(y);
        for (int x = 0; x < weight.cols; ++x) {
            double gX = Xr[x], gY = Yr[x];
            if (gX == 0.0 && gY == 0.0) {
                continue;
            }

            //testPossibleCentersFormula(x, y, weight, gX, gY, outSum);
            // for all possible centers
            for (int cy = 0; cy < outSum.rows; ++cy) {
                double *Or = outSum.ptr<double>(cy);
                const unsigned char *Wr = weight.ptr<unsigned char>(cy);
                for (int cx = 0; cx < outSum.cols; ++cx) {
                    if (x == cx && y == cy) {
                        continue;
                    }
                    // create a vector from the possible center to the gradient origin
                    double dx = x - cx;
                    double dy = y - cy;
                    // normalize d
                    double magnitude = sqrt((dx * dx) + (dy * dy));
                    dx = dx / magnitude;
                    dy = dy / magnitude;
                    double dotProduct = dx*gX + dy*gY;
                    dotProduct = std::max(0.0,dotProduct);
                    // square and multiply by the weight
                    if (kEnableWeight) {
                        Or[cx] += dotProduct * dotProduct * (Wr[cx]/kWeightDivisor);
                    } else {
                        Or[cx] += dotProduct * dotProduct;
                    }
                }
            }



        }
    }
    // scale all the values down, basically averaging them
    double numGradients = (weight.rows*weight.cols);
    cv::Mat out;
    outSum.convertTo(out, CV_32F,1.0/numGradients);
    //imshow(debugWindow,out);
    //-- Find the maximum point
    cv::Point maxP;
    double maxVal;
    cv::minMaxLoc(out, NULL,&maxVal,NULL,&maxP);
    //-- Flood fill the edges
    if(kEnablePostProcess) {
        cv::Mat floodClone;
        //double floodThresh = computeDynamicThreshold(out, 1.5);
        double floodThresh = maxVal * kPostProcessThreshold;
        cv::threshold(out, floodClone, floodThresh, 0.0f, cv::THRESH_TOZERO);
        if(kPlotVectorField) {
            //plotVecField(gradientX, gradientY, floodClone);
           // imwrite("eyeFrame.png",eyeROIUnscaled);
        }

        //cv::Mat mask = floodKillEdges(floodClone);

        //rectangle(floodClone,cv::Rect(0,0,floodClone.cols,floodClone.rows),255);

        cv::Mat mask(floodClone.rows, floodClone.cols, CV_8U, 255);
        std::queue<cv::Point> toDo;
        toDo.push(cv::Point(0,0));
        while (!toDo.empty()) {
            cv::Point p = toDo.front();
            toDo.pop();
            if (floodClone.at<float>(p) == 0.0f) {
                continue;
            }




            // add in every direction
            cv::Point np(p.x + 1, p.y); // right
            if (np.x >= 0 && np.x < floodClone.cols && np.y >= 0 && np.y < floodClone.rows) toDo.push(np);
            np.x = p.x - 1; np.y = p.y; // left
            if (np.x >= 0 && np.x < floodClone.cols && np.y >= 0 && np.y < floodClone.rows) toDo.push(np);
            np.x = p.x; np.y = p.y + 1; // down
            if (np.x >= 0 && np.x < floodClone.cols && np.y >= 0 && np.y < floodClone.rows) toDo.push(np);
            np.x = p.x; np.y = p.y - 1; // up
            if (np.x >= 0 && np.x < floodClone.cols && np.y >= 0 && np.y < floodClone.rows) toDo.push(np);
            // kill it
            floodClone.at<float>(p) = 0.0f;
            mask.at<uchar>(p) = 0;
        }

        //imshow(debugWindow + " Mask",mask);
        //imshow(debugWindow,out);
        // redo max
        cv::minMaxLoc(out, NULL,&maxVal,NULL,&maxP,mask);
    }

    //cv::Point unscalePoint(cv::Point p, cv::Rect origSize);
    float ratio = (((float)kFastEyeWidth)/eye.width);
    int x = floor((maxP.x / ratio)+0.5);
    int y = floor((maxP.y / ratio)+0.5);
   // msg = "" ;
    sprintf(buffer,"%d",x); // the x value of the point
    strcat(msg,buffer);
    strcat(msg,seperator);
    sprintf(buffer,"%d",y); // the x value of the point
    strcat(msg,buffer);
    //strcat(msg,seperator);
    return (*env).NewStringUTF(msg);
}


