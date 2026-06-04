# 変更履歴 (CHANGELOG)

初回実装(SPEC.md / README.md 初版)以降に追加・変更した内容をまとめる。
詳しい設計判断・既知の問題は `ISSUES.md`、使い方は `README.md` を参照。

---

## 永続化・再起動まわり

- **試合状態はメモリのみ**: チケット・ポイント・保有 kit・所属チームは再起動でリセットされる。
- **旗は位置のみ永続化**: `flags.yml` に座標だけ残り、再起動時は全て中立に戻る
  (`FlagManager.load()` で owner=null 固定)。
- kit の定義は Java コード(`Kit` 実装)であり、ファイル保存ではない。

## 旗 (Flag) の可視化

- `/flag create` は**見ているブロック**(最大5m)が必要。その1つ上に旗ブロックを設置する。
- 旗の上に**ホログラム**(TextDisplay)で旗名を表示。`setPersistent(false)` でワールド非保存、
  onEnable で貼り直し / onDisable で掃除。
- 占拠すると旗ブロックが**所有チーム色のコンクリート**に置き換わる(中立=白)。
- 旗ブロックを**破壊すると旗が無効化**(削除)される(バグ防止用。ゲーム中の破壊は想定外)。
- **占領範囲をパーティクル表示**: `FlagParticleTask` が 0.5秒ごとに所有チーム色の DUST リングを描画。

## 権限 (OP 判定)

- 運営系コマンドを **OP のみ** に制限: `/ktf start|stop|reload|tickets|initialtickets|flagdrain`、
  `/flag create|remove|setowner`、`/team setspawn|set`。
- 参照系(`/ktf status`, `/flag list`)とプレイヤー操作(`/team join`, `/kit`)は全員可。
- 判定は各コマンド内の `sender.isOp()`。

## チームスポーン

- `parseLocation()` が yaw/pitch も読むようにし、スポーン時に向きも復元。
- **`/team setspawn <id>`**(OP)を追加。現在地(向き込み)をそのチームのスポーンに設定し、
  `GameManager.setTeamSpawn()` が config.yml の `teams` を書き換えて保存する。

## プレイヤー挙動

- **死亡で kit リセット**: `PlayerListener.onDeath()` → `resetKitOnDeath()` で `currentKitId=null`。
  次のリスポーンで初期装備(革+石剣)に戻る。ポイントは死亡では消えない。
- **kit 購入でスポーン帰還**: `buyKit()` 成功時に自チームスポーンへ `teleport()`(失敗時は移動なし)。

## スコアボード (ネームタグ色 + サイドバー)

- バニラのメインボードは `/scoreboard`・`/team` で外から上書きされ競合するため使わず、
  **プラグイン専用ボードをプレイヤーごとに1枚**持つ(`GameManager.boards`、UUID キー)。
  ポイントが人ごとに違うため共有1枚では出せず、プレイヤー単位ボードを採用。
- **ネームタグ色**: 各ボードに全チームを登録し、`syncMembership` で全オンライン者を所属チームに入れる。
  config の `teams[].color` を `setColor()` で反映。参加・`/team join`・自動配属・試合開始時に
  `applyNameTag()` が全員のボードを同期。
- **サイドバー(画面右)**: `updateSidebar()` がチーム別チケット +「あなたのポイント」を表示。
  `GameTask` が毎秒更新。試合中でなくても表示(タイトルに待機中/進行中)。
- 切断時は `onQuit` → `removeBoard()` でボード破棄。
- 既知の小欠点: 行はスコア値の降順に並ぶ Minecraft 仕様のため、ポイントがチケットを超えると
  ポイント行が上に来る。

## フレンドリーファイア無効化

- `PlayerListener.onDamage()` が `EntityDamageByEntityEvent` を見て、同じチームのプレイヤー同士の
  ダメージをキャンセルする。近接攻撃に加え、矢などの飛び道具は `resolveAttacker()` で撃った主を
  判定して対象にする。チーム未所属同士・自傷はキャンセルしない。

## kit ラインナップ実装 (詳細は KITS.md)

- サンプル `ArcherKit`/`KnightKit` を廃し、**6 kit を実装**: `archer`(50) / `skirmisher`(100) /
  `arbalest`(150) / `lancer`(180/騎兵) / `robinhood`(180/騎兵) / `sniper`(240)。
  - `arbalest`(重弩兵): 防具なし・貫通(Piercing IV)クロスボウ×3・矢4スタック・常時鈍足I。
- **アイテム投棄禁止 (試合中のみ)**: `PlayerListener.onDrop`(`PlayerDropItemEvent`)を
  `game.isRunning()` のときだけキャンセル。試合外(ロビー等)では捨てられる。
- **試合開始時の装備配布を1秒遅延**: `startMatch()` でテレポート/チケット初期化/スコアボードは即時、
  `equip()` だけ `runTaskLater(20tick)` で配布。開始直後だと他処理のインベントリクリアとレースして
  装備が消えることがあったため。
- **軍兵 (soldier) 追加**: 150pt。鉄装備フル + ダイヤ剣 + 木の斧(盾割り用) + 貫通クロスボウ×1 + 矢1スタック。
- **kit 武器の調整**: 軽散兵からクロスボウ/矢を撤去(石斧+盾の近接寄りに)。軍兵に貫通クロスボウ×1+矢1スタックを追加。
  重弩兵に石の剣を追加(接近戦用)。
