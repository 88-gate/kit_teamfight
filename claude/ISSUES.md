# 実装メモ・起きた問題・未実装 (ISSUES)

初回実装で遭遇した問題、暫定対応、設計判断、今後の課題をまとめる。

---

## 1. ビルド時に起きた問題

### Maven が PATH に無い
- `mvn` コマンドがコマンドラインから見つからなかった。
- **対応**: IntelliJ 同梱の Maven を直接呼んでビルドした。
  `C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.2.1\plugins\maven\lib\maven3\bin\mvn.cmd`
- **恒久対応案**: スタンドアロン Maven を入れて PATH に通すか、Maven Wrapper (`mvnw`) をリポジトリに追加する。
- ビルド自体は成功 (`target/kit_teamfight-1.0-SNAPSHOT.jar` 生成、約 42KB)。

---

## 2. 設計上の判断 (仕様で曖昧だった点をこう決めた)

- **キャプチャ進捗は「停止」式**: 敵が割り込んだら進捗は止まるだけで巻き戻さない (SPEC 通りシンプル優先)。
- **複数チームが旗範囲に混在**: どのチームも進捗を進められず膠着 (`evaluateCapture` で `teamsPresent.size() != 1` なら停止)。
- **旗保持ドレインの配分**: 旗を N 個持つチームは、自分以外の**全敵チーム**それぞれから `N × ticketDrainPerFlag` を奪う (3チーム以上対応)。
- **勝敗判定**: いずれかのチケットが 0 になった瞬間に終了。残存チームの最大チケット保有が勝者。
- **kit の「完全置換」**: 購入時に防具+インベントリを `clear()` してから `apply()`。所持アイテム (矢など) も消える点に注意。

---

## 3. 既知の制限・注意点

- **`KitShop` のクリック判定が表示名ベース**: クリックされたアイテムの表示名から kit を逆引きしている。
  kit の `displayName` が重複すると誤判定する。将来は PersistentDataContainer に kit id を埋める方が堅牢。
- **`InventoryView#getTitle()` は新 Paper で非推奨**: ショップ判定に使用。現状は動くが、将来 Component ベースへ移行が必要かもしれない。
- **`org.bukkit.ChatColor` を使用**: レガシー API。動作はするが、Adventure (`Component` / `NamedTextColor`) への移行余地あり。
- **旗の視覚表現 (実装済み)**: 旗はコンクリートブロック + ホログラム (TextDisplay) で可視化する。
  - `/flag create` は**見ているブロック**(最大5m)が必要。その1つ上に旗ブロックを設置する。
  - 占拠で所有チーム色のコンクリートに置き換わる (中立=白)。色は `FlagVisualizer.toDye()` でマッピング。
  - 旗ブロックを**破壊すると旗が無効化** (削除) される。ゲーム中の破壊は想定外のバグ防止用。
  - ホログラムは `setPersistent(false)` でワールド保存しないため再起動で残らない。onEnable で `renderAllFlags()` により貼り直し、onDisable でホログラムを掃除する。
  - 注意: ブロックはコンクリートを使用 (バナーは設置面が無いと落ちるため回避)。
  - **占領範囲パーティクル (実装済み)**: 旗の周囲に所有チーム色の `DUST` パーティクルでリングを描画。
    `FlagParticleTask` が 0.5秒 (10 tick) ごとに `FlagVisualizer.showCaptureRing()` を呼ぶ。
    半径は `flag.captureRadius` に追従。試合中でなくても表示される (設置確認に便利)。
- **スポーン未設定時**: `teams[].spawn` が無い/ワールド不一致だとテレポートをスキップする (エラーにはしない)。
- **フレンドリーファイア (無効化済み)**: `PlayerListener.onDamage` が `EntityDamageByEntityEvent` を見て、
  同じチームのプレイヤー同士のダメージをキャンセルする。近接に加え矢などの飛び道具も
  `resolveAttacker()` で撃った主を判定して対象にする。チーム未所属同士・自傷は対象外。
