#include <jni.h>
#include <android/log.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/objdetect/objdetect.hpp>

using namespace cv;
using namespace std;

extern "C" {

    // ============================== START fastICA ==================================
    void remean(cv::Mat input,cv::Mat & output) {
        cv::Mat mean;
        cv::reduce(input,mean,0,CV_REDUCE_AVG);
        cv::Mat temp = cv::Mat::ones(input.rows, 1, CV_64FC1);
        output=input-temp*mean;
    }


//    void remean(cv::Mat& input,cv::Mat& output,cv::Mat & mean) {
//        cv::reduce(input,mean,0,CV_REDUCE_AVG);
//        cv::Mat temp = cv::Mat::ones(input.rows, 1, CV_64FC1);
//        output=input-temp*mean;
//    }


//    void whiten(cv::Mat input,cv::Mat &output) {
//        // need to be remean before whiten
//
//        const int N=input.rows;  //num of data
//        const int M=input.cols;  //dimention
//
//        cv::Mat cov;
//        cv::Mat D;
//        cv::Mat E;
//        cv::Mat temp=cv::Mat::eye(M,M,CV_64FC1);
//        cv::Mat temp2;
//
//        cov=input.t()*input/N;
//        cv::eigen(cov,D,E);
//        cv::sqrt(D,D);
//
//        for(int i=0; i<M; i++) {
//            temp.at<double>(i,i)=D.at<double>(i,0);
//        }
//
//        temp2=E*temp.inv()*E.t()*input.t();
//        output=temp2.t();
//    }


    void whiten(cv::Mat input,cv::Mat &output,cv::Mat &E,cv::Mat &D) {
        // need to be remean before whiten

        const int N=input.rows;  //num of data
        const int M=input.cols;  //dimention

        cv::Mat cov;
        cv::Mat D2;
        cv::Mat temp=cv::Mat::eye(M,M,CV_64FC1);
        cv::Mat temp2;
        cv::Mat E2;

        cov=input.t()*input/N;
        cv::eigen(cov,D,E2);
        cv::sqrt(D,D2);
        E=E2.t();

        for(int i=0;i<M;i++) {
            temp.at<double>(i,i)=D2.at<double>(i,0);
        }

        temp2=E2*temp.inv()*E2.t()*input.t();
        output=temp2.t();
    }


    //output =Independent components matrix,W=Un-mixing matrix
    void runICA(cv::Mat input, cv::Mat &output,cv::Mat &W,int snum) {
        const  int M=input.rows;    // number of data
        const  int N=input.cols;    // data dimension

        const int maxIterations=1000;
        const double epsilon=0.0001;

        if(N<snum) {
            snum=M;
            printf(" Can't estimate more independent components than dimension of data ");
        }

        cv::Mat R(snum,N,CV_64FC1);
        cv::randn(R, cv::Scalar(0), cv::Scalar(1));
        cv::Mat ONE=cv::Mat::ones(M,1,CV_64FC1);

        for(int i=0; i<snum; ++i) {
            int iteration=0;
            cv::Mat P(1,N,CV_64FC1);
            R.row(i).copyTo(P.row(0));

            while(iteration <= maxIterations) {
                iteration++;
                cv::Mat P2;
                P.copyTo(P2);
                cv::Mat temp1,temp2,temp3,temp4;
                temp1=P*input.t();
                cv::pow(temp1,3,temp2);
                cv::pow(temp1,2,temp3);
                temp3=3*temp3;
                temp4=temp3*ONE;
                P=temp2*input/M-temp4*P/M;

                if(i!=0)
                {
                    cv::Mat temp5;
                    cv::Mat wj(1,N,CV_64FC1);
                    cv::Mat temp6=cv::Mat::zeros(1,N,CV_64FC1);

                    for(int j=0;j<i;++j)
                    {
                        R.row(j).copyTo(wj.row(0));
                        temp5=P*wj.t()*wj;
                        temp6=temp6+temp5;

                    }
                    P=P-temp6;
                }
                double Pnorm=cv::norm(P,4);
                P=P/Pnorm;

                double j1=cv::norm(P-P2,4);
                double j2=cv::norm(P+P2,4);
                if(j1<epsilon || j2<epsilon)
                {
                    P.row(0).copyTo(R.row(i));
                    break;
                }
                else if( iteration==maxIterations)
                {
                    P.row(0).copyTo(R.row(i));
                }
            }
        }
        output=R*input.t();
        W=R;
    }
    // ============================== END fastICA ==================================

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


        __android_log_print(ANDROID_LOG_DEBUG, (char *) "native-lib :: ",
                            (char *) "face %d found ", faces.size());

        for (int i = 0; i < faces.size(); i++) {
            double real_facesize_x = faces[i].x / resizeRatio;
            double real_facesize_y = faces[i].y / resizeRatio;
            double real_facesize_width = faces[i].width / resizeRatio;
            double real_facesize_height = faces[i].height / resizeRatio;

            Point center(real_facesize_x + real_facesize_width / 2,
                         real_facesize_y + real_facesize_height / 2);
            rectangle(img_result, Point(real_facesize_x, real_facesize_y)
                    , Point(real_facesize_x + real_facesize_width, real_facesize_y + real_facesize_height), Scalar(255, 0, 255), 30, 8, 0);
//            ellipse(img_result, center, Size(real_facesize_width / 2, real_facesize_height / 2), 0,
//                    0, 360,
//                    Scalar(255, 0, 255), 30, 8, 0);


            Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,
                           real_facesize_height);
            Mat faceROI = img_gray(face_area);
//            std::vector<Rect> eyes;

            //-- In each face, detect eyes
//            ((CascadeClassifier *) cascadeClassifier_eye)->detectMultiScale(faceROI, eyes, 1.1, 2,
//                                                                            0 | CASCADE_SCALE_IMAGE,
//                                                                            Size(30, 30));

//            for (size_t j = 0; j < eyes.size(); j++) {
//                Point eye_center(real_facesize_x + eyes[j].x + eyes[j].width / 2,
//                                 real_facesize_y + eyes[j].y + eyes[j].height / 2);
//                int radius = cvRound((eyes[j].width + eyes[j].height) * 0.25);
//                circle(img_result, eye_center, radius, Scalar(255, 0, 0), 30, 8, 0);
//            }
            cv::Mat D,E,W,S;
            if(!faceROI.empty()) {
                __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",
                                    "find faceROI");
                //remean(faceROI,img_result);
                //whiten(faceROI,img_result,E,D);
                //runICA(faceROI,S,W,img_result.cols);
            }


            //cout<<W<<endl;
            //cout<<S<<endl;
        }
    }


}