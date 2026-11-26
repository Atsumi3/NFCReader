# NFCReader
## 説明
Androidが公開するタグ技術について、取得可能な情報を読み取り専用で記録・表示するベストエフォートのNFCダンプツールです。タグ・端末・プロトコル・アクセス条件に依存するため、すべてのNFCデータを完全取得できるとは限りません。

## ダンプの範囲と制限

- 取得結果は技術ごとに「完全」「部分」「メタデータのみ」「未対応」「失敗」「未試行」を区別します。「完全」は宣言された読取範囲を全て取得し、未読範囲・エラー・切詰めがない場合だけです。容量不明や未対応技術は完全扱いしません。
- タグID・型付きの公開discovery getterから取得できたメタデータ、raw応答、decoded fields、issues、coverage、unread rangesを保持します。raw captureは1ダンプあたり最大256 KiB、field値は合計256 Ki文字です。上限到達時は切詰めを結果に明示します。
- ダンプには読取開始時刻を保持し、JSON/text exportにも出力します。技術カードは折りたたみ可能で、raw・decoded・metadata・coverage・issues・数値付きunread rangesを分けて確認できます。大きな詳細はlazy listで表示し、raw chunkは必要な時に全体を開けます。
- 技術ごとに期限とtransceive回数を制限します（NDEF 10秒/16回、MIFARE Ultralight 20秒/512回、MIFARE Classic 30秒/2048回、FeliCa・NFC-V・ISO-DEP 30秒/4096回、その他5秒/64回）。中断・期限到達時は専用workerから接続を閉じ、既取得の部分結果を保持します。タグ読取は前の読取job終了後に開始し、古い世代の結果は新しい表示へ反映しません。
- NDEFのNdefMessage.toByteArray()はAndroidが再シリアライズしたメッセージで、RF上の生バイト列ではありません。record payloadと別項目として表示します。
- FeliCaはsystemごとにPollingのIDm/PMmを保持し、Suica system 0003 / history service 090Fの先頭20 blockを読みます。加えてRequest Serviceでsystemごとに最大2048候補を調べ、最大64 batchの総枠をsystem間に配分して、認証不要と判定できるserviceのみblockを読みます。認証が必要なserviceは試さず、未知service、候補/交換/時間/block上限、systemごとの停止理由を結果に残します。全system/serviceの網羅は保証しません。
- MIFARE UltralightはGET_VERSIONで既知容量を確定できるNTAG213/215/216とUltralight EV1で、末尾pageまで最大4-page単位のFAST_READを試し、失敗時は単page READへfallbackします。未知profileは最大256 pageのbounded READにとどめ、容量不明として扱います。4-page READ応答はraw全体を保存しますが、先頭page以外へデータを推測割当てしません。MIFARE Classicは既存の組み込み既定鍵のみで認証し、Key Aで読めないblockは同じ既定鍵範囲のKey Bでも試します。sector trailerはAndroidから返されたbytesをそのまま保持します（Android実装が鍵bytesをmaskする場合があります）。鍵探索や認証回避はしません。
- NFC-VはSystem Informationの容量が確認できた範囲を読み、standard/extended block addressを扱います。容量不明時は最大256 block、容量既知時は最大4094 blockまでの上限付きscanで、上限・エラーは未読として表示します。
- ISO-DEPは既知EMV AIDに限りread-only GET DATA（9F36/9F13/9F17/9F4F）とbounded READ RECORDを使います。参照のない探索はSFI 1..10 / record 1..16、FCI 9F4Dで明示されたログはSFI 1..30と広告されたrecord数が対象です。EMV取引開始のGPO、認証、書込みは行いません。ISO-DEPアプリは独自プロトコルのため、このreaderを全ISO-DEPデータの取得とは見なしません。
- 取得結果は値・技術・全体単位でコピーできます。clipboard copyはUTF-8で64 KiBまでとし、超過時は切り詰めずにファイル保存/共有を案内します。テキスト/JSONの保存はSAF、共有はFileProviderのread-only添付として行い、フォーマットとファイル書込はUI thread外で処理します。SAF picker待機中のsnapshot本文はprivate cache file、復元用tokenはSavedStateHandleに保持するため、Activity/process再生成後も対象snapshotを復元します。保存操作中に新しい読取結果が来ても、操作開始時のsnapshotを保存します。ダンプにはカード識別子や機微な値が含まれる可能性があります。保存先・共有先を確認してください。
- タグ離脱・I/O失敗・中断・未対応範囲は隠さず、取得済みの結果と未読理由を保持します。端末が公開しない技術、必要な認証情報、独自・暗号化プロトコルの内部データをアプリが取得する保証はありません。

## 対応（開発版）
Android 7.0（API 24）以上のNFC対応端末

Android 6.0以下（API 23以下）は対象外です。

## ダウンロード
最新の公開版は **2.0.1** です。下記APKは今回の開発版における `minSdk 24` への変更を含まない、既公開のリリースです。

https://github.com/Atsumi3/NFCReader/releases/download/2.0.1/nfcreader-2.0.1.apk

## 開発環境・検証
- JDK 21（Android 17向けRobolectricテストに必要）、Gradle Wrapper 9.9.2
- Android SDK Platform 37.1、Build Tools 37.0.0
- AGP 9.5の互換性表ではBuild Tools 36.0.0が最小（既定）です。SDK Repositoryの最新安定版37.0.0を使い、CIと同じ版に固定しています。

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug assembleRelease
```

GitHub ActionsはJDK 21でPull Requestと`develop`へのpushを検証し、SDK Platform 37.1とBuild Tools 37.0.0をインストールします。

成功したrunはDebug APKを`debug-apk` artifactとして7日間保存します。
