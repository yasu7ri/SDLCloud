# SDLCloud
[ハッカソンでアプリ作って、SDL（スマート・デバイス・リンク）アプリコンテスト2019に応募しよう！](https://hmcn.connpass.com/event/143901/)のサンプルアプリ[SDLCloud](https://github.com/oic0310/SDLCloud)を元に[Manticore](https://www.smartdevicelink.com/resources/manticore/)から取得してたデータをfirebaseへ保存するまでの適当なプログラム<br>
適当なので良い子はマネしないでねｗ

## まずば素のサンプルをSDLCloudを動かくまで
* [SDLCloud](https://github.com/oic0310/SDLCloud)をcloneする
* local.propertiesを自分の環境に合わせてNDKとSDKのパスを設定する
  * windowsであればこんな感じ（{user name}は環境に合わせて変更する）
    ```
    ndk.dir=C\:\\Users\\{user name}\\AppData\\Local\\Android\\Sdk\\ndk-bundle
    sdk.dir=C\:\\Users\\{user name}\\AppData\\Local\\Android\\Sdk
    ```
* [Manticore](https://www.smartdevicelink.com/resources/manticore/)へログインしてManticoreを起動させる
* 右側に表示されているPORT NUMBERをcom.oec.sdl.cloud.SdlServiceの68ステップ目に書かれている`TCP_PORT`へ記述する
    ``` java
    private static final int TCP_PORT = 14385;
    ```
* 起動する
  * emulatorは`Nexus 6P API 28`を使用（種類によってはエラーになる）

## サンプルを改造してManticoreから取得したデータをfirebaseへ登録する
### firebaseの準備
* プロジェクトの作成
  * プロジェクト名は任意
  * Google アナリティクス、Google アナリティクスの構成はdefaultのまま
* アプリを追加する、こんかいはAndroidを選択
  * アプリの登録
    * Android パッケージ名は通常、アプリレベルの build.gradle ファイルの applicationId
      ```
      applicationId "com.oec.sdl.vehicle"
      ```
    * 設定ファイルのダウンロードをして google-services.json ファイルを Android アプリ モジュールの ルート ディレクトリへ配置する
* Firebase SDK の追加
  * プロジェクト レベルの build.gradle（<project>/build.gradle）
    ``` yml
    buildscript {    
        repositories {
            google()
            jcenter()
        }
        dependencies {
            classpath 'com.android.tools.build:gradle:3.5.1'
            classpath 'com.google.gms:google-services:4.0.1'
        }
    }
    allprojects {
        repositories {
            google()
            jcenter()
        }
    }
    task clean(type: Delete) {
        delete rootProject.buildDir
    }
    ```
  * アプリレベルの build.gradle（<project>/<app-module>/build.gradle）
    ``` yml
    apply plugin: 'com.android.application'
    apply plugin: 'com.google.gms.google-services'

    android {
        compileSdkVersion 28
        compileOptions {
            targetCompatibility = "8"
            sourceCompatibility = "8"
        }
        defaultConfig {
            applicationId "com.oec.sdl.vehicle"
            minSdkVersion 24
            targetSdkVersion 28
            versionCode 1
            versionName "1.0"
            testInstrumentationRunner "android.support.test.runner.AndroidJUnitRunner"
        }
        buildTypes {
            release {
                minifyEnabled false
                proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            }
        }
        flavorDimensions "default"
        productFlavors{
            multi_sec_high {
                buildConfigField 'String', 'TRANSPORT', '"MULTI"'
                buildConfigField 'String', 'SECURITY', '"HIGH"'
            }
            multi_sec_med {
                buildConfigField 'String', 'TRANSPORT', '"MULTI"'
                buildConfigField 'String', 'SECURITY', '"MED"'
            }
            multi_sec_low {
                buildConfigField 'String', 'TRANSPORT', '"MULTI"'
                buildConfigField 'String', 'SECURITY', '"LOW"'
            }
            multi_sec_off {
                buildConfigField 'String', 'TRANSPORT', '"MULTI"'
                buildConfigField 'String', 'SECURITY', '"OFF"'
            }
            multi_high_bandwidth {
                buildConfigField 'String', 'TRANSPORT', '"MULTI_HB"'
                buildConfigField 'String', 'SECURITY', '"OFF"'
            }
            tcp {
                buildConfigField 'String', 'TRANSPORT', '"TCP"'
                buildConfigField 'String', 'SECURITY', '"OFF"'
            }
        }
        lintOptions {
            disable 'GoogleAppIndexingWarning'
        }
    }

    dependencies {
        implementation fileTree(dir: 'libs', include: ['*.jar'])
        implementation 'com.android.support:appcompat-v7:28.0.0'
        implementation 'com.android.support.constraint:constraint-layout:1.1.3'
        testImplementation 'junit:junit:4.12'
        androidTestImplementation 'com.android.support.test:runner:1.0.2'
        androidTestImplementation 'com.android.support.test.espresso:espresso-core:3.0.2'
        implementation group: 'com.squareup.okhttp3', name: 'okhttp', version: '3.14.1'
        implementation 'com.smartdevicelink:sdl_android:4.9.1'



        implementation('com.google.firebase:firebase-core:16.0.4') {
            exclude group: 'com.android.support'
            exclude module: 'appcompat-v7'
            exclude module: 'support-v4'
        }
        implementation('com.google.firebase:firebase-database:16.0.3') {
            exclude group: 'com.android.support'
            exclude module: 'appcompat-v7'
            exclude module: 'support-v4'
        }
    }
    ```
* アプリを実行してインストールを確認はスキップ

### データベースの作成
* セキュリティは、とりあえずテストモードで開始
* 今回は`Cloud Firestore`でなく`Realtime Database`を選択する
* ルールもとりあえず全公開
  ```
  {
    /* Visit https://firebase.google.com/docs/database/security to learn more about security rules. */
    "rules": {
        ".read": true,
        ".write": true
    }
  }
  ```

### Manticoreから取得したデータをfirebaseへ登録する
* 定期受信したいデータを設定する<br>
`com.smartdevicelink.managers.CompletionListener#onComplete(boolean success) `
    ``` java
    subscribeRequest.setRpm(true);                          //エンジン回転数
    subscribeRequest.setPrndl(true);                        //シフトレーバの状態
    subscribeRequest.setSpeed(true);
    subscribeRequest.setGps(true);
    ```
* Manticoreから取得したデータをfirebaseへ登録する
`com.smartdevicelink.managers.SdlManagerListener#onStart()`
``` java
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
```
