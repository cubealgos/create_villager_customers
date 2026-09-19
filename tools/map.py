#!/usr/bin/env python3
"""Generate the repository map from the source: docs/map.md locates every package, docs/map/<project>.md
lists every type's summary and non-private signatures. Parses Java, Kotlin and Python as text; never
runs the code it describes (standards/tech/generated-directory-maps.md). `--check` regenerates in
memory and fails when the committed map is stale."""
from __future__ import annotations

import argparse
import ast
import re
import sys
from pathlib import Path

MAP_FILE = Path("docs/map.md")
MAP_DIR = Path("docs/map")
IGNORED_DIRS = {"build", ".gradle", ".worktrees", ".git", ".gitkontor", "run", "node_modules", "__pycache__"}
SOURCE_SETS = ("main", "test", "gametest")
MODIFIERS = {"public", "protected", "private", "static", "final", "abstract", "default", "synchronized",
             "native", "strictfp", "sealed", "non-sealed", "transient", "volatile"}
DECL_RE = re.compile(r"\b(class|record|interface|enum)\s+(\w+)")
MEMBER_RE = re.compile(r"^(?:(?P<generics><[^{};]*?>)\s+)?(?P<type>[\w.$]+(?:<[^{};]*?>)?(?:\[\])*)\s+(?P<name>\w+)\s*\((?P<params>[^)]*)\)")
CTOR_RE = re.compile(r"^(?P<name>\w+)\s*\((?P<params>[^)]*)\)")
CONST_RE = re.compile(r"^(?P<type>[\w.$]+(?:<[^{};=]*?>)?(?:\[\])*)\s+(?P<name>[A-Z_][A-Z0-9_]*)\s*(?:=|;|$)")


# ---------------------------------------------------------------- Java, as text

def scan_java(src: str) -> tuple[str, list[tuple[int, str]]]:
    """Blank comments and string literals so braces inside them do not count; keep Javadoc blocks
    with their end offset so a declaration can claim the one right before it."""
    out: list[str] = []
    javadocs: list[tuple[int, str]] = []
    i, n = 0, len(src)
    while i < n:
        ch = src[i]
        if src.startswith("/**", i) and not src.startswith("/**/", i):
            end = src.find("*/", i + 3)
            end = n if end < 0 else end + 2
            javadocs.append((i, src[i:end]))  # offsets are preserved: every replacement keeps its length
            out.append(" " * (end - i))
            i = end
        elif src.startswith("/*", i):
            end = src.find("*/", i + 2)
            end = n if end < 0 else end + 2
            out.append(" " * (end - i))
            i = end
        elif src.startswith("//", i):
            end = src.find("\n", i)
            end = n if end < 0 else end
            out.append(" " * (end - i))
            i = end
        elif ch == '"':
            if src.startswith('"""', i):
                end = src.find('"""', i + 3)
                end = n if end < 0 else end + 3
            else:
                j = i + 1
                while j < n and src[j] != '"':
                    j += 2 if src[j] == "\\" else 1
                end = min(j + 1, n)
            out.append('"' + " " * (end - i - 2) + '"' if end - i >= 2 else " ")
            i = end
        elif ch == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == "\\" else 1
            end = min(j + 1, n)
            out.append(" " * (end - i))
            i = end
        else:
            out.append(ch)
            i += 1
    return "".join(out), javadocs


