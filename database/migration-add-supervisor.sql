-- =============================================================
-- 门店督导归属回写脚本（依据《7月督导区域划分.xlsx》）
-- 生成方式：Excel 简称 → store_info.store_name 匹配（含别字/多候选人工确认）
-- 影响门店数：149
-- 执行前请先备份 store_info
-- =============================================================

-- 1) 新增督导字段（若已存在请注释掉本段）
ALTER TABLE store_info
    ADD COLUMN supervisor_name VARCHAR(50) DEFAULT NULL COMMENT '督导（月度区域划分，本地维护）' AFTER owner_phone;

-- 2) 按门店回写督导
-- 督导：马迎峰（16 家）
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpaocu2b00fu3pmbwf04mo09';  -- 象子茶铺茶世纪金源店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpaocua000hn3pmbe8m4pdif';  -- 象子茶铺茶中铁云时代店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmp3hkfj701wy3ps118v7sky9';  -- 象子茶铺茶北辰财富中心店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpaocunp00le3pmbgrv5o5bi';  -- 象子茶铺茶吴井路店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpaocufc00j13pmb4dk5xbor';  -- 象子茶铺茶呈贡第七街区店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpnw02np029z3pk3olncxkvk';  -- 象子茶铺茶园西路店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpnw036o02ee3pk3oxese1bg';  -- 象子茶铺茶天骄北麓店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpnw02n3029u3pk3mx0vr247';  -- 象子茶铺茶富康城店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpnw02ua02bd3pk3gwbzbqpg';  -- 象子茶铺茶果林广场店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpaocus500mn3pmbzfw9mhi3';  -- 象子茶铺茶柏联广场店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpaoctk400b93pmbd8v3zj4k';  -- 象子茶铺茶海乐达人汇店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpnw036202e93pk3o7c1i0ck';  -- 象子茶铺茶王府井滇池小镇店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpaocubk00i23pmb3bxmyoqe';  -- 象子茶铺茶瑞鼎城店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmpaocuht00jq3pmbwgtosfb2';  -- 象子茶铺茶紫金中心店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmp4vo7j1023g3ps1m5qlimmo';  -- 象子茶铺茶西山区好悦天地店
UPDATE store_info SET supervisor_name='马迎峰' WHERE store_id='cmp3hkhwq01yo3ps1f058kss7';  -- 象子茶铺茶银海尚御店

-- 督导：李婉晴（16 家）
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpnw02j802903pk30xpbgnq3';  -- 象子茶铺茶东川店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpaocupa00lt3pmb04eg3beb';  -- 象子茶铺茶元谋店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpnw030u02cz3pk3utnkoy2g';  -- 象子茶铺茶南华店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpnw02yx02cf3pk3p2hnbdrd';  -- 象子茶铺茶姚安店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmqrfhlir06nr3pq3kws5wfil';  -- 象子茶铺茶安宁吾悦店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpaocuma00kz3pmbltedt7om';  -- 象子茶铺茶安宁金色时代店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpaocu8g00h83pmbefwlwve4';  -- 象子茶铺茶宜良店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpaoctl800bj3pmby8x67mqu';  -- 象子茶铺茶富民彩玉国际店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmp3hkhsu01xu3ps1f5eu1z76';  -- 象子茶铺茶晋城区店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpnw032b02df3pk3g1lqinad';  -- 象子茶铺茶晋宁嘉誉广场店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpaoctnv00c83pmbflyt9mel';  -- 象子茶铺茶楚雄丹麓旗舰店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpnw032y02dk3pk3dp4idgbw';  -- 象子茶铺茶楚雄大姚店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpnw02wq02bv3pk3ushlro0s';  -- 象子茶铺茶楚雄彝人古镇店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpnw02vr02bm3pk3tm33dbe0';  -- 象子茶铺茶楚雄彝海公园
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpaocu0700fa3pmba47qeuxb';  -- 象子茶铺茶楚雄牟定店
UPDATE store_info SET supervisor_name='李婉晴' WHERE store_id='cmpaoctxf00el3pmbikfhxjir';  -- 象子茶铺茶禄劝店

