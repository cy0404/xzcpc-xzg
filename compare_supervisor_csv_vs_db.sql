-- ============================================================
-- CSV 数据 vs supervisor_store_access 表对比分析
-- CSV 文件: _SELECT_d_store_name_d_record_id_c_region_name_b_supervisor_name_202607311512.csv
-- 生成时间: 2026-07-31 16:01:43
-- CSV 总行数: 161
-- ============================================================

-- 步骤1: 创建临时表存放 CSV 数据
DROP TEMPORARY TABLE IF EXISTS tmp_csv_data;
CREATE TEMPORARY TABLE tmp_csv_data (
    store_name    VARCHAR(200),
    store_id      VARCHAR(50),
    region_name   VARCHAR(100),
    admin_name    VARCHAR(100),
    PRIMARY KEY (store_id)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 步骤2: 插入 CSV 数据INSERT INTO tmp_csv_data VALUES ('象子茶铺茶天骄北麓店', 'cmpnw036o02ee3pk3oxese1bg', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶新平店', 'cmpnw037902ej3pk3w57xd3rn', '玉溪', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶砚山七都广场店', 'cmpnw039202ey3pk309hgy8oe', '文山', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶长水机场店', 'cmpnw039p02f33pk3o3a4hwqg', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶王府井滇池小镇店', 'cmpnw036202e93pk3o7c1i0ck', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶保山板桥镇店', 'cmpnw035h02e43pk3oqdqnq7l', '保山', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶丽江七星街店', 'cmpnw033k02dp3pk3iycpwe1e', '丽江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶楚雄大姚店', 'cmpnw032y02dk3pk3dp4idgbw', '楚雄', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶晋宁嘉誉广场店', 'cmpnw032b02df3pk3g1lqinad', '昆明周边', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶宾川店', 'cmpnw031n02da3pk3n72nhcqb', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶南华店', 'cmpnw030u02cz3pk3utnkoy2g', '楚雄', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶陇川店', 'cmpnw02zy02cq3pk3oyu6gvnq', '德宏', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶姚安店', 'cmpnw02yx02cf3pk3p2hnbdrd', '楚雄', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶砚山嘉禾路店', 'cmpnw02xq02c63pk3bmaulhiv', '文山', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶果林广场店', 'cmpnw02ua02bd3pk3gwbzbqpg', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶绿春店', 'cmpnw02ta02b83pk3j51aqeg4', '红河', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶陆良店', 'cmpnw02kp029a3pk3a7ze7gvj', '曲靖', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶保山东城店', 'cmpnw02so02b33pk394rfjlf7', '保山', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶墨江店', 'cmpnw02s102ay3pk3eiqqqqbw', '普洱', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶河口店', 'cmpnw02qq02ao3pk3dc923f3o', '红河', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶塘子巷店', 'cmpnw02pf02ae3pk3eamh9p1b', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶师宗店', 'cmpnw02ov02a93pk3tuad001a', '曲靖', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶宣威店', 'cmpnw02lb029f3pk3c1k2db61', '曲靖', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶文山光大店', 'cmp3hkfh301we3ps1xi1nqwvj', '文山', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶永平县店', 'cmp3hkfi901wo3ps1gw3nxcwl', '保山', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶宜宾唐人财富中心店', 'cmp3hkfip01wt3ps1ald5dyrg', '贵州/四川', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶北辰财富中心店', 'cmp3hkfj701wy3ps118v7sky9', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶石屏店', 'cmp3hkfk701x83ps1w3301jiz', '红河', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶晋城区店', 'cmp3hkhsu01xu3ps1f5eu1z76', '昆明周边', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶澄江店', 'cmp3hkhvr01ye3ps124g3hhkk', '玉溪', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶银海尚御店', 'cmp3hkhwq01yo3ps1f058kss7', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶镇康南伞店', 'cmp3hkhxa01yt3ps1buu08pso', '临沧', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶西山区好悦天地店', 'cmp4vo7j1023g3ps1m5qlimmo', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶丽江古城店', 'cmp4vo7kp023l3ps1nocqjwzu', '丽江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶翠湖店', 'cmpaoctij00az3pmbfl6c71mz', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶施甸交通路店', 'cmpaoctjb00b43pmbntacy4bi', '保山', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶海乐达人汇店', 'cmpaoctk400b93pmbd8v3zj4k', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶福德店', 'cmpaoctko00be3pmbvpeiogtc', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶富民彩玉国际店', 'cmpaoctl800bj3pmby8x67mqu', '昆明周边', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶芒市三棵树店', 'cmpaoctlq00bo3pmbh7n6te7r', '德宏', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶南亚风情店', 'cmpaoctm800bt3pmbdo8azaqf', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶翰林大观店', 'cmpaoctmr00by3pmb20hdtxl5', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶保山吾悦店', 'cmpaoctn900c33pmb5al74m4p', '保山', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶楚雄丹麓旗舰店', 'cmpaoctnv00c83pmbflyt9mel', '楚雄', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶大理泰业店', 'cmpaoctod00cd3pmbnux3srjj', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶沧源店', 'cmpaoctou00ci3pmbjbfguo5o', '临沧', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶怒江西岸店', 'cmpaoctpd00cn3pmbvufgxp8s', '怒江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶易门店', 'cmpaoctpv00cs3pmbhizaelfq', '玉溪', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶高山铺店', 'cmpaoctqy00cx3pmb43nouafn', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶仕林街店', 'cmpaoctrs00d23pmb0nygzz46', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶昌宁店', 'cmpaocts900d73pmbehtktmhz', '德宏', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶弥勒金辰店', 'cmpaoctsq00dc3pmbhjnibzpd', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶瑞丽翡翠园店', 'cmpaoctt800dh3pmbb51j6wx1', '德宏', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶弥渡店', 'cmpaoctu600dr3pmbzw08uw77', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶凤仪店', 'cmpaoctur00dw3pmb570eobo5', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶新亚洲店', 'cmpaoctv800e13pmbw3w2ahxu', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶呈贡七彩云南第壹城店', 'cmpaoctvq00e63pmbobapy64w', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶施甸肆方街店', 'cmpaoctw700eb3pmb3e66xj14', '保山', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶龙陵盛和雅苑店', 'cmpaoctww00eg3pmbwq58ot35', '保山', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶禄劝店', 'cmpaoctxf00el3pmbikfhxjir', '昆明周边', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶大理正阳店', 'cmpaoctxw00eq3pmb4w2lilg3', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶景谷店', 'cmpaoctyj00ev3pmbo3dxzrm1', '普洱', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶弥勒印象街店', 'cmpaoctz100f03pmbuyaf4h73', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶梁河店', 'cmpaoctzm00f53pmbinxvl6p2', '德宏', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶楚雄牟定店', 'cmpaocu0700fa3pmba47qeuxb', '楚雄', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶华坪店', 'cmpaocu0o00ff3pmblc7bap7l', '丽江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶南涧店', 'cmpaocu1600fk3pmb6h3e7ntb', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶江川店', 'cmpaocu1n00fp3pmbbutnsku1', '玉溪', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶世纪金源店', 'cmpaocu2b00fu3pmbwf04mo09', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶澜沧店', 'cmpaocu3200fz3pmbwrlc4uk8', '普洱', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶兰坪店', 'cmpaocu4l00g93pmb0r3zeqjk', '怒江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶滇池名门店', 'cmpaocu5500ge3pmb70lbe792', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶巍山店', 'cmpaocu5t00gj3pmbqt5rdgbz', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶镇沅店', 'cmpaocu6d00go3pmbf779nx4d', '普洱', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶保山五洲店', 'cmpaocu6y00gt3pmbzzje05ni', '保山', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶怒江东岸人民路店', 'cmpaocu7f00gy3pmbqoqoh5y7', '怒江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶保山三馆店', 'cmpaocu7x00h33pmb9g8fut1r', '保山', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶宜良店', 'cmpaocu8g00h83pmbefwlwve4', '昆明周边', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶盘州店', 'cmpaocu8x00hd3pmbrjv6qg5p', '贵州/四川', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶福保店', 'cmpaocu9f00hi3pmbmpvq836q', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶中铁云时代店', 'cmpaocua000hn3pmbe8m4pdif', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶勐海店', 'cmpaocub300hx3pmb9n0gis5s', '版纳', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶瑞鼎城店', 'cmpaocubk00i23pmb3bxmyoqe', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶大理市天街店', 'cmpaocuc800i73pmbe87g92xz', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶云县店', 'cmpaocucp00ic3pmb3l3cgc41', '临沧', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶新迎新城店', 'cmpaocud700ih3pmbe6opynv0', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶瑞丽彩云城店', 'cmpaocudp00im3pmbs3q0p529', '德宏', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶六盘水店', 'cmpaocue900ir3pmbzef9yzuu', '贵州/四川', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶双江店', 'cmpaocueq00iw3pmbhzkhlsbx', '临沧', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶呈贡第七街区店', 'cmpaocufc00j13pmb4dk5xbor', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶华宁店', 'cmpaocufu00j63pmbaoig7krk', '玉溪', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶普洱悦城店', 'cmpaocugb00jb3pmbcnsj4kjf', '普洱', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶丘北店', 'cmpaocugt00jg3pmbibe1oo1b', '文山', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶腾冲天成店', 'cmpaocuhb00jl3pmb99zr2r3n', '德宏', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶紫金中心店', 'cmpaocuht00jq3pmbwgtosfb2', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶恒基广场店', 'cmpaocuir00k03pmbboxmssqg', '临沧', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶丽江玉龙店', 'cmpaocuj800k53pmbx1fw8o4z', '丽江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶丽江祥和店', 'cmpaocujq00ka3pmb2kpxvvdz', '丽江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶祥云城南店', 'cmpaocuk700kf3pmb75e9s0ih', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶丽江白沙古镇店', 'cmpaocuku00kk3pmbpji33nqr', '丽江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶曲靖万达店', 'cmpaoculc00kp3pmbkblhlnyn', '曲靖', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶大理古城店', 'cmpaocult00ku3pmbgqmu7w7f', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶安宁金色时代店', 'cmpaocuma00kz3pmbltedt7om', '昆明周边', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶万宏国际店', 'cmpaocun900l93pmbczh07a1b', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶吴井路店', 'cmpaocunp00le3pmbgrv5o5bi', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶宜宾长宁宏泰财富广场店', 'cmpaocuo600lj3pmbb6uukpqx', '贵州/四川', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶昭通实验中学店', 'cmpaocuoo00lo3pmbiwyeakdk', '昭通', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶元谋店', 'cmpaocupa00lt3pmb04eg3beb', '楚雄', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶大理剑川店', 'cmpaocupr00ly3pmbol801znp', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶丽江束河古镇店', 'cmpaocuq800m33pmbkr999ukh', '丽江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶凤庆店', 'cmpaocuqp00m83pmbu48evshq', '临沧', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶曲靖会泽店', 'cmpaocuro00mi3pmb3v5u3nc5', '曲靖', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶柏联广场店', 'cmpaocus500mn3pmbzfw9mhi3', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶昭通合景广场店', 'cmpaocusn00ms3pmbwzu4qg1q', '昭通', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶普洱孟连店', 'cmpaocuu600n73pmbx2efwhxa', '普洱', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶保山潞江坝镇', 'cmpaocuup00nc3pmbasprkdjp', '保山', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶大理满江店', 'cmpaocuv600nh3pmbcy9pm54g', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶大理洱源店', 'cmpaocuvo00nm3pmbep491ola', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶大理云龙店', 'cmpaocuw600nr3pmbkcpch2s6', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶瑞丽德龙珠宝城', 'cmpaocuwn00nw3pmbnlkjavfz', '德宏', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶普洱创基店', 'cmpaocux500o13pmb5yog1s1l', '普洱', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶曲靖花柯店', 'cmpaocuze00ol3pmbq78thom9', '曲靖', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶广南店', 'cmpaocuy400ob3pmb6mzv6ppu', '文山', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶丽江旅游学院店', 'cmpaocuzx00oq3pmbe7yvn9gg', '丽江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶孟定店', 'cmp4vo7mu02403ps1hxrjujb5', '临沧', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶建水小桂湖店', 'cmp4vo7lg023q3ps10d4jevol', '红河', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶红河泸西店', 'cmpw8zu8800u23pq35b0q7zdn', '红河', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶大理古城二店', 'cmpw9a8it00un3pq3epyrnye5', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶漾濞店', 'cmpw9ltj400x73pq39uqttify', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶南屏街世纪广场店', 'cmpwa93tr010v3pq3hzddyxs2', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶大理泰业二店', 'cmpwamro401283pq3c3z6t97p', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶广福路爱琴海店', 'cmpwau8kz013n3pq322wypoqt', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶昭通金池店', 'cmpway27w014d3pq3jvhup4pb', '昭通', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶长水机场到达厅', 'cmpwcym7p01693pq3ps61q1rz', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶长水机场卫星厅', 'cmpwcx3oo015k3pq3bivxc6e0', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶曲靖沾益西正街店', 'cmpnw02lw029k3pk311bekjaz', '曲靖', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶东川店', 'cmpnw02j802903pk30xpbgnq3', '昆明周边', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶丽江永胜店', 'cmpnw034502du3pk3aodryu7j', '丽江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶昆明建工新城店', 'cmpwa91ya010f3pq343o6pqxk', '直营店', '罗正彩');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶楚雄彝人古镇店', 'cmpnw02wq02bv3pk3ushlro0s', '楚雄', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶贵州望谟店', 'cmpnw02oa02a43pk35j04jbjg', '贵州/四川', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶蒙自南湖荟店', 'cmpw9lsb300wz3pq3ez6f1dju', '红河', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶香格里拉店', 'cmpaocur700md3pmbhxdfsoa3', '丽江', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶官渡古镇店', 'cmpw9d9jm00vc3pq3weyoug12', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶祥云印象花园店', 'cmpaocttp00dm3pmbysze7vef', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶黔西南兴义梦乐城店', 'cmpw9lqu300wr3pq33cog9iq9', '贵州/四川', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶西双版纳景洪曼城店', 'cmpaocut300mx3pmblfjyarxm', '版纳', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶昆明财大龙泉路店', 'cmpwa90gt01073pq3uw60hg23', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶德宏盈江店', 'cmpnw037w02eo3pk3obt1bzg8', '德宏', '董建明');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶富康城店', 'cmpnw02n3029u3pk3mx0vr247', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶园西路店', 'cmpnw02np029z3pk3olncxkvk', '昆明', '马迎峰');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶西双版纳景洪大润发店', 'cmpaocutk00n23pmbamimpsnw', '版纳', '唐敏');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶大理机场店', 'cmrj0npkj07hx3pq3w8ld6q5h', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶楚雄彝海公园店', 'cmpnw02vr02bm3pk3tm33dbe0', '楚雄', '李婉晴');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶景东店', 'cmp4vo7m5023v3ps1e5k9eru0', '普洱', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶临沧中骏世界城店', 'cmpnw02q202aj3pk3i0a7084x', '临沧', '李淑仪');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶开远店', 'cmpnw02jz02953pk3xu4hmv35', '红河', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶玉溪峨山店', 'cmpnw038h02et3pk3zi3iwbv1', '玉溪', '杨艳华');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶鹤庆店', 'cmpaocuai00hs3pmbc3ta1d36', '大理', '蒋忠康');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶贵州黔西文峰路店', 'cmpnw02rb02at3pk3xs15zeg9', '贵州/四川', '李靖');
