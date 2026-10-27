#!/usr/bin/env python3
"""
Turns this 1.21.11 source tree into the Minecraft 26.1.2 version, in place (run on a fresh checkout, CI does it).

26.1 ships unobfuscated with renamed GUI classes and a few renamed APIs; everything else is the same code. Keeping
one source and converting it means every change lands in both jars.
"""
import os
import re
import sys

ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else ".")
JAVA = os.path.join(ROOT, "src", "main", "java")


def matching_paren(s, open_idx):
    depth, i, n = 0, open_idx, len(s)
    while i < n:
        c = s[i]
        if c == '"':
            i += 1
            while s[i] != '"':
                i += 2 if s[i] == '\\' else 1
        elif c == "'":
            i += 1
            while s[i] != "'":
                i += 2 if s[i] == '\\' else 1
        elif c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError("unbalanced")


def last_top_level_comma(s, start, end):
    depth, i, last = 0, start, -1
    while i < end:
        c = s[i]
        if c == '"':
            i += 1
            while s[i] != '"':
                i += 2 if s[i] == '\\' else 1
        elif c in '([{':
            depth += 1
        elif c in ')]}':
            depth -= 1
        elif c == ',' and depth == 0:
            last = i
        i += 1
    return last


def chat_messages(s):
    """player.displayClientMessage(msg, false|true) -> sendSystemMessage(msg) / sendOverlayMessage(msg)."""
    out, pos = [], 0
    key = ".displayClientMessage("
    while True:
        i = s.find(key, pos)
        if i < 0:
            out.append(s[pos:])
            return "".join(out)
        op = i + len(key) - 1
        cl = matching_paren(s, op)
        comma = last_top_level_comma(s, op + 1, cl)
        msg, flag = s[op + 1:comma], s[comma + 1:cl].strip()
        name = ".sendOverlayMessage(" if flag == "true" else ".sendSystemMessage("
        out.append(s[pos:i] + name + msg + ")")
        pos = cl + 1


RULES = [
    (r"\bGuiGraphics\b", "GuiGraphicsExtractor"),
    (r"\.drawString\(", ".text("),
    (r"\.renderOutline\(", ".outline("),
    (r"public void render\(GuiGraphicsExtractor ", "public void extractRenderState(GuiGraphicsExtractor "),
    (r"public void renderBackground\(GuiGraphicsExtractor ", "public void extractBackground(GuiGraphicsExtractor "),
    (r"super\.render\(g,", "super.extractRenderState(g,"),
    (r"\b(b|editBox)\.render\((g|currentG),", r"\1.extractRenderState(\2,"),
    (r"net\.fabricmc\.fabric\.api\.client\.keybinding\.v1\.KeyBindingHelper", "net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper"),
    (r"KeyBindingHelper\.registerKeyBinding\(", "KeyMappingHelper.registerKeyMapping("),
    (r"\bClientCommandManager\b", "ClientCommands"),
    (r"p\.hasEffect\(net\.minecraft\.world\.effect\.MobEffects\.BLINDNESS\)", "p.isMobilityRestricted()"),
]

changed = 0
for dp, _, files in os.walk(JAVA):
    for f in files:
        if not f.endswith(".java"):
            continue
        p = os.path.join(dp, f)
        s = o = open(p, encoding="utf-8").read()
        if "/core/" not in p.replace("\\", "/"):
            for pat, rep in RULES:
                s = re.sub(pat, rep, s)
            s = chat_messages(s)
        if s != o:
            open(p, "w", encoding="utf-8").write(s)
            changed += 1


def edit(rel, pairs):
    p = os.path.join(ROOT, rel)
    s = open(p, encoding="utf-8").read()
    for a, b in pairs:
        if a not in s:
            sys.exit(f"port-26.1: '{a}' not found in {rel}")
        s = s.replace(a, b)
    open(p, "w", encoding="utf-8").write(s)


edit("build.gradle", [
    ("id 'net.fabricmc.fabric-loom-remap'", "id 'net.fabricmc.fabric-loom'"),
    ("    mappings loom.officialMojangMappings()\n", ""),
    ('modImplementation "net.fabricmc:fabric-loader', 'implementation "net.fabricmc:fabric-loader'),
    ('modImplementation "net.fabricmc.fabric-api', 'implementation "net.fabricmc.fabric-api'),
    ("modCompileOnly(", "compileOnly("),
    ("it.options.release = 21", "it.options.release = 25"),
    ("JavaVersion.VERSION_21", "JavaVersion.VERSION_25"),
])
props = os.path.join(ROOT, "gradle.properties")
s = open(props, encoding="utf-8").read()
s = re.sub(r"minecraft_version=.*", "minecraft_version=26.1.2", s)
s = re.sub(r"loader_version=.*", "loader_version=0.19.2", s)
s = re.sub(r"loom_version=.*", "loom_version=1.16-SNAPSHOT", s)
s = re.sub(r"fabric_version=.*", "fabric_version=0.155.3+26.1.2", s)
s = re.sub(r"modmenu_version=.*", "modmenu_version=18.0.2", s)
open(props, "w", encoding="utf-8").write(s)
edit("gradle/wrapper/gradle-wrapper.properties", [("gradle-9.2.1-bin.zip", "gradle-9.6.0-bin.zip")])
edit("src/main/resources/fabric.mod.json", [
    ('"fabricloader": ">=0.18.2"', '"fabricloader": ">=0.19.2"'),
    ('"minecraft": "~1.21.11"', '"minecraft": "~26.1"'),
    ('"java": ">=21"', '"java": ">=25"'),
])
edit("src/main/resources/combatdata.mixins.json", [('"JAVA_21"', '"JAVA_25"')])
print(f"port-26.1: {changed} source files converted")