-- 督导：蒋忠康（18 家）
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaoctur00dw3pmb570eobo5';  -- 象子茶铺茶凤仪店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaocu1600fk3pmb6h3e7ntb';  -- 象子茶铺茶南涧店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaocuw600nr3pmbkcpch2s6';  -- 象子茶铺茶大理云龙店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaocupr00ly3pmbol801znp';  -- 象子茶铺茶大理剑川店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpw9a8it00un3pq3epyrnye5';  -- 象子茶铺茶大理古城二店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaocult00ku3pmbgqmu7w7f';  -- 象子茶铺茶大理古城店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaocuc800i73pmbe87g92xz';  -- 象子茶铺茶大理市天街店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaoctxw00eq3pmb4w2lilg3';  -- 象子茶铺茶大理正阳店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpwamro401283pq3c3z6t97p';  -- 象子茶铺茶大理泰业二店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaoctod00cd3pmbnux3srjj';  -- 象子茶铺茶大理泰业店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaocuvo00nm3pmbep491ola';  -- 象子茶铺茶大理洱源店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaocuv600nh3pmbcy9pm54g';  -- 象子茶铺茶大理满江店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpnw031n02da3pk3n72nhcqb';  -- 象子茶铺茶宾川店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaocu5t00gj3pmbqt5rdgbz';  -- 象子茶铺茶巍山店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaoctu600dr3pmbzw08uw77';  -- 象子茶铺茶弥渡店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaocttp00dm3pmbysze7vef';  -- 象子茶铺茶祥云印象花园店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaocuk700kf3pmb75e9s0ih';  -- 象子茶铺茶祥云城南店
UPDATE store_info SET supervisor_name='蒋忠康' WHERE store_id='cmpaocuai00hs3pmbc3ta1d36';  -- 象子茶铺茶鹤庆店

-- 督导：唐敏（16 家）
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpnw033k02dp3pk3iycpwe1e';  -- 象子茶铺茶丽江七星街店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmp4vo7kp023l3ps1nocqjwzu';  -- 象子茶铺茶丽江古城店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaocuq800m33pmbkr999ukh';  -- 象子茶铺茶丽江束河古镇店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpnw034502du3pk3aodryu7j';  -- 象子茶铺茶丽江永胜店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaocuj800k53pmbx1fw8o4z';  -- 象子茶铺茶丽江玉龙店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaocuku00kk3pmbpji33nqr';  -- 象子茶铺茶丽江白沙古镇店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaocujq00ka3pmb2kpxvvdz';  -- 象子茶铺茶丽江祥和店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaocu4l00g93pmb0r3zeqjk';  -- 象子茶铺茶兰坪店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaocub300hx3pmb9n0gis5s';  -- 象子茶铺茶勐海店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaocu0o00ff3pmblc7bap7l';  -- 象子茶铺茶华坪店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaocu7f00gy3pmbqoqoh5y7';  -- 象子茶铺茶怒江东岸人民路店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaoctpd00cn3pmbvufgxp8s';  -- 象子茶铺茶怒江西岸店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaocutk00n23pmbamimpsnw';  -- 象子茶铺茶版纳大润发店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaocut300mx3pmblfjyarxm';  -- 象子茶铺茶版纳曼城店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmpaocur700md3pmbhxdfsoa3';  -- 象子茶铺茶香格里拉店
UPDATE store_info SET supervisor_name='唐敏' WHERE store_id='cmqrfhlh406nh3pq3v0y32b94';  -- 象子茶铺茶香格里拉月光印象店

