"""
初始化 admin_permission.user_id（一次性脚本）
================================================
背景：新门店接口(xinfo)的督导标识是飞书 user_id（企业内稳定ID），
本地库只有 open_id（自己应用维度），两者需通过飞书通讯录 API 建立映射。

流程：
1. 读 admin_permission 中 open_id 非空且 user_id 为空的记录
2. 飞书 API: GET /open-apis/contact/v3/users/{open_id}?user_id_type=open_id
   （把 open_id 填到路径参数，指定 user_id_type=open_id 即按 open_id 查询）
3. 生成 UPDATE SQL 到 admin_user_id_backfill.sql（默认），或 --apply 直接写库
4. 查不到的 open_id 输出清单，人工确认（离职/不在通讯录）

前置条件：
- 已执行 database/migration-add-supervisor-user-id.sql（admin_permission 有 user_id 列）
- 飞书应用有通讯录只读权限（contact:user.base:readonly）
- 飞书 app_id/app_secret 从 server/src/main/resources/application-prod.yml 的 feishu 配置复制

用法：
    python init_admin_user_id.py            # 只生成 SQL 文件 + 打印清单
    python init_admin_user_id.py --apply    # 直接回写数据库
"""
import os
import sys
import urllib.request
import urllib.parse
import urllib.error
import json
import pymysql

# ============ 数据库连接（同 match_qmai_stores.py） ============
DB_CONFIG = {
    'host': '162.14.122.80',
    'port': 3306,
    'user': 'store_inventory',
    'password': 'Xzcpc@2026',
    'database': 'store_inventory',
    'charset': 'utf8mb4',
}

# ============ 飞书应用配置（与 application.yml feishu 配置一致） ============
# 注意：FEISHU_APP_SECRET 为生产密钥，勿提交 git；跑完建议清空或改用环境变量注入
FEISHU_APP_ID = 'cli_aa90f3d66d7adbb7'
FEISHU_APP_SECRET = os.environ.get('FEISHU_APP_SECRET', 'wcQCAqZQML7YZn8h6vK0TdOy3t88fnP7')

OUTPUT_SQL = 'admin_user_id_backfill.sql'

# ============ 飞书 API ============

def get_tenant_token():
    req = urllib.request.Request(
        'https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal',
        data=json.dumps({'app_id': FEISHU_APP_ID, 'app_secret': FEISHU_APP_SECRET}).encode('utf-8'),
        headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = json.loads(resp.read().decode('utf-8'))
    if body.get('code') != 0:
        raise RuntimeError(f"获取飞书 token 失败: {body}")
    return body['tenant_access_token']


def openid_to_userid(token, open_id):
    """GET /open-apis/contact/v3/users/{user_id}?user_id_type=open_id → data.user.user_id"""
    encoded = urllib.parse.quote(open_id, safe='')
    url = f'https://open.feishu.cn/open-apis/contact/v3/users/{encoded}?user_id_type=open_id'
    req = urllib.request.Request(url, headers={'Authorization': f'Bearer {token}'})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        detail = e.read().decode('utf-8', errors='replace')[:200]
        return None, f'HTTP {e.code}: {detail}'
    if body.get('code') != 0:
        return None, body.get('msg')
    user = (body.get('data') or {}).get('user') or {}
    return user.get('user_id'), None


# ============ 主流程 ============

def main():
    if not FEISHU_APP_ID or not FEISHU_APP_SECRET:
        print('错误：请先在脚本顶部填写 FEISHU_APP_ID / FEISHU_APP_SECRET（见 application-prod.yml）')
        sys.exit(1)

    apply = '--apply' in sys.argv

    # 1. 读待回填记录
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor(pymysql.cursors.DictCursor)
    cursor.execute("""
        SELECT id, open_id, name, mobile
        FROM admin_permission
        WHERE del_flag = 0
          AND open_id IS NOT NULL AND open_id != ''
          AND (user_id IS NULL OR user_id = '')
        ORDER BY id
    """)
    rows = cursor.fetchall()
    print(f"待回填 user_id 的用户数: {len(rows)}")
    if not rows:
        cursor.close()
        conn.close()
        print('无需回填，结束。')
        return

    # 2. 飞书转换
    token = get_tenant_token()
    ok_list, fail_list, skip_list = [], [], []
    for r in rows:
        # 跳过非真实飞书 open_id（如本地测试用户 dev_admin_001），不占用请求
        if not r['open_id'].startswith('ou_'):
            skip_list.append((r['open_id'], r['name']))
            print(f"  - {r['name']:<8} {r['open_id']} 非真实open_id，跳过")
            continue
        uid, err = openid_to_userid(token, r['open_id'])
        if uid:
            ok_list.append((r['open_id'], uid, r['name']))
            print(f"  ✓ {r['name']:<8} {r['open_id']} → {uid}")
        else:
            fail_list.append((r['open_id'], r['name'], r['mobile'], err))
            print(f"  ✗ {r['name']:<8} {r['open_id']} 转换失败: {err}")

    print(f"\n转换成功 {len(ok_list)} 条，失败 {len(fail_list)} 条，跳过本地测试 {len(skip_list)} 条")

    # 3. 生成 SQL / 直接执行
    lines = [f"UPDATE admin_permission SET user_id = '{uid}' WHERE open_id = '{oid}' AND del_flag = 0;"
             for oid, uid, _ in ok_list]
    if apply:
        for oid, uid, _ in ok_list:
            cursor.execute(
                "UPDATE admin_permission SET user_id = %s WHERE open_id = %s AND del_flag = 0",
                (uid, oid))
        conn.commit()
        print(f"已直接回写数据库 {len(ok_list)} 条")
    else:
        with open(OUTPUT_SQL, 'w', encoding='utf-8') as f:
            f.write('\n'.join(lines) + '\n')
        print(f"SQL 已写入 {OUTPUT_SQL}，执行后 user_id 即完成回填")
        print('  （也可加 --apply 直接写库，无需人工执行 SQL）')

    # 4. 失败清单
    if fail_list:
        print(f"\n========== 转换失败清单（需人工确认） ==========")
        for oid, name, mobile, err in fail_list:
            print(f"  {name:<8} open_id={oid}  mobile={mobile or '-'}  {err}")

    cursor.close()
    conn.close()


if __name__ == '__main__':
    main()
