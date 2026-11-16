# Apache-2.0 へのライセンス変更に関する調査メモ

## 目的
CINPO のライセンスを現行の ANAL-Tight（AokiApp Normative Application License - Tight 1.0.1）から Apache License 2.0 に置き換える場合に必要な変更点、確認事項、影響範囲を整理する。

この文書は調査用であり、ライセンス変更の実施や法的判断を確定するものではない。最終判断は権利者の確認と、必要に応じた専門家の助言に基づいて行う。

## 現状
- 本体ライセンス: `LICENSE.md`（ANAL-Tight 1.0.1, 非 OSS / source-available）
- `README.md`: 「source-available, not open source」と記載、ライセンスバッジが `ANAL--T`
- `THIRD_PARTY_NOTICES.md`: CINPO 本体は ANAL-Tight の対象外（第三者コンポーネントは各ライセンス）と記載。LGPL コンポーネント向けの「Limited relinking permission」を含む
- `build.gradle`: Maven POM メタデータに ANAL-Tight を記載、`processResources` で `LICENSE.md` を `META-INF/cinpo/CINPO-LICENSE.md` として同梱
- 配布物/ビルド: `runtimeJar` / `cinpoJar`（テンプレート側タスク）で成果物を生成。`CinpoJarTask` は依存アーカイブを展開して fat JAR を構成する

## 変更が必要になりそうなファイル・箇所（ソース）
| 対象 | 現状の内容 | 想定される変更 |
| --- | --- | --- |
| `LICENSE.md` | ANAL-Tight 本文 | Apache License 2.0 本文へ置換（必要なら `NOTICE` ファイルの新設も検討） |
| `README.md` | ライセンスバッジ `ANAL--T`、License 節で ANAL-Tight と説明 | バッジ・説明文・リンク先を Apache-2.0 に更新 |
| `THIRD_PARTY_NOTICES.md` | CINPO 本体が ANAL-Tight 対象外である旨、LGPL 向け Limited relinking permission | 本体ライセンス表記の更新。Apache-2.0 採用時に Limited relinking permission が必要か再評価（LGPL 部品の義務自体は残る） |
| `build.gradle` | POM の `licenses/license/name,url,distribution,comments`、`processResources` での同梱名/内容 | POM メタデータを Apache-2.0 に更新。`META-INF/cinpo/` に同梱するライセンス/NOTICE の構成を見直し |
| `docs/vendor.md` 等 | 本体ライセンスへの直接言及は少ないが、配布/利用前提の記述を確認 | 文脈上の齟齬がないか確認 |
| ソースファイルヘッダ | 現時点で全ファイルに統一ヘッダがあるかは未確認 | Apache-2.0 採用時のヘッダ方針（付与するか、`LICENSE`/`NOTICE` のみとするか）を決定 |
| テンプレート (`template/*`) | テンプレート自体に独立した `LICENSE` は見当たらない | 生成物/テンプレート利用者の成果物に関するライセンス方針を明確化 |

## 生成物・ビルド関連で確認が必要な点
- `build/` 配下の POM・同梱済み `CINPO-LICENSE.md` 等には旧ライセンス表記が残るが、再ビルド/再生成で更新される想定。
- `CinpoJarTask` は依存 JAR を展開して統合し、`META-INF/MANIFEST.MF` と署名ファイル（`.SF/.DSA/.RSA`）を除外する。fat JAR では `META-INF/LICENSE` / `META-INF/NOTICE` が上書き・混在しやすいため、Apache-2.0 採用時も含めて第三者通知の保持方法を見直す必要がある。
- `processResources` は `LICENSE.md`、`THIRD_PARTY_NOTICES.md`、`third-party/licenses` を `META-INF/cinpo/` に配置している。Apache-2.0 では `NOTICE` ファイルを用いる場合があるため、同梱構成の設計が必要。

## 第三者依存との関係
### 確認済み・記載済み
- **GlobalPlatformPro**: `LGPL-3.0-or-later`（`THIRD_PARTY_NOTICES.md` 記載、`jcardengine` 経由）。LGPL 文書は `third-party/licenses/` に同梱。
- **Gradle Wrapper**: Apache-2.0（`THIRD_PARTY_NOTICES.md` 記載）。

