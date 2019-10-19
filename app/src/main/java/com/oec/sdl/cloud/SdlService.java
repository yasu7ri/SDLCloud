package com.oec.sdl.cloud;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.managers.permission.PermissionElement;
import com.smartdevicelink.managers.permission.PermissionStatus;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.RPCResponse;
import com.smartdevicelink.proxy.rpc.DisplayCapabilities;
import com.smartdevicelink.proxy.rpc.GPSData;
import com.smartdevicelink.proxy.rpc.GetVehicleData;
import com.smartdevicelink.proxy.rpc.GetVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.OnVehicleData;
import com.smartdevicelink.proxy.rpc.SubscribeVehicleData;
import com.smartdevicelink.proxy.rpc.UnsubscribeVehicleData;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.PRNDL;
import com.smartdevicelink.proxy.rpc.enums.SystemCapabilityType;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import com.smartdevicelink.transport.BaseTransportConfig;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.transport.TCPTransportConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Vector;

//HTTP通信
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SdlService extends Service {
	private GPSData beforeGpsData;
	private Double beforeSpeed;

	private static final String TAG 					= "SDL Vehicle";

	private static final String APP_NAME 				= "SDL Vehicle";
	private static final String APP_ID 					= "014003";

	private static final String ICON_FILENAME 			= "hello_sdl_icon.png";

	private static final int FOREGROUND_SERVICE_ID = 113;

	// TCP/IP transport config
	// The default port is 12345
	// The IP is of the machine that is running SDL Core
	private static final int TCP_PORT = 10261;
	private static final String DEV_MACHINE_IP_ADDRESS = "m.sdl.tools";

	// variable to create and call functions of the SyncProxy
	private SdlManager sdlManager = null;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate");
		super.onCreate();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			enterForeground();
		}
	}

	/**
	 * POSTする
	 * @param url
	 * @param engineRPM
	 * @throws IOException
	 */
	public void doPost(String url,String engineRPM) throws IOException {
		final FormBody.Builder formBodyBuilder = new FormBody.Builder();

		//POSTするデータ
		formBodyBuilder.add("engine", engineRPM);

		final Request request = new Request.Builder()
				.url(url)
				.header("User-Agent", "SDL client")
				.post(formBodyBuilder.build())
				.build();
		OkHttpClient client = new OkHttpClient.Builder()
				.build();
		//同期呼び出し
		Response response = client.newCall(request).execute();
	}

	/**
	 * JSON形式でPOSTしたい場合　
	 * @param url
	 * @param jsonString
	 * @throws IOException
	 */
	public void doJsonPost(String url, String jsonString) throws IOException {
		okhttp3.MediaType mediaTypeJson = okhttp3.MediaType.parse("application/json; charset=utf-8");

		RequestBody requestBody = RequestBody.create(mediaTypeJson, jsonString);

		final Request request = new Request.Builder()
				.url(url)
				.post(requestBody)//POST指定
				.build();
		OkHttpClient client = new OkHttpClient.Builder()
				.build();
		//同期呼び出し
		Response response = client.newCall(request).execute();

	}


	// Helper method to let the service enter foreground mode
	@SuppressLint("NewApi")
	public void enterForeground() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(APP_ID, "SdlService", NotificationManager.IMPORTANCE_DEFAULT);
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null) {
				notificationManager.createNotificationChannel(channel);
				Notification serviceNotification = new Notification.Builder(this, channel.getId())
						.setContentTitle("Connected through SDL")
						.setSmallIcon(R.drawable.ic_sdl)
						.build();
				startForeground(FOREGROUND_SERVICE_ID, serviceNotification);
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		startProxy();
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			stopForeground(true);
		}

		if (sdlManager != null) {
			sdlManager.dispose();
		}

		super.onDestroy();
	}

	private void startProxy() {

		if (sdlManager == null) {
			Log.i(TAG, "Starting SDL Proxy");

			BaseTransportConfig transport = null;
			if (BuildConfig.TRANSPORT.equals("MULTI")) {
				int securityLevel;
				if (BuildConfig.SECURITY.equals("HIGH")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH;
				} else if (BuildConfig.SECURITY.equals("MED")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED;
				} else if (BuildConfig.SECURITY.equals("LOW")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW;
				} else {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF;
				}
				transport = new MultiplexTransportConfig(this, APP_ID, securityLevel);
			} else if (BuildConfig.TRANSPORT.equals("TCP")) {
				transport = new TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true);
			} else if (BuildConfig.TRANSPORT.equals("MULTI_HB")) {
				MultiplexTransportConfig mtc = new MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);
				mtc.setRequiresHighBandwidth(true);
				transport = mtc;
			}

			// The app type to be used
			final Vector<AppHMIType> appType = new Vector<>();
			appType.add(AppHMIType.MEDIA);


			// The manager listener helps you know when certain events that pertain to the SDL Manager happen
			// Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
			SdlManagerListener listener = new SdlManagerListener() {
			    private PRNDL beforePrndl = null;
                @Override
				public void onStart() {
					// HMI Status Listener
					sdlManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
						@Override
						public void onNotified(RPCNotification notification) {

							OnHMIStatus status = (OnHMIStatus) notification;
							if (status.getHmiLevel() == HMILevel.HMI_FULL && ((OnHMIStatus) notification).getFirstRun()) {

								checkTemplateType();

								checkPermission();

                                setDisplayDefault();
							}
						}
					});

                    //これをすると定期的にデータが取得可能
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_VEHICLE_DATA, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnVehicleData onVehicleDataNotification = (OnVehicleData) notification;

                            sdlManager.getScreenManager().beginTransaction();

                            //エンジン回転数を指定URLにHTTP POSTする
							try {
								//FIXME POSTするURLを書いてね
								//doPost("https://ドメイン",onVehicleDataNotification.getRpm().toString());
								Double speed = onVehicleDataNotification.getSpeed();
								// GPS
								GPSData gpsData = onVehicleDataNotification.getGps();
								// 緯度
								Double latitudeDegrees = Double.valueOf(0);
								// 経度
								Double longitudeDegrees = Double.valueOf(0);

								if (gpsData != null) {
									// 待避
									beforeGpsData = gpsData;
									// 緯度
									latitudeDegrees = gpsData.getLatitudeDegrees();
									// 経度
									longitudeDegrees = gpsData.getLongitudeDegrees();
								}
								//System.out.println("--- rpm : " + rpm);
								FirebaseDatabase database = FirebaseDatabase.getInstance();
								Date d = new Date();

								Sdl sdl = new Sdl();
								sdl.setVin("vin99999");
								sdl.setSpeed(speed);
								sdl.setGps(String.format("[%s, %s]", latitudeDegrees.toString(), longitudeDegrees.toString()));
								sdl.setCalegory("1");

								DatabaseReference myRef = database.getReference(d.toString() + "/");
								myRef.setValue(sdl);
							} catch (Exception e) {
								//FIXME ちゃんとエラー処理してね
								e.printStackTrace();
							}

							SdlArtwork artwork = null;

                            //回転数が3000以上か、以下で画像を切り替える
                            Integer rpm = onVehicleDataNotification.getRpm();
                            if(rpm != null) {
                                if (rpm > 3000) {
                                    if (sdlManager.getScreenManager().getPrimaryGraphic().getResourceId() != R.drawable.oldman) {
                                        artwork = new SdlArtwork("oldman.png", FileType.GRAPHIC_PNG, R.drawable.oldman, true);
                                    }
                                } else {
                                    if (sdlManager.getScreenManager().getPrimaryGraphic().getResourceId() != R.drawable.oldman) {
                                        artwork = new SdlArtwork("clap.png", FileType.GRAPHIC_PNG, R.drawable.clap, true);
                                    }
                                }
                                if (artwork != null) {
                                    sdlManager.getScreenManager().setPrimaryGraphic(artwork);
                                }
                            }

                            //テキストを登録する場合
                            sdlManager.getScreenManager().setTextField1("RPM: " + onVehicleDataNotification.getRpm());


                            PRNDL prndl = onVehicleDataNotification.getPrndl();
                            if (prndl != null) {
                                sdlManager.getScreenManager().setTextField2("ParkBrake: " + prndl.toString());

                                //パーキングブレーキの状態が変わった時だけSpeedを受信させる
                                if(beforePrndl != prndl){
                                    beforePrndl = prndl;
                                    setOnTimeSpeedResponse();
                                }
                            }

                            sdlManager.getScreenManager().commit(new CompletionListener() {
                                @Override
                                public void onComplete(boolean success) {
                                    if (success) {
                                        Log.i(TAG, "change successful");
                                    }
                                }
                            });
                        }
                    });
				}

				@Override
				public void onDestroy() {
					UnsubscribeVehicleData unsubscribeRequest = new UnsubscribeVehicleData();
					unsubscribeRequest.setRpm(true);
                    unsubscribeRequest.setPrndl(true);
					unsubscribeRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
						@Override
						public void onResponse(int correlationId, RPCResponse response) {
							if(response.getSuccess()){
								Log.i("SdlService", "Successfully unsubscribed to vehicle data.");
							}else{
								Log.i("SdlService", "Request to unsubscribe to vehicle data was rejected.");
							}
						}
					});
					sdlManager.sendRPC(unsubscribeRequest);

					SdlService.this.stopSelf();
				}

				@Override
				public void onError(String info, Exception e) {
				}
			};

			// Create App Icon, this is set in the SdlManager builder
			SdlArtwork appIcon = new SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true);

			// The manager builder sets options for your session
			SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
			builder.setAppTypes(appType);
			//builder.setTransportType(transport);
			builder.setTransportType(new TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true));
			builder.setAppIcon(appIcon);
			sdlManager = builder.build();
			sdlManager.start();


		}
	}

    /**
     * 一度だけの情報受信
     */
	private void setOnTimeSpeedResponse(){

        GetVehicleData vdRequest = new GetVehicleData();
        vdRequest.setSpeed(true);
        vdRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
            @Override
            public void onResponse(int correlationId, RPCResponse response) {
                if(response.getSuccess()){
                    Double speed = ((GetVehicleDataResponse) response).getSpeed();
                    changeSpeedTextField(speed);

                }else{
                    Log.i("SdlService", "GetVehicleData was rejected.");
                }
            }
        });
        sdlManager.sendRPC(vdRequest);
    }

    /**
     * Speed情報の往診
     * @param speed
     */
	private void changeSpeedTextField(double speed){
        sdlManager.getScreenManager().beginTransaction();

        //テキストを登録する場合
        sdlManager.getScreenManager().setTextField3("Speed: " + speed);

        sdlManager.getScreenManager().commit(new CompletionListener() {
            @Override
            public void onComplete(boolean success) {
                if (success) {
                    Log.i(TAG, "change successful");
                }
            }
        });
    }

	/**
	 * 利用可能なテンプレートをチェックする
	 */
	private void checkTemplateType(){

		Object result = sdlManager.getSystemCapabilityManager().getCapability(SystemCapabilityType.DISPLAY);
		if( result instanceof DisplayCapabilities){
			List<String> templates = ((DisplayCapabilities) result).getTemplatesAvailable();

			Log.i("Templete", templates.toString());

		}
	}

    /**
     * 利用する項目が利用可能かどうか
     */
    private void checkPermission(){
        List<PermissionElement> permissionElements = new ArrayList<>();

        //チェックを行う項目
        List<String> keys = new ArrayList<>();
        keys.add(GetVehicleData.KEY_RPM);
        keys.add(GetVehicleData.KEY_SPEED);
        keys.add(GetVehicleData.KEY_PRNDL);
        permissionElements.add(new PermissionElement(FunctionID.GET_VEHICLE_DATA, keys));

        Map<FunctionID, PermissionStatus> status = sdlManager.getPermissionManager().getStatusOfPermissions(permissionElements);

        //すべてが許可されているかどうか
        Log.i("Permission", "Allowed:" + status.get(FunctionID.GET_VEHICLE_DATA).getIsRPCAllowed());

        //各項目ごとも可能
        Log.i("Permission", "KEY_RPM　Allowed:" + status.get(FunctionID.GET_VEHICLE_DATA).getAllowedParameters().get(GetVehicleData.KEY_RPM));

    }

	/**
	 * DEFULTテンプレートのサンプル
	 */
	private void setDisplayDefault(){

        sdlManager.getScreenManager().beginTransaction();

        //テキストを登録する場合
        sdlManager.getScreenManager().setTextField1("RPM: None");
        sdlManager.getScreenManager().setTextField2("ParkBrake: None");
        sdlManager.getScreenManager().setTextField3("Speed: None");

        //画像を登録する
        SdlArtwork artwork = new SdlArtwork("clap.png", FileType.GRAPHIC_PNG, R.drawable.clap, true);

        sdlManager.getScreenManager().setPrimaryGraphic(artwork);
        sdlManager.getScreenManager().commit(new CompletionListener() {
            @Override
            public void onComplete(boolean success) {
                if (success) {
                    //定期受信用のデータを設定する
                    SubscribeVehicleData subscribeRequest = new SubscribeVehicleData();
                    subscribeRequest.setRpm(true);                          //エンジン回転数
                    subscribeRequest.setPrndl(true);                        //シフトレーバの状態
					subscribeRequest.setSpeed(true);
					subscribeRequest.setGps(true);
					subscribeRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
                        @Override
                        public void onResponse(int correlationId, RPCResponse response) {
                            if (response.getSuccess()) {
                                Log.i("SdlService", "Successfully subscribed to vehicle data.");
                            } else {
                                Log.i("SdlService", "Request to subscribe to vehicle data was rejected.");
                            }
                        }
                    });
                    sdlManager.sendRPC(subscribeRequest);

                }
            }
		});
	}

	private static class Sdl {
		private String gps;
		private String vin;
		private Double speed;
		private String calegory;

		public String getGps() {
			return gps;
		}

		public void setGps(String gps) {
			this.gps = gps;
		}

		public String getVin() {
			return vin;
		}

		public void setVin(String vin) {
			this.vin = vin;
		}

		public Double getSpeed() {
			return speed;
		}

		public void setSpeed(Double speed) {
			this.speed = speed;
		}

		public String getCalegory() {
			return calegory;
		}

		public void setCalegory(String calegory) {
			this.calegory = calegory;
		}
	}
}
