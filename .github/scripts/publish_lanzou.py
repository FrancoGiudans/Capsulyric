#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Upload a release APK to LanZouCloud for AppShare.

The upstream ``lanzou-api`` package talks to LanZouCloud's web endpoints.  This
wrapper deliberately keeps the package pinned, restores TLS certificate
verification, never prints credentials, and exposes a small CI-friendly
interface for upload and post-publish retention cleanup.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from typing import Any, Callable, Dict, Iterable, Optional, Sequence, Tuple


MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024
DEFAULT_PARENT_FOLDER_ID = -1
RELEASE_FOLDER_NAME = "Capsulyric-Releases"
EXPERIMENT_FOLDER_NAME = "Capsulyric-Experiment"
OUTPUT_KEYS = ("share_url", "share_password", "appshare_link", "file_id", "folder_id")


def emit_warning(message: str) -> None:
    print(f"::warning::{message}")


def emit_error(message: str) -> None:
    print(f"::error::{message}")


def write_outputs(values: Dict[str, str], output_path: Optional[str] = None) -> None:
    """Write CI outputs without echoing their values to the job log."""
    path = output_path or os.environ.get("GITHUB_OUTPUT")
    if not path:
        return
    with open(path, "a", encoding="utf-8", newline="\n") as output:
        for key in OUTPUT_KEYS:
            value = values.get(key, "").replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
            output.write(f"{key}={value}\n")


def empty_outputs(output_path: Optional[str] = None) -> None:
    write_outputs({key: "" for key in OUTPUT_KEYS}, output_path)


def sanitize_version(version_name: str) -> str:
    value = re.sub(r"[^0-9A-Za-z._-]+", "-", version_name.strip())
    value = value.strip(".-_")
    return (value or "unknown")[:80]


def build_file_name(version_name: str) -> str:
    return f"Capsulyric-{sanitize_version(version_name)}.apk"


def folder_name_for_channel(channel: str) -> str:
    return RELEASE_FOLDER_NAME if channel.strip().lower() in {"stable", "release", "preview"} else EXPERIMENT_FOLDER_NAME


def default_keep_for_channel(channel: str) -> int:
    return 15 if folder_name_for_channel(channel) == RELEASE_FOLDER_NAME else 1


def build_appshare_link(share_url: str, share_password: str) -> str:
    share_url = share_url.strip()
    share_password = share_password.strip()
    if not share_password:
        return share_url
    return f"{share_url} 提取码: {share_password}"


def _client_success(client: Any, code: Any) -> bool:
    return code == getattr(client, "SUCCESS", 0)


def _file_id(item: Any) -> int:
    return int(getattr(item, "id", getattr(item, "file_id", -1)))


def _file_name(item: Any) -> str:
    return str(getattr(item, "name", ""))


def _find_folder(client: Any, parent_id: int, name: str) -> Optional[int]:
    folders = client.get_dir_list(parent_id)
    folder = folders.find_by_name(name) if hasattr(folders, "find_by_name") else next(
        (item for item in folders if str(getattr(item, "name", "")) == name), None
    )
    if folder is None:
        return None
    return int(getattr(folder, "id"))


def _get_or_create_folder(client: Any, parent_id: int, name: str) -> int:
    existing = _find_folder(client, parent_id, name)
    if existing is not None:
        return existing
    folder_id = int(client.mkdir(parent_id, name, "Capsulyric AppShare releases"))
    if folder_id <= 0:
        raise RuntimeError(f"创建蓝奏云目录失败: {name}")
    return folder_id


