# Neon B11.8

本说明汇总 `B11.7` 之后至 `B11.8` 的全部提交。

## 中文

- **模组更新中心的镜像地址改为运行时解析**（支持镜像服务器迁移）：
  - 版本列表、发布备份与镜像文件下载请求不再使用写死的服务器地址，改为在每次请求时通过 `play.mindustry.men` 域名动态解析出服务器 IPv4 后直连；
  - 镜像服务器迁移后无需修改模组或重新发布，客户端重启即自动跟随新地址；
  - 解析失败时自动回退为域名直连，不影响正常使用；
  - 镜像下载相关的界面文案同步更新。

## English

This release summarizes every commit after `B11.7` through `B11.8`.

- **Mod updater mirror host is now resolved at runtime** (mirror server migration support):
  - version-list, release-backup and mirror download requests no longer use a hard-coded server address; each request resolves the current IPv4 of `play.mindustry.men` at runtime and connects to it directly;
  - after the mirror server migrates, no mod update or republish is required — clients pick up the new address automatically on the next request;
  - on resolution failure the request falls back to the domain name, so normal use is unaffected;
  - mirror-related UI strings updated accordingly.
