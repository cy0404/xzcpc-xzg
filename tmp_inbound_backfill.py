# -*- coding: utf-8 -*-
"""
入库单一次性回填：企迈 OpenAPI 9.2.2 拉入库单 + 9.1.17 报货单归属匹配 → 生成幂等 SQL
用法：python tmp_inbound_backfill.py [入库窗口天数=30] [报货匹配窗口天数=60]
输出：tmp_inbound_backfill.sql（用户自行在目标库执行）
前置：目标库已执行 database/migration-add-inbound-order.sql（建 inbound_order / inbound_order_item）

关键约定（与后端 MpInboundServiceImpl 一致）：
- 只同步 1仓配入库 / 3采购入库
- 归属匹配四级：
  ① 入库单 bizNo ∈ 该门店报货单键集（declareNo/requireNo/bizNo/requireNoList/purchaseApplyNoList/purchaseNoList/bizNoList）
  ② warehouseCode ∈ 本店仓库编码集（报货单 storeWarehouseNo ∪ store_info.cangkuid）
  ③ 归一化仓库名 = 门店名（去括号/空白）
  ④ 归一化后首尾包含（毕节店↔毕节招商花园店），需唯一候选
- 金额/单价 9.2.2 返回「分」→ 存库统一「元」（÷100）
- 状态：1待入库→pending 2已入库→done 3已作废→cancelled
- 幂等：订单按 inbound_no 唯一键 upsert（已存在只更新企迈侧字段，local_status 仅在本地仍 pending 时联动）；
  明细仅当该订单本地无明细时回填
- 层级③④名称匹配到的仓编码，若该店 cangkuid 缺失则回填（不覆盖已有值）
"""
import hmac, hashlib, base64, time, random, json, sys, datetime, re
from urllib.parse import quote
import urllib.request
import pymysql

OPEN_ID = "71fcea7abc9709d653693116410b5385"
GRANT_CODE = "WaGI2rqy9b"
OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ"
BASE = "https://openapi.qmai.cn"

DB = dict(host="162.14.122.80", port=3306, user="store_inventory",
          password="Xzcpc@2026", database="store_inventory")

INBOUND_DAYS = int(sys.argv[1]) if len(sys.argv) > 1 else 30
DECLARE_DAYS = int(sys.argv[2]) if len(sys.argv) > 2 else 60
OUT_FILE = "tmp_inbound_backfill.sql"

DTF = "%Y-%m-%d %H:%M:%S"
_now = datetime.datetime.now()


def call(url, params, retry=3):
    last = None
    for i in range(retry):
        ts = int(time.time()); nonce = random.randint(10000, 99999)
        sorted_str = "grantCode=%s&nonce=%s&openId=%s&timestamp=%s" % (GRANT_CODE, nonce, OPEN_ID, ts)
        restored = quote(sorted_str, safe='').replace("%3D", "=").replace("%26", "&")
        tok = base64.b64encode(hmac.new(OPEN_KEY.encode(), restored.encode(), hashlib.sha1).digest()).decode()
        body = {"openId": OPEN_ID, "grantCode": GRANT_CODE, "nonce": nonce,
                "timestamp": ts, "token": quote(tok, safe=''), "params": params}
        req = urllib.request.Request(url, data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode())
        except Exception as e:
            last = e; time.sleep(1.5)
    raise RuntimeError("call failed %s: %s" % (url, last))


def pull_inbounds(start, end):
    out = []
    for page in range(1, 60):
        resp = call(BASE + "/v3/scm/order/inbound/order/list",
                    {"createdStartAt": start, "createdEndAt": end, "pageNo": page, "pageSize": 1000})
        data = resp.get("data") or {}
        recs = data.get("records") if isinstance(data, dict) else data
        if isinstance(data, dict) and recs is None:
            recs = data.get("data")
        if not recs:
            break
        out.extend(recs)
        if page % 5 == 0:
            print("  入库单已拉 %d 页 / %d 条" % (page, len(out)))
        if len(recs) < 1000:
            break
    return out


