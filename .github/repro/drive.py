#!/usr/bin/env python3
"""Temporary: completes the onboarding and opens the EPUB in the reader."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = sys.argv[1]
OUT = sys.argv[2]
shot = 0


def adb(*args):
  return subprocess.run(["adb", *args], capture_output=True)


def dump():
  for _ in range(5):
    out = adb("exec-out", "uiautomator", "dump", "/dev/tty").stdout.decode(errors="replace")
    s, e = out.find("<?xml"), out.rfind("</hierarchy>")
    if s != -1 and e != -1:
      root = ET.fromstring(out[s:e + len("</hierarchy>")])
      return list(root.iter("node")), out[s:e + 12]
    time.sleep(1)
  return [], ""


def screenshot(name):
  global shot
  shot += 1
  with open(f"{OUT}/{shot:02d}-{name}.png", "wb") as f:
    f.write(adb("exec-out", "screencap", "-p").stdout)


def find(nodes, pattern, pkg=None):
  for n in nodes:
    if pkg and not re.fullmatch(pkg, n.get("package", "")):
      continue
    label = n.get("text", "") or n.get("content-desc", "")
    if re.fullmatch(pattern, label, re.IGNORECASE):
      return n
  return None


def tap(node):
  l, t, r, b = map(int, re.findall(r"-?\d+", node.get("bounds")))
  adb("shell", "input", "tap", str((l + r) // 2), str((t + b) // 2))


PICKER = r".*documentsui"
buttons = [
  (r"Start Listening", PKG),
  (r"Add \d+ books?", PKG),
  (r"Allow", PICKER),
  (r"Choose a folder", PKG),
  (r"Continue without analytics", PKG),
  (r"Next", PKG),
  (r"Let's go", PKG),
]

adb("shell", "am", "start", "-W", "-n", f"{PKG}/voice.app.MainActivity")
time.sleep(5)
deadline = time.time() + 180
while True:
  if time.time() > deadline:
    screenshot("stuck")
    print("onboarding stuck")
    sys.exit(2)
  nodes, xml = dump()
  hit = None
  for pattern, pkg in buttons:
    hit = find(nodes, pattern, pkg)
    if hit is not None:
      break
  if hit is None:
    if find(nodes, r"Book", PICKER) is not None:
      hit = find(nodes, r"Use this folder", PICKER)
    else:
      hit = find(nodes, r"Audiobooks", PICKER)
  if hit is None:
    time.sleep(1)
    continue
  label = hit.get("text") or hit.get("content-desc")
  print("tap", label)
  screenshot(re.sub(r"\W", "_", label))
  tap(hit)
  time.sleep(2)
  if label.lower() == "start listening":
    break

deadline = time.time() + 120
book = None
while time.time() < deadline:
  nodes, xml = dump()
  book = find(nodes, r"(?s).*alice.*", PKG)
  if book is not None:
    break
  print("labels:", [n.get("text") or n.get("content-desc") for n in nodes if n.get("text") or n.get("content-desc")])
  adb("shell", "input", "swipe", "500", "1800", "500", "800", "300")
  time.sleep(3)
screenshot("library")
with open(f"{OUT}/library.xml", "w") as f:
  f.write(xml)
if book is None:
  print("the library does not show the EPUB")
  sys.exit(3)
print("open", book.get("text") or book.get("content-desc"))
tap(book)
for i in range(3):
  time.sleep(5)
  screenshot(f"reader{i}")
nodes, xml = dump()
print("reader labels:", [n.get("package") + ":" + (n.get("text") or n.get("content-desc")) for n in nodes if n.get("text") or n.get("content-desc")])
with open(f"{OUT}/reader.xml", "w") as f:
  f.write(xml)
top = adb("shell", "dumpsys", "activity", "activities").stdout.decode(errors="replace")
print("resumed:", [l for l in top.splitlines() if "mResumedActivity" in l or "topResumedActivity" in l])
alive = adb("shell", "pidof", PKG).stdout.decode().strip()
print("app alive:", alive)
if not alive or not any(PKG in l for l in top.splitlines() if "topResumedActivity" in l):
  sys.exit(4)
