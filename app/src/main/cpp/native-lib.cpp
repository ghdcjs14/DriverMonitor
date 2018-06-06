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

    JNIEXPORT jlongArray JNICALL
    Java_kr_ac_kaist_drivermonitor_MainActivity_detect(JNIEnv *env, jclass type,
                                                       jlong cascadeClassifier_face,
                                                       jlong cascadeClassifier_eye,
                                                       jlong matAddrInput,
                                                       jlong matAddrResult) {

        // TODO
        Mat &img_input = *(Mat *) matAddrInput;
        Mat &img_result = *(Mat *) matAddrResult;
        double bpm = 0;
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


            Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,
                           real_facesize_height);
            faceROI = img_input(face_area);

            // 1. faceROI를 RGB channels로 split한다.
            split(faceROI, channels); // 0: Blue, 1: Green, 2: Red

            // faceROI의 색깔 별 평균 구하기
            //meanR = mean(channels[2]);
            //meanG = mean(channels[1]);
            //meanB = mean(channels[0]);
            //greenTraceQueue.push(meanG.val[0]);

            // 2. green 픽셀들을 normalize 한다.
            normalize(channels[1], normGreen,0,1,NORM_MINMAX,CV_32F);

            // 3. normalize 된 green 픽셀들에 대해 DFT를 통해 주파수 영역으로 분리 시킨다.
            dft(normGreen, dftGreen, DFT_COMPLEX_OUTPUT);

            // dft의 평균
            double meanval0 = mean(dftGreen).val[0];

            // 4. 주파수 영역의 x축 y축을 이용하여 magnitude를 구하고 max 값을 추출한다.
            Mat magGreen, gx, gy;
            double minVal,maxVal;

            Sobel(dftGreen, gx, dftGreen.depth(), 1,0,3);
            Sobel(dftGreen, gy, dftGreen.depth(), 0,1,3);
            magnitude(gx,gy,magGreen);
            minMaxLoc(abs(magGreen), &minVal, &maxVal);

            // 5. Max magnitude를 이용하여 주파수를 얻고 bpm으로 바꾼다.
            double fps = 5;
            double feq = 0; // 초기 주파수 값
            double tempFeq = maxVal * fps / (dftGreen.rows * dftGreen.cols);

            if(tempFeq >= 0.75 && tempFeq >= 4) {
                // 초기 주파수가 측정 안되어 있을 때, 이전 bpm과 차이가 12bpm 이상 나는 것은 무시
                if(feq == 0 || abs(feq-tempFeq)*60 <= 12) {
                    feq = tempFeq;
                }
            }
            bpm = feq * 60;

            // 6. bpm을 화면에 출력한다.
            meanval0 = meanval0 * 60;
            char ss[256];
            sprintf(ss,"%.2f",tempFeq);
            putText(img_result, ss
                    , Point(real_facesize_x + real_facesize_width + 10, real_facesize_y)
                    , FONT_HERSHEY_SIMPLEX
                    , 2, Scalar(255,255,255), 2);

            sprintf(ss,"%.2f",feq);
            putText(img_result, ss
                    , Point(real_facesize_x + real_facesize_width + 10, real_facesize_y+50)
                    , FONT_HERSHEY_SIMPLEX
                    , 2, Scalar(255,0,255), 2);


        }
        jlongArray ret = env->NewLongArray(2);
        jlong *clongArray = new jlong[2];
        clongArray[0] = faces.size();
        clongArray[1] = bpm;
        env->SetLongArrayRegion(ret, 0, 2, clongArray);
        delete [] clongArray;
        return ret;

    }


}