# NFCReader
## 説明
シンプルなアプリです。タッチされたICカードの種類を表示します。

## 対応（開発版）
Android 7.0（API 24）以上のNFC対応端末

Android 6.0以下（API 23以下）は対象外です。

## ダウンロード
最新の公開版は **2.0.1** です。下記APKは今回の開発版における `minSdk 24` への変更を含まない、既公開のリリースです。

https://github.com/Atsumi3/NFCReader/releases/download/2.0.1/nfcreader-2.0.1.apk

## 開発環境・検証
- JDK 17、Gradle Wrapper 9.9.2
- Android SDK Platform 37.1、Build Tools 37.0.0
- AGP 9.5の互換性表ではBuild Tools 36.0.0が最小（既定）です。SDK Repositoryの最新安定版37.0.0を使い、CIと同じ版に固定しています。

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug assembleRelease
```

GitHub ActionsはPull Requestと`develop`へのpushで同じ検証を実行し、SDK Platform 37.1とBuild Tools 37.0.0をインストールします。