INSERT INTO tmp_csv_data VALUES ('象子茶铺茶毕节招商花园店', 'cmpnw02mh029p3pk3huw4beqc', '贵州/四川', '李靖');

-- ============================================================
-- 步骤3: CSV 有但数据库没有的（需新增）
-- ============================================================
SELECT 'NEW' AS change_type, c.store_id, c.store_name, c.admin_name, c.region_name
FROM tmp_csv_data c
LEFT JOIN supervisor_store_access s ON s.store_id = c.store_id AND s.admin_name = c.admin_name AND s.del_flag = 0
WHERE s.id IS NULL
ORDER BY c.admin_name, c.store_name;

-- ============================================================
-- 步骤4: 数据库有但 CSV 没有的（需删除/标记）
-- ============================================================
SELECT 'REMOVED' AS change_type, s.store_id, s.store_name, s.admin_name
FROM supervisor_store_access s
LEFT JOIN tmp_csv_data c ON c.store_id = s.store_id AND c.admin_name = s.admin_name
WHERE s.del_flag = 0 AND c.store_id IS NULL
ORDER BY s.admin_name, s.store_name;

-- ============================================================
-- 步骤5: store_id 相同但 admin_name 不同（督导变更）
-- ============================================================
SELECT 'SUPERVISOR_CHANGED' AS change_type,
       c.store_id, c.store_name, c.region_name,
       s.admin_name AS old_supervisor,
       c.admin_name AS new_supervisor