def summary_of(javadoc: str | None) -> str:
    if not javadoc:
        return ""
    body = javadoc.strip()
    body = body[3:-2] if body.startswith("/**") else body
    lines = [re.sub(r"^\s*\*\s?", "", line) for line in body.splitlines()]
    text = " ".join(line.strip() for line in lines if line.strip() and not line.strip().startswith("@"))
    text = re.sub(r"\{@(?:link|code|linkplain)\s+([^}]*)\}", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    match = re.search(r"^(.*?[.!?])(?:\s|$)", text)
    return (match.group(1) if match else text).strip()


def squeeze(text: str) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    text = re.sub(r"\s*([(),<>\[\]])\s*", r"\1", text)
    text = text.replace(",", ", ")
    return re.sub(r">(?=\w)", "> ", text)


def strip_annotations(head: str) -> str:
    return re.sub(r"@\w+(?:\([^)]*\))?\s*", "", head).strip()


def parse_java(path: Path) -> dict:
    src = path.read_text(encoding="utf-8")
    clean, javadocs = scan_java(src)
    package = ""
    m = re.search(r"^\s*package\s+([\w.]+)\s*;", clean, re.M)
    if m:
        package = m.group(1)
    types: list[dict] = []
    stack: list[dict | None] = []  # one entry per open brace: the type it opened, or None
    depth = 0
    seg_start = 0
    i, n = 0, len(clean)
    doc_idx = 0

    def javadoc_before(pos: int, after: int) -> str | None:
        found = None
        for end, text in javadocs:
            if after <= end <= pos:
                found = text
        return found

    def owner() -> dict | None:
        return stack[-1] if stack else None

    while i < n:
        ch = clean[i]
        if ch in "{};":
            head = clean[seg_start:i]
            head_stripped = strip_annotations(head).strip()
            doc = javadoc_before(i, seg_start)
            if ch == "{":
                decl = DECL_RE.search(head_stripped)
                if decl and not re.match(r"^(if|for|while|switch|try|do|else|synchronized|catch)\b", head_stripped) and "new " not in head_stripped:
                    kind, name = decl.group(1), decl.group(2)
                    mods = [w for w in head_stripped[:decl.start()].split() if w in MODIFIERS]
                    entry = {"kind": kind, "name": name, "visibility": "private" if "private" in mods else "public",
                             "summary": summary_of(doc), "members": [], "components": "", "depth": depth, "parent": owner()}
                    if kind == "record":
                        rec = re.search(r"\brecord\s+\w+\s*(?:<[^>]*>)?\s*\(([^)]*)\)", head_stripped, re.S)
                        entry["components"] = squeeze(rec.group(1)) if rec else ""
                    types.append(entry)
                    stack.append(entry)
                else:
                    own = owner()
                    if own is not None and depth == own["depth"] + 1:
                        member = parse_member(head_stripped, own["name"], doc)
                        if member:
                            own["members"].append(member)
                    stack.append(None)
                depth += 1
            elif ch == "}":
                depth -= 1
                if stack:
                    stack.pop()
            else:  # ';'
                own = owner()
                if own is not None and depth == own["depth"] + 1 and own["kind"] != "enum" or (own is not None and own["kind"] == "enum" and "(" in head_stripped and depth == own["depth"] + 1):
                    member = parse_member(head_stripped, own["name"], doc)
                    if member:
                        own["members"].append(member)
            seg_start = i + 1
        i += 1
    public_types = [t for t in types if t["visibility"] != "private"]
    for t in public_types:
        t.pop("parent", None)
    return {"path": path, "package": package, "types": public_types, "file_summary": ""}


def parse_member(head: str, type_name: str, doc: str | None) -> dict | None:
    head = head.strip()
    if not head or head.startswith(("return", "throw", "if", "for", "while", "switch", "case", "default:")):
        return None
    words = head.split()
    mods = []
    while words and words[0] in MODIFIERS:
        mods.append(words.pop(0))
    if "private" in mods:
        return None
    rest = " ".join(words)
    rest = re.sub(r"\s*throws\s+[\w.,\s]+$", "", rest)
    rest = squeeze(rest)
    ctor = CTOR_RE.match(rest)
    if ctor and ctor.group("name") == type_name and "=" not in rest:
        return {"kind": "ctor", "text": f"{type_name}({ctor.group('params')})", "summary": summary_of(doc)}
    method = MEMBER_RE.match(rest)
    if method and "=" not in rest.split("(")[0] and method.group("type") not in ("new", "return"):
        generics = (method.group("generics") + " ") if method.group("generics") else ""
        return {"kind": "method", "text": f"{generics}{method.group('type')} {method.group('name')}({method.group('params')})",
                "summary": summary_of(doc), "static": "static" in mods}
    const = CONST_RE.match(rest)
    if const and "static" in mods and "final" in mods:
        return {"kind": "const", "text": f"{const.group('type')} {const.group('name')}", "summary": summary_of(doc), "static": True}
    return None


def package_summary(package_info: Path | None) -> str:
    if package_info is None or not package_info.exists():
        return ""
    _, docs = scan_java(package_info.read_text(encoding="utf-8"))
    return summary_of(docs[-1][1]) if docs else ""


# ---------------------------------------------------------------- Kotlin and Python, lightly

def parse_kotlin(path: Path) -> dict:
    src = path.read_text(encoding="utf-8")
    first = ""
    m = re.match(r"\s*(?:/\*\*?(.*?)\*/|//(.*))", src, re.S)
    if m:
        first = summary_of("/**" + (m.group(1) or m.group(2) or "") + "*/")
    return {"path": path, "summary": first}


def parse_python(path: Path) -> dict:
    tree = ast.parse(path.read_text(encoding="utf-8"))
    doc = ast.get_docstring(tree) or ""
    summary = summary_of("/**" + doc + "*/")
    defs = []
    for node in tree.body:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and not node.name.startswith("_"):
            args = ", ".join(a.arg for a in node.args.args)
            defs.append(f"{node.name}({args})")
    return {"path": path, "summary": summary, "defs": defs}


# ---------------------------------------------------------------- the tree

def project_of(rel: Path) -> tuple[str, str, str]:
    """(project label, source set, source root) for a file under a Gradle source set."""
    parts = rel.parts
    if "src" in parts:
        idx = parts.index("src")
        project = "-".join(parts[:idx]) or "root"
        source_set = parts[idx + 1] if idx + 1 < len(parts) else "main"
        root = Path(*parts[: idx + 3]) if idx + 2 < len(parts) else Path(*parts[: idx + 2])
        return project, source_set, str(root)
    return parts[0] if parts else "root", "main", str(rel.parent)


def walk(root: Path):
    for path in sorted(root.rglob("*")):
        rel = path.relative_to(root)
        if any(part in IGNORED_DIRS for part in rel.parts):
            continue
        if path.is_file() and path.suffix in (".java", ".kts", ".kt", ".py"):
            yield path, rel


def collect(root: Path) -> dict:
    packages: dict[tuple[str, str, str], dict] = {}
    kotlin: list[dict] = []
    python: list[dict] = []
    for path, rel in walk(root):
        if path.suffix == ".java":
            parsed = parse_java(path)
            project, source_set, src_root = project_of(rel)
            key = (project, source_set, parsed["package"])
            pkg = packages.setdefault(key, {"project": project, "source_set": source_set, "package": parsed["package"],
                                            "root": src_root, "files": [], "summary": None})
            if path.name == "package-info.java":
                pkg["summary"] = package_summary(path)
                continue
            pkg["files"].append({"rel": str(rel), "types": parsed["types"]})
            if pkg["summary"] is None and (path.parent / "package-info.java").exists():
                pkg["summary"] = package_summary(path.parent / "package-info.java")
        elif path.suffix in (".kt", ".kts"):
            kotlin.append({"rel": str(rel), **parse_kotlin(path)})
        elif path.suffix == ".py":
            python.append({"rel": str(rel), **parse_python(path)})
    return {"packages": packages, "kotlin": kotlin, "python": python}


# ---------------------------------------------------------------- rendering

HEADER = "<!-- generated by tools/map.py; edit the source, then run `just map` -->"


def page_of(pkg: dict) -> Path:
    """One signature page per package (and per test source set), so no page outruns the budget."""
    suffix = "" if pkg["source_set"] == "main" else f".{pkg['source_set']}"
    return MAP_DIR / pkg["project"] / f"{pkg['package']}{suffix}.md"


def render_locator(tree: dict) -> str:
    out = [HEADER, "", "# Map", "",
           "Where things live. One line per package: its project, its types and the first sentence of its",
           "`package-info.java`. A type's file is `<source root>/<package as path>/<Type>.java`; a package's",
           "signature page is `docs/map/<project>/<package>.md` (`<package>.test.md` for its tests). Read the",
           "signature page before calling into a package you did not write.", ""]
    projects: dict[str, list[dict]] = {}
    for pkg in tree["packages"].values():
        projects.setdefault(pkg["project"], []).append(pkg)
    out += ["| project | source roots |", "|---|---|"]
    for project in sorted(projects):
        roots = sorted({p["root"] for p in projects[project]})
        out.append(f"| `{project}` | {', '.join('`' + r + '`' for r in roots)} |")
    out += ["", "| package | project | types | what |", "|---|---|---|---|"]
    for key in sorted(tree["packages"], key=lambda k: (k[0], k[1] != "main", k[2])):
        pkg = tree["packages"][key]
        names = []
        for f in pkg["files"]:
            for t in f["types"]:
                if t["depth"] == 0:
                    names.append(t["name"])
        label = pkg["project"] + ("" if pkg["source_set"] == "main" else f" ({pkg['source_set']})")
        out.append(f"| `{pkg['package']}` | {label} | {', '.join(names)} | {pkg['summary'] or ''} |")
    if tree["kotlin"]:
        out += ["", "| build script | what |", "|---|---|"]
        for k in tree["kotlin"]:
            out.append(f"| `{k['rel']}` | {k['summary']} |")
    if tree["python"]:
        out += ["", "| tool | what | entry points |", "|---|---|---|"]
        for p in tree["python"]:
            out.append(f"| `{p['rel']}` | {p['summary']} | {', '.join('`' + d + '`' for d in p['defs'])} |")
    return "\n".join(out) + "\n"


def render_package(pkg: dict) -> str:
    suffix = "" if pkg["source_set"] == "main" else f" ({pkg['source_set']})"
    out = [HEADER, "", f"# `{pkg['package']}`{suffix} — {pkg['project']}", "",
           "Every type with its summary and every non-private constructor, method and constant. The",
           "signature is the contract; read the source only when the summary is not enough.", ""]
    if pkg["summary"]:
        out.append(pkg["summary"] + "\n")
    if True:
        for f in sorted(pkg["files"], key=lambda f: f["rel"]):
            for t in f["types"]:
                indent = "" if t["depth"] == 0 else "    "
                head = f"{t['kind']} {t['name']}" + (f"({t['components']})" if t["components"] else "")
                out.append(f"{indent}### `{head}` — `{f['rel']}`" if t["depth"] == 0 else f"{indent}- **nested** `{head}`")
                if t["summary"]:
                    out.append(f"{indent}{t['summary']}")
                for m in t["members"]:
                    note = f" — {m['summary']}" if m.get("summary") else ""
                    out.append(f"{indent}- `{m['text']}`{note}")
                out.append("")
    return "\n".join(out) + "\n"


def render_all(root: Path) -> dict[Path, str]:
    tree = collect(root)
    files = {MAP_FILE: render_locator(tree)}
    for pkg in tree["packages"].values():
        if pkg["files"]:
            files[page_of(pkg)] = render_package(pkg)
    return files


def write(root: Path, files: dict[Path, str]) -> None:
    for rel, text in files.items():
        (root / rel).parent.mkdir(parents=True, exist_ok=True)
        (root / rel).write_text(text, encoding="utf-8")
    for stale in (root / MAP_DIR).rglob("*.md"):
        if stale.relative_to(root) not in files:
            stale.unlink()
    for folder in sorted((root / MAP_DIR).rglob("*"), reverse=True):
        if folder.is_dir() and not any(folder.iterdir()):
            folder.rmdir()


def check(root: Path, files: dict[Path, str]) -> list[str]:
    problems = []
    for rel, text in files.items():
        target = root / rel
        if not target.exists():
            problems.append(f"missing: {rel}")
        elif target.read_text(encoding="utf-8") != text:
            problems.append(f"stale: {rel}")
    for extra in sorted((root / MAP_DIR).rglob("*.md")) if (root / MAP_DIR).exists() else []:
        if extra.relative_to(root) not in files:
            problems.append(f"orphan: {extra.relative_to(root)}")
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="repository root (default: cwd)")
    parser.add_argument("--check", action="store_true", help="fail when the committed map differs from the source")
    args = parser.parse_args(argv)
    root = args.root.resolve()
    files = render_all(root)
    if args.check:
        problems = check(root, files)
        for p in problems:
            print(f"map: {p}", file=sys.stderr)
        if problems:
            print("map: stale; run `just map`", file=sys.stderr)
            return 1
        print(f"map: {len(files)} files current")
        return 0
    write(root, files)
    print(f"map: wrote {len(files)} files under {MAP_FILE.parent}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