### 追加確認が必要
- **JCardEngine**: 実行時依存。公開情報上は Apache-2.0 と確認できるが、CINPO の `THIRD_PARTY_NOTICES.md` への記載や配布物での表示は要確認。
- **SnakeYAML 2.2 / picocli 4.7.6**: 実行時依存。配布物に同梱される場合、必要なライセンス文書・NOTICE の扱いを確認する。
- **その他の推移的依存**: アプライアンス/fat JAR に含まれる全コンポーネントの棚卸しが必要。
- **テンプレート依存**: `template/basic` 等には Bouncy Castle などテンプレート側の依存がある。フレームワーク本体のライセンス変更と、テンプレート生成物/利用者プロジェクトの責務分界を明確にする。

### Apache-2.0 採用時の論点
- Apache-2.0 は寛容ライセンスであり、LGPL コンポーネントと組み合わせた配布自体は一般に可能だが、LGPL 部品に関する義務（ライセンス表示、対応ソース提供、差し替え/再リンク可能性など）は引き続き満たす必要がある。
- 現行の `THIRD_PARTY_NOTICES.md` にある Limited relinking permission は、ANAL-Tight の制限を補う目的で記載されている。Apache-2.0 では CINPO 本体の改変・再配布自体は許容されるため、この条項の要否や表現は見直しが必要。
- 配布物で Apache-2.0 の `NOTICE` を用いる場合、帰属表示の集約方法を設計する必要がある。

## 既存の配布物で見えた課題（参考）
ローカルで作成したテンプレート由来のアプライアンス JAR を確認したところ、次の状況があった。
- `META-INF/cinpo/` 配下に CINPO/GlobalPlatformPro 向けの文書は同梱されていた。
- 一方で、JAR 直下の `META-INF/LICENSE` が Apache License 2.0 本文、`META-INF/NOTICE` が Jackson の NOTICE になっており、同梱コンポーネント全体の表示としては不整合・不足の可能性がある。

Apache-2.0 への変更に合わせて、fat JAR の通知同梱方法（個別保持、集約 NOTICE、依存ごとの配置など）を是正することが望ましい。

## 移行時の作業案
1. **権利・方針の確認**: 著作権者、過去のコントリビューション、配布済みバージョンの扱い、変更適用範囲を確認する。
2. **ライセンス本文の決定**: `LICENSE.md` を Apache-2.0 に置換し、`NOTICE` の要否と内容を決める。
3. **ソース内表記の更新**: `README.md`、`THIRD_PARTY_NOTICES.md`、`build.gradle`、必要なドキュメント/ヘッダを更新する。
4. **第三者通知の再整備**: 同梱・配布対象の全依存を棚卸しし、必要なライセンス文書/NOTICE/帰属表示を揃える。`CinpoJarTask` と `processResources` の同梱方法を見直す。
5. **検証**: 再ビルド後の成果物（runtime jar, plugin jar, template から生成した appliance jar 等）にライセンス/NOTICE が意図どおり含まれるか確認する。
6. **リリース/告知**: バージョン、変更履歴、利用者への影響（再配布条件の変化、テンプレート生成物の扱い）を整理する。

## 未確定事項・確認が必要な質問
- Apache-2.0 適用の対象範囲（フレームワーク本体のみか、テンプレート、ドキュメント、生成物の扱いも含むか）
- `NOTICE` ファイルを置くか、置く場合の記載内容
- ソースファイルへのライセンスヘッダを付与するか
- 過去リリース済み成果物のライセンス表記・再配布条件の扱い
- 変更に必要な権利者/貢献者の同意範囲
- `THIRD_PARTY_NOTICES.md` の Limited relinking permission をどう再構成するか（削除、条件付き維持、LGPL 向け案内への置換など）
- fat JAR における第三者 `META-INF/LICENSE`/`NOTICE` の保持方針
- SNAPSHOT 依存（`jcardengine` / GlobalPlatformPro）のバージョン固定・入手性と、配布時の対応ソース提供手順

## 参考（調査で見た主な関連ファイル）
- `LICENSE.md`
- `README.md`
- `THIRD_PARTY_NOTICES.md`
- `build.gradle`
- `src/main/java-gradle/app/aoki/cinpo/gradle/task/CinpoJarTask.java`
- `docs/vendor.md`
- `third-party/licenses/GPL-3.0.txt`
- `third-party/licenses/LGPL-3.0.txt`