def pull_declare_keys(qmai_store_id, start, end):
    """一家门店近 N 天报货单 → (归属键集合, 本店仓库编码集合)"""
    keys = set()
    whs = set()
    for page in range(1, 10):
        resp = call(BASE + "/v3/newPattern/scmApiserver/post/declare/order/list",
                    {"storeIdList": [qmai_store_id],
                     "createdStartAt": start, "createdEndAt": end,
                     "pageNo": page, "pageSize": 50, "statusList": [3, 4], "payStatusList": [1, 2]})
        data = resp.get("data") or {}
        recs = data.get("data") if isinstance(data, dict) else None
        if not recs:
            break
        for r in recs:
            for f in ("declareNo", "requireNo", "bizNo"):
                if r.get(f):
                    keys.add(str(r[f]))
            for f in ("requireNoList", "purchaseApplyNoList", "purchaseNoList", "bizNoList"):
                for v in (r.get(f) or []):
                    if v:
                        keys.add(str(v))
            if r.get("storeWarehouseNo"):
                whs.add(str(r["storeWarehouseNo"]))
        if len(recs) < 50:
            break
    return keys, whs


def esc(v):
    if v is None:
        return "NULL"
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def num(v):
    try:
        return float(v) if v not in (None, "") else 0.0
    except (TypeError, ValueError):
        return 0.0


def fen2yuan(v):
    x = num(v)
    return round(x / 100.0, 2) if x > 0 else 0.0


def norm(s):
    """归一化：去括号（全角/半角）、空白（含全角空格）"""
    return re.sub(r"[()（）\s　]", "", s or "")


def stem_match(a, b):
    """归一化后一方以另一方开头/结尾（毕节店↔毕节招商花园店），较短方长度≥2 才命中"""
    if a == b:
        return True
    longer, shorter = (a, b) if len(a) >= len(b) else (b, a)
    if len(shorter) < 2:
        return False
    return longer.startswith(shorter) or longer.endswith(shorter)


LOCAL_STATUS = {1: "pending", 2: "done"}


