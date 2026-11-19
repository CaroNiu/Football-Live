---
项目: 通用（IntelliJ 插件）
类型: 操作指南
业务领域: 插件工程
技术栈: GitHub Actions, Gradle, Kotlin/Java
状态: 生效中
创建日期: 2026-11-19
负责人: Caro_Niu
tags: [IntelliJ插件, GitHub Actions, CI, Marketplace, 发布流水线]
参考实现: D:\testProject\game-plugin2\game-plugin（NBA Live Score，已稳定运行 10+ 版本）
---

# IntelliJ 插件 CI 自动发布操作指南（GitHub Actions → JetBrains Marketplace）

> 从 NBA Live Score 插件（10+ 版本零事故）提炼的可复用流水线。一次配置，之后发版只需
> 「同步版本号 → 打 tag → push」三条命令。其他 IntelliJ 插件项目照此配置即可复用。

## 一、前置准备（每插件一次性配置）

### 1. JetBrains Marketplace Token

1. 登录 [Marketplace](https://plugins.jetbrains.com/) → 头像 → Settings
2. 旧版入口：My Account → Plugin Publishing → **Generate Token**（或个人资料页 My Tokens）
3. 复制 token（只显示一次）

### 2. GitHub 仓库 Secret

仓库 → Settings → Secrets and variables → Actions → **New repository secret**

- Name：`JETBRAINS_MARKETPLACE_TOKEN`
- Value：上一步的 token

### 3. 行尾防护（Windows 项目必做）

仓库根加 `.gitattributes`（CRLF 的 yml 会让 Linux bash run 块同秒失败）：

```gitattributes
*.yml text eol=lf
*.yaml text eol=lf
*.sh text eol=lf
```

## 二、Workflow 文件

新建 `.github/workflows/build-and-publish.yml`（两个独立阶段：PR 构建验证 / tag 发布）。
完整模板见参考实现 `.github/workflows/build-and-publish.yml`，要点：

```yaml
name: Build and Publish Plugin

on:
  pull_request:
    branches: [ main ]
  push:
    tags: [ 'v*' ]
  workflow_dispatch:

jobs:
  build:   # PR/手动触发：构建验证 + artifact 上传（给用户下载测试）
    if: github.event_name == 'pull_request' || github.event_name == 'workflow_dispatch'
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - run: |
          chmod +x gradlew
          ./gradlew buildPlugin --no-daemon --console=plain
      - uses: actions/upload-artifact@v4
        with:
          name: <插件名>-${{ github.event.pull_request.number || github.run_number }}
          path: build/distributions/*.zip
          retention-days: 14

  publish:   # v* tag：发布 Marketplace + GitHub Release
    if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4

      # 防呆：tag 必须与版本一致（版本号漏改在这里直接拦截，不污染 Marketplace）
      - name: 校验 tag 与版本一致
        run: |
          VERSION=$(grep -E '^version = "[0-9.]+"' build.gradle.kts | head -1 | sed 's/.*"\([0-9.]*\)".*/\1/')
          TAG="${GITHUB_REF_NAME#v}"
          if [ "$TAG" != "$VERSION" ]; then
            echo "::error::tag v$TAG 与 build.gradle.kts 版本 $VERSION 不一致，请同步后再发布"
            exit 1
          fi

      - name: Publish to JetBrains Marketplace
        env:
          JETBRAINS_MARKETPLACE_TOKEN: ${{ secrets.JETBRAINS_MARKETPLACE_TOKEN }}
        run: |
          chmod +x gradlew
          ./gradlew publishPlugin --no-daemon --console=plain

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          name: <插件名> ${{ github.ref_name }}
          files: build/distributions/*.zip
```

> ⚠️ workflow 文件行尾必须 **LF**（CRLF 的 bash run 块会同秒失败）。

## 三、build.gradle.kts 关键配置

IntelliJ Platform Gradle Plugin 2.x 的 `intellijPlatform { }` 块内：

```kotlin
intellijPlatform {
    pluginConfiguration {
        name = "<插件名>"
        version = project.version.toString()   // 与项目版本联动（发版同步点 -1）
        ideaVersion { sinceBuild = "242" }     // 按插件兼容范围
    }
    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
    }
}
```

> `version` 联动 `project.version` 后，发版同步点从三处降为两处
> （build.gradle.kts version + plugin.xml change-notes）。

**未配置签名证书时 `publishPlugin` 自动跳过签名不阻塞发布**（2.x 行为）。

## 四、版本发布约定（每次发版缺一不可）

1. `build.gradle.kts` 的 `version = "..."`（项目版本，联动 pluginConfiguration）
2. `src/main/resources/META-INF/plugin.xml` 的 `<change-notes>` **顶部**新增对应版本条目

> 发版前自检命令（三处一致性，防 tag 校验失败）：
> `grep -n 'version = "' build.gradle.kts && grep -n "<h3>X.Y.Z" src/main/resources/META-INF/plugin.xml`

## 五、发版操作（三条命令）

```bash
git add -A && git commit -m "feat(x): ..."
git push origin main
git tag -a vX.Y.Z -m "X.Y.Z：版本说明" && git push origin vX.Y.Z
```

CI 自动完成：tag/版本一致性校验 → publishPlugin 上传 Marketplace → 创建 GitHub Release。
上传成功后 Marketplace 进**审核队列**（几十分钟~数小时），过审自动上架。

## 六、踩坑清单（每条都实付过学费）

1. **仓库 Actions 全局开关被关**：push 无 run、workflow_dispatch 卡 queued、events 流无 PushEvent
   → Settings → Actions → General → Allow all actions（workflow state 显示 active 不代表全局开关开着）
2. **.gitignore 否定例外顺序**：`!xxx.jar` 必须写在 `*.jar` **之后**（后者赢），
   `git check-ignore -v <文件>` 验证——gradle-wrapper.jar 没入库会让 CI 的 ./gradlew 同秒失败
3. **tag 触发的 workflow 用 tag 指向 commit 上的 yml**：修 CI 后必须删 tag 重打到修复 commit，
   光推 main 没用
4. **发版漏改版本号**：change-notes 补条目重试时把版本号同步漏掉，CI 防呆拦截
   （`tag vX 与 build.gradle.kts 版本 Y 不一致`）。连锁补救：修版本 commit → 删 tag（本地+远程）
   → 重打到新 commit → push 触发新 run
5. **Marketplace 拒绝同版本号重复上传**（默认无覆盖开关）：删 tag 重打前先确认旧 run 是否已
   成功上传（run conclusion=success = 已上传）；被拒需到 Marketplace 后台删除该版本再重传
6. **tag push 偶发 `remote rejected (failed)`**：瞬时故障，原命令重试一次即成功
7. **JSON 数据里的 `\/` 转义**：python json.load 自动解转义会掩盖原始字节，插件侧手写解析时
   记得 `.replace("\\/", "/")`（否则拼 URL 拿 404）
8. **settings.gradle.kts 缺 `pluginManagement.repositories`**（本插件首次 CI 实踩）：IPG 2.x 的
   buildscript 传递依赖（org.jetbrains:annotations:13.0、undertow-core、structure-*:3.300、
   plugin-repository-rest-client 等）不在 gradlePluginPortal，只在 Maven Central / JetBrains 仓库。
   本地构建能过（GRADLE_USER_HOME 的 init 脚本/缓存兜底）但 CI 裸环境必挂，症状 =
   `Could not find org.jetbrains:annotations:13.0 ... Searched in: plugins.gradle.org/m2`。
   修法：settings.gradle.kts 补 `pluginManagement { repositories { mavenCentral(); gradlePluginPortal(); JetBrains 仓库 } }`
   + `dependencyResolutionManagement { repositories { ... } }`（mavenCentral 放 Portal 之前）。
   教训：新项目接 CI 前先在干净环境（`GRADLE_USER_HOME=$(mktemp -d)`）跑一遍 `./gradlew buildPlugin`
   再推，本地绿 ≠ CI 绿。
9. **修完 settings 后第二轮照挂——setup-gradle 负面缓存复用**（紧接上条的第二刀）：Gradle 会把
   静态版本"查不到"的结果缓存进 `modules-2`，`gradle/actions/setup-gradle@v4` 又会在 run 之间恢复它。
   症状：加了 mavenCentral 后新 run 依然 `Could not find`，且日志里 4 个仓库的"Searched in"
   时间戳间隔仅 3ms——**根本没有发起网络请求**。修法：CI 构建与发布命令都加
   `--refresh-dependencies`（一次性成本，之后缓存里是正面结果）。排查手法：直接看 CI 日志时间戳
   判断是真下载还是缓存命中。

## 七、验证与排查

- 发布成功 ≠ 立即可见：Marketplace 进审核队列（几十分钟~数小时）
- run/日志排查用 GitHub API（token 走 `git credential fill`）；jobs 日志 blob 会过期，
  run conclusion=success 即发布成功
- 多 IDE 兼容矩阵：Marketplace 后台 Compatibility verification → Schedule Verification
  云端跑（覆盖 WebStorm/PyCharm/GoLand/PhpStorm/Android Studio 等，每项约 10-15 分钟）
- 本地验证：`./gradlew verifyPlugin`（需配 pluginVerification.ides，否则报"无 IDE"）
