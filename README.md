# SDLCloud
[ハッカソンでアプリ作って、SDL（スマート・デバイス・リンク）アプリコンテスト2019に応募しよう！](https://hmcn.connpass.com/event/143901/)のサンプルアプリ[SDLCloud](https://github.com/oic0310/SDLCloud)を元に[Manticore](https://www.smartdevicelink.com/resources/manticore/)から取得してたデータをfirebaseへ保存する

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
    ```
    private static final int TCP_PORT = 14385;
    ```
* 起動する
  * emulatorは`Nexus 6P API 28`を使用（種類によってはエラーになる）