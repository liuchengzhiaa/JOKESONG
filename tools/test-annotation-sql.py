"""Generated in-memory SQLite only. Complements, not replaces, Android Room tests."""
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCHEMAS = ROOT / "app/schemas/com.example.asmrplayer.core.database.AsmrDatabase"
SOURCE = ROOT / "app/src/main/java/com/example/asmrplayer/core/annotations"


def schema_db(version):
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    schema = json.loads((SCHEMAS / f"{version}.json").read_text(encoding="utf-8"))["database"]
    for table in schema["entities"]:
        for sql in [table["createSql"], *[i["createSql"] for i in table.get("indices", [])]]:
            db.execute(sql.replace("${TABLE_NAME}", table["tableName"]))
    return db


def signature(db, table):
    return (db.execute(f"PRAGMA table_info({table})").fetchall(),
            sorted(db.execute(f"PRAGMA foreign_key_list({table})").fetchall()),
            sorted(db.execute(f"PRAGMA index_list({table})").fetchall(), key=lambda r: r[1]))


def main():
    old, fresh = schema_db(1), schema_db(2)
    old.execute("INSERT INTO playback_bookmarks(audioDocumentUri,positionMs,label,createdAtMs) VALUES('generated',123,'keep',1)")
    before = {t: signature(old, t) for (t,) in old.execute("SELECT name FROM sqlite_master WHERE type='table'") if t != "sqlite_sequence"}
    migration = (SOURCE / "AnnotationMigration.kt").read_text(encoding="utf-8")
    statements = re.findall(r'db.execSQL\(("[^\n]+")\)', migration)
    assert len(statements) == 6
    for quoted in statements:
        old.execute(json.loads(quoted))
    for table, sig in before.items():
        assert signature(old, table) == sig, table
    for table in ["user_items", "user_tags", "user_item_tags"]:
        assert signature(old, table) == signature(fresh, table), table
    assert old.execute("SELECT label FROM playback_bookmarks").fetchone() == ("keep",)
    text = (SOURCE / "AnnotationDao.kt").read_text(encoding="utf-8")
    queries = [json.loads(q) for q in re.findall(r'@Query\(("[^\n]+")\)', text)]
    items_query = next(q for q in queries if q.startswith("SELECT * FROM user_items i"))
    tags_query = next(q for q in queries if q.startswith("SELECT t.id"))
    for i in range(125):
        root = "root" if i < 124 else "other"
        kind = "Folder" if i == 0 else "Audio"
        score = None if i == 0 else i % 101
        old.execute("INSERT INTO user_items(rootUri,documentUri,kind,title,subtitle,paletteSeed,score,lyricSize,lyricModified) VALUES(?,?,?,?,?,0,?,0,0)", (root, str(i), kind, f"Song{i:03}", "MP3", score))
    old.execute("INSERT INTO user_tags(rootUri,name,nameKey) VALUES('root','Rain','rain')")
    tag = old.execute("SELECT id FROM user_tags").fetchone()[0]
    old.executemany("INSERT INTO user_item_tags VALUES('root',?,?)", [(str(i), tag) for i in [0, 1, 90]])
    args = dict(root="root", tagId=None, score=90, limit=51, offset=0)
    assert [r[1] for r in old.execute(items_query, args)] == ["90"]
    args["score"] = 0
    assert [r[1] for r in old.execute(items_query, args)] == ["101"]  # not the unrated folder
    args.update(score=None, tagId=tag)
    assert len(old.execute(items_query, args).fetchall()) == 3
    args.update(score=None, tagId=None, limit=50)
    pages = []
    for offset in (0, 50, 100):
        pages.extend(r[1] for r in old.execute(items_query, dict(args, offset=offset)))
    assert len(pages) == len(set(pages)) == 124
    assert old.execute(tags_query, dict(root="root", pattern="%", limit=51, offset=0)).fetchone()[2] == 3
    print("PASS: additive v1-to-v2 schema, old row preservation, exact score/zero/unrated, tags/counts, root isolation and pagination (generated desktop SQLite)")


if __name__ == "__main__":
    main()
