package kr.ac.kaist.drivermonitor;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;

public class GpsInfoService extends Service implements LocationListener {

    private final Context mContext;


    // GPS 관련 변수
    boolean isGPSEnabled = false;       // 현재 GPS 사용유무
    boolean isNetworkEnabled = false;   // 네트워크 사용유무
    boolean isGetLocation = false;      // GPS 상태값
    Location location;                  // 위치 정보
    double lat;                         // 위도
    double lon;                         // 경도
    // 최소 GPS 정보 업데이트 거리(10미터)
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10;
    // 최소 GPS 정보 업데이트 시간(밀리세컨드, 1분 설정)
    private static final long MIN_TIME_BW_UPDATES = 1000 * 60 * 1;
    // 위치 정보를 알아오는 클래스
    protected LocationManager locationManager;

    public GpsInfoService(Context context) {
        this.mContext = context;
        getLocation();
    }

    public Location getLocation() {

        if(Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(
                    mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    mContext, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        try {
            locationManager = (LocationManager) mContext.getSystemService(LOCATION_SERVICE);

            // GPS 정보 가져오기
            isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            // 현재 네트워크 상태 값 알아오기
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if(!isGPSEnabled && !isNetworkEnabled) {
                // GPS와 네트워크 사용이 가능하지 않을 때
            } else {
                this.isGetLocation = true;

                // 네트워크 정보로 부터 위치 값 가져오기
                if(isNetworkEnabled) {
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                    if(locationManager != null) {
                        location = locationManager
                                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if(location != null) {
                            // 위도 경도 저장
                            lat = location.getLatitude();
                            lon = location.getLongitude();
                        }
                    }

                }

                if(isGPSEnabled) {
                    if(location == null) {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                                MIN_TIME_BW_UPDATES,
                                MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                        if(locationManager != null) {
                            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                            if(location != null) {
                                lat = location.getLatitude();
                                lon = location.getLongitude();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return location;
    }

    /**
     * GPS 종료
     */
    public void stopUsingGPS() {
        if(locationManager != null) {
            locationManager.removeUpdates(GpsInfoService.this);
        }
    }

    /**
     * 위도 값을 가져온다.
     */
    public double getLatitude() {
        if(location != null) {
            lat = location.getLatitude();
        }
        return lat;
    }

    /**
     * 경도 값을 가져온다.
     */
    public double getLongitude() {
        if(location != null) {
            lon = location.getLongitude();
        }
        return lon;
    }

    /**
     * GPS 나 WIFI 정보가 켜져 있는지 확인
     */
    public boolean isGetLocation() {
        return this.isGetLocation;
    }

    /**
     * GPS 정보를 가져오지 못했을 때
     * 설정 값으로 갈지 물어보는 alert 창
     */
    public void showSettingsAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(mContext);

        alertDialog.setTitle("GPS 사용유무셋팅");
        alertDialog.setMessage("GPS 세팅이 되지 않았을 수도 있습니다. \n 설정창으로 가시겠습니까?");

        // OK 를 누르면 설정창 이동
        alertDialog.setPositiveButton("Settings",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent[] intent = {new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)};
                        mContext.startActivities(intent);
                    }
                });
        // Cancle을 누르면 종료
        alertDialog.setNegativeButton("Cancle",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });

        alertDialog.show();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    public void onLocationChanged(Location location) {
        // TODO Auto-generated method stub
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
    }

    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
    }

    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
    }

}
