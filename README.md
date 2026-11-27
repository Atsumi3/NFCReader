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
- MIFARE UltralightはGET_VERSIONで既知容量を確定できるNTAG213/215/216とUltralight EV1で、末尾pageまで最大4-page単位のFAST_READを試し、失敗時は単page READへfallbackします。既知profileではPWD/PACK pageを識別し、raw応答を保持しつつ実値不明・未読として扱うため、技術結果をCOMPLETEにしません。未知profileは最大256 pageのbounded READにとどめ、容量不明として扱います。4-page READ応答はraw全体を保存しますが、先頭page以外へデータを推測割当てしません。MIFARE Classicは既存の組み込み既定鍵のみで認証し、Key Aで読めないblockは同じ既定鍵範囲のKey Bでも試します。Key B候補の認証失敗後は元の成功鍵で再認証して次blockを読みます。TagLost時は残りのscanを停止し、未取得block範囲を未読として記録します。sector trailerはAndroidから返されたbytesをそのまま保持します（Android実装が鍵bytesをmaskする場合があります）。鍵探索や認証回避はしません。
- NFC-VはSystem Informationの容量が確認できた範囲を読み、standard/extended block addressを扱います。容量不明時は最大256 block、容量既知時は最大4094 blockまでの上限付きscanで、上限・エラーは未読として表示します。budget超過時も取得済み範囲と未読範囲を記録して停止します。
- ISO-DEPは既知EMV AIDに限りread-only GET DATA（9F36/9F13/9F17/9F4F）とbounded READ RECORDを使います。参照のない探索はSFI 1..10 / record 1..16、FCI 9F4Dで明示されたログはSFI 1..30と広告されたrecord数が対象です。EMV取引開始のGPO、認証、書込みは行いません。ISO-DEPアプリは独自プロトコルのため、このreaderを全ISO-DEPデータの取得とは見なしません。
- 取得結果は値・技術・全体単位でコピーできます。clipboard copyはUTF-8で64 KiBまでとし、超過時は切り詰めずにファイル保存/共有を案内します。テキスト/JSONの保存はSAF、共有はFileProviderのread-only添付として行い、フォーマットとファイル書込はUI thread外で処理します。SAF picker待機中のsnapshot本文はprivate cache file、復元用tokenはSavedStateHandleに保持するため、Activity/process再生成後も対象snapshotを復元します。保存操作中に新しい読取結果が来ても、操作開始時のsnapshotを保存します。ダンプにはカード識別子や機微な値が含まれる可能性があります。保存先・共有先を確認してください。共有添付は起動時または次回共有時に24時間より古いものを掃除します。共有後・キャンセル直後の削除や定時削除は保証せず、OSが先にcacheを整理する場合もあります。
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

## Google Play 掲載案

- **掲載名 (9/30):** NFCReader
- **短い説明 (34/80):** NFCタグを読み取り、取得結果・未読範囲・rawデータを見やすく表示
- **詳しい説明:**

NFCReaderは、Androidが公開するNFCタグ情報を読み取り専用で確認するツールです。

タグの種類、取得できた情報、デコード結果、raw bytes、読取範囲、問題や未読理由をまとめて表示します。技術ごとに結果を整理し、部分的な取得や読み取れなかった範囲も確認できます。

読み取り結果は端末内で処理し、開発者のサーバーへ送信しません。テキストまたはJSONで保存したり、利用者が選んだアプリへ共有したりできます。共有前に、ダンプへカード識別子などの機微情報が含まれていないか確認してください。

このアプリはタグへの書き込みや認証回避を行いません。すべてのタグや内部データの取得を保証するものではなく、認証が必要な領域や独自方式は読み取れないことがあります。

### 掲載前の確認

- Google Playのサポートメールは未確認です。掲載可能なメールの確認後にConsoleへ登録してください。
- プライバシーポリシーHTMLは`app/src/main/assets/privacy-policy.html`です。公開URLへは未デプロイです。
- Data safety、対象年齢、コンテンツレーティング、本人確認、署名登録、審査送信は未実施です。2023-11-13以降に作成された新規個人アカウントに該当する場合、production公開前に12人以上・連続14日間のclosed testが必要です。
- targetSdkは37です。現行の新規掲載・更新要件（36以上）を満たすため、下げないでください。
- phone screenshotsは実機UIから2〜8枚を撮影してください。4枚・1080x1920を推奨基準にし、捏造UIや秘密情報は含めないでください。
- ソース`6530f10e16e330070ad8defcbdb5cfeb43c48dd2`のartifact-only署名APK ([run](https://github.com/Atsumi3/NFCReader/actions/runs/45865563391)) と署名AAB ([run](https://github.com/Atsumi3/NFCReader/actions/runs/45866374094)) では、native `.so` のELF `PT_LOAD` 16 KB alignmentを確認済みです。APK内のnative libraryはZIP上の16 KB alignmentも確認しました。これは当該revisionの成果物に対する検証で、将来のビルドには適用されません。Play Consoleへ提出する最終buildと、Play App Signing後に配信されるAPKは未確認のため、提出時に再検証してください。
- `play_version_code`にはPlay Consoleに登録済みの最大値より大きい正整数を指定し、上限`2,100,000,000`を超えないようにしてください。ソース既定値`2000100`は検証用で、本番値として未確定です。Console上の既存最大値は未確認です。
- AABは既存のRelease workflowを対象ブランチで手動実行し、`artifact_only=true`、`artifact_format=aab`、確認済みの`play_version_code`を指定します。version bump・tag・GitHub Releaseは作りません。Actions artifactは公開リポジトリのread accessを持つ人が取得できるため、秘密情報を入れないでください。
- APKは既存のRelease workflowを`develop` refで手動実行し、`artifact_only=true`、`artifact_format=apk`を指定します（`play_version_code`は不要です）。workflowは起動時のSHA（`github.sha`）をcheckoutし、checkoutしたcommitとの一致を検証します。署名済みAPKは`signed-release-apk-<source-sha>` artifactとして7日間保存されます。version bump・tag・GitHub Releaseは作りません。

### Data safety 申告メモ

現行コードでは広告・分析SDK、ネットワーク権限、アプリからのサーバー送信は確認されていません。読取結果はメモリ上で扱い、履歴として永続保存しません。利用者が保存先を選んだファイル、共有先アプリ、クリップボードにはデータが渡ります。共有用cacheはOSの整理まで残る場合があります。Play Consoleの質問に沿って最終確認し、未確認の申告を完了扱いにしないでください。