def main():
    s0 = _now - datetime.timedelta(days=INBOUND_DAYS)
    s1 = _now - datetime.timedelta(days=DECLARE_DAYS)
    inb_start = s0.strftime(DTF); inb_end = _now.strftime(DTF)
    dec_start = s1.strftime(DTF); dec_end = _now.strftime(DTF)
    print("入库窗口: %s ~ %s" % (inb_start, inb_end))
    print("报货匹配窗口: %s ~ %s" % (dec_start, dec_end))

    # 1. 门店列表（生产库只读）
    conn = pymysql.connect(**DB, charset="utf8mb4")
    cur = conn.cursor()
    cur.execute("SELECT store_id, qmai_store_id, store_name, cangkuid FROM store_info WHERE del_flag=0")
    stores = [(r[0], r[1], r[2], r[3]) for r in cur.fetchall()]
    conn.close()
    print("门店 %d 家（有 qmai_store_id %d 家，有 cangkuid %d 家）" % (
        len(stores),
        sum(1 for s in stores if s[1]),
        sum(1 for s in stores if s[3])))

    # 2. 归属键映射 bizKey -> storeId + 本店仓库编码集合（层级1/2：按 qmai_store_id 拉报货单）
    key2store = {}
    wh2store = {}
    declare_wh_by_store = {}    # sid -> 该店报货单里出现的仓库编码集合（cangkuid 回填用）
    fail = 0
    for i, (sid, qid, _, _) in enumerate(stores):
        if not qid:
            continue
        try:
            keys, whs = pull_declare_keys(qid, dec_start, dec_end)
            for k in keys:
                key2store.setdefault(k, sid)
            for w in whs:
                wh2store.setdefault(w, sid)
            if whs:
                declare_wh_by_store.setdefault(sid, set()).update(whs)
        except Exception as e:
            fail += 1
            print("  门店 %s 报货单拉取失败: %s" % (sid, e))
        if (i + 1) % 20 == 0:
            print("  归属键收集进度 %d/%d，键 %d 个 / 仓库 %d 个" % (i + 1, len(stores), len(key2store), len(wh2store)))
        time.sleep(0.1)
    print("归属键 %d 个，报货仓库编码 %d 个（失败门店 %d）" % (len(key2store), len(wh2store), fail))

    # 3. 拉入库单
    print("拉取入库单（9.2.2）...")
    inbounds = pull_inbounds(inb_start, inb_end)
    print("入库单总数 %d 条" % len(inbounds))

    # 4. 过滤类型 1/3 + 四级归属匹配
    #    ① bizNo ∈ 报货单键集（qmai_store_id 拉的）
    #    ② warehouseCode ∈ 本店仓库编码集（报货单 storeWarehouseNo ∪ store_info.cangkuid）
    #    ③ 归一化仓库名 = 门店名（去括号/空白）
    #    ④ 归一化后首尾包含（毕节店↔毕节招商花园店），需唯一候选
    store_by_id = {s[0]: s for s in stores}
    ck2store = {s[3]: s for s in stores if s[3]}           # cangkuid -> store 行
    name2store = {}
    for s in stores:
        name2store.setdefault(norm(s[2]), s)               # 归一化店名 -> store 行
    order_rows = {}     # inboundNo -> (storeRow, rec, tier) 去重
    tier_stat = [0, 0, 0, 0]
    unmatched = []

    def assign(r, srow, tier):
        order_rows.setdefault(r.get("inboundNo"), (srow, r, tier))
        tier_stat[tier] += 1

    for r in inbounds:
        if r.get("inboundType") not in (1, 3):
            continue
        biz = r.get("bizNo")
        if biz and str(biz) in key2store:
            assign(r, store_by_id[key2store[str(biz)]], 0)
            continue
        wc = r.get("warehouseCode")
        if wc and wc in wh2store:
            assign(r, store_by_id[wh2store[wc]], 1)
            continue
        if wc and wc in ck2store:
            assign(r, ck2store[wc], 1)
            continue
        wn = norm(r.get("warehouseName"))
        if wn:
            if wn in name2store:
                assign(r, name2store[wn], 2)
                continue
            cands = [s for s in stores if s[2] and stem_match(wn, norm(s[2]))]
            if len(cands) == 1:
                assign(r, cands[0], 3)
                continue
        unmatched.append(r)
    orders = list(order_rows.values())
    print("类型1/3 匹配: 层级1(bizNo) %d / 层级2(仓库) %d / 层级3(名称) %d / 层级4(模糊) %d；未匹配 %d 条" % (
        tier_stat[0], tier_stat[1], tier_stat[2], tier_stat[3], len(unmatched)))
    for r in unmatched[:20]:
        print("  未匹配样例: %s type=%s warehouseName=%s warehouseCode=%s bizNo=%s" % (
            r.get("inboundNo"), r.get("inboundType"), r.get("warehouseName"),
            r.get("warehouseCode"), r.get("bizNo")))

    # 5. 生成 SQL
    lines = [
        "-- 入库单一次性回填（tmp_inbound_backfill.py 生成）",
        "-- 窗口：入库 %d 天 / 报货匹配 %d 天；生成时间 %s" % (INBOUND_DAYS, DECLARE_DAYS, _now.strftime(DTF)),
        "-- 幂等：可重复执行。订单按 inbound_no upsert（local_status 仅本地 pending 时联动）；",
        "--       明细仅当该订单本地无明细时回填。金额已分→元。",
        "SET NAMES utf8mb4;",
        "",
    ]
    o_count = i_count = 0
    cangkuid_fix = set()   # (store_id, warehouseCode) 去重：名称匹配到店的仓编码回填 store_info.cangkuid
    for srow, r, tier in orders:
        inbound_no = r.get("inboundNo")
        if not inbound_no:
            continue
        sid, qid = srow[0], srow[1]
        amt = fen2yuan(r.get("amount"))
        st = r.get("status")
        local = LOCAL_STATUS.get(st, "cancelled")
        cols = ("store_id, qmai_store_id, warehouse_id, warehouse_code, warehouse_name, inbound_no, biz_no,"
                " source_require_no, inbound_at, inbound_type, status, amount, product_all_num, product_type_num,"
                " creator, inbound_person, remark, created_at_qmai, local_status")
        vals = "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s" % (
            esc(sid), qid or "NULL", esc(r.get("warehouseId")), esc(r.get("warehouseCode")),
            esc(r.get("warehouseName")), esc(inbound_no), esc(r.get("bizNo")),
            esc(r.get("bizNo")), esc(r.get("inboundAt")), r.get("inboundType") or "NULL",
            st or "NULL", "%.2f" % amt, "%.4f" % num(r.get("productAllNum")),
            r.get("productTypeNum") or "NULL",
            esc(r.get("creator")), esc(r.get("inboundPerson")), esc(r.get("remark")),
            esc(r.get("createdAt")), esc(local))
        upd = ("qmai_store_id=VALUES(qmai_store_id), warehouse_id=VALUES(warehouse_id),"
               " warehouse_code=VALUES(warehouse_code), warehouse_name=VALUES(warehouse_name),"
               " inbound_at=VALUES(inbound_at), inbound_type=VALUES(inbound_type), status=VALUES(status),"
               " amount=VALUES(amount), product_all_num=VALUES(product_all_num),"
               " product_type_num=VALUES(product_type_num), creator=VALUES(creator),"
               " inbound_person=VALUES(inbound_person), remark=VALUES(remark),"
               " local_status=IF(local_status='pending', VALUES(local_status), local_status)")
        lines.append("INSERT INTO inbound_order (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s;" % (cols, vals, upd))
        o_count += 1
        # 层级3/4 名称匹配：仓库名即本店仓库 → 该店 cangkuid 缺失时回填（不覆盖已有值）
        if tier >= 2 and r.get("warehouseCode"):
            cangkuid_fix.add((sid, str(r.get("warehouseCode"))))

        items = r.get("inboundProductList") or []
        for p in items:
            code = p.get("productCode")
            if not code:
                continue
            pnum = num(p.get("inboundNum")) or num(p.get("num"))
            price = fen2yuan(p.get("inboundPrice")) or fen2yuan(p.get("costPrice"))
            it_vals = ("'%s',%s,%s,%s,%s,%.2f,%.2f,%.2f,0,0" % (
                code.replace("'", "''"), p.get("productId") or "NULL",
                esc(p.get("productName")), esc(p.get("productSpec")), esc(p.get("productUnit")),
                pnum, price, round(pnum * price, 2)))
            lines.append(
                "INSERT INTO inbound_order_item"
                " (inbound_order_id, product_code, product_id, product_name, product_spec,"
                "  product_unit, product_num, price, amount, received_qty, received)"
                " SELECT o.id, %s FROM inbound_order o"
                " WHERE o.inbound_no = %s AND NOT EXISTS (SELECT 1 FROM inbound_order_item i WHERE i.inbound_order_id = o.id);"
                % (it_vals, esc(inbound_no)))
            i_count += 1
        if o_count % 500 == 0:
            print("  已生成 %d 单 / %d 明细" % (o_count, i_count))

    # 6. cangkuid 缺失回填（幂等：仅当本地 cangkuid 为空时写入）
    #    来源：a) 名称匹配到的仓编码；b) 报货单 storeWarehouseNo（该店唯一仓库时）
    for sid, wc in sorted(cangkuid_fix):
        lines.append(
            "UPDATE store_info SET cangkuid=%s WHERE store_id=%s AND del_flag=0 AND (cangkuid IS NULL OR cangkuid='');"
            % (esc(wc), esc(sid)))
    cangkuid_miss = {s[0]: s for s in stores if not s[3]}   # 本地 cangkuid 缺失的门店
    for sid in sorted(set(declare_wh_by_store) & set(cangkuid_miss)):
        whs = declare_wh_by_store[sid]
        if len(whs) == 1 and (sid, list(whs)[0]) not in cangkuid_fix:
            wc = list(whs)[0]
            lines.append(
                "UPDATE store_info SET cangkuid=%s WHERE store_id=%s AND del_flag=0 AND (cangkuid IS NULL OR cangkuid='');"
                % (esc(wc), esc(sid)))
            cangkuid_fix.add((sid, wc))
    print("cangkuid 缺失回填: %d 家门店" % len(cangkuid_fix))

    with open(OUT_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("完成：%d 单 / %d 明细 → %s" % (o_count, i_count, OUT_FILE))
    print("执行方式（在生产库）：mysql -h162.14.122.80 -ustore_inventory -p store_inventory --default-character-set=utf8mb4 < tmp_inbound_backfill.sql")


if __name__ == "__main__":
    main()
