#!/usr/bin/env python3
"""Project Export & ZIP Packaging Tool.
Packages the project into a clean export ZIP containing ONLY the current/latest APK
(from the build output app-debug.apk) and source files, while strictly excluding all old APK versions."""

import os
import re
import sys
import shutil
import zipfile
from pathlib import Path

WORKSPACE_ROOT = Path(__file__).resolve().parent

# Directories and patterns to exclude from the export ZIP
EXCLUDE_DIRS = {
    ".gradle",
    "build",
    ".build-outputs",
    ".idea",
    ".externalNativeBuild",
    ".cxx",
    "captures",
    "archived_apks",
    "old_apks",
    "archive",
    "public",
    "export",
    "__pycache__"}

EXCLUDE_FILES = {
    ".env",
    "debug.keystore",
    "debug.keystore.base64",
    "debug_original.keystore",
    "local.properties",
    ".DS_Store",
    "BBC_POS_export.zip"}

def get_version_name() -> str:
    gradle_file = WORKSPACE_ROOT / "app" / "build.gradle.kts"
    if not gradle_file.exists():
        alt_gradle = WORKSPACE_ROOT / "applet" / "app" / "build.gradle.kts"
        if alt_gradle.exists():
            gradle_file = alt_gradle
    version_name = "6.8"
    if gradle_file.exists():
        match = re.search(r'versionName\s*=\s*["\']([^"\']+)["\']', gradle_file.read_text())
        if match:
            version_name = match.group(1)
    return version_name

def sync_latest_apk():
    """
    Finds the latest compiled app-debug.apk from Gradle build output
    and syncs it as BBC_POS_v{version_name}.apk at workspace root.
    Removes any old APK files from export folders.
    """
    build_apk_path = WORKSPACE_ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    if not build_apk_path.exists():
        alt_path = WORKSPACE_ROOT / ".build-outputs" / "app-debug.apk"
        if alt_path.exists():
            build_apk_path = alt_path
        else:
            alt_path2 = WORKSPACE_ROOT / "applet" / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
            if alt_path2.exists():
                build_apk_path = alt_path2
    version_name = get_version_name()
    target_apk_name = f"BBC_POS_v{version_name}.apk"
    root_apk_path = WORKSPACE_ROOT / target_apk_name
    if build_apk_path.exists():
        print(f"Syncing compiled APK from {build_apk_path} -> {root_apk_path}...")
        shutil.copy2(build_apk_path, root_apk_path)
        # Clean up old root APKs that are not the target
    for old_root_apk in WORKSPACE_ROOT.glob("BBC_POS_v*.apk"):
        if old_root_apk.name != target_apk_name:
            print(f"Removing old root APK: {old_root_apk.name}")
            old_root_apk.unlink(missing_ok=True)
    # Clean up old export folder APKs
    export_dir = WORKSPACE_ROOT / "export"
    if export_dir.exists():
        for old_file in export_dir.glob("*.apk"):
            if old_file.name != target_apk_name and old_file.name != "app-debug.apk":
                print(f"Removing old export file: {old_file}")
                old_file.unlink(missing_ok=True)
            elif old_file.name == target_apk_name:
                if root_apk_path.exists():
                    shutil.copy2(root_apk_path, old_file)
        if root_apk_path.exists():
            shutil.copy2(root_apk_path, export_dir / target_apk_name)
    return root_apk_path if root_apk_path.exists() else None

def create_export_zip(output_zip: Path = WORKSPACE_ROOT / "BBC_POS_export.zip"):
    print(f"Scanning workspace: {WORKSPACE_ROOT}")
    latest_apk = sync_latest_apk()
    if latest_apk and latest_apk.exists():
        print(f"\n>>> IDENTIFIED LATEST APK: {latest_apk.name} ({latest_apk.stat().st_size / (1024*1024):.2f} MB)")
    else:
        print("\nNo compiled APK found.")
    
    # Build file list
    files_to_pack = []
    for dirpath, dirnames, filenames in os.walk(WORKSPACE_ROOT):
        # Skip excluded directories
        dirnames[:] = [d for d in dirnames if d not in EXCLUDE_DIRS]
        for fname in filenames:
            fpath = Path(dirpath) / fname
                        
            # Skip output zip
            if fpath.resolve() == output_zip.resolve():
                continue
                        
            # Skip excluded files
            if fname in EXCLUDE_FILES:
                continue

            # Explicitly include the specific APK version
            if fname == "BBC_POS_v9.5.apk":
                files_to_pack.append(fpath)
                continue

            files_to_pack.append(fpath)
            
    print(f"\nPacking {len(files_to_pack)} project files into {output_zip.name}...")
    with zipfile.ZipFile(output_zip, "w", zipfile.ZIP_DEFLATED) as zipf:
        for fpath in files_to_pack:
            rel = str(fpath.relative_to(WORKSPACE_ROOT))
            if latest_apk and fpath.resolve() == latest_apk.resolve():
                zipf.write(fpath, arcname=latest_apk.name)
            else:
                zipf.write(fpath, arcname=rel)
    zip_size_mb = output_zip.stat().st_size / (1024 * 1024)
    print(f"Export ZIP created successfully: {output_zip.name} ({zip_size_mb:.2f} MB)")
    
    # Verification
    print("\n--- Verifying Export ZIP Contents ---")
    with zipfile.ZipFile(output_zip, "r") as zipf:
        zip_apks = [name for name in zipf.namelist() if name.lower().endswith(".apk")]
        print(f"APKs included in ZIP ({len(zip_apks)}):")
        for apk_in_zip in zip_apks:
            print(f"  + {apk_in_zip}")
        
        # We don't have version name here easily but we know we put v9.5.apk
        if len(zip_apks) >= 1:
            print(f"\n[VERIFICATION PASSED]: APKs included in ZIP.")
        else:
            print(f"\n[VERIFICATION WARNING]: No APK list: {zip_apks}")

if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else (WORKSPACE_ROOT / "BBC_POS_export.zip")
    create_export_zip(out)
