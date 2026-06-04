# kit_teamfight 実装ガイド (説明用)

`SPEC.md` の仕様をもとに作った初回実装の説明。クラス構成・使い方・各機能の対応をまとめる。

---

## ビルド方法

Maven が PATH に無い環境のため、IntelliJ 同梱の Maven を使ってビルドした。

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.2.1\plugins\maven\lib\maven3\bin\mvn.cmd" `
  -f "C:\Users\nakam\IdeaProjects\kit_teamfight\pom.xml" clean package
```

成果物: `target/kit_teamfight-1.0-SNAPSHOT.jar` (shade 済み)。これを Paper サーバの `plugins/` に入れる。

IntelliJ からは Maven ツールウィンドウの `package` で同じものが作れる。

---

## クラス構成

```
lobby.kit_teamfight
├── Kit_teamfight            メインクラス。配線・コマンド登録・タスク起動
├── game
│   ├── GameManager          ハブ。チーム/プレイヤー/装備/購入/チケット/勝敗
│   ├── GameConfig           config.yml の値を保持
│   ├── GameTask             1秒ループ (ポイント/旗キャプチャ/ドレイン)
│   └── Team                 チーム1つ (チケット・スポーン・色)
├── player
│   └── PlayerData           プレイヤーの所属/ポイント/保有kit
├── kit
│   ├── Kit                  kit インターフェース (枠組み)
│   ├── KitRegistry          kit の登録・取得
│   ├── KitShop              チェストGUI + クリック処理
│   └── kits/                サンプル kit (ArcherKit, KnightKit)
├── flag
│   ├── Flag                 旗1つ (所有チーム・キャプチャ進捗)
│   └── FlagManager          旗の生成/削除/flags.yml 永続化
├── command
│   ├── GameCommand          /ktf
│   ├── TeamCommand          /team
│   ├── KitCommand           /kit
│   └── FlagCommand          /flag
└── listener
    └── PlayerListener       参加/退出/死亡/リスポーン/旗ブロック破壊
```

スコアボード(ネームタグ色・サイドバー)は専用クラスを設けず `GameManager` 内に実装
(`boards` / `applyNameTag` / `syncMembership` / `updateSidebar` 他)。

---

## 仕様との対応

| 仕様 (SPEC.md) | 実装 |
|---|---|
| 革装備+石剣スタート | `GameManager.giveStartingLoadout()` |
| 時間経過でポイント | `GameTask` → `GameManager.tickPoints()` (config 間隔) |
| kit 購入で装備強化 | `GameManager.buyKit()` / `KitShop` |
| kit は1つだけ・購入で完全置換 | `buyKit()` が `clearEquipment()` → `kit.apply()` |
| リスポーンで kit 再適用 | `PlayerListener.onRespawn()` → `equip()` |
| 旗をコマンドで設置 (任意個数) | `/flag create`、`FlagManager` |
| 旗周囲に数秒留まると占拠 | `GameTask.evaluateCapture()` |
| キル/旗保持で敵チケット減少 | `onDeath()` / `tickFlagDrain()` |
| チケット0で勝利 | `checkVictory()` |
| 2チーム→N チーム拡張 | `teams` を config 定義、全処理をループ化 |
| 制限時間なし | タイマー終了条件は実装していない |
| チームスポーンをゲーム内設定 | `/team setspawn` → `GameManager.setTeamSpawn()` (向き込み、config保存) |
| 死亡で kit リセット | `PlayerListener.onDeath()` → `resetKitOnDeath()` (次リスポーンで初期装備) |
| kit 購入でスポーン帰還 | `buyKit()` 成功時に自チームスポーンへ `teleport()` |
| チームのネームタグ色 | 専用ボードのチームに `setColor()` (`applyNameTag`) |
| サイドバー表示 | `updateSidebar()` = チーム別チケット + 自分のポイント (毎秒) |
| チケット数を手動変更 | `/ktf tickets <team> <n>` [OP] → `Team.setTickets()` + サイドバー更新 |
| 初期チケット数を変更 | `/ktf initialtickets <n>` [OP] → `setInitialTickets()` (config.yml 保存・次試合反映) |
| 旗ドレイン間隔を変更 | `/ktf flagdrain <秒>` [OP] → `setFlagDrainInterval()` (config.yml 保存・即時反映) |
| プレイヤーのチーム変更 | `/team set <player> <id>` [OP] → `joinTeam()` (対象を別チームへ) |

---

## 使い方 (サーバ内コマンド)

```
/team join <id|auto>      チームに参加 (auto = 人数の少ない方へ)
/team list                チーム一覧とチケット表示
/team setspawn <id>       現在地(向き込み)をそのチームのスポーンに設定し config保存 [OP]
/team set <player> <id>   指定プレイヤーのチームを変更 [OP]