-- 督导：李淑仪（17 家）
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaocu7x00h33pmb9g8fut1r';  -- 象子茶铺茶保山三馆店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpnw02so02b33pk394rfjlf7';  -- 象子茶铺茶保山东城店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaocu6y00gt3pmbzzje05ni';  -- 象子茶铺茶保山五洲店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaoctn900c33pmb5al74m4p';  -- 象子茶铺茶保山吾悦店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpnw035h02e43pk3oqdqnq7l';  -- 象子茶铺茶保山板桥镇店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaocuup00nc3pmbasprkdjp';  -- 象子茶铺茶保山潞江坝镇
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpnw02s102ay3pk3eiqqqqbw';  -- 象子茶铺茶墨江店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaoctjb00b43pmbntacy4bi';  -- 象子茶铺茶施甸交通路店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaoctw700eb3pmb3e66xj14';  -- 象子茶铺茶施甸肆方街店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaocux500o13pmb5yog1s1l';  -- 象子茶铺茶普洱创基店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaocuu600n73pmbx2efwhxa';  -- 象子茶铺茶普洱孟连店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaocugb00jb3pmbcnsj4kjf';  -- 象子茶铺茶普洱悦城店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmp4vo7m5023v3ps1e5k9eru0';  -- 象子茶铺茶景东店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaoctyj00ev3pmbo3dxzrm1';  -- 象子茶铺茶景谷店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmp3hkfi901wo3ps1gw3nxcwl';  -- 象子茶铺茶永平县店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaocu3200fz3pmbwrlc4uk8';  -- 象子茶铺茶澜沧店
UPDATE store_info SET supervisor_name='李淑仪' WHERE store_id='cmpaocu6d00go3pmbf779nx4d';  -- 象子茶铺茶镇沅店

-- 督导：杨艳华（17 家）
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpaocugt00jg3pmbibe1oo1b';  -- 象子茶铺茶丘北店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpaocufu00j63pmbaoig7krk';  -- 象子茶铺茶华宁店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpaocuy400ob3pmb6mzv6ppu';  -- 象子茶铺茶广南店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmp4vo7lg023q3ps10d4jevol';  -- 象子茶铺茶建水小桂湖店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmqrfhli506nm3pq3wevdtbvb';  -- 象子茶铺茶建水紫陶街店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpnw02jz02953pk3xu4hmv35';  -- 象子茶铺茶开远店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmp3hkfh301we3ps1xi1nqwvj';  -- 象子茶铺茶文山光大店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpnw037902ej3pk3w57xd3rn';  -- 象子茶铺茶新平店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpaoctpv00cs3pmbhizaelfq';  -- 象子茶铺茶易门店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpaocu1n00fp3pmbbutnsku1';  -- 象子茶铺茶江川店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpnw02qq02ao3pk3dc923f3o';  -- 象子茶铺茶河口店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmp3hkhvr01ye3ps124g3hhkk';  -- 象子茶铺茶澄江店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpnw038h02et3pk3zi3iwbv1';  -- 象子茶铺茶玉溪峨山店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmp3hkfk701x83ps1w3301jiz';  -- 象子茶铺茶石屏店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpnw039202ey3pk309hgy8oe';  -- 象子茶铺茶砚山七都广场店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpnw02xq02c63pk3bmaulhiv';  -- 象子茶铺茶砚山嘉禾路店
UPDATE store_info SET supervisor_name='杨艳华' WHERE store_id='cmpnw02ta02b83pk3j51aqeg4';  -- 象子茶铺茶绿春店

-- 督导：董建明（18 家）
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpnw02q202aj3pk3i0a7084x';  -- 象子茶铺茶临沧百树广场店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaocucp00ic3pmb3l3cgc41';  -- 象子茶铺茶云县店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaocuqp00m83pmbu48evshq';  -- 象子茶铺茶凤庆店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaocueq00iw3pmbhzkhlsbx';  -- 象子茶铺茶双江店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmp4vo7mu02403ps1hxrjujb5';  -- 象子茶铺茶孟定店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpnw037w02eo3pk3obt1bzg8';  -- 象子茶铺茶德宏盈江店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaocuir00k03pmbboxmssqg';  -- 象子茶铺茶恒基广场店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaocts900d73pmbehtktmhz';  -- 象子茶铺茶昌宁店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaoctzm00f53pmbinxvl6p2';  -- 象子茶铺茶梁河店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaoctou00ci3pmbjbfguo5o';  -- 象子茶铺茶沧源店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaocudp00im3pmbs3q0p529';  -- 象子茶铺茶瑞丽彩云城店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaocuwn00nw3pmbnlkjavfz';  -- 象子茶铺茶瑞丽德龙珠宝城
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaoctt800dh3pmbb51j6wx1';  -- 象子茶铺茶瑞丽翡翠园店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaocuhb00jl3pmb99zr2r3n';  -- 象子茶铺茶腾冲天成店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaoctlq00bo3pmbh7n6te7r';  -- 象子茶铺茶芒市三棵树店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmp3hkhxa01yt3ps1buu08pso';  -- 象子茶铺茶镇康南伞店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpnw02zy02cq3pk3oyu6gvnq';  -- 象子茶铺茶陇川店
UPDATE store_info SET supervisor_name='董建明' WHERE store_id='cmpaoctww00eg3pmbwq58ot35';  -- 象子茶铺茶龙陵盛和雅苑店