def _secure_client_class() -> Any:
    """Load the pinned dependency and override its verify=False request paths."""
    try:
        import requests
        from lanzou.api import LanZouCloud
    except ImportError as error:  # pragma: no cover - exercised by CI setup, not unit tests
        raise RuntimeError("缺少 lanzou-api==2.6.10，请先安装 .github/scripts/requirements-lanzou.txt") from error

    class SecureLanZouCloud(LanZouCloud):
        def _get(self, url: str, **kwargs: Any) -> Any:
            for possible_url in dict.fromkeys(self._all_possible_urls(url)):
                try:
                    request_kwargs = dict(kwargs)
                    request_kwargs.setdefault("timeout", self._timeout)
                    request_kwargs.setdefault("headers", self._headers)
                    request_kwargs["verify"] = True
                    return self._session.get(possible_url, **request_kwargs)
                except requests.RequestException:
                    continue
            return None

        def _post(self, url: str, data: Any = None, **kwargs: Any) -> Any:
            for possible_url in dict.fromkeys(self._all_possible_urls(url)):
                try:
                    request_kwargs = dict(kwargs)
                    request_kwargs.setdefault("timeout", self._timeout)
                    request_kwargs.setdefault("headers", self._headers)
                    request_kwargs["verify"] = True
                    return self._session.post(possible_url, data=data, **request_kwargs)
                except requests.RequestException:
                    continue
            return None

    return SecureLanZouCloud


def create_client() -> Any:
    return _secure_client_class()()


def _credentials_from_environment(environment: Optional[Dict[str, str]] = None) -> Tuple[str, str]:
    env = environment or os.environ
    return env.get("LANZOU_YLOGIN", "").strip(), env.get("LANZOU_PHPDISK_INFO", "").strip()


def login_client(client: Any, ylogin: str, phpdisk_info: str) -> None:
    if not ylogin or not phpdisk_info:
        raise ValueError("未配置 LANZOU_YLOGIN 或 LANZOU_PHPDISK_INFO")
    code = client.login_by_cookie({"ylogin": ylogin, "phpdisk_info": phpdisk_info})
    if not _client_success(client, code):
        raise RuntimeError("蓝奏云 Cookie 登录失败或已过期")


def upload_and_share(
    client: Any,
    apk_path: Path,
    version_name: str,
    channel: str,
    parent_folder_id: int,
) -> Dict[str, str]:
    if not apk_path.is_file():
        raise FileNotFoundError(f"APK 文件不存在: {apk_path}")
    byte_size = apk_path.stat().st_size
    if byte_size >= MAX_FILE_SIZE_BYTES:
        raise ValueError("APK 达到或超过蓝奏云默认 100 MiB 限制，已禁止绕过限制上传")

    folder_id = _get_or_create_folder(client, parent_folder_id, folder_name_for_channel(channel))
    file_name = build_file_name(version_name)
    upload_path = apk_path
    if apk_path.name != file_name:
        # The library uses the local basename as the remote name.  Keep the
        # build output untouched and create a temporary same-directory copy.
        upload_path = apk_path.with_name(file_name)
        upload_path.write_bytes(apk_path.read_bytes())

    uploaded_ids: list[int] = []

    def uploaded_handler(file_id: int, is_file: bool) -> None:
        if is_file:
            uploaded_ids.append(int(file_id))

    try:
        code = client.upload_file(str(upload_path), folder_id, uploaded_handler=uploaded_handler)
    finally:
        if upload_path != apk_path:
            upload_path.unlink(missing_ok=True)
    if not _client_success(client, code):
        raise RuntimeError(f"蓝奏云上传失败，错误码: {code}")

    if not uploaded_ids:
        files = client.get_file_list(folder_id)
        matching = [item for item in files if _file_name(item) == file_name]
        if matching:
            uploaded_ids.append(max((_file_id(item) for item in matching)))
    if not uploaded_ids:
        raise RuntimeError("蓝奏云上传成功但未取得文件 ID")

    file_id = uploaded_ids[-1]
    share_info = client.get_share_info(file_id, is_file=True)
    if not _client_success(client, getattr(share_info, "code", None)):
        raise RuntimeError("蓝奏云分享链接获取失败")
    share_url = str(getattr(share_info, "url", "")).strip()
    share_password = str(getattr(share_info, "pwd", "") or "").strip()
    if not share_url:
        raise RuntimeError("蓝奏云返回了空分享链接")

    detail = client.get_file_info_by_url(share_url, share_password)
    if not _client_success(client, getattr(detail, "code", None)):
        raise RuntimeError("蓝奏云分享链接验证失败")
    detail_name = str(getattr(detail, "name", "") or "")
    if detail_name and not detail_name.lower().endswith(".apk"):
        raise RuntimeError("蓝奏云分享链接验证到的文件不是 APK")

    return {
        "share_url": share_url,
        "share_password": share_password,
        "appshare_link": build_appshare_link(share_url, share_password),
        "file_id": str(file_id),
        "folder_id": str(folder_id),
    }