FROM tmp_csv_data c
JOIN supervisor_store_access s ON s.store_id = c.store_id AND s.del_flag = 0
WHERE s.admin_name != c.admin_name
ORDER BY c.admin_name, c.store_name;

-- ============================================================
-- 步骤6: 汇总统计（拆为独立查询，避免临时表重复引用限制）
-- ============================================================
SELECT 'CSV总记录' AS metric, COUNT(*) AS count FROM tmp_csv_data;
SELECT '数据库总记录' AS metric, COUNT(*) AS count FROM supervisor_store_access WHERE del_flag = 0;

SELECT '新增(CSV有DB无)' AS metric, COUNT(*) AS count
FROM tmp_csv_data c
LEFT JOIN supervisor_store_access s ON s.store_id = c.store_id AND s.admin_name = c.admin_name AND s.del_flag = 0
WHERE s.id IS NULL;

SELECT '删除(DB有CSV无)' AS metric, COUNT(*) AS count
FROM supervisor_store_access s
LEFT JOIN tmp_csv_data c ON c.store_id = s.store_id AND c.admin_name = s.admin_name
WHERE s.del_flag = 0 AND c.store_id IS NULL;

SELECT '督导变更(同店不同人)' AS metric, COUNT(*) AS count
FROM tmp_csv_data c
JOIN supervisor_store_access s ON s.store_id = c.store_id AND s.del_flag = 0
WHERE s.admin_name != c.admin_name;