-- 督导：李靖（15 家）
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpaocue900ir3pmbzef9yzuu';  -- 象子茶铺茶六盘水店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmp3hkfip01wt3ps1ald5dyrg';  -- 象子茶铺茶宜宾唐人财富中心店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpaocuo600lj3pmbb6uukpqx';  -- 象子茶铺茶宜宾长宁宏泰财富广场店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpnw02lb029f3pk3c1k2db61';  -- 象子茶铺茶宣威店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpnw02ov02a93pk3tuad001a';  -- 象子茶铺茶师宗店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpaocusn00ms3pmbwzu4qg1q';  -- 象子茶铺茶昭通合景广场店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpaocuoo00lo3pmbiwyeakdk';  -- 象子茶铺茶昭通实验中学店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpaoculc00kp3pmbkblhlnyn';  -- 象子茶铺茶曲靖万达店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpaocuro00mi3pmb3v5u3nc5';  -- 象子茶铺茶曲靖会泽店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpnw02lw029k3pk311bekjaz';  -- 象子茶铺茶曲靖沾益西正街店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpaocuze00ol3pmbq78thom9';  -- 象子茶铺茶曲靖花柯店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpaocu8x00hd3pmbrjv6qg5p';  -- 象子茶铺茶盘州店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpnw02oa02a43pk35j04jbjg';  -- 象子茶铺茶贵州望谟店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpnw02rb02at3pk3xs15zeg9';  -- 象子茶铺茶贵州黔西店
UPDATE store_info SET supervisor_name='李靖' WHERE store_id='cmpnw02kp029a3pk3a7ze7gvj';  -- 象子茶铺茶陆良店

-- 督导：罗正彩（16 家）
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaocun900l93pmbczh07a1b';  -- 象子茶铺茶万宏国际店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaoctrs00d23pmb0nygzz46';  -- 象子茶铺茶仕林街店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaoctm800bt3pmbdo8azaqf';  -- 象子茶铺茶南亚风情店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpwa93tr010v3pq3hzddyxs2';  -- 象子茶铺茶南屏街世纪广场店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaoctvq00e63pmbobapy64w';  -- 象子茶铺茶呈贡七彩云南第壹城店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaoctz100f03pmbuyaf4h73';  -- 象子茶铺茶弥勒印象街店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaoctsq00dc3pmbhjnibzpd';  -- 象子茶铺茶弥勒金辰店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaoctv800e13pmbw3w2ahxu';  -- 象子茶铺茶新亚洲店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaocud700ih3pmbe6opynv0';  -- 象子茶铺茶新迎新城店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpwa91ya010f3pq343o6pqxk';  -- 象子茶铺茶昆明建工新城店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaocu5500ge3pmb70lbe792';  -- 象子茶铺茶滇池名门店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaocu9f00hi3pmbmpvq836q';  -- 象子茶铺茶福保店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaoctko00be3pmbvpeiogtc';  -- 象子茶铺茶福德店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaoctij00az3pmbfl6c71mz';  -- 象子茶铺茶翠湖店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaoctmr00by3pmb20hdtxl5';  -- 象子茶铺茶翰林大观店
UPDATE store_info SET supervisor_name='罗正彩' WHERE store_id='cmpaoctqy00cx3pmb43nouafn';  -- 象子茶铺茶高山铺店

-- 3) 执行后校验：各督导门店数
-- SELECT supervisor_name, COUNT(*) FROM store_info WHERE del_flag=0 GROUP BY supervisor_name ORDER BY supervisor_name;
-- 未分配督导的门店（应为新开/机场等）：
-- SELECT store_id, store_name FROM store_info WHERE del_flag=0 AND supervisor_name IS NULL AND store_name NOT LIKE '测试%';