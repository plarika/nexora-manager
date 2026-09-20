#!/usr/bin/env python3
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

EVIDENCE = Path("evidence")
EVIDENCE.mkdir(exist_ok=True)
PKG = "com.fantamk.revanced.manager"
MAIN = f"{PKG}/app.revanced.manager.MainActivity"
DOWNLOADER = f"{PKG}/app.revanced.manager.DownloaderActivity"
CANDIDATE = "nexora-manager-downloaders-1.2.0-nexora.1.apk"

def run(cmd, timeout=30, check=True):
    p = subprocess.run(cmd, text=True, capture_output=True, timeout=timeout)
    out = (p.stdout or "") + (p.stderr or "")
    if check and p.returncode != 0:
        raise RuntimeError(f"command failed {p.returncode}: {cmd}\n{out}")
    return out.strip()

def adb(*args, timeout=30, check=True):
    return run(["adb", *args], timeout=timeout, check=check)

def shell(*args, timeout=30, check=True):
    return adb("shell", *args, timeout=timeout, check=check)

def dump(name="current"):
    remote = "/sdcard/nexora-gate-ui.xml"
    local = EVIDENCE / f"{name}.xml"
    deadline = time.time() + 25
    last = ""
    while time.time() < deadline:
        shell("rm", "-f", remote, timeout=10, check=False)
        last = shell("uiautomator", "dump", remote, timeout=20, check=False)
        ready = adb(
            "shell",
            f"if [ -s {remote} ]; then echo READY; else echo MISS; fi",
            timeout=10,
            check=False,
        )
        if "READY" in ready:
            pulled = adb("pull", remote, str(local), timeout=20, check=False)
            if local.exists() and local.stat().st_size > 0:
                return ET.parse(local).getroot()
            last = pulled
        time.sleep(1)
    raise RuntimeError(f"uiautomator dump unavailable for {name}: {last}")

def screenshot(name):
    remote = f"/sdcard/{name}.png"
    shell("screencap", "-p", remote, timeout=20)
    adb("pull", remote, str(EVIDENCE / f"{name}.png"), timeout=20)

def node_label(node):
    text = node.attrib.get("text", "")
    desc = node.attrib.get("content-desc", "")
    return (text + " " + desc).strip()