-- ============================================================
-- 步骤7: 按督导统计 CSV 门店分布
-- ============================================================
SELECT c.admin_name, COUNT(*) AS store_count
FROM tmp_csv_data c
GROUP BY c.admin_name
ORDER BY store_count DESC;

-- ============================================================
-- ===== 以下为写入操作，请确认对比结果后再执行 =====
-- ============================================================

-- ============================================================
-- 费用+工时缺失检查（2026年7月）
-- ============================================================
SELECT
    c.admin_name AS 督导,
    b.store_name AS 门店,
    CASE WHEN sal.store_id IS NULL THEN '❌ 缺' ELSE '✅ 有' END AS 人员工资,
    CASE WHEN rent.store_id IS NULL THEN '❌ 缺' ELSE '✅ 有' END AS 租金成本,
    CASE WHEN wh.store_id IS NULL THEN '❌ 缺' ELSE '✅ 有' END AS 人员工时
FROM store_info b
INNER JOIN supervisor_store_access c ON c.store_id = b.store_id COLLATE utf8mb4_0900_ai_ci AND c.del_flag = 0
LEFT JOIN (
    SELECT DISTINCT store_id FROM expense_record
    WHERE type_name = '人员工资'
      AND occurred_date BETWEEN '2026-07-01' AND '2026-08-01'
) sal ON sal.store_id = b.store_id COLLATE utf8mb4_0900_ai_ci
LEFT JOIN (
    SELECT DISTINCT store_id FROM expense_record
    WHERE first_type_name = '租金成本'
      AND occurred_date BETWEEN '2026-07-01' AND '2026-08-01'
) rent ON rent.store_id = b.store_id COLLATE utf8mb4_0900_ai_ci
LEFT JOIN (
    SELECT DISTINCT store_id FROM store_work_hours
    WHERE record_time = '2026-07' AND del_flag = 0
) wh ON wh.store_id = b.store_id COLLATE utf8mb4_0900_ai_ci
WHERE sal.store_id IS NULL OR rent.store_id IS NULL OR wh.store_id IS NULL
ORDER BY c.admin_name, b.store_name;

