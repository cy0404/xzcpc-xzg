# -*- coding: utf-8 -*-
"""
SQL 语法校验器（只解析、不执行）
================================
剥掉 -- 行注释（不动字符串内的 --），按 ; 切分，每条语句用 MySQL 的
PREPARE 解析一遍。PREPARE 只做语法/对象校验，**不会执行任何 DML**。

用法：python check_sql_syntax.py <sql文件> [<sql文件> ...]

为什么需要它：成本卡包材迁移脚本曾因 AND/OR 优先级 + 注释漏加 -- 两处问题
在客户端执行时炸掉，事后才发现。这个脚本能在交付前把这类问题挡掉。
"""
import sys, io, os
import pymysql

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
from _conn import MY   # 连接参数见 _conn.py（含密码，不入库）


def split_statements(text):
    """→ [(起始行号, 语句)]，跳过注释与空语句"""
    out, buf, line, start = [], [], 1, 1
    i, n, in_str = 0, len(text), False
    while i < n:
        ch = text[i]
        if in_str:
            buf.append(ch)
            if ch == "'":
                if i + 1 < n and text[i + 1] == "'":
                    buf.append(text[i + 1]); i += 2; continue
                in_str = False
            if ch == "\n":
                line += 1
            i += 1
            continue
        if ch == "'":
            in_str = True; buf.append(ch); i += 1; continue
        if ch == "-" and i + 1 < n and text[i + 1] == "-":
            j = text.find("\n", i)
            j = n if j < 0 else j
            cnt = text.count("\n", i, j)
            buf.append("\n" * cnt)          # 保留行号
            line += cnt
            i = j
            continue
        if ch == ";":
            s = "".join(buf).strip()
            if s:
                out.append((start, s))
            buf = []
            line += 1
            start = line
            i += 1
            continue
        if ch == "\n":
            line += 1
        buf.append(ch)
        i += 1
    s = "".join(buf).strip()
    if s:
        out.append((start, s))
    return out


def check(path, cur):
    print("\n" + "=" * 100)
    print("文件:", path)
    print("=" * 100)
    text = io.open(path, encoding="utf-8").read()
    stmts = split_statements(text)
    print("  切出语句 %d 条" % len(stmts))
    bad = 0
    for ln, st in stmts:
        head = " ".join(st.split())[:70]
        try:
            cur.execute("PREPARE __chk FROM %s", (st,))
            cur.execute("DEALLOCATE PREPARE __chk")
            print("    L%-5d ✓ %s" % (ln, head))
        except Exception as e:
            code = e.args[0] if e.args else "?"
            if code == 1295:      # ER_UNSUPPORTED_PS
                print("    L%-5d ~ %s  (不支持 PREPARE，跳过)" % (ln, head))
                continue
            bad += 1
            print("    L%-5d ✗ %s" % (ln, head))
            print("            → %s" % e)
    print("  %s" % ("全部语句语法通过 ✓" if bad == 0 else "!! %d 条语法错误" % bad))
    return bad


def main():
    files = sys.argv[1:]
    if not files:
        base = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "database"))
        files = [os.path.join(base, n) for n in
                 ("migration-cost-card-pack-20260911.sql",
                  "restore-cost-card-raw-semi-20260911.sql")]
    conn = pymysql.connect(**MY)
    cur = conn.cursor()
    total = 0
    for f in files:
        total += check(f, cur)
    conn.close()
    print("\n合计语法错误：%d" % total)
    sys.exit(1 if total else 0)


if __name__ == "__main__":
    main()