def prune_history(client: Any, channel: str, parent_folder_id: int, keep: int) -> int:
    if keep < 1:
        raise ValueError("--keep 必须大于 0")
    folder_id = _find_folder(client, parent_folder_id, folder_name_for_channel(channel))
    if folder_id is None:
        return 0
    files = client.get_file_list(folder_id)
    candidates = [
        item
        for item in files
        if _file_name(item).startswith("Capsulyric-") and _file_name(item).lower().endswith(".apk")
    ]
    candidates.sort(key=_file_id, reverse=True)
    removed = 0
    for item in candidates[keep:]:
        file_id = _file_id(item)
        delete_code = client.delete(file_id, is_file=True)
        if not _client_success(client, delete_code):
            raise RuntimeError(f"清理蓝奏云历史文件失败，文件 ID: {file_id}")
        recycle_code = client.delete_rec(file_id, is_file=True)
        if not _client_success(client, recycle_code):
            raise RuntimeError(f"永久删除蓝奏云历史文件失败，文件 ID: {file_id}")
        removed += 1
    return removed


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="将 APK 上传到蓝奏云并为 AppShare 生成三方网盘链接")
    parser.add_argument("--apk", help="待上传的 APK 文件路径")
    parser.add_argument("--channel", default="Stable", help="Stable / Preview / Experiment / Canary")
    parser.add_argument("--version-name", required=True, help="版本名称，用于生成远端文件名")
    parser.add_argument("--keep", type=int, help="清理模式保留的文件数量")
    parser.add_argument("--parent-folder-id", type=int, default=DEFAULT_PARENT_FOLDER_ID, help="蓝奏云父目录 ID，默认根目录")
    parser.add_argument("--prune-only", action="store_true", help="只清理专用目录，不上传文件")
    parser.add_argument("--dry-run", action="store_true", help="只校验本地参数，不执行网络操作")
    return parser.parse_args(argv)


def main(
    argv: Optional[Sequence[str]] = None,
    environment: Optional[Dict[str, str]] = None,
    output_path: Optional[str] = None,
    client_factory: Callable[[], Any] = create_client,
) -> int:
    args = parse_args(argv)
    empty_outputs(output_path)
    apk_path = Path(args.apk) if args.apk else None

    if not args.prune_only and apk_path is None:
        emit_error("上传模式必须提供 --apk")
        return 1
    if apk_path is not None and not apk_path.is_file():
        emit_error(f"APK 文件不存在: {apk_path}")
        return 1
    if apk_path is not None and apk_path.stat().st_size >= MAX_FILE_SIZE_BYTES:
        emit_warning("APK 达到或超过 100 MiB，跳过蓝奏云与 AppShare 发布，不启用任何绕过限制")
        return 0
    if args.dry_run:
        if not args.prune_only:
            print(f"蓝奏云演练：目录={folder_name_for_channel(args.channel)}, 文件名={build_file_name(args.version_name)}")
        else:
            print(f"蓝奏云清理演练：目录={folder_name_for_channel(args.channel)}, 保留={args.keep or default_keep_for_channel(args.channel)}")
        return 0

    ylogin, phpdisk_info = _credentials_from_environment(environment)
    if not ylogin or not phpdisk_info:
        emit_warning("未配置 LANZOU_YLOGIN 或 LANZOU_PHPDISK_INFO，跳过蓝奏云与 AppShare 发布")
        return 0

    client = None
    try:
        client = client_factory()
        login_client(client, ylogin, phpdisk_info)
        if args.prune_only:
            keep = args.keep or default_keep_for_channel(args.channel)
            removed = prune_history(client, args.channel, args.parent_folder_id, keep)
            print(f"蓝奏云历史清理完成，删除 {removed} 个文件。")
            return 0
        result = upload_and_share(client, apk_path, args.version_name, args.channel, args.parent_folder_id)
        write_outputs(result, output_path)
        print("蓝奏云上传与分享链接验证成功。")
        return 0
    except Exception as error:
        emit_error(f"蓝奏云步骤失败: {error}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