-- 步骤9: 新增记录（CSV有、DB无）
-- ============================================================
INSERT INTO supervisor_store_access (open_id, admin_name, store_id, store_name)
SELECT ap.open_id, c.admin_name, c.store_id, c.store_name
FROM tmp_csv_data c
JOIN admin_permission ap ON ap.name = c.admin_name AND ap.del_flag = 0
LEFT JOIN supervisor_store_access s ON s.store_id = c.store_id AND s.admin_name = c.admin_name AND s.del_flag = 0
WHERE s.id IS NULL;

-- 检查是否有因 admin_permission 中找不到对应 name 而遗漏的记录
SELECT 'MISSING_OPENID' AS warn, c.admin_name, COUNT(*) AS store_count
FROM tmp_csv_data c
LEFT JOIN supervisor_store_access s ON s.store_id = c.store_id AND s.admin_name = c.admin_name AND s.del_flag = 0
LEFT JOIN admin_permission ap ON ap.name = c.admin_name AND ap.del_flag = 0
WHERE s.id IS NULL AND ap.id IS NULL
GROUP BY c.admin_name;

-- ============================================================
-- 步骤10: 督导变更 → 软删除旧记录（新记录已由步骤9插入）
-- ============================================================
UPDATE supervisor_store_access s
JOIN tmp_csv_data c ON c.store_id = s.store_id AND s.del_flag = 0
SET s.del_flag = 1
WHERE s.admin_name != c.admin_name;

-- ============================================================
-- 步骤11: 删除记录（DB有、CSV无）→ 软删除
-- ============================================================
UPDATE supervisor_store_access s
LEFT JOIN tmp_csv_data c ON c.store_id = s.store_id AND c.admin_name = s.admin_name
SET s.del_flag = 1
WHERE s.del_flag = 0 AND c.store_id IS NULL;

-- ============================================================
-- 步骤8: 清理临时表
-- ============================================================
DROP TEMPORARY TABLE IF EXISTS tmp_csv_data;