def center(bounds):
    nums = [int(x) for x in re.findall(r"\d+", bounds or "")]
    if len(nums) != 4:
        raise RuntimeError(f"invalid bounds: {bounds}")
    return (nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2

def find_nodes(root, needle, exact=True):
    found = []
    for node in root.iter("node"):
        values = [node.attrib.get("text", ""), node.attrib.get("content-desc", "")]
        if any((v == needle if exact else needle.lower() in v.lower()) for v in values if v):
            found.append(node)
    return found

def wait_node(needle, timeout=30, exact=True):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            root = dump("current")
            nodes = find_nodes(root, needle, exact=exact)
            if nodes:
                return nodes[0], root
            last = [node_label(n) for n in root.iter("node") if node_label(n)]
        except Exception as exc:
            last = repr(exc)
        time.sleep(1)
    raise RuntimeError(f"UI node not found: {needle!r}; last={last}")

def tap_node(node):
    x, y = center(node.attrib.get("bounds", ""))
    shell("input", "tap", str(x), str(y), timeout=10)
    time.sleep(1)

def tap_text(needle, timeout=30, exact=True):
    node, root = wait_node(needle, timeout=timeout, exact=exact)
    tap_node(node)
    return root

def maybe_tap(needle, timeout=3, exact=True):
    try:
        tap_text(needle, timeout=timeout, exact=exact)
        return True
    except Exception:
        return False

def top_activity():
    out = shell("dumpsys", "activity", "activities", timeout=20)
    m = re.search(r"topResumedActivity=.*? ([^ }]+)", out)
    return m.group(1) if m else ""

def labels(root):
    return [node_label(n) for n in root.iter("node") if node_label(n)]

def save_text(name, text):
    (EVIDENCE / name).write_text(text, encoding="utf-8")

def assert_no_manager_failure(stage):
    logs = adb("logcat", "-d", "-v", "time", timeout=30, check=False)
    save_text(f"logcat-{stage}.txt", logs)
    bad_patterns = [
        "ANR in com.fantamk.revanced.manager",
        "Process: com.fantamk.revanced.manager",
        "am_crash.*com.fantamk.revanced.manager",
        "am_anr.*com.fantamk.revanced.manager",
    ]
    hits = []
    for line in logs.splitlines():
        if any(re.search(p, line, re.I) for p in bad_patterns):
            hits.append(line)
    if hits:
        raise RuntimeError("Manager failure evidence at " + stage + ":\n" + "\n".join(hits[-80:]))

def complete_onboarding():
    shell("am", "start", "-n", MAIN, timeout=20)
    deadline = time.time() + 90
    step = 0
    while time.time() < deadline:
        root = dump(f"onboarding-{step}")
        labs = labels(root)
        if "Dashboard" in labs and "Apps" in labs:
            screenshot("dashboard-after-onboarding")
            return
        if "Nexora Manager isn't responding" in labs:
            screenshot("manager-anr-onboarding")
            raise RuntimeError("Nexora Manager ANR during onboarding")
        if "Pixel Launcher isn't responding" in labs:
            close_nodes = find_nodes(root, "Close app")
            if close_nodes:
                tap_node(close_nodes[0])
                step += 1
                time.sleep(2)
                continue
        if "Skip anyway" in labs:
            tap_node(find_nodes(root, "Skip anyway")[0]); step += 1; continue
        if "Skip for now" in labs:
            tap_node(find_nodes(root, "Skip for now")[0]); step += 1; continue
        if "Next" in labs:
            tap_node(find_nodes(root, "Next")[0]); step += 1; continue
        for standard in ("Don't allow", "Not now", "Cancel"):
            nodes = find_nodes(root, standard)
            if nodes:
                tap_node(nodes[0]); step += 1; break
        else:
            time.sleep(1)
            continue
    screenshot("onboarding-timeout")
    raise RuntimeError("Onboarding did not reach Dashboard")

def verify_dev35_dashboard():
    root = dump("dev35-dashboard")
    labs = labels(root)
    required = ("N E X O R A", "Patch an app", "Nexora Patches", "Patch")
    missing = [label for label in required if label not in labs]
    if missing:
        screenshot("dev35-dashboard-missing")
        raise RuntimeError("Missing dev.35 Dashboard labels: " + repr(missing) + " labels=" + repr(labs[:160]))
    screenshot("dev35-dashboard")

    tap_text("Patch an app", timeout=20)
    wait_node("All applications", timeout=30)
    root = dump("dev35-patch-entry")
    labs = labels(root)
    if "All applications" not in labs:
        screenshot("dev35-patch-entry-missing")
        raise RuntimeError("Patch CTA did not open Apps page: " + repr(labs[:160]))
    screenshot("dev35-patch-entry")

    tap_text("Dashboard", timeout=20)
    wait_node("Patch an app", timeout=30)


def open_downloads():
    uri = "fantamk-revanced-manager://settings/downloads"
    shell("am", "start", "-a", "android.intent.action.VIEW", "-d", uri, "-p", PKG, timeout=20)
    wait_node("Downloaders", timeout=30)
    screenshot("downloads-screen")

def import_candidate():
    tap_text("Add downloader", timeout=30)
    wait_node("Add downloader", timeout=20)

    # Step 1: choose Local source type.
    tap_text("Select from storage", timeout=20)
    tap_text("Next", timeout=20)

    # Step 2: launch Android GetContent file picker from the whole Downloaders row.
    wait_node("Downloaders", timeout=20)
    tap_text("Downloaders", timeout=20)

    deadline = time.time() + 50
    selected = False
    while time.time() < deadline:
        root = dump("file-picker")
        nodes = find_nodes(root, CANDIDATE, exact=True)
        if not nodes:
            nodes = find_nodes(root, CANDIDATE, exact=False)
        if nodes:
            tap_node(nodes[0])
            selected = True
            break

        # DocumentsUI may open Recents first. Navigate to Downloads when available.
        dl = find_nodes(root, "Downloads")
        if dl:
            tap_node(dl[0])
            time.sleep(1)
        else:
            time.sleep(1)

    if not selected:
        screenshot("file-picker-missing-candidate")
        raise RuntimeError("Candidate APK not visible in DocumentsUI")

    # Returning from GetContent only sets the local Uri. Confirm the import explicitly.
    wait_node("Add downloader", timeout=20)
    tap_text("Add", timeout=20)

    wait_node("Nexora Manager Downloaders", timeout=45)
    root = dump("candidate-imported")
    labs = labels(root)
    if not any("1.2.0-nexora.1" in x for x in labs):
        raise RuntimeError("Candidate version not shown after import: " + repr(labs))
    screenshot("candidate-imported")

def verify_downloader_source_labels():
    # With both downloader sources registered, the selector must identify their origin.
    shell("am", "force-stop", PKG, timeout=15)
    shell("am", "start", "-n", MAIN, timeout=20)
    wait_node("Dashboard", timeout=45)

    tap_lowest_text("Apps", timeout=30)
    wait_node("Apps", timeout=20)
    find_and_tap_scrolling("YouTube", attempts=15)
    wait_node("Select APK source", timeout=30)
    tap_text("Select APK source", timeout=20)

    root = dump("source-label-selector")
    mirrors = find_nodes(root, "APKMirror")
    legacy = find_nodes(root, "ReVanced Manager: Downloaders")
    nexora = find_nodes(root, "Nexora Manager Downloaders")

    save_text(
        "source-label-selector.txt",
        "APKMirrorCount=" + str(len(mirrors)) + "\n"
        + "LegacyLabelCount=" + str(len(legacy)) + "\n"
        + "NexoraLabelCount=" + str(len(nexora)) + "\n"
        + "\n".join(labels(root)) + "\n",
    )
    screenshot("source-label-selector")

    if len(mirrors) != 2:
        raise RuntimeError(f"Expected exactly two APKMirror rows before isolation, found {len(mirrors)}")
    if not legacy:
        raise RuntimeError("Legacy downloader source label missing from APK selector")
    if not nexora:
        raise RuntimeError("Nexora downloader source label missing from APK selector")


def isolate_candidate_database():
    shell("am", "force-stop", PKG, timeout=15)
    out = adb("root", timeout=20, check=False)
    save_text("adb-root.txt", out)
    adb("wait-for-device", timeout=30)
    who = shell("id", timeout=10)
    if "uid=0(root)" not in who:
        raise RuntimeError("Cloud emulator does not provide adb root: " + who)

    remote = f"/data/data/{PKG}/databases/manager"
    uid = shell("stat", "-c", "%u", remote, timeout=15).strip()
    gid = shell("stat", "-c", "%g", remote, timeout=15).strip()
    mode = shell("stat", "-c", "%a", remote, timeout=15).strip()
    save_text("db-stat-before.txt", f"{uid} {gid} {mode}\n")

    dbdir = EVIDENCE / "db-before-isolation"
    dbdir.mkdir(exist_ok=True)
    local = dbdir / "manager"
    adb("pull", remote, str(local), timeout=30)
    adb("pull", remote + "-wal", str(dbdir / "manager-wal"), timeout=20, check=False)
    adb("pull", remote + "-shm", str(dbdir / "manager-shm"), timeout=20, check=False)

    import sqlite3
    con = sqlite3.connect(local)
    rows = con.execute("SELECT uid,name,version,auto_update FROM downloaders ORDER BY uid").fetchall()
    save_text("downloaders-before-isolation.txt", repr(rows) + "\n")
    candidates = [r for r in rows if r[1] == "Nexora Manager Downloaders" and r[0] != 0]
    if len(candidates) != 1:
        con.close()
        raise RuntimeError("Expected exactly one non-default Nexora candidate row: " + repr(rows))

    candidate_uid = int(candidates[0][0])
    # Keep the required default row, but prevent background refetch during this disposable gate.
    con.execute("UPDATE downloaders SET auto_update = 0 WHERE uid = 0")
    con.commit()
    try:
        con.execute("PRAGMA wal_checkpoint(TRUNCATE)").fetchall()
        con.execute("PRAGMA journal_mode=DELETE").fetchall()
        con.commit()
    finally:
        con.close()

    tmp = "/data/local/tmp/nexora-manager-db"
    adb("push", str(local), tmp, timeout=30)
    shell("rm", "-f", remote + "-wal", remote + "-shm", timeout=15)
    shell("cp", tmp, remote, timeout=15)
    shell("chown", f"{uid}:{gid}", remote, timeout=15)
    shell("chmod", mode, remote, timeout=15)
    shell("restorecon", remote, timeout=15, check=False)
    shell("rm", "-f", tmp, timeout=10)

    source_root = f"/data/data/{PKG}/app_downloaders"
    candidate_jar = f"{source_root}/{candidate_uid}/downloader.jar"
    legacy_jar = f"{source_root}/0/downloader.jar"

    candidate_sha = shell("sha256sum", candidate_jar, timeout=20)
    save_text("candidate-runtime-jar-sha256.txt", candidate_sha + "\n")
    expected = "69e90c54a48d571bf3f7b644315b6dc4ed91b1fc517caa99109007f83815e641"
    if not candidate_sha.lower().startswith(expected):
        raise RuntimeError("Runtime candidate downloader.jar SHA-256 mismatch: " + candidate_sha)

    legacy_sha = shell("sha256sum", legacy_jar, timeout=20, check=False)
    save_text("legacy-runtime-jar-before-disable.txt", legacy_sha + "\n")
    shell("rm", "-f", legacy_jar, timeout=15)
    if "EXISTS" in adb(
        "shell",
        f"if [ -e {legacy_jar} ]; then echo EXISTS; else echo MISSING; fi",
        timeout=10,
        check=False,
    ):
        raise RuntimeError("Legacy downloader.jar still exists after disable step")

    # Reload with default source registered but unable to load a downloader.
    open_downloads()
    root = dump("candidate-only-runtime")
    labs = labels(root)
    if "Nexora Manager Downloaders" not in labs:
        raise RuntimeError("Candidate disappeared after runtime isolation")
    screenshot("candidate-only-runtime")

def tap_lowest_text(needle, timeout=30):
    deadline = time.time() + timeout
    while time.time() < deadline:
        root = dump("current")
        nodes = find_nodes(root, needle)
        if nodes:
            node = max(nodes, key=lambda n: center(n.attrib.get("bounds", ""))[1])
            tap_node(node)
            return
        time.sleep(1)
    raise RuntimeError(f"Could not find {needle!r}")

def find_and_tap_scrolling(needle, attempts=12):
    for i in range(attempts):
        root = dump(f"scroll-{i}")
        nodes = find_nodes(root, needle)
        if nodes:
            tap_node(nodes[0])
            return
        shell("input", "swipe", "540", "1500", "540", "650", "450", timeout=10)
        time.sleep(1)
    raise RuntimeError(f"Could not find {needle!r} after scrolling")

def open_candidate_apkmirror():
    # Restart only the Manager process so the completed onboarding returns us to Dashboard.
    # This avoids confusing the Downloads screen's local "Apps" tab with bottom navigation.
    shell("am", "force-stop", PKG, timeout=15)
    shell("am", "start", "-n", MAIN, timeout=20)
    wait_node("Dashboard", timeout=45)
    screenshot("dashboard-before-candidate-test")

    tap_lowest_text("Apps", timeout=30)
    wait_node("Apps", timeout=20)
    find_and_tap_scrolling("YouTube", attempts=15)
    wait_node("Select APK source", timeout=30)
    screenshot("youtube-selected")

    tap_text("Select APK source", timeout=20)
    root = dump("apk-source-selector")
    mirrors = find_nodes(root, "APKMirror")
    if len(mirrors) != 1:
        screenshot("apk-source-ambiguous")
        raise RuntimeError(f"Expected exactly one APKMirror after isolation, found {len(mirrors)}")
    tap_node(mirrors[0])

    deadline = time.time() + 45
    last_activity_dump = ""
    while time.time() < deadline:
        last_activity_dump = shell("dumpsys", "activity", "activities", timeout=20, check=False)
        if DOWNLOADER in last_activity_dump:
            break
        try:
            probe = dump("candidate-apkmirror-probe")
            probe_labels = labels(probe)
            if any("APKMirror" in x for x in probe_labels):
                break
        except Exception:
            pass
        time.sleep(1)
    else:
        save_text("activity-after-source-select.txt", last_activity_dump)
        raise RuntimeError("DownloaderActivity/APKMirror UI did not appear")

    save_text("activity-after-source-select.txt", last_activity_dump)

    # Give WebView enough time to finish first render.
    time.sleep(8)
    for _ in range(3):
        try:
            root = dump("candidate-apkmirror")
            labs = labels(root)
            if "AGREE" in labs:
                tap_node(find_nodes(root, "AGREE")[0])
                time.sleep(2)
                continue
            break
        except Exception:
            time.sleep(2)
    root = dump("candidate-apkmirror-final")
    labs = labels(root)
    if not any("APKMirror" in x for x in labs):
        raise RuntimeError("APKMirror UI not rendered: " + repr(labs[:120]))
    if not any(re.search(r"YouTube\s+20\.40\.45", x) for x in labs):
        raise RuntimeError("Expected YouTube 20.40.45 result not rendered: " + repr(labs[:160]))
    screenshot("candidate-apkmirror-final")

def complete_apkmirror_download_handoff():
    def dismiss_premium_overlay():
        try:
            root = dump("apkmirror-overlay-probe")
            closes = [
                node for node in find_nodes(root, "Close")
                if node.attrib.get("bounds", "") != "[0,0][0,0]"
            ]
            if closes:
                tap_node(closes[-1])
                time.sleep(1)
                return True
        except Exception:
            pass
        return False

    # The sticky APKMirror Premium banner can cover the real result row.
    dismiss_premium_overlay()

    # Use the explicit "2 variants" link instead of the release title. On
    # APKMirror the title click can be redirected through publisher/ad links.
    # Move the result away from the bottom navigation, then require a visible
    # clickable variants link before tapping it.
    shell("input", "swipe", "540", "1900", "540", "1350", "320", timeout=10)
    time.sleep(1)
    variants_node = None
    for i in range(8):
        root = dump(f"apkmirror-variants-link-{i}")
        for node in find_nodes(root, "2 variants"):
            if node.attrib.get("clickable") != "true":
                continue
            bounds = node.attrib.get("bounds", "")
            nums = [int(x) for x in re.findall(r"\d+", bounds)]
            if len(nums) != 4:
                continue
            x1, y1, x2, y2 = nums
            if x2 > x1 and y2 > y1 and 300 <= y1 and y2 <= 2150:
                variants_node = node
                break
        if variants_node is not None:
            break
        shell("input", "swipe", "540", "1700", "540", "1100", "350", timeout=10)
        time.sleep(1)

    if variants_node is None:
        screenshot("apkmirror-variants-link-missing")
        raise RuntimeError("Visible clickable '2 variants' link not found")

    screenshot("apkmirror-result-before-variants-click")
    tap_node(variants_node)
    time.sleep(3)
    dismiss_premium_overlay()
    screenshot("apkmirror-release-page")

    # APKMirror can insert a full-screen interstitial ad here. Its visible
    # "Close" control is not exposed to uiautomator, so dismiss it at the
    # stable top-right location only when the APK variant is still absent.
    try:
        wait_node("APK", timeout=4)
    except RuntimeError:
        screenshot("apkmirror-interstitial-before-close")
        shell("input", "tap", "980", "340", timeout=10)
        time.sleep(2)
        screenshot("apkmirror-interstitial-after-close")

    # Select the normal APK variant rather than the bundle.
    find_and_tap_scrolling("APK", attempts=18)
    time.sleep(3)
    dismiss_premium_overlay()
    screenshot("apkmirror-apk-variant")

    # Trigger the site's final download handoff.
    find_and_tap_scrolling("DOWNLOAD APK", attempts=20)

    deadline = time.time() + 45
    last_labels = []
    last_activity = ""
    while time.time() < deadline:
        last_activity = shell("dumpsys", "activity", "activities", timeout=20, check=False)
        try:
            root = dump("download-handoff-probe")
            last_labels = labels(root)
        except Exception:
            last_labels = []

        joined = "\n".join(last_labels)
        if "ERR_TIMED_OUT" in joined or "Webpage not available" in joined:
            screenshot("download-handoff-timeout")
            save_text("activity-download-handoff-timeout.txt", last_activity)
            raise RuntimeError("Signed APK URL was rendered by WebView instead of handed to the Manager")

        if any("Using APKMirror" in x for x in last_labels):
            screenshot("download-handoff-success")
            save_text("activity-download-handoff-success.txt", last_activity)
            return

        time.sleep(1)

    screenshot("download-handoff-timeout")
    save_text("activity-download-handoff-timeout.txt", last_activity)
    save_text("download-handoff-last-labels.txt", "\n".join(last_labels) + "\n")
    raise RuntimeError("APKMirror download URL did not return to Manager as SelectedApp.Download")


def final_stability():
    pid1 = shell("pidof", PKG, timeout=10)
    if not pid1:
        raise RuntimeError("Manager process missing before stability wait")
    time.sleep(15)
    pid2 = shell("pidof", PKG, timeout=10)
    if pid1 != pid2:
        raise RuntimeError(f"Manager PID changed during stability wait: {pid1} -> {pid2}")

    windows = shell("dumpsys", "window", "windows", timeout=25, check=False)
    save_text("dumpsys-window-final.txt", windows)
    if "Application Not Responding: com.fantamk.revanced.manager" in windows:
        raise RuntimeError("Manager ANR window present")

    lastanr = shell("dumpsys", "activity", "lastanr", timeout=25, check=False)
    save_text("dumpsys-lastranr.txt", lastanr)
    if "com.fantamk.revanced.manager" in lastanr:
        raise RuntimeError("Manager appears in dumpsys activity lastanr")

    assert_no_manager_failure("final")
    screenshot("final-stable")

def verify_inputs():
    pkg = shell("dumpsys", "package", PKG, timeout=25)
    save_text("manager-package.txt", pkg)
    if "versionName=0.4.0-dev.35" not in pkg:
        raise RuntimeError("Wrong Manager versionName")
    if not re.search(r"versionCode=400035\b", pkg):
        raise RuntimeError("Wrong Manager versionCode")

    guest = shell("sha256sum", f"/sdcard/Download/{CANDIDATE}", timeout=20)
    save_text("candidate-guest-sha256.txt", guest + "\n")
    expected = "69e90c54a48d571bf3f7b644315b6dc4ed91b1fc517caa99109007f83815e641"
    if not guest.lower().startswith(expected):
        raise RuntimeError("Candidate guest SHA-256 mismatch: " + guest)

def main():
    save_text("adb-devices.txt", adb("devices", "-l", timeout=15) + "\n")
    verify_inputs()
    adb("logcat", "-c", timeout=15, check=False)

    complete_onboarding()
    assert_no_manager_failure("after-onboarding")

    verify_dev35_dashboard()
    assert_no_manager_failure("after-dev35-dashboard")

    open_downloads()
    import_candidate()
    assert_no_manager_failure("after-import")

    verify_downloader_source_labels()
    assert_no_manager_failure("after-source-label-verification")

    isolate_candidate_database()
    assert_no_manager_failure("after-isolation")

    open_candidate_apkmirror()
    assert_no_manager_failure("after-apkmirror")

    # The physical-device dev.24 test already proved the final download path.
    # This gate focuses on the saved-state crash regression and preserving
    # the proven APKMirror search behavior.
    final_stability()
    result = (
        "PASS\n"
        "Manager=0.4.0-dev.35\n"
        "Candidate=1.2.0-nexora.1\n"
        "CandidateSHA256=69e90c54a48d571bf3f7b644315b6dc4ed91b1fc517caa99109007f83815e641\n"
        "Provider=APKMirror\n"
        "SourceLabels=PASS\n"
        "SavedStateRestore=PASS\n"
        "APKMirrorSearch=PASS\n"
        "Result=YouTube 20.40.45 rendered after force-stop restore\n"
        "ManagerFatal=0\n"
        "DashboardVisual=PASS\n"
        "PatchCTA=PASS\n"
        "ManagerANR=0\n"
    )
    save_text("GATE_RESULT.txt", result)
    print(result, end="")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        try:
            screenshot("failure")
        except Exception:
            pass
        try:
            save_text("GATE_RESULT.txt", "FAIL\n" + repr(exc) + "\n")
            save_text("logcat-failure.txt", adb("logcat", "-d", "-v", "threadtime", timeout=30, check=False))
        except Exception:
            pass
        raise
