package kr.ac.kaist.drivermonitor;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.squareup.picasso.Picasso;

import org.json.JSONObject;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2, OnInitListener,
            OnMapReadyCallback {

    private static final String TAG = "opencv";
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat matInput;
    private Mat matResult;

    //TTS
    private TextToSpeech TTS;
    private long atime = 0;
    private long htime;
    private long lower_bpm = 0;
    private long higher_bpm = 130;


    // GPS 변수 START
    private Button btnShowLocation;

    private GpsInfoService gps;
    private String intent_lat;
    private String intent_lon;
    // GPS 변수 END

    // Firebase 변수 START
    private static final int RC_SIGN_IN = 1001;

        // Firebase - Realtime Database
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mDatabaseReference;
    private ChildEventListener mChildEventListener;

        // Firebase - Authentication
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private GoogleApiClient mGoogleApiClient;
    private FileOutputStream fos;

        // Views
    private ListView mListView;
    private SignInButton mBtnGoogleSignIn; // 로그인 버튼
    private Button mBtnGoogleSignOut; // 로그아웃 버튼
    private TextView mTxtProfileInfo; // 사용자 정보 표시
    private ImageView mImgProfile; // 사용자 프로필 이미지 표시

        // Values
    private ChatAdapter mAdapter;
    private String userName;

    private static final String FCM_MESSAGE_URL = "https://fcm.googleapis.com/fcm/send";
    private static final String SERVER_KEY = "AAAATPkqmX4:APA91bHHxZzGkmfKLV4MKxVvJgR8YD8tVN90VRaQa5XSLxPgsTIY-dRCN4yq_-dmvzuGs83NpuoeyMJau6p4rnx0buBnKGk7sZDQZ6jGyM70gucK1q7_0xaE12Rl-O7AAEA6t0okRu1N";

    private String fileNm = "FcmTokenKeys.txt";
    // Firebase 변수 END

    // Tab START
    private TabHost tabHost;
    // Tab END

    // GoogleMap START
    private GoogleMap mMap;
//    mMap.setMinZoomPreference(6.0f);
//    mMap.setMaxZoomPreference(14.0f);
    // GoogleMap END

//    public native void ConvertRGBtoGray(long matAddrInput, long matAddrResult);

    public static native long loadCascade(String cascadeFileName);
    public static native long[] detect(long cascadeClassifier_face,
                                     long cascadeClassifier_eye,
                                     long matAddrInput, long matAddrResult);

    public long cascadeClassifier_face = 0;
    public long cascadeClassifier_eye = 0;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                , WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //퍼미션 상태 확인
            if (!hasPermissions(PERMISSIONS)) {

                //퍼미션 허가 안되어있다면 사용자에게 요청
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            } else {
                read_cascade_file();
            }
        } else {
            read_cascade_file();
        }

        // Tab START
        tabHost = (TabHost) findViewById(R.id.tabHost);
        tabHost.setup();

        // 첫 번째 Tab.
        TabHost.TabSpec ts1 = tabHost.newTabSpec("Tab Spec 1");
        ts1.setContent(R.id.content1);
        ts1.setIndicator("Camera");
        tabHost.addTab(ts1);

        // 두 번째 Tab.
        TabHost.TabSpec ts2 = tabHost.newTabSpec("Tab Spec 2");
        ts2.setContent(R.id.content2);
        ts2.setIndicator("Map");
        tabHost.addTab(ts2);

        // 세 번째 Tab.
        TabHost.TabSpec ts3 = tabHost.newTabSpec("Tab Spec 3");
        ts3.setContent(R.id.content3);
        ts3.setIndicator("Contact");
        tabHost.addTab(ts3);
        // Tab END

        // Map START
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        gps = new GpsInfoService(MainActivity.this);
        // Map END

        mOpenCvCameraView = (CameraBridgeViewBase)findViewById(R.id.activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mOpenCvCameraView.setCameraIndex(1); // front-camera(1),  back-camera(0)

        mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);

        //TTS
        TTS = new TextToSpeech(this, this);

        // Firebase Message START

        initViews();
        initFirebaseDatabase();
        initFirebaseAuth();
        initValues();

        // Firebase Message END

        try {
            FileInputStream fis = openFileInput(fileNm);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));

            StringBuffer sb = new StringBuffer();
            String temp = "";
            while((temp=br.readLine()) != null) {
                sb.append(temp);
            }
            Log.d(TAG, "onCreate: " + sb.toString());
        } catch(IOException e) {
            Log.d(TAG, "onCreate: no file");
        } catch (Exception e) {

        }

    }

    private void sendPostToFCM(final ChatData chatData, final String message) {
        Log.d(TAG, "sendPostToFCM: " + chatData.userEmail.substring(0, chatData.userEmail.indexOf('@')));
        DatabaseReference temp = mFirebaseDatabase.getReference("users").child("test");
        Log.d(TAG, "sendPostToFCM: " + temp.getKey());
        mFirebaseDatabase.getReference("users")
                .child(chatData.userEmail.substring(0, chatData.userEmail.indexOf('@')))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        final UserData userData = dataSnapshot.getValue(UserData.class);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // FMC 메시지 생성 start
                                    JSONObject root = new JSONObject();
                                    JSONObject notification = new JSONObject();
                                    JSONObject data = new JSONObject();

                                    notification.put("body", message);
                                    notification.put("title", getString(R.string.app_name));


                                    data.put("lat",gps.getLatitude());
                                    data.put("lon",gps.getLongitude());

                                    root.put("notification", notification);
                                    root.put("data", data);
                                    root.put("to", userData.fcmToken);
                                    // FMC 메시지 생성 end

                                    URL Url = new URL(FCM_MESSAGE_URL);
                                    HttpURLConnection conn = (HttpURLConnection) Url.openConnection();
                                    conn.setRequestMethod("POST");
                                    conn.setDoOutput(true);
                                    conn.setDoInput(true);
                                    conn.addRequestProperty("Authorization", "key=" + SERVER_KEY);
                                    conn.setRequestProperty("Accept", "application/json");
                                    conn.setRequestProperty("Content-type", "application/json");
                                    OutputStream os = conn.getOutputStream();
                                    os.write(root.toString().getBytes("utf-8"));
                                    os.flush();
                                    conn.getResponseCode();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
    }

    private void sendLocationToFriend(String message) {

        ChatData chatData = new ChatData();
        String temp = "";
        FileInputStream fis = null;

        Log.d(TAG, "onCameraFrame: "
                + "\nlat: " + gps.getLatitude()
                + "\nlon: " + gps.getLongitude());

        try {
            fis = openFileInput(fileNm);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));

            while((temp=br.readLine()) != null) {
                Log.d(TAG, "onCameraFrame: " + temp);
                chatData.userEmail = temp;

                if(chatData.userEmail.indexOf('@') != -1) {
                    sendPostToFCM(chatData, message
                            + "\nlat: " + gps.getLatitude()
                            + "\nlon: " + gps.getLongitude());
                    Log.d(TAG, "onCameraFrame: "
                            + "\nlat: " + gps.getLatitude()
                            + "\nlon: " + gps.getLongitude());
                }
            }

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void initViews() {
        mListView = (ListView) findViewById(R.id.list_message);
        mAdapter = new ChatAdapter(this, 0);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final ChatData chatData = mAdapter.getItem(position);
                if (!TextUtils.isEmpty(chatData.userEmail)) {
                    final EditText editText = new EditText(MainActivity.this);
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage(chatData.userEmail + " 님 에게 메시지 보내기")
                            .setView(editText)
                            .setPositiveButton("보내기", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    sendPostToFCM(chatData, editText.getText().toString());
                                }
                            })
                            .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // not thing..
                                }
                            }).show();
                }
            }
        });

