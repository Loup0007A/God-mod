"""Construit le site : dist/index.html (GitHub Pages) à partir de page.html et commands.json.
Lancer depuis la racine du dépôt : python3 site/build.py"""
import json, os, re

here = os.path.dirname(os.path.abspath(__file__))
page = open(os.path.join(here, "page.html"), encoding="utf-8").read()
cmds = json.load(open(os.path.join(here, "commands.json"), encoding="utf-8"))

# Les commandes viennent de l'aide du mod (Help.java) : on les relit si le code est présent.
help_java = os.path.join(here, "..", "src", "main", "java", "com", "godmod", "command", "Help.java")
if os.path.exists(help_java):
    s = open(help_java, encoding="utf-8").read()
    fresh = []
    for m in re.finditer(r'cat\("(\w+)",\s*"([^"]+)",\s*ChatFormatting\.\w+,(.*?)\);\n', s, re.S):
        items = re.findall(r'p\("((?:[^"\\]|\\.)*)",\s*"((?:[^"\\]|\\.)*)"\)', m.group(3))
        if items:
            fresh.append({"id": m.group(1), "label": m.group(2), "items": [[a.replace('\\"', '"'), b.replace('\\"', '"')] for a, b in items]})
    if fresh:
        cmds = fresh
        json.dump(cmds, open(os.path.join(here, "commands.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=0)

data = json.dumps(cmds, ensure_ascii=False).replace("</", "<\\/")
page = page.replace("__COMMANDS__", data)
head, body = page.split("<!--BODY-->", 1)

dist = os.path.join(here, "dist")
os.makedirs(dist, exist_ok=True)
full = ('<!doctype html>\n<html lang="fr">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
        '<link rel="icon" href="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 26 26%22%3E%3Cpath d=%22M13 2 L15.6 9.4 L23.5 9.6 L17.2 14.4 L19.5 22 L13 17.5 L6.5 22 L8.8 14.4 L2.5 9.6 L10.4 9.4 Z%22 fill=%22%23e6b84c%22/%3E%3C/svg%3E">\n'
        + head + '</head>\n<body>\n' + body + '\n</body>\n</html>\n')
open(os.path.join(dist, "index.html"), "w", encoding="utf-8").write(full)
# Version « fragment » (sans doctype ni head) pour un aperçu publié ailleurs.
open(os.path.join(dist, "fragment.html"), "w", encoding="utf-8").write(head + body)
open(os.path.join(dist, ".nojekyll"), "w").write("")
print("site/dist/index.html :", len(full), "octets,", sum(len(c["items"]) for c in cmds), "commandes")
