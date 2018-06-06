#include <jni.h>
#include <android/log.h>
#include <queue>
#include <iostream>
#include <cmath>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/objdetect/objdetect.hpp>

using namespace cv;
using namespace std;

extern "C" {

    JNIEXPORT jlong JNICALL
    Java_kr_ac_kaist_drivermonitor_MainActivity_loadCascade(JNIEnv *env, jclass type,
                                                            jstring cascadeFileName_) {
        const char *nativeFileNameString = env->GetStringUTFChars(cascadeFileName_, 0);

        // TODO
        string baseDir("/storage/emulated/0/");
        baseDir.append(nativeFileNameString);
        const char *pathDir = baseDir.c_str();

        jlong ret = 0;
        ret = (jlong) new CascadeClassifier(pathDir);
        if (((CascadeClassifier *) ret)->empty()) {
            __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",
                                "CascadeClassifier로 로딩 실패  %s", nativeFileNameString);
        }
        else
            __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",
                                "CascadeClassifier로 로딩 성공 %s", nativeFileNameString);

        env->ReleaseStringUTFChars(cascadeFileName_, nativeFileNameString);

        return ret;

    }

    float resize(Mat img_src, Mat &img_resize, int resize_width){

        float scale = resize_width / (float)img_src.cols ;
        if (img_src.cols > resize_width) {
            int new_height = cvRound(img_src.rows * scale);
            resize(img_src, img_resize, Size(resize_width, new_height));
        }
        else {
            img_resize = img_src;
        }
        return scale;
    }

    JNIEXPORT void JNICALL
    Java_kr_ac_kaist_drivermonitor_MainActivity_detect(JNIEnv *env, jclass type,
                                                       jlong cascadeClassifier_face,
                                                       jlong cascadeClassifier_eye,
                                                       jlong matAddrInput,
                                                       jlong matAddrResult) {

        // TODO
        Mat &img_input = *(Mat *) matAddrInput;
        Mat &img_result = *(Mat *) matAddrResult;

        img_result = img_input.clone();

        std::vector<Rect> faces;
        Mat img_gray;

        cvtColor(img_input, img_gray, COLOR_BGR2GRAY);
        equalizeHist(img_gray, img_gray);

        Mat img_resize;
        float resizeRatio = resize(img_gray, img_resize, 640);

        //-- Detect faces
        ((CascadeClassifier *) cascadeClassifier_face)
                ->detectMultiScale( img_resize, faces, 1.1, 2, 0|CASCADE_SCALE_IMAGE, Size(30, 30) );


//        __android_log_print(ANDROID_LOG_DEBUG, (char *) "native-lib :: ",
//                            (char *) "face %d found ", faces.size());

        Mat faceROI;
        vector<Mat> channels;
        Scalar meanR, meanG, meanB;
        queue<double> greenTraceQueue;
        Mat normGreen, dftGreen;

        for (int i = 0; i < faces.size(); i++) {
            double real_facesize_x = faces[i].x / resizeRatio;
            double real_facesize_y = faces[i].y / resizeRatio;
            double real_facesize_width = faces[i].width / resizeRatio;
            double real_facesize_height = faces[i].height / resizeRatio;

            Point center(real_facesize_x + real_facesize_width / 2,
                         real_facesize_y + real_facesize_height / 2);
            rectangle(img_result, Point(real_facesize_x, real_facesize_y)
                    , Point(real_facesize_x + real_facesize_width, real_facesize_y + real_facesize_height), Scalar(255, 0, 0), 30, 8, 0);
//            ellipse(img_result, center, Size(real_facesize_width / 2, real_facesize_height / 2), 0,
//                    0, 360,
//                    Scalar(255, 0, 255), 30, 8, 0);


            Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,
                           real_facesize_height);
            faceROI = img_input(face_area);
            split(img_input, channels); // 0: Blue, 1: Green, 2: Red

            // 1. faceROI의 색깔 별 평균 구하기
            //meanR = mean(channels[2]);
//            meanG = mean(channels[1]);
            //meanB = mean(channels[0]);

//            greenTraceQueue.push(meanG.val[0]);



            normalize(channels[1], normGreen,0,1,NORM_MINMAX,CV_32F);

            dft(normGreen, dftGreen, DFT_COMPLEX_OUTPUT);

//            double magGreen = max(magnitude(dftGreen.d,dftGreen[1],dftGreen[0]))



            double meanval0 = mean(dftGreen).val[0];
//            double meanval1 = mean(dftGreen).val[1];
//            double meanval2 = mean(dftGreen).val[2];
//            double meanval3 = mean(dftGreen).val[3];
//            __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",
//                                "meanval0: %.2f", meanval0);
//            __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",
//                                "meanval1: %.2f", meanval1);
//            __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",
//                                "meanval2: %.2f", meanval2);
//            __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",
//                                "meanval3: %.2f", meanval3);
            Mat magGreen, gx, gy;
            Sobel(dftGreen, gx, dftGreen.depth(), 1,0,3);
            Sobel(dftGreen, gy, dftGreen.depth(), 0,1,3);
            magnitude(gx,gy,magGreen);
            double minVal,maxVal;
            minMaxLoc(magGreen, &minVal, &maxVal);
//            double fps = 15;
            double tempFeq = maxVal / (dftGreen.rows * dftGreen.cols);
            double bpm = tempFeq * 60;


//            double tempFeq = magGreen.val[0] * 60.0 / (double)(dftGreen.rows * dftGreen.cols) ;

            meanval0 = meanval0 * 60;
            char ss[256];
            sprintf(ss,"%.2f",meanval0);
            putText(img_result, ss
                    , Point(real_facesize_x + real_facesize_width + 10, real_facesize_y)
                    , FONT_HERSHEY_SIMPLEX
                    , 2, Scalar(255,255,255), 2);

            sprintf(ss,"%.2f",bpm);
            putText(img_result, ss
                    , Point(real_facesize_x + real_facesize_width + 10, real_facesize_y+50)
                    , FONT_HERSHEY_SIMPLEX
                    , 2, Scalar(255,0,255), 2);



        }

    }


}