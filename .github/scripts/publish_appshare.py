#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AppShare OAuth 自动化发版脚本 (API v5.1.5+)
支持自动计算 MD5 签名、申请预上传地址、秒传判断、二进制 PUT 直传、版本发布及超时结果兜底查询。
"""

import argparse
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Dict, Optional

# 确保在各操作系统控制台输出中文时不因默认编码报错
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

DEFAULT_APP_ID = "AC0100AE9F074DEE9A71C8"
BASE_URL = "https://app.sharess.cn"


def mask_secret(secret: str) -> str:
    if not secret:
        return "<EMPTY>"
    if len(secret) <= 6:
        return "***"
    return f"{secret[:3]}***{secret[-3:]}"


def compute_file_md5(file_path: str) -> str:
    """计算文件的 32 位小写十六进制 MD5"""
    hasher = hashlib.md5()
    with open(file_path, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest().lower()


def md5_sign(payload: str) -> str:
    """按 UTF-8 计算字符串的 32 位小写 MD5 签名"""
    return hashlib.md5(payload.encode("utf-8")).hexdigest().lower()


def http_post(url: str, params: Dict[str, Any], timeout: int = 30) -> Dict[str, Any]:
    """发送 application/x-www-form-urlencoded POST 请求并解析 JSON 响应"""
    encoded_data = urllib.parse.urlencode(params).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=encoded_data,
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "User-Agent": "IslandLyrics-CI-Publisher/1.0"
        },
        method="POST"
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        resp_bytes = resp.read()
        return json.loads(resp_bytes.decode("utf-8"))


def http_get(url: str, params: Dict[str, Any], timeout: int = 30) -> Dict[str, Any]:
    """发送 GET 请求并解析 JSON 响应"""
    query_string = urllib.parse.urlencode(params)
    full_url = f"{url}?{query_string}" if query_string else url
    req = urllib.request.Request(
        full_url,
        headers={
            "User-Agent": "IslandLyrics-CI-Publisher/1.0"
        },
        method="GET"
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        resp_bytes = resp.read()
        return json.loads(resp_bytes.decode("utf-8"))


def upload_binary_put(presign_url: str, file_path: str, byte_size: int, timeout: int = 300) -> None:
    """将 APK 二进制文件通过 PUT 上传至预签名地址"""
    with open(file_path, "rb") as f:
        data = f.read()

    req = urllib.request.Request(
        presign_url,
        data=data,
        headers={
            "Content-Type": "application/octet-stream",
            "Content-Length": str(byte_size),
        },
        method="PUT"
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        if resp.status not in (200, 204):
            raise RuntimeError(f"PUT 上传失败，HTTP 状态码: {resp.status}")


def query_publish_result(base_url: str, app_id: str, record_id: int, app_key: str, max_retries: int = 3) -> Optional[Dict[str, Any]]:
    """发布超时后兜底查询发版日志与最终结果"""
    print(f"[AppShare] 正在按 recordId={record_id} 查询最终发版结果...")
    sign = md5_sign(f"{app_id}{record_id}{app_key}")
    params = {
        "appId": app_id,
        "recordId": str(record_id),
        "sign": sign,
    }
    url = f"{base_url}/oauth/v1/getPublishResult"

    for attempt in range(1, max_retries + 1):
        try:
            res = http_post(url, params, timeout=15)
            code = res.get("code")
            msg = res.get("message")
            data = res.get("data") or {}
            print(f"[AppShare] 查询响应 (第 {attempt} 次): code={code}, message={msg}, data={data}")
            if code == 100:
                result = data.get("result")
                # result: 1=成功, 0=失败
                if result == 1 or data.get("versionId", 0) > 0:
                    return data
            time.sleep(3)
        except Exception as e:
            print(f"[AppShare] 查询异常 (第 {attempt} 次): {e}")
            time.sleep(3)
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="AppShare OAuth CI 发布工具")
    parser.add_argument("--apk", required=True, help="待发布的 APK 文件路径")
    parser.add_argument("--name", type=str, help="发布到 AppShare 的文件名（默认采用 APK 文件的 basename）")
    parser.add_argument("--app-id", default=os.getenv("APPSHARE_APP_ID", DEFAULT_APP_ID), help="AppShare 分配的 appId")
    parser.add_argument("--app-key", default=os.getenv("appKey") or os.getenv("APPSHARE_APP_KEY") or os.getenv("APPKEY"), help="AppShare 分配的 appKey")
    parser.add_argument("--version-code", type=int, required=True, help="版本号 versionCode（必须与 APK 包内一致）")
    parser.add_argument("--version-name", type=str, required=True, help="版本名 versionName（必须与 APK 包内一致）")
    parser.add_argument("--channel", type=str, default="Stable", help="构建渠道: Stable / Preview / Experiment / Canary")
    parser.add_argument("--type", type=int, choices=[1, 2, 3], help="版本类型: 1=正式 / 2=测试 / 3=谷歌。不传时根据 channel 自动推断")
    parser.add_argument("--changelog-file", type=str, help="更新日志文本文件路径")
    parser.add_argument("--changelog", type=str, help="更新日志直接内容（优先级低于 changelog-file）")
    parser.add_argument("--link", type=str, required=True, help="三方网盘或发布直链 (如 GitHub/Gitee Release 页面)")
    parser.add_argument("--source", type=str, help="来源说明 (type=2 测试版时必填，长度 <= 512)")
    parser.add_argument("--dry-run", action="store_true", help="演练模式：仅打印参数与签名，不发起实际网络请求")
    parser.add_argument("--check-update", action="store_true", help="发版后自动调用 checkUpdate 校验最新版本")

    args = parser.parse_args()

    # 1. 基础校验
    if not os.path.exists(args.apk):
        print(f"::error::APK 文件不存在: {args.apk}")
        return 1

    app_key = (args.app_key or "").strip()
    if not app_key:
        print("::warning::未检测到 appKey Secret，跳过 AppShare 发布。")
        return 0

    app_id = (args.app_id or "").strip()
    if not app_id:
        print("::error::appId 不能为空。")
        return 1

    # 2. 版本类型 (type) 推断
    if args.type is not None:
        version_type = args.type
    else:
        ch = args.channel.strip().lower()
        if ch in ("stable", "release"):
            version_type = 1  # 正式版
        else:
            version_type = 2  # Preview / Experiment / Canary 均为测试版

    # 3. 来源 (source) 处理 (当 type=2 时文档要求必填)
    source = args.source
    if not source:
        if version_type == 2:
            source = f"GitHub Actions ({args.channel})"
        else:
            source = ""
    if len(source) > 512:
        source = source[:512]

    # 4. 更新日志提取与文本保全
    update_log = ""
    if args.changelog_file and os.path.exists(args.changelog_file):
        with open(args.changelog_file, "r", encoding="utf-8") as f:
            update_log = f.read().strip()
    elif args.changelog:
        update_log = args.changelog.strip()

    if not update_log:
        update_log = f"Capsulyric {args.version_name} ({args.channel})"
    else:
        # 移除 HTML 标签（如 <img ...>），避免在不支持 HTML 渲染的平台裸露标签
        import re
        update_log = re.sub(r"<[^>]+>", "", update_log).strip()
        if len(update_log) > 4000:
            update_log = update_log[:3990] + "\n..."

    # 5. 文件信息提取
    apk_name = args.name.strip() if args.name else os.path.basename(args.apk)
    byte_size = os.path.getsize(args.apk)
    file_sign = compute_file_md5(args.apk)

    print("==================================================")
    print("🚀 AppShare OAuth 自动化发版任务启动")
    print("==================================================")
    print(f"AppId:        {app_id}")
    print(f"AppKey:       {mask_secret(app_key)}")
    print(f"APK 名称:     {apk_name}")
    print(f"APK 大小:     {byte_size} 字节 ({byte_size / (1024 * 1024):.2f} MB)")
    print(f"APK MD5:      {file_sign}")
    print(f"VersionCode:  {args.version_code}")
    print(f"VersionName:  {args.version_name}")
    print(f"版本类型:     {version_type} ({'正式版' if version_type == 1 else '测试版'})")
    print(f"第三方链接:   {args.link}")
    if version_type == 2:
        print(f"来源说明:     {source}")
    print("==================================================")

    if args.dry_run:
        print("演练模式 (dry-run) 开启，以下为生成的签名公式结果：")
        sign1 = md5_sign(f"{app_id}{apk_name}{file_sign}{app_key}")
        print(f"1) generateUploadUrl sign: {sign1}")
        sign3 = md5_sign(f"{app_id}RECORD_ID_PLACEHOLDER{args.version_code}{args.version_name}{version_type}{app_key}")
        print(f"2) publishVersion sign:   {sign3}")
        print("演练完成，退出。")
        return 0

    # -------------------------------------------------------------
    # 步骤 1: 申请预上传地址 (generateUploadUrl)
    # 签名: md5(appId + name + fileSign + appKey)
    # -------------------------------------------------------------
    print("\n[步骤 1/3] 申请官方安装包预上传地址 (generateUploadUrl)...")
    step1_sign = md5_sign(f"{app_id}{apk_name}{file_sign}{app_key}")
    step1_params = {
        "appId": app_id,
        "name": apk_name,
        "fileSign": file_sign,
        "byteSize": str(byte_size),
        "sign": step1_sign,
    }

    try:
        step1_res = http_post(f"{BASE_URL}/oauth/v1/generateUploadUrl", step1_params, timeout=30)
    except Exception as e:
        print(f"::error::申请上传地址网络请求失败: {e}")
        return 1

    code = step1_res.get("code")
    message = step1_res.get("message")
    data = step1_res.get("data") or {}

    if code != 100:
        print(f"::error::申请预上传地址失败: [{code}] {message}")
        return 1

    record_id = data.get("recordId")
    presign_url = data.get("presignUrl")
    reuse = bool(data.get("reuse", False))

    print(f"✓ 预上传申请成功: recordId={record_id}, reuse={reuse}")

    # -------------------------------------------------------------
    # 步骤 2: 上传安装包 (PUT) - 若 reuse=true 则跳过
    # -------------------------------------------------------------
    if reuse:
        print("\n[步骤 2/3] 平台已存在相同文件，触发秒传 (reuse=true)，跳过二进制上传。")
    else:
        if not presign_url:
            print("::error::服务端返回 reuse=false 但未提供 presignUrl。")
            return 1

        print(f"\n[步骤 2/3] 正在向预签名地址流式上传 APK 二进制流 ({byte_size / (1024 * 1024):.2f} MB)...")
        try:
            upload_binary_put(presign_url, args.apk, byte_size, timeout=600)
            print("✓ 二进制安装包上传成功。")
        except Exception as e:
            print(f"::error::PUT 上传安装包失败: {e}")
            return 1

    # -------------------------------------------------------------
    # 步骤 3: 发布版本 (publishVersion)
    # 签名: md5(appId + recordId + versionCode + versionName + type + appKey)
    # 注意: link, updateLog, source 等不参与签名
    # -------------------------------------------------------------
    print("\n[步骤 3/3] 提交版本发布 (publishVersion)...")
    step3_sign = md5_sign(f"{app_id}{record_id}{args.version_code}{args.version_name}{version_type}{app_key}")
    step3_params = {
        "appId": app_id,
        "recordId": str(record_id),
        "versionCode": str(args.version_code),
        "versionName": str(args.version_name),
        "type": str(version_type),
        "updateLog": update_log,
        "link": args.link,
        "sign": step3_sign,
    }
    if version_type == 2 and source:
        step3_params["source"] = source

    step3_res = None
    try:
        step3_res = http_post(f"{BASE_URL}/oauth/v1/publishVersion", step3_params, timeout=60)
    except urllib.error.URLError as e:
        print(f"[AppShare] publishVersion 网络超时或中断 ({e})，进入兜底状态查询...")
        publish_data = query_publish_result(BASE_URL, app_id, record_id, app_key)
        if publish_data:
            print(f"🎉 版本发布成功 (通过结果确认): versionId={publish_data.get('versionId')}")
            return 0
        else:
            print(f"::error::版本发布超时且未查询到成功状态: {e}")
            return 1
    except Exception as e:
        print(f"::error::publishVersion 调用异常: {e}")
        return 1

    code = step3_res.get("code")
    message = step3_res.get("message")
    data = step3_res.get("data") or {}

    if code != 100:
        print(f"::error::发布版本失败: [{code}] {message}")
        return 1

    version_id = data.get("versionId")
    app_main_id = data.get("appMainId")
    cert_sha256 = data.get("certSha256")

    print("==================================================")
    print("🎉 AppShare 版本发布成功！")
    print(f"Version ID:    {version_id}")
    print(f"App Main ID:   {app_main_id}")
    print(f"Cert SHA-256:  {cert_sha256}")
    print("==================================================")

    # -------------------------------------------------------------
    # 步骤 4: 可选校验更新 (checkUpdate)
    # -------------------------------------------------------------
    if args.check_update:
        print("\n[可选检查] 调用 checkUpdate 检查当前线上最新版本...")
        try:
            chk_sign = md5_sign(f"{app_id}{args.version_code}{version_type}{app_key}")
            chk_params = {
                "appId": app_id,
                "versionCode": str(args.version_code),
                "type": str(version_type),
                "sign": chk_sign,
            }
            chk_res = http_get(f"{BASE_URL}/oauth/v1/checkUpdate", chk_params, timeout=15)
            if chk_res.get("code") == 100:
                chk_data = chk_res.get("data") or {}
                print(f"最新线上版本: {chk_data.get('latestVersionName')} (code: {chk_data.get('latestVersionCode')})")
            else:
                print(f"checkUpdate 提示: {chk_res.get('message')}")
        except Exception as e:
            print(f"checkUpdate 检查跳过: {e}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