//        mEdtMessage = (EditText) findViewById(R.id.edit_message);
        findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // 등록 다이얼로그
                AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                alert.setTitle("Register your friend");

                alert.setMessage("Friend's Google E-mail: ");
                final EditText firendGmail = new EditText(MainActivity.this);
                alert.setView(firendGmail);

                alert.setPositiveButton("Register", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "onClick: Register~~~~~~~~~~~!");

                        try {
                            // 친구의 Gmail 계정을 내부 파일에 저장
                            fos = openFileOutput(fileNm, MODE_PRIVATE);
                            Log.d(TAG, "onClick: " + getFilesDir());
                            fos.write(("\n"+firendGmail.getText().toString()).getBytes());
                            fos.close();

                            // 사용자를 조회해서 화면에 보여주기 위해 메시지 보낸다.
                            ChatData chatData = new ChatData();
                            chatData.userEmail = firendGmail.getText().toString(); // 사용자 이메일 주소
                            if (!TextUtils.isEmpty(chatData.userEmail)) {
                                chatData.userName = chatData.userEmail
                                        .substring(0, chatData.userEmail.indexOf('@'));
                                chatData.message = "Your Friend";
                                chatData.time = System.currentTimeMillis();
                                mDatabaseReference.push().setValue(chatData);
                            }

                        } catch(Exception e) {
                            e.printStackTrace();
                        }

                    }
                });
                alert.setNegativeButton("Cancle", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });

                alert.show();

            }
        });

        mBtnGoogleSignIn = (SignInButton) findViewById(R.id.btn_google_signin);
        mBtnGoogleSignOut = (Button) findViewById(R.id.btn_google_signout);
        mBtnGoogleSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signIn();
            }
        });
        mBtnGoogleSignOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signOut();
            }
        });

        mTxtProfileInfo = (TextView) findViewById(R.id.txt_profile_info);
        mImgProfile = (ImageView) findViewById(R.id.img_profile);
    }


    private void initFirebaseDatabase() {
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mDatabaseReference = mFirebaseDatabase.getReference("message");
        mChildEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                ChatData chatData = dataSnapshot.getValue(ChatData.class);
                chatData.firebaseKey = dataSnapshot.getKey();
                mAdapter.add(chatData);
                mListView.smoothScrollToPosition(mAdapter.getCount());
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                String firebaseKey = dataSnapshot.getKey();
                int count = mAdapter.getCount();
                for (int i = 0; i < count; i++) {
                    if (mAdapter.getItem(i).firebaseKey.equals(firebaseKey)) {
                        mAdapter.remove(mAdapter.getItem(i));
                        break;
                    }
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        };

        mDatabaseReference.addChildEventListener(mChildEventListener);
    }

    private void initValues() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            userName = "Guest" + new Random().nextInt(5000);
        } else {
            userName = user.getDisplayName();
        }
    }

    private void initFirebaseAuth() {
        mAuth = FirebaseAuth.getInstance();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

                    }
                })
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                updateProfile();
            }
        };
    }

    private void updateProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            // 비 로그인 상태 (메시지를 전송할 수 없다.)
            mBtnGoogleSignIn.setVisibility(View.VISIBLE);
            mBtnGoogleSignOut.setVisibility(View.GONE);
            mTxtProfileInfo.setVisibility(View.GONE);
            mImgProfile.setVisibility(View.GONE);
            findViewById(R.id.btn_send).setVisibility(View.GONE);
            mAdapter.setEmail(null);
            mAdapter.notifyDataSetChanged();
        } else {
            // 로그인 상태
            mBtnGoogleSignIn.setVisibility(View.GONE);
            mBtnGoogleSignOut.setVisibility(View.VISIBLE);
            mTxtProfileInfo.setVisibility(View.VISIBLE);
            mImgProfile.setVisibility(View.VISIBLE);
            findViewById(R.id.btn_send).setVisibility(View.VISIBLE);

            userName = user.getDisplayName(); // 채팅에 사용 될 닉네임 설정
            String email = user.getEmail();
            StringBuilder profile = new StringBuilder();
            profile.append(userName).append("\n").append(user.getEmail());
            mTxtProfileInfo.setText(profile);
            mAdapter.setEmail(email);
            mAdapter.notifyDataSetChanged();

            Picasso.with(this).load(user.getPhotoUrl()).into(mImgProfile);

            UserData userData = new UserData();
            userData.userEmailID = email.substring(0, email.indexOf('@'));
            userData.fcmToken = FirebaseInstanceId.getInstance().getToken();

            mFirebaseDatabase.getReference("users").child(userData.userEmailID).setValue(userData);
        }
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut() {
        mAuth.signOut();
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        updateProfile();
                    }
                });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: " + requestCode);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            Log.d(TAG, "onActivityResult: " + result.isSuccess());
            if (result.isSuccess()) {
                Log.d(TAG, "onActivityResult: Result Success");
                GoogleSignInAccount account = result.getSignInAccount();
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                mAuth.signInWithCredential(credential)
                        .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (!task.isSuccessful()) {
                                    Log.d(TAG, "onActivityResult: Authentication failed");
                                    Toast.makeText(MainActivity.this, "Authentication failed.",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    Log.d(TAG, "onActivityResult: Authentication success");
                                    Toast.makeText(MainActivity.this, "Authentication success.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            } else {
                updateProfile();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        mAuth.removeAuthStateListener(mAuthListener);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "onResume :: OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        //TTS
        TTS.shutdown();

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        mDatabaseReference.removeEventListener(mChildEventListener);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }


    //여기서부턴 퍼미션 관련 메소드
    static final int PERMISSIONS_REQUEST_CODE = 1000;
//    String[] PERMISSIONS  = {"android.permission.CAMERA"};
    String[] PERMISSIONS  = {"android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.INTERNET",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION"};


    private boolean hasPermissions(String[] permissions) {
        int result;

        //스트링 배열에 있는 퍼미션들의 허가 상태 여부 확인
        for (String perms : permissions){

            result = ContextCompat.checkSelfPermission(this, perms);

            if (result == PackageManager.PERMISSION_DENIED){
                //허가 안된 퍼미션 발견
                return false;
            }
        }

        //모든 퍼미션이 허가되었음
        return true;
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch(requestCode){

            case PERMISSIONS_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean cameraPermissionAccepted = grantResults[0]
                            == PackageManager.PERMISSION_GRANTED;

                    boolean writePermissionAccepted = grantResults[1]
                            == PackageManager.PERMISSION_GRANTED;

                    boolean internetPermissionAccepted = grantResults[2]
                            == PackageManager.PERMISSION_GRANTED;

                    boolean accessFineLocationPermissionAccepted = grantResults[3]
                            == PackageManager.PERMISSION_GRANTED;

                    boolean accessCoarseLocationPermissionAccepted = grantResults[4]
                            == PackageManager.PERMISSION_GRANTED;

                    if (!cameraPermissionAccepted
                            || !writePermissionAccepted
                            || !internetPermissionAccepted
                            || !accessFineLocationPermissionAccepted
                            || !accessCoarseLocationPermissionAccepted ) {
                        showDialogForPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.");
                        return;
                    } else {
                        read_cascade_file();
                    }

                }
                break;
        }
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {

        AlertDialog.Builder builder = new AlertDialog.Builder( MainActivity.this);
        builder.setTitle("알림");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id){
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.create().show();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        matInput = inputFrame.rgba();

        if ( matResult != null ) matResult.release();
        matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());

        //ConvertRGBtoGray(matInput.getNativeObjAddr(), matResult.getNativeObjAddr());
        Core.flip(matInput, matInput, 1);

        long[] bResult = {0, 0};

        bResult = detect(cascadeClassifier_face,cascadeClassifier_eye, matInput.getNativeObjAddr(),
                matResult.getNativeObjAddr());

//        Log.d(TAG, "detectFaceResult: " + bResult);
        // ctime - current time, atime - alarm time
        long ctime = System.currentTimeMillis();
        // 알림음 10초 뒤
        Log.d(TAG, "detectFaceResult: " + bResult[1]);
        if (bResult[0] == 0 && ctime - atime > 50000) {
            TTS.speak("Your face can't be recognized.", TextToSpeech.QUEUE_FLUSH, null);
            atime = System.currentTimeMillis();
//            htime = ctime;
        }
        else {
            if (bResult[1] > 1) {
                // 60이하 발생하면 bpm 업데이트
                if (bResult[1] < 60 && lower_bpm < 60) {
                    lower_bpm = bResult[1];
                    Log.d(TAG, "detectLowbpm " + lower_bpm);
                    // 1분이 넘어 서맥이고, 알람 울린지 10초가 지났으면,
                    if (ctime - htime > 4000 && ctime - atime > 20000) {

                        // 서맥 발생시 친구에게 Notification
                        sendLocationToFriend("Warning! bradycardia!");

                        // TTS speak
                        TTS.speak("Warning! bradycardia! your heart rate is under 60", TextToSpeech.QUEUE_FLUSH, null);
                        atime = System.currentTimeMillis();
                    }
                }
                // 120이상 발생하면 bpm 업데이트
                else if (bResult[1] > 100 && higher_bpm > 100){
                    higher_bpm = bResult[1];
                    Log.d(TAG, "detectHighbpm " + higher_bpm);
                    // 1분이 넘어 빈맥이고, 알람 울린지 10초가 지났으면,
                    if (ctime - htime > 4000 && ctime - atime > 20000) {
                        // 서맥 발생시 친구에게 Notification
                        sendLocationToFriend("Warning! tachycardia!");

                        // TTS speak
                        TTS.speak("Warning! tachycardia! your heart rate is over 120", TextToSpeech.QUEUE_FLUSH, null);
                        atime = System.currentTimeMillis();
                    }
                }
                else {
                    htime = ctime;
                }

            }

        }

        return matResult;
    }

    private void copyFile(String filename) {
        String baseDir = Environment.getExternalStorageDirectory().getPath();
        String pathDir = baseDir + File.separator + filename;

        AssetManager assetManager = this.getAssets();

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            Log.d( TAG, "copyFile :: 다음 경로로 파일복사 "+ pathDir);
            inputStream = assetManager.open(filename);
            outputStream = new FileOutputStream(pathDir);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            inputStream = null;
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception e) {
            Log.d(TAG, "copyFile :: 파일 복사 중 예외 발생 "+e.toString() );
        }

    }


    private void read_cascade_file(){

        //copyFile 메소드는 Assets에서 해당 파일을 가져와
        //외부 저장소 특정위치에 저장하도록 구현된 메소드입니다.
        copyFile("haarcascade_frontalface_alt.xml");
        copyFile("haarcascade_eye_tree_eyeglasses.xml");

        Log.d(TAG, "read_cascade_file:");


        //loadCascade 메소드는 외부 저장소의 특정 위치에서 해당 파일을 읽어와서
        //CascadeClassifier 객체로 로드합니다.
        cascadeClassifier_face = loadCascade( "haarcascade_frontalface_alt.xml");
        Log.d(TAG, "read_cascade_file:");

        cascadeClassifier_eye = loadCascade( "haarcascade_eye_tree_eyeglasses.xml");
    }

    @Override
    public void onInit(int status) {

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        // Add a marker in Sydney, Australia,
        // and move the map's camera to the same location.

        Intent intent = getIntent();
        intent_lat = intent.getStringExtra("lat");
        intent_lon = intent.getStringExtra("lon");
        Log.d(TAG, "onCreate: lat: " + intent_lat);
        Log.d(TAG, "onCreate: lon: " + intent_lon);

        LatLng current;
        if(intent_lat != null && intent_lon != null) {
//            current = new LatLng(Double.parseDouble(intent_lat),Double.parseDouble(intent_lon));
            current = new LatLng(37.3480129,127.1133748);   // 우리집
//            current = new LatLng(37.483543,127.043964);// 학교
        } else {
            GpsInfoService mgps = getGps();
            current = new LatLng(mgps.getLatitude(), mgps.getLongitude());
        }
        googleMap.addMarker(new MarkerOptions().position(current)
                .title("Marker in Current Location"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(current, 15.0f));
    }

    private GpsInfoService getGps() {
        gps = new GpsInfoService(MainActivity.this);

        // GPS 사용유무 가져오기
        if(gps.isGetLocation()) {
            double latitude = gps.getLatitude();
            double longitude = gps.getLongitude();

            Toast.makeText(
                    getApplicationContext(),
                    "당신의 위치\n위도: " + latitude + "\n경도: " + longitude,
                    Toast.LENGTH_LONG).show();

        } else {
            // GPS를 사용할 수 없을 때
            gps.showSettingsAlert();
        }
        return gps;
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
//    public native String stringFromJNI();


}