- **ポイントは全オンライン+チーム所属者に加算**: 観戦やロビー状態の概念がまだないため、参加者全員が常時加算される。
- **スコアボード (実装済み)**: バニラのメインボードは `/scoreboard`・`/team` で外から上書きされ競合するため使わず、
  **プラグイン専用ボードをプレイヤーごとに1枚** (`getNewScoreboard()`) 持つ (`GameManager.boards` に UUID キーで保持)。
  ポイントは人ごとに違うため共有1枚では出せず、プレイヤー単位ボードにした。
  - **ネームタグ色**: 各ボードに全チームを登録し (`setupTeamsOn`)、`syncMembership` で全オンライン者を
    所属チームのエントリに入れる。config の `teams[].color` を `setColor()` で反映。色以外の ChatColor は無視。
    参加時・`/team join`・`autoAssign`・試合開始時に `applyNameTag()` が **全員のボード** を同期し直す。
  - **サイドバー (横のスコア表示)**: `updateSidebar()` が各プレイヤーのボードの SIDEBAR Objective に
    「各チーム行 (エントリ=色付きチーム名、スコア=チケット)」+「自分のポイント行 (`POINT_LINE` 固定ラベル、
    スコア=ポイント値)」を出す。`GameTask` が毎秒更新。試合中でなくても表示 (タイトルに待機中/進行中)。
  - 切断時は `PlayerListener.onQuit` → `removeBoard()` でボードを破棄。
  - **消える不具合の対策 (修正済み)**: リスポーン等でプレイヤーの表示ボードがメインに戻ると
    サイドバーが消えたまま復活しなかった。対策として ①`updateSidebar()` で毎秒
    `player.getScoreboard() != 専用ボード` なら `setScoreboard()` で貼り直す自己修復、
    ②`onRespawn` で `applyNameTag()` を再呼び出し、の2点を入れた。
  - 既知の小欠点: サイドバー行は **スコア値の降順** に並ぶため、ポイントがチケットより大きくなると
    ポイント行が上に来る (Minecraft の仕様)。表示順を固定したい場合は別途インデックス制御が必要。
  - フレンドリーファイアは別途 `PlayerListener.onDamage` で無効化済み(同チーム間ダメージをキャンセル)。
    スコアボードチームの friendlyFire フラグには依存していない。
- **死亡で kit リセット (実装済み)**: `PlayerListener.onDeath` で `resetKitOnDeath()` を呼び `currentKitId=null`。
  次のリスポーンで `equip()` が初期装備(革+石剣)を配る。ポイントは死亡では消えない。
- **kit 購入でスポーン帰還 (実装済み)**: `GameManager.buyKit()` 成功時、自チームスポーンへ `teleport()`。
  スポーン未設定なら TP をスキップ。購入失敗(ポイント不足等)では移動しない。

---

## 3.5 リスポーン時の装備リセット (修正済み / keepInventory 前提)

- **症状**: 復活後に死亡前の装備(革装備kit等)が残る。`PlayerPostRespawnEvent` で積み替えても直らなかった。
- **原因**: keepInventory が有効だと、リスポーン後にサーバが「保持インベントリ」を復元し、
  PostRespawn でこちらが付けた装備を**上書き**してしまうため。
- **対応 (確定方針)**: **運用上 keepInventory は基本オン**。装備の積み替えを `PlayerDeathEvent` で行う:
  `event.getDrops().clear()` + `event.setKeepInventory(true)` の上で `equip()` を呼び、
  初期装備(革+石剣+パン)をインベントリに積む。→ 保持されるインベントリ自体が初期装備になり、
  復活後それで出る。`PlayerRespawnEvent` ではリスポーン地点設定とスコアボード貼り直しのみ。
  - 注意: この方式は keepInventory を前提にする(死亡イベントで `setKeepInventory(true)` を明示)。
    keepInventory を切る運用に戻す場合は、復活時の装備付与を別途用意する必要がある。

## 4. 未実装 (SPEC の Open Questions に対応)

- リスポーン待機時間の制御 (現状は即時リスポーン)
- 試合状態のちゃんとした遷移 (ロビー→開始→終了→リセット)。今は `running` フラグのみ
  - **永続化方針 (確定)**: サーバ再起動で試合状況はリセットする。チケット・ポイント・保有kit・
    所属チームはメモリのみで再起動で消える。旗は**位置のみ** `flags.yml` に残り、再起動時は
    全て中立に戻る (`FlagManager.load()` で owner=null 固定)。
- 試合開始の自動トリガー (人数到達など)。今は `/ktf start` 手動のみ
- 観戦モード
- スコアボード / ボスバー表示 (チケット残量・自分のポイント・保有kit)
- 旗の視覚ブロック設置とパーティクル等の演出
- 試合終了後の処理 (ロビー送還・自動再戦)
- キル/旗保持によるボーナスポイント (`PointSource` の追加実装)。今は時間経過のみ
- config の旗ドレイン・キャプチャ値のバランス調整 (暫定値)

---

## 5. 動作確認状況

- [x] Maven ビルド成功 (jar 生成)
- [ ] 実サーバでの起動確認 (未実施 — Paper サーバへ投入してのテストが必要)
- [ ] 各コマンドの実機動作確認
- [ ] 旗占拠・チケット減少・勝敗のゲームプレイ確認

> 次のステップ: Paper 1.21 サーバに jar を入れて `/ktf start` まで通し、
> 旗占拠とチケット減少の挙動を実際に確認する。