- **試合開始時に全旗を中立化**: `startMatch()` が `neutralizeAllFlags()` を呼び、前の試合の旗所有者を
  引き継がず owner=null・ブロック色も白に戻す(従来は `resetAllCaptures()` で進捗だけリセットしていた)。
- **旗のチケット減少を15秒に1チケットへ**: `flag.ticketDrainIntervalSeconds` を 5→15 に変更
  (config.yml と GameConfig の既定値の両方)。旗1個につき15秒ごとに敵チケット-1。
- **チケット数の手動変更コマンド**: `/ktf tickets <team> <amount>` を追加 (**OP のみ**)。
  指定チームのチケットを任意の値に設定し、サイドバーを即時更新する。
  `Team.setTickets()` は 0 未満を 0 に丸める。タブ補完でサブコマンド/チーム id を候補表示。
- **初期チケット数の変更コマンド**: `/ktf initialtickets <amount>` を追加 (**OP のみ**)。
  `GameManager.setInitialTickets()` が `config.ticketInitial` を更新し config.yml に保存。
  次の試合開始時から反映される(進行中の現在チケットは変えない。0未満は1に丸める)。
- **旗ドレイン間隔の変更コマンド**: `/ktf flagdrain <seconds>` を追加 (**OP のみ**)。
  旗保持で「何秒に1回チケットが減るか」を設定する。`GameManager.setFlagDrainInterval()` が
  `config.flagDrainIntervalSeconds` を更新し config.yml(`flag.ticketDrainIntervalSeconds`)に保存。
  毎秒の試合ループが config 値を直接見るため**進行中でも即時反映**(1未満は1に丸める)。
- **プレイヤーのチーム変更コマンド**: `/team set <player> <teamId>` を追加 (**OP のみ**)。
  対象オンラインプレイヤーを別チームへ移す(`joinTeam()` を流用)。コンソールからも実行可。
  タブ補完でオンライン名/チーム id を候補表示。
- **槍騎兵の馬HPを2倍 (44)**: `Kit.horseMaxHealth()` を追加し kit ごとに馬HPを指定可能化。
  `lancer` は 44、それ以外の騎兵(robinhood)は既定 22。`spawnCavalryHorse()` が `horseMaxHealth()` を使用。
- **槍騎兵の弱体化**: 馬HPを 44→**33**(通常22の3/2)に、武器をネザライトの槍→**鉄の槍** (`IRON_SPEAR`) に変更。
  馬速度の kit 別指定用に `Kit.horseMovementSpeed()`(既定0.225)を追加し、`spawnCavalryHorse()` がこれを使用。
  ※ 馬速度は 3/2(0.3375)→2/3(0.15)と試したが、遅すぎたため最終的に**通常(0.225)**へ戻した
  (LancerKit の override を削除し既定値を使用)。
- **コスト調整 + 重弩兵に防具追加**: ロビンフット(弓騎兵)を 180→**125pt**、重弩兵を 150→**110pt** に値下げ。
  重弩兵に**革装備一式**を追加(従来は防具なし)。
- **初期装備も Kit 化**: `DefaultKit`(id `default`、革一式+石剣)を追加。kit 未購入時・死亡からの復帰で適用。
  ショップ/一覧には出さない(KitRegistry には登録せず GameManager が直接保持)。
  これにより **初期装備でもパン2スタックが共通配布される**(死亡後にパンが配られなかった不具合を修正)。
  `equip()` は kit が null のとき `defaultKit` にフォールバックするので、パン配布が全ケースで通る。
- **共通配布**: 全 kit にパン2スタック。騎兵系は追加で小麦2スタック。`GameManager.equip()` で配布。
- **ポーション効果**: kit を選ぶたび・死亡時に全効果を除去(`clearPotionEffects`)。スナイパーは
  apply 時に無限時間の鈍足IIを付与。
- **騎兵系 (cavalry)**: `Kit.isCavalry()` / `horseArmor()` を追加。`spawnCavalryHorse()` が
  飼い慣らし済み・サドル・馬鎧(BODYスロット)付きの馬を出して**自動騎乗**。性能は固定
  (体力22 / 速度0.225 / ジャンプ0.7)。`setPersistent(false)`。
- **馬の管理**: `playerHorses`(UUID→馬UUID)で1人1頭を追跡。kit切替/死亡/退出/試合終了/onDisable で除去。
  **馬は復活しない**(殺されても再発行せず、kit を選び直すと新規スポーン)。
- **騎乗制限**: `PlayerListener.onMount` で騎兵系以外の馬騎乗をキャンセル(敵の馬でも騎兵系なら可)。
- **ネザライトの槍**: 1.21.11 実在の `Material.NETHERITE_SPEAR` を使用。
- `buyKit()` は「kit設定 → スポーンへTP → equip」の順(馬がスポーン地点に出るように)。

---

## まだ手を付けていない主な項目 (要相談)

ゲームバランス/体験に効くが未対応のもの(詳細は `ISSUES.md` 参照):

- 環境死・自殺でもチケットが減る(敵キル限定にするか要検討)
- リスポーン待機・スポーン保護(即リスポーンでスポーンキル可能)
- アリーナ境界(ワールド全体が PvP)
- キル/旗保持ボーナスポイント(今は時間経過のみ)
- 試合状態の遷移(ロビー→開始→終了→自動リセット)・最低人数ガード

## 動作確認状況

- [x] Maven ビルド成功(`target/kit_teamfight-1.0-SNAPSHOT.jar`)
- [ ] **実 Paper サーバでの起動・動作確認は未実施**(最大の未検証点)