/kit shop                 kit ショップGUIを開く
/kit list                 kit 一覧をチャット表示
/kit buy <id>             直接購入 (archer/skirmisher/soldier/arbalest/lancer/robinhood/sniper)

/flag create <id> [team]  足元に旗を設置 (team 省略で中立)
/flag remove <id>         旗を撤去
/flag list                旗一覧
/flag setowner <id> <team|none>  所有者を直接設定

/ktf start                試合開始 (チケットリセット・旗中立化・装備配布・スポーンへTP) [OP]
/ktf stop                 試合停止 [OP]
/ktf reload               config.yml 再読込 [OP]
/ktf status               進行状況・各チームのチケット・旗数
/ktf tickets <team> <n>   指定チームの現在チケット数を n に設定 [OP]
/ktf initialtickets <n>   試合開始時の初期チケット数を n に設定し config保存 (次試合から) [OP]
/ktf flagdrain <秒>       旗保持で何秒に1回チケットが減るかを設定し config保存 (即時反映) [OP]
```

### 権限 (OP 判定)
変えられるとマズい運営系コマンドは **OP のみ** 実行可能:
- `/ktf start` / `stop` / `reload` / `tickets` / `initialtickets` / `flagdrain`
- `/team setspawn` / `set`
- `/flag create` / `remove` / `setowner`

参照系 (`/ktf status`, `/flag list`) とプレイヤー操作 (`/team join`, `/kit`) は全員可。
判定は各コマンド内の `sender.isOp()`。より細かい権限ノードが必要になったら
`plugin.yml` の `permissions:` + `hasPermission()` へ拡張する。

### 最短の試遊手順
1. `config.yml` の `teams[].spawn.world` を実際のワールド名に合わせる
2. サーバ起動、プレイヤーが参加 (自動でチーム配属)
3. 必要なら `/flag create mid` などで旗を設置 (無くてもOK)
4. `/ktf start` で開始
5. キルや旗占拠で敵チケットが減り、0 で決着

---

## スコアボード / プレイヤー挙動

- **ネームタグ色**: チームの `color`(config)が頭上の名前・タブの色に反映される。
  バニラのメインボードは外部コマンドで上書きされるため使わず、**プラグイン専用ボードをプレイヤーごとに1枚**持つ。
- **サイドバー (画面右)**: 常時表示。各チームのチケット残量 + **自分のポイント**を出す。
  ```
  Kit Teamfight
  Red Team        100
  Blue Team        95
  あなたのポイント    42   ← 自分だけの値
  ```
  ※ 行はスコア値の降順に並ぶ Minecraft 仕様。ポイントがチケットを超えると順序が入れ替わる。
- **死亡で kit リセット**: 死ぬと保有 kit を失い、次のリスポーンで初期装備(革+石剣)に戻る。ポイントは消えない。
- **kit 購入でスポーン帰還**: kit を買うと自チームのスポーンへ自動で戻される(購入失敗時は移動なし)。
- **フレンドリーファイア**: 無効化済み。同じチームのプレイヤー同士はダメージが通らない(矢などの飛び道具も含む)。

---

## kit について

現在のラインナップは5つ: `archer`(50) / `skirmisher`(100) / `lancer`(180/騎兵) /
`robinhood`(180/騎兵) / `sniper`(240)。各 kit の中身・騎兵系の仕様は **`KITS.md`** を参照。
新規 kit は `Kit` を実装したクラスを作り、`Kit_teamfight.onEnable()` で
`game.getKitRegistry().register(...)` するだけで増やせる。コア側の変更は不要
(騎兵化したい場合は `isCavalry()`/`horseArmor()` を override するだけ)。

---

詳しい設計判断・既知の問題・未実装は `ISSUES.md` を参照。
