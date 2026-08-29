-- ============================================================
-- 更新 store_info.store_code 为 xinfo 新门店接口的门店 ID
-- 数据源：GET http://162.14.122.80:18088/api/external/stores（机器码认证）
-- 匹配键：store_info.chat_id（飞书运营群ID） = 接口返回 feishuChatId（已 trim 首尾空格）
-- 共更新 179 家；7 家群ID缺失/重复未处理（见文件末尾）
-- 生成日期：2026-08-21
-- ============================================================

-- 1) 执行前先备份原值（如失败可回滚）
CREATE TABLE IF NOT EXISTS store_inventory.store_info_backup_20260821 AS
SELECT id, store_id, store_name, store_code, chat_id
FROM store_inventory.store_info WHERE del_flag = 0;

-- 2) 预检：列出将要更新的门店（当前 store_code -> 新 store_code）
--    该 SELECT 直接可读，无需先执行 UPDATE
SELECT store_id, store_name, chat_id, store_code AS old_store_code,
       CASE chat_id
           WHEN 'oc_ad0d326c0ade64a2fec6ea55ffbc2c66' THEN '200' -- 象子茶铺茶(七彩云南第壹城店)
           WHEN 'oc_6c15c0a7436488e6afc4a23495738202' THEN '366' -- 象子茶铺茶(七彩蜜糖店)
           WHEN 'oc_22dd707fca076b08ae4ce5afdb1dcc94' THEN '214' -- 象子茶铺茶(七星街店)
           WHEN 'oc_b2025452510d1f4e4bcd37a9b3a26105' THEN '170' -- 象子茶铺茶(七都广场店)
           WHEN 'oc_2152dbf41937d36e145b32af10ad4ea1' THEN '266' -- 象子茶铺茶(万宏国际店)
           WHEN 'oc_16172865153f11b1ba21b0afe957b9d0' THEN '227' -- 象子茶铺茶(三馆店)
           WHEN 'oc_607fb56228fbefa1a7b6fdec21442e7b' THEN '351' -- 象子茶铺茶(世纪广场店)
           WHEN 'oc_1ba79daf3ac006d14203f5f6bb9773e8' THEN '217' -- 象子茶铺茶(世纪金源店)
           WHEN 'oc_56e03201fa59d62fe908da8ee8d392fd' THEN '251' -- 象子茶铺茶(丘北店)
           WHEN 'oc_40443900a43769c8a878b6fc63a24d34' THEN '280' -- 象子茶铺茶(东城店)
           WHEN 'oc_1267d8636e3bd782628fd1e5b4a78fed' THEN '226' -- 象子茶铺茶(东岸人民路店)
           WHEN 'oc_ea916ee096f79357c3ce2bcf806b7e8f' THEN '302' -- 象子茶铺茶(东川店)
           WHEN 'oc_1bb44b73fa0d38b9a78fd5eae25397ea' THEN '234' -- 象子茶铺茶(中铁云时代店)
           WHEN 'oc_d45585893e393d844331ef34a9518fdb' THEN '311' -- 象子茶铺茶(中骏世界城店)
           WHEN 'oc_e55229e50b249ab24f7ba7efcfa28aae' THEN '181' -- 象子茶铺茶(丹麓小镇店)
           WHEN 'oc_bea97c025f07ab68cb25f708ddf6690b' THEN '295' -- 象子茶铺茶(丽江古城店)
           WHEN 'oc_4df03b617a5b6e773986f3f66eba8f2f' THEN '240' -- 象子茶铺茶(云县店)
           WHEN 'oc_c573c17061795fb0dbc8f9af66b9a0b2' THEN '291' -- 象子茶铺茶(云龙店)
           WHEN 'oc_fc935c54b075076f67b504ea8b10d98d' THEN '225' -- 象子茶铺茶(五洲店)
           WHEN 'oc_1459c84900eac6ce428faf26362390c1' THEN '190' -- 象子茶铺茶(仕林街店)
           WHEN 'oc_8a1bcff6daa83c7d415775788d7be748' THEN '281' -- 象子茶铺茶(会泽店)
           WHEN 'oc_cdb70b3110c2de3d5db6900c0ddda27e' THEN '180' -- 象子茶铺茶(保山吾悦店)
           WHEN 'oc_21d3e453e690078427a2c0f4fa8c6745' THEN '273' -- 象子茶铺茶(元谋店)
           WHEN 'oc_142fe4a887918b1774fd4b40a9909d02' THEN '361' -- 象子茶铺茶(元阳店)
           WHEN 'oc_667c4d093bc96f7563ae5b9d61758a0f' THEN '267' -- 象子茶铺茶(光大店)
           WHEN 'oc_ca15393203d15c303a9b77fc254fd81e' THEN '346' -- 象子茶铺茶(公园1903店)
           WHEN 'oc_307cf62de9408becce20a559056bf6bf' THEN '244' -- 象子茶铺茶(六盘水川心小区店)
           WHEN 'oc_19e27ace2a8a4625278fa16f2f9c6196' THEN '220' -- 象子茶铺茶(兰坪店)
           WHEN 'oc_a0a61b9bacdf9e6cc95d5028ac735739' THEN '363' -- 象子茶铺茶(兰茂广场店)
           WHEN 'oc_f7525779dd69827573063154211cc399' THEN '198' -- 象子茶铺茶(凤仪店)
           WHEN 'oc_026ac97df5c953c214420a3cfc4d1d41' THEN '278' -- 象子茶铺茶(凤庆店)
           WHEN 'oc_eb9b751396c9a226ac126f8768881c79' THEN '293' -- 象子茶铺茶(创基尚城店)
           WHEN 'oc_7a7e95a18dac274ab677357c8ae442b3' THEN '274' -- 象子茶铺茶(剑川店)
           WHEN 'oc_fd012d9e81fa434362a7330d4aa2f6ad' THEN '236' -- 象子茶铺茶(勐海店)
           WHEN 'oc_dd94bb051802c1b2ab1c41a6419ddc9f' THEN '354' -- 象子茶铺茶(北城天地店)
           WHEN 'oc_8b0d65e1324cd31bd0e4100689997c23' THEN '245' -- 象子茶铺茶(北辰财富中心店)
           WHEN 'oc_5ffdf74aeab80b8fa26401a5fa8f2bf0' THEN '211' -- 象子茶铺茶(华坪店)
           WHEN 'oc_721e64fa72cee60bb01731490cf63a99' THEN '175' -- 象子茶铺茶(南亚风情店)
           WHEN 'oc_196c18e7d67c44c31204a10bcf86d908' THEN '230' -- 象子茶铺茶(南华店)
           WHEN 'oc_c42b0dd26e5448e1614cb27344675560' THEN '212' -- 象子茶铺茶(南涧店)
           WHEN 'oc_fb772bf297e66262b2d0fe4ef54a4fbd' THEN '321' -- 象子茶铺茶(南湖荟店)
           WHEN 'oc_d9ce2c4ef405c4e66d740b76e568b9cb' THEN '195' -- 象子茶铺茶(印象花园店)
           WHEN 'oc_dbcea0ab7210ed4b7fc6fa9d779bd213' THEN '246' -- 象子茶铺茶(双江店)
           WHEN 'oc_1e9bd36cf4b5b34598a543ab617bca39' THEN '284' -- 象子茶铺茶(合景广场店)
           WHEN 'oc_97651ecb4f498a969846bca97e5a4e49' THEN '268' -- 象子茶铺茶(吴井路店)
           WHEN 'oc_d84e47deb6f6302570e023c71545a0a6' THEN '330' -- 象子茶铺茶(呈贡吾悦店)
           WHEN 'oc_7a0f37656062de7bf6bce12c443dc9e5' THEN '270' -- 象子茶铺茶(唐人财富中心店)
           WHEN 'oc_17859da0822781b2b87fdd8e2fa00094' THEN '364' -- 象子茶铺茶(喜洲古镇店)
           WHEN 'oc_e53b1bdb694e4c4a91c38d26187ed42c' THEN '242' -- 象子茶铺茶(嘉禾路店)
           WHEN 'oc_0cbc7198bc88c25977069373b5fe14dd' THEN '215' -- 象子茶铺茶(嘉誉广场店)
           WHEN 'oc_bb041e4a5f87b7c1e0484b45bfbccdc4' THEN '305' -- 象子茶铺茶(园西路店)
           WHEN 'oc_08d9722ba2db394b6ee9debbd43705e2' THEN '260' -- 象子茶铺茶(城南店)
           WHEN 'oc_0c543ba3622d0aa96d5e0e05dce63492' THEN '314' -- 象子茶铺茶(塘子巷店)
           WHEN 'oc_5d36d88736015f699c3ef387ef3b647b' THEN '277' -- 象子茶铺茶(墨江店)
           WHEN 'oc_05cc7a666f42f722dcf7df0c3abe63f0' THEN '216' -- 象子茶铺茶(大姚店)
           WHEN 'oc_2ed37d04b416679b967505aae592e728' THEN '286' -- 象子茶铺茶(大润发店)
           WHEN 'oc_05923d4f2bf37a6051be814c3ee87e3d' THEN '263' -- 象子茶铺茶(大理古城复兴路店)
           WHEN 'oc_68525c31f47d5e46a61fc88292cd937f' THEN '319' -- 象子茶铺茶(大理古城玉洱路店)
           WHEN 'oc_a2796abd9dbddf808901062f8de50f7a' THEN '348' -- 象子茶铺茶(大理机场店)
           WHEN 'oc_c3744787bf80154cf5c24de4675e9064' THEN '192' -- 象子茶铺茶(天骄北麓店)
           WHEN 'oc_3390af8150e77604f46928cebbf7c132' THEN '297' -- 象子茶铺茶(好悦天地店)
           WHEN 'oc_fb25a4495db1f1ab3a212112fed6167b' THEN '232' -- 象子茶铺茶(姚安店)
           WHEN 'oc_32af8f1a2500a7b3b88bec9631e80bf9' THEN '310' -- 象子茶铺茶(孟定店)
           WHEN 'oc_40f5c6651eb2e563a99b87f658039f31' THEN '287' -- 象子茶铺茶(孟连店)
           WHEN 'oc_9190e777a403e6564fb17f80f04298fc' THEN '327' -- 象子茶铺茶(安宁吾悦店)
           WHEN 'oc_7739317259e66337955db5d04ae19cf9' THEN '269' -- 象子茶铺茶(宏泰财富广场店)
           WHEN 'oc_4196cb6c029019690e7f387dee6ae7e9' THEN '326' -- 象子茶铺茶(官渡古镇店)
           WHEN 'oc_d5b8800e2e4884aeabbf0cc086c15947' THEN '228' -- 象子茶铺茶(宜良云锦中心店)
           WHEN 'oc_13e12ed1c4f6245459ff54b32774bf01' THEN '312' -- 象子茶铺茶(宣威店)
           WHEN 'oc_b931aea97013542ae9f516484475fb87' THEN '231' -- 象子茶铺茶(宾川店)
           WHEN 'oc_5984bc68e6319e0b6993a9e9f83f9493' THEN '358' -- 象子茶铺茶(富宁店)
           WHEN 'oc_4806a563516e9b2a2cd03f6f2835bdcf' THEN '307' -- 象子茶铺茶(富康城店)
           WHEN 'oc_d4e410390b1987232f2abec69b866798' THEN '265' -- 象子茶铺茶(小桂湖店)
           WHEN 'oc_15e0b2ea16f396b853991fca871b92be' THEN '186' -- 象子茶铺茶(峨山店)
           WHEN 'oc_23c6dee063d4f9e8e434e100cf8e33eb' THEN '222' -- 象子茶铺茶(巍山店)
           WHEN 'oc_e57ba1424f910a485607bdb83cafa210' THEN '303' -- 象子茶铺茶(师宗店)
           WHEN 'oc_21052e49c707740345895257e9170e9d' THEN '329' -- 象子茶铺茶(广福路爱琴海店)
           WHEN 'oc_c295f5218730d270e6280762708a752f' THEN '317' -- 象子茶铺茶(建工新城店)
           WHEN 'oc_d20ece0f15a849405c4b2111815c794a' THEN '300' -- 象子茶铺茶(开远店)
           WHEN 'oc_27c9fa25ee9d004895c67a8070b3ed34' THEN '207' -- 象子茶铺茶(弥勒印象街店)
           WHEN 'oc_b1966daf19711032ee3a1dcc42fd6b69' THEN '197' -- 象子茶铺茶(弥渡店)
           WHEN 'oc_e6c6d3ee0ad883dd2e74a9927dfc77d0' THEN '248' -- 象子茶铺茶(彝人古镇店)
           WHEN 'oc_2dea5634fa4423cb117448651d508bfb' THEN '255' -- 象子茶铺茶(彝海公园店)
           WHEN 'oc_ae29f18450c8a6a67805b8da6810e485' THEN '243' -- 象子茶铺茶(彩云城店)
           WHEN 'oc_85b6b4206b3c5b0d1622fc15e0cefc24' THEN '173' -- 象子茶铺茶(彩玉国际店)
           WHEN 'oc_4ef0eba8822ff9fc689688b874b99334' THEN '292' -- 象子茶铺茶(德龙珠宝城店)
           WHEN 'oc_53ea18e62b8e319b7a442a9f8113a5be' THEN '257' -- 象子茶铺茶(恒基广场店)
           WHEN 'oc_716eed324d0215766a7946b7315611b2' THEN '308' -- 象子茶铺茶(招商花园店)
           WHEN 'oc_dbe0be797bb53a5e9aac6a1a217b9cb8' THEN '283' -- 象子茶铺茶(文峰路店)
           WHEN 'oc_e9fc878dead17de41b6f09bd93b0de07' THEN '199' -- 象子茶铺茶(新亚洲店)
           WHEN 'oc_06de92a81bc43a263f43e697246f7993' THEN '183' -- 象子茶铺茶(新平店)
           WHEN 'oc_a807445cae091d193d8446d8e8e9ef41' THEN '241' -- 象子茶铺茶(新迎新城店)
           WHEN 'oc_fdb5782857c217d10dfe6d2af2bd926f' THEN '169' -- 象子茶铺茶(施甸交通路店)
           WHEN 'oc_0211b11ebe2c2e252b8f94dc733ec5ed' THEN '201' -- 象子茶铺茶(施甸肆方街店)
           WHEN 'oc_ece03731bb6927ff459ee64c7063b73d' THEN '301' -- 象子茶铺茶(旅游学院店)
           WHEN 'oc_7b709773d3f03af3142c097e406a00ba' THEN '367' -- 象子茶铺茶(时代俊园店)
           WHEN 'oc_277dac9330fe835296b71704eb9964a8' THEN '239' -- 象子茶铺茶(时代天街店)
           WHEN 'oc_a38badc8bc54189c67b654600769f336' THEN '191' -- 象子茶铺茶(昌宁店)
           WHEN 'oc_4bcf5c9a0ddf74e7b07323274e686f94' THEN '188' -- 象子茶铺茶(易门店)
           WHEN 'oc_1dae941724e3e44778c9fac2b7aef8fa' THEN '357' -- 象子茶铺茶(昭通吾悦店)
           WHEN 'oc_7b4395fc55f948e8b52d6301a421358f' THEN '272' -- 象子茶铺茶(昭通实验中学店)
           WHEN 'oc_c3099c6e15e9684bbb27764a71a32827' THEN '223' -- 象子茶铺茶(晋城区店)
           WHEN 'oc_a32fa9b648c02a8643234cc09506caf4' THEN '250' -- 象子茶铺茶(普洱悦城店)
           WHEN 'oc_fe2681d97414b640a91485dfe59280a3' THEN '256' -- 象子茶铺茶(景东店)
           WHEN 'oc_68fe8ca181d6d625d28ca3545ef36254' THEN '206' -- 象子茶铺茶(景谷店)
           WHEN 'oc_e63e45b4e404fdb1ee5e409a94f33bcc' THEN '262' -- 象子茶铺茶(曲靖万达店)
           WHEN 'oc_6bcfedf6c5d3ee2f7b91faca1e1e548e' THEN '285' -- 象子茶铺茶(曼城店)
           WHEN 'oc_24298dd2217f041c467fe1744bd6a32d' THEN '332' -- 象子茶铺茶(月光印巷店)
           WHEN 'oc_1aecb6e3781c4934c481cd148e4f2e81' THEN '304' -- 象子茶铺茶(望谟店)
           WHEN 'oc_c3d988189c52e18a3f2dc9d204b2be0d' THEN '275' -- 象子茶铺茶(束河古镇店)
           WHEN 'oc_523527a4702b6695771e6206df984811' THEN '203' -- 象子茶铺茶(板桥店)
           WHEN 'oc_7200e61cbee4471e648830bcbaaf2586' THEN '271' -- 象子茶铺茶(果林广场店)
           WHEN 'oc_274711c29a9927e72ce859344bb7fe39' THEN '282' -- 象子茶铺茶(柏联广场店)
           WHEN 'oc_92824dfb71dc432bc94a3726752d6a13' THEN '209' -- 象子茶铺茶(梁河店)
           WHEN 'oc_e624967481827c8339c3874bf3475b6f' THEN '322' -- 象子茶铺茶(欢乐橙店)
           WHEN 'oc_04a210c3e2ca031e79587f8b4c699d2f' THEN '205' -- 象子茶铺茶(正阳店)
           WHEN 'oc_53208b64791c960a5c92342b86cad8d0' THEN '178' -- 象子茶铺茶(永平店)
           WHEN 'oc_754971d8d76721b8edb9c67facd34a72' THEN '362' -- 象子茶铺茶(永德店)
           WHEN 'oc_f8860f1a5432d5bd617d30be29c230a4' THEN '208' -- 象子茶铺茶(永胜店)
           WHEN 'oc_c631121dc56ebbbd6e335160f2351bff' THEN '213' -- 象子茶铺茶(江川店)
           WHEN 'oc_ee0d311997541abe499d1a97310ad46f' THEN '185' -- 象子茶铺茶(沧源店)
           WHEN 'oc_d6696d672cc0f925b36b18fa7e4bf1a2' THEN '294' -- 象子茶铺茶(河口店)
           WHEN 'oc_3b789156605aad8195346ba6f1b35e9f' THEN '309' -- 象子茶铺茶(沾益区西正街店)
           WHEN 'oc_751a225e04ad93ac227d242358eec2df' THEN '182' -- 象子茶铺茶(泰业一店)
           WHEN 'oc_f75fe3fcf965d9e9803a234687776211' THEN '315' -- 象子茶铺茶(泰业二店)
           WHEN 'oc_f030224aaed78e47dd4a6a563eb76220' THEN '313' -- 象子茶铺茶(泸西店)
           WHEN 'oc_3a9699cc6879aaf2cf9b583612022cc0' THEN '290' -- 象子茶铺茶(洱源店)
           WHEN 'oc_b85930c803d4f352a7de6bc66f082793' THEN '171' -- 象子茶铺茶(海乐达人汇店)
           WHEN 'oc_fec7be5898e970cc649922d0905c8442' THEN '221' -- 象子茶铺茶(滇池名门店)
           WHEN 'oc_98dca0efe5ac5ad3471f5015757b4d8d' THEN '289' -- 象子茶铺茶(满江店)
           WHEN 'oc_307d7917f9cbc353aeabb3cb7409a448' THEN '316' -- 象子茶铺茶(漾濞店)
           WHEN 'oc_cb46fee660214eec4a13a8f16d3438e2' THEN '288' -- 象子茶铺茶(潞江坝店)
           WHEN 'oc_fc0151fb14fdf1759b6e7c1b17dde579' THEN '177' -- 象子茶铺茶(澄江店)
           WHEN 'oc_c34d38d06f82a431590e042376bec76e' THEN '218' -- 象子茶铺茶(澜沧店)
           WHEN 'oc_950c2f61111c0b858ff49ebaaab4d8ca' THEN '210' -- 象子茶铺茶(牟定店)
           WHEN 'oc_12d0664d27e194c9cb459791c3b48853' THEN '258' -- 象子茶铺茶(玉龙店)
           WHEN 'oc_444dedfd8550b0d774dbc8bc8fd456cb' THEN '196' -- 象子茶铺茶(王府井滇池小镇店)
           WHEN 'oc_5c5ee4effda873962f14f6657fa074b3' THEN '238' -- 象子茶铺茶(瑞鼎城店)
           WHEN 'oc_6b38f0483349099a6e3aec3776807f2a' THEN '261' -- 象子茶铺茶(白沙古镇店)
           WHEN 'oc_93ec26ab34f58ab3d58b234ba696ad94' THEN '355' -- 象子茶铺茶(白龙派公园店)
           WHEN 'oc_98c00abc3e4c27f50e55320534ab0d5e' THEN '184' -- 象子茶铺茶(盈江店)
           WHEN 'oc_33e8ecbbbedb953aad30af2a7ae50a7b' THEN '229' -- 象子茶铺茶(盘州店)
           WHEN 'oc_d00f54d8f93e4b9a88493997c3209202' THEN '202' -- 象子茶铺茶(盛和雅苑店)
           WHEN 'oc_9ccb0480106f6f99e8edf7eaaa9d2f4b' THEN '254' -- 象子茶铺茶(石屏店)
           WHEN 'oc_c5b23b4c47a402b89080aef0ca78c2a5' THEN '259' -- 象子茶铺茶(祥和店)
           WHEN 'oc_baff826998dc7faa5807161036719e5c' THEN '324' -- 象子茶铺茶(禄丰店)
           WHEN 'oc_907fee96c237467334a0634f0d21ca66' THEN '204' -- 象子茶铺茶(禄劝店)
           WHEN 'oc_06895ffa3c4b7db5a6511a6378c58e33' THEN '233' -- 象子茶铺茶(福保店)
           WHEN 'oc_a50e3e0410635586fcc63a61cce825d3' THEN '172' -- 象子茶铺茶(福德店)
           WHEN 'oc_96dc6aba6d83584e7d12f086ac6fb348' THEN '247' -- 象子茶铺茶(第七街区店)
           WHEN 'oc_99d4f912ad10558002010eca168fd4e9' THEN '253' -- 象子茶铺茶(紫金中心店)
           WHEN 'oc_9ad71ecec4380888266ce346c8c483dc' THEN '328' -- 象子茶铺茶(紫陶街店)
           WHEN 'oc_7588f3edff852f96683e5d6639d2ccdb' THEN '325' -- 象子茶铺茶(红河店)
           WHEN 'oc_1db141902c9e6ac3f793e5d5eea9c6c2' THEN '276' -- 象子茶铺茶(绿春店)
           WHEN 'oc_bf5f9bd735071084abe4d33aeb1b4de8' THEN '167' -- 象子茶铺茶(翠湖店)
           WHEN 'oc_8c030838d2bfb5e9454d489ab5b706f8' THEN '194' -- 象子茶铺茶(翡翠园店)
           WHEN 'oc_d4f0908660bb5f4fe4175cee934a2329' THEN '179' -- 象子茶铺茶(翰林大观店)
           WHEN 'oc_a5efa3df34edabbe1e39f33de76498f9' THEN '252' -- 象子茶铺茶(腾冲天成店)
           WHEN 'oc_beeadb4a361ed81cde53e2a0e8728d03' THEN '174' -- 象子茶铺茶(芒市三棵树店)
           WHEN 'oc_afb137cc32afb1c6e88b89b8958aa2c8' THEN '365' -- 象子茶铺茶(芒市财富广场店)
           WHEN 'oc_b0b6ae7fa135c887d57e0ab56b385b14' THEN '299' -- 象子茶铺茶(花柯店)
           WHEN 'oc_a6b9eaab50166fd0d4085e60868ce3ed' THEN '356' -- 象子茶铺茶(茶马花街店)
           WHEN 'oc_2b9c82a405ee6c0eb0bf143b05bb4039' THEN '187' -- 象子茶铺茶(西岸店)
           WHEN 'oc_b03c5750d7eecd3a693182c4d8feb81f' THEN '320' -- 象子茶铺茶(财大龙泉路店)
           WHEN 'oc_d1ae64b95873d0df6b32bbadf4cc9caa' THEN '349' -- 象子茶铺茶(里外里店)
           WHEN 'oc_994578bbb2228c19e014db1aaf24115a' THEN '323' -- 象子茶铺茶(金池购物中心店)
           WHEN 'oc_3294bf48fc090f245df476997e75f2ac' THEN '264' -- 象子茶铺茶(金色时代店)
           WHEN 'oc_381c34043fee2cc3770e04f779462fd7' THEN '193' -- 象子茶铺茶(金辰店)
           WHEN 'oc_acf6bff1fc9dd3d693b8817bae625768' THEN '168' -- 象子茶铺茶(银海尚御店)
           WHEN 'oc_2b8991de8f2038dc0d029762e89b07ef' THEN '224' -- 象子茶铺茶(镇沅店)
           WHEN 'oc_9e9820f330aca0b93999080eebf2140d' THEN '359' -- 象子茶铺茶(镇雄店)
           WHEN 'oc_a9f1fd5b5ef8178edcf7b4f02adcd635' THEN '279' -- 象子茶铺茶(长征大道店)
           WHEN 'oc_e2ba82b2e91998b131a3c7a54e93bc3c' THEN '352' -- 象子茶铺茶(长水机场到达厅店)
           WHEN 'oc_b0da1aadfed4e2579dcb5224117d22e6' THEN '353' -- 象子茶铺茶(长水机场卫星厅店)
           WHEN 'oc_505688162590a5f3533e541add2b60e0' THEN '347' -- 象子茶铺茶(长水机场店)
           WHEN 'oc_104f354b03ba997b7ef8b41c5b2baa07' THEN '298' -- 象子茶铺茶(陆良店)
           WHEN 'oc_ac0edeffa0e3215b740f4ed6f1bf7d7e' THEN '237' -- 象子茶铺茶(陇川店)
           WHEN 'oc_b6dd7fdb6a8df39a385f64bd07eef088' THEN '189' -- 象子茶铺茶(高山铺店)
           WHEN 'oc_5aecde41a5b018c37a881dc01af74815' THEN '235' -- 象子茶铺茶(鹤庆店)
       END AS new_store_code
FROM store_inventory.store_info
WHERE del_flag = 0 AND chat_id IN (
    'oc_ad0d326c0ade64a2fec6ea55ffbc2c66',
    'oc_6c15c0a7436488e6afc4a23495738202',
    'oc_22dd707fca076b08ae4ce5afdb1dcc94',
    'oc_b2025452510d1f4e4bcd37a9b3a26105',
    'oc_2152dbf41937d36e145b32af10ad4ea1',
    'oc_16172865153f11b1ba21b0afe957b9d0',
    'oc_607fb56228fbefa1a7b6fdec21442e7b',
    'oc_1ba79daf3ac006d14203f5f6bb9773e8',
    'oc_56e03201fa59d62fe908da8ee8d392fd',
    'oc_40443900a43769c8a878b6fc63a24d34',
    'oc_1267d8636e3bd782628fd1e5b4a78fed',
    'oc_ea916ee096f79357c3ce2bcf806b7e8f',
    'oc_1bb44b73fa0d38b9a78fd5eae25397ea',
    'oc_d45585893e393d844331ef34a9518fdb',
    'oc_e55229e50b249ab24f7ba7efcfa28aae',
    'oc_bea97c025f07ab68cb25f708ddf6690b',
    'oc_4df03b617a5b6e773986f3f66eba8f2f',
    'oc_c573c17061795fb0dbc8f9af66b9a0b2',
    'oc_fc935c54b075076f67b504ea8b10d98d',
    'oc_1459c84900eac6ce428faf26362390c1',
    'oc_8a1bcff6daa83c7d415775788d7be748',
    'oc_cdb70b3110c2de3d5db6900c0ddda27e',
    'oc_21d3e453e690078427a2c0f4fa8c6745',
    'oc_142fe4a887918b1774fd4b40a9909d02',
    'oc_667c4d093bc96f7563ae5b9d61758a0f',
    'oc_ca15393203d15c303a9b77fc254fd81e',
    'oc_307cf62de9408becce20a559056bf6bf',
    'oc_19e27ace2a8a4625278fa16f2f9c6196',
    'oc_a0a61b9bacdf9e6cc95d5028ac735739',
    'oc_f7525779dd69827573063154211cc399',
    'oc_026ac97df5c953c214420a3cfc4d1d41',
    'oc_eb9b751396c9a226ac126f8768881c79',
    'oc_7a7e95a18dac274ab677357c8ae442b3',
    'oc_fd012d9e81fa434362a7330d4aa2f6ad',
    'oc_dd94bb051802c1b2ab1c41a6419ddc9f',
    'oc_8b0d65e1324cd31bd0e4100689997c23',
    'oc_5ffdf74aeab80b8fa26401a5fa8f2bf0',
    'oc_721e64fa72cee60bb01731490cf63a99',
    'oc_196c18e7d67c44c31204a10bcf86d908',
    'oc_c42b0dd26e5448e1614cb27344675560',
    'oc_fb772bf297e66262b2d0fe4ef54a4fbd',
    'oc_d9ce2c4ef405c4e66d740b76e568b9cb',
    'oc_dbcea0ab7210ed4b7fc6fa9d779bd213',
    'oc_1e9bd36cf4b5b34598a543ab617bca39',
    'oc_97651ecb4f498a969846bca97e5a4e49',
    'oc_d84e47deb6f6302570e023c71545a0a6',
    'oc_7a0f37656062de7bf6bce12c443dc9e5',
    'oc_17859da0822781b2b87fdd8e2fa00094',
    'oc_e53b1bdb694e4c4a91c38d26187ed42c',
    'oc_0cbc7198bc88c25977069373b5fe14dd',
    'oc_bb041e4a5f87b7c1e0484b45bfbccdc4',
    'oc_08d9722ba2db394b6ee9debbd43705e2',
    'oc_0c543ba3622d0aa96d5e0e05dce63492',
    'oc_5d36d88736015f699c3ef387ef3b647b',
    'oc_05cc7a666f42f722dcf7df0c3abe63f0',
    'oc_2ed37d04b416679b967505aae592e728',
    'oc_05923d4f2bf37a6051be814c3ee87e3d',
    'oc_68525c31f47d5e46a61fc88292cd937f',
    'oc_a2796abd9dbddf808901062f8de50f7a',
    'oc_c3744787bf80154cf5c24de4675e9064',
    'oc_3390af8150e77604f46928cebbf7c132',
    'oc_fb25a4495db1f1ab3a212112fed6167b',
    'oc_32af8f1a2500a7b3b88bec9631e80bf9',
    'oc_40f5c6651eb2e563a99b87f658039f31',
    'oc_9190e777a403e6564fb17f80f04298fc',
    'oc_7739317259e66337955db5d04ae19cf9',
    'oc_4196cb6c029019690e7f387dee6ae7e9',
    'oc_d5b8800e2e4884aeabbf0cc086c15947',
    'oc_13e12ed1c4f6245459ff54b32774bf01',
    'oc_b931aea97013542ae9f516484475fb87',
    'oc_5984bc68e6319e0b6993a9e9f83f9493',
    'oc_4806a563516e9b2a2cd03f6f2835bdcf',
    'oc_d4e410390b1987232f2abec69b866798',
    'oc_15e0b2ea16f396b853991fca871b92be',
    'oc_23c6dee063d4f9e8e434e100cf8e33eb',
    'oc_e57ba1424f910a485607bdb83cafa210',
    'oc_21052e49c707740345895257e9170e9d',
    'oc_c295f5218730d270e6280762708a752f',
    'oc_d20ece0f15a849405c4b2111815c794a',
    'oc_27c9fa25ee9d004895c67a8070b3ed34',
    'oc_b1966daf19711032ee3a1dcc42fd6b69',
    'oc_e6c6d3ee0ad883dd2e74a9927dfc77d0',
    'oc_2dea5634fa4423cb117448651d508bfb',
    'oc_ae29f18450c8a6a67805b8da6810e485',
    'oc_85b6b4206b3c5b0d1622fc15e0cefc24',
    'oc_4ef0eba8822ff9fc689688b874b99334',
    'oc_53ea18e62b8e319b7a442a9f8113a5be',
    'oc_716eed324d0215766a7946b7315611b2',
    'oc_dbe0be797bb53a5e9aac6a1a217b9cb8',
    'oc_e9fc878dead17de41b6f09bd93b0de07',
    'oc_06de92a81bc43a263f43e697246f7993',
    'oc_a807445cae091d193d8446d8e8e9ef41',
    'oc_fdb5782857c217d10dfe6d2af2bd926f',
    'oc_0211b11ebe2c2e252b8f94dc733ec5ed',
    'oc_ece03731bb6927ff459ee64c7063b73d',
    'oc_7b709773d3f03af3142c097e406a00ba',
    'oc_277dac9330fe835296b71704eb9964a8',
    'oc_a38badc8bc54189c67b654600769f336',
    'oc_4bcf5c9a0ddf74e7b07323274e686f94',
    'oc_1dae941724e3e44778c9fac2b7aef8fa',
    'oc_7b4395fc55f948e8b52d6301a421358f',
    'oc_c3099c6e15e9684bbb27764a71a32827',
    'oc_a32fa9b648c02a8643234cc09506caf4',
    'oc_fe2681d97414b640a91485dfe59280a3',
    'oc_68fe8ca181d6d625d28ca3545ef36254',
    'oc_e63e45b4e404fdb1ee5e409a94f33bcc',
    'oc_6bcfedf6c5d3ee2f7b91faca1e1e548e',
    'oc_24298dd2217f041c467fe1744bd6a32d',
    'oc_1aecb6e3781c4934c481cd148e4f2e81',
    'oc_c3d988189c52e18a3f2dc9d204b2be0d',
    'oc_523527a4702b6695771e6206df984811',
    'oc_7200e61cbee4471e648830bcbaaf2586',
    'oc_274711c29a9927e72ce859344bb7fe39',
    'oc_92824dfb71dc432bc94a3726752d6a13',
    'oc_e624967481827c8339c3874bf3475b6f',
    'oc_04a210c3e2ca031e79587f8b4c699d2f',
    'oc_53208b64791c960a5c92342b86cad8d0',
    'oc_754971d8d76721b8edb9c67facd34a72',
    'oc_f8860f1a5432d5bd617d30be29c230a4',
    'oc_c631121dc56ebbbd6e335160f2351bff',
    'oc_ee0d311997541abe499d1a97310ad46f',
    'oc_d6696d672cc0f925b36b18fa7e4bf1a2',
    'oc_3b789156605aad8195346ba6f1b35e9f',
    'oc_751a225e04ad93ac227d242358eec2df',
    'oc_f75fe3fcf965d9e9803a234687776211',
    'oc_f030224aaed78e47dd4a6a563eb76220',
    'oc_3a9699cc6879aaf2cf9b583612022cc0',
    'oc_b85930c803d4f352a7de6bc66f082793',
    'oc_fec7be5898e970cc649922d0905c8442',
    'oc_98dca0efe5ac5ad3471f5015757b4d8d',
    'oc_307d7917f9cbc353aeabb3cb7409a448',
    'oc_cb46fee660214eec4a13a8f16d3438e2',
    'oc_fc0151fb14fdf1759b6e7c1b17dde579',
    'oc_c34d38d06f82a431590e042376bec76e',
    'oc_950c2f61111c0b858ff49ebaaab4d8ca',
    'oc_12d0664d27e194c9cb459791c3b48853',
    'oc_444dedfd8550b0d774dbc8bc8fd456cb',
    'oc_5c5ee4effda873962f14f6657fa074b3',
    'oc_6b38f0483349099a6e3aec3776807f2a',
    'oc_93ec26ab34f58ab3d58b234ba696ad94',
    'oc_98c00abc3e4c27f50e55320534ab0d5e',
    'oc_33e8ecbbbedb953aad30af2a7ae50a7b',
    'oc_d00f54d8f93e4b9a88493997c3209202',
    'oc_9ccb0480106f6f99e8edf7eaaa9d2f4b',
    'oc_c5b23b4c47a402b89080aef0ca78c2a5',
    'oc_baff826998dc7faa5807161036719e5c',
    'oc_907fee96c237467334a0634f0d21ca66',
    'oc_06895ffa3c4b7db5a6511a6378c58e33',
    'oc_a50e3e0410635586fcc63a61cce825d3',
    'oc_96dc6aba6d83584e7d12f086ac6fb348',
    'oc_99d4f912ad10558002010eca168fd4e9',
    'oc_9ad71ecec4380888266ce346c8c483dc',
    'oc_7588f3edff852f96683e5d6639d2ccdb',
    'oc_1db141902c9e6ac3f793e5d5eea9c6c2',
    'oc_bf5f9bd735071084abe4d33aeb1b4de8',
    'oc_8c030838d2bfb5e9454d489ab5b706f8',
    'oc_d4f0908660bb5f4fe4175cee934a2329',
    'oc_a5efa3df34edabbe1e39f33de76498f9',
    'oc_beeadb4a361ed81cde53e2a0e8728d03',
    'oc_afb137cc32afb1c6e88b89b8958aa2c8',
    'oc_b0b6ae7fa135c887d57e0ab56b385b14',
    'oc_a6b9eaab50166fd0d4085e60868ce3ed',
    'oc_2b9c82a405ee6c0eb0bf143b05bb4039',
    'oc_b03c5750d7eecd3a693182c4d8feb81f',
    'oc_d1ae64b95873d0df6b32bbadf4cc9caa',
    'oc_994578bbb2228c19e014db1aaf24115a',
    'oc_3294bf48fc090f245df476997e75f2ac',
    'oc_381c34043fee2cc3770e04f779462fd7',
    'oc_acf6bff1fc9dd3d693b8817bae625768',
    'oc_2b8991de8f2038dc0d029762e89b07ef',
    'oc_9e9820f330aca0b93999080eebf2140d',
    'oc_a9f1fd5b5ef8178edcf7b4f02adcd635',
    'oc_e2ba82b2e91998b131a3c7a54e93bc3c',
    'oc_b0da1aadfed4e2579dcb5224117d22e6',
    'oc_505688162590a5f3533e541add2b60e0',
    'oc_104f354b03ba997b7ef8b41c5b2baa07',
    'oc_ac0edeffa0e3215b740f4ed6f1bf7d7e',
    'oc_b6dd7fdb6a8df39a385f64bd07eef088',
    'oc_5aecde41a5b018c37a881dc01af74815'
)
ORDER BY store_name;

-- 3) 正式更新：store_code = xinfo 门店 ID
UPDATE store_inventory.store_info
SET store_code = CASE chat_id
    WHEN 'oc_ad0d326c0ade64a2fec6ea55ffbc2c66' THEN '200'  -- 象子茶铺茶(七彩云南第壹城店)
    WHEN 'oc_6c15c0a7436488e6afc4a23495738202' THEN '366'  -- 象子茶铺茶(七彩蜜糖店)
    WHEN 'oc_22dd707fca076b08ae4ce5afdb1dcc94' THEN '214'  -- 象子茶铺茶(七星街店)
    WHEN 'oc_b2025452510d1f4e4bcd37a9b3a26105' THEN '170'  -- 象子茶铺茶(七都广场店)
    WHEN 'oc_2152dbf41937d36e145b32af10ad4ea1' THEN '266'  -- 象子茶铺茶(万宏国际店)
    WHEN 'oc_16172865153f11b1ba21b0afe957b9d0' THEN '227'  -- 象子茶铺茶(三馆店)
    WHEN 'oc_607fb56228fbefa1a7b6fdec21442e7b' THEN '351'  -- 象子茶铺茶(世纪广场店)
    WHEN 'oc_1ba79daf3ac006d14203f5f6bb9773e8' THEN '217'  -- 象子茶铺茶(世纪金源店)
    WHEN 'oc_56e03201fa59d62fe908da8ee8d392fd' THEN '251'  -- 象子茶铺茶(丘北店)
    WHEN 'oc_40443900a43769c8a878b6fc63a24d34' THEN '280'  -- 象子茶铺茶(东城店)
    WHEN 'oc_1267d8636e3bd782628fd1e5b4a78fed' THEN '226'  -- 象子茶铺茶(东岸人民路店)
    WHEN 'oc_ea916ee096f79357c3ce2bcf806b7e8f' THEN '302'  -- 象子茶铺茶(东川店)
    WHEN 'oc_1bb44b73fa0d38b9a78fd5eae25397ea' THEN '234'  -- 象子茶铺茶(中铁云时代店)
    WHEN 'oc_d45585893e393d844331ef34a9518fdb' THEN '311'  -- 象子茶铺茶(中骏世界城店)
    WHEN 'oc_e55229e50b249ab24f7ba7efcfa28aae' THEN '181'  -- 象子茶铺茶(丹麓小镇店)
    WHEN 'oc_bea97c025f07ab68cb25f708ddf6690b' THEN '295'  -- 象子茶铺茶(丽江古城店)
    WHEN 'oc_4df03b617a5b6e773986f3f66eba8f2f' THEN '240'  -- 象子茶铺茶(云县店)
    WHEN 'oc_c573c17061795fb0dbc8f9af66b9a0b2' THEN '291'  -- 象子茶铺茶(云龙店)
    WHEN 'oc_fc935c54b075076f67b504ea8b10d98d' THEN '225'  -- 象子茶铺茶(五洲店)
    WHEN 'oc_1459c84900eac6ce428faf26362390c1' THEN '190'  -- 象子茶铺茶(仕林街店)
    WHEN 'oc_8a1bcff6daa83c7d415775788d7be748' THEN '281'  -- 象子茶铺茶(会泽店)
    WHEN 'oc_cdb70b3110c2de3d5db6900c0ddda27e' THEN '180'  -- 象子茶铺茶(保山吾悦店)
    WHEN 'oc_21d3e453e690078427a2c0f4fa8c6745' THEN '273'  -- 象子茶铺茶(元谋店)
    WHEN 'oc_142fe4a887918b1774fd4b40a9909d02' THEN '361'  -- 象子茶铺茶(元阳店)
    WHEN 'oc_667c4d093bc96f7563ae5b9d61758a0f' THEN '267'  -- 象子茶铺茶(光大店)
    WHEN 'oc_ca15393203d15c303a9b77fc254fd81e' THEN '346'  -- 象子茶铺茶(公园1903店)
    WHEN 'oc_307cf62de9408becce20a559056bf6bf' THEN '244'  -- 象子茶铺茶(六盘水川心小区店)
    WHEN 'oc_19e27ace2a8a4625278fa16f2f9c6196' THEN '220'  -- 象子茶铺茶(兰坪店)
    WHEN 'oc_a0a61b9bacdf9e6cc95d5028ac735739' THEN '363'  -- 象子茶铺茶(兰茂广场店)
    WHEN 'oc_f7525779dd69827573063154211cc399' THEN '198'  -- 象子茶铺茶(凤仪店)
    WHEN 'oc_026ac97df5c953c214420a3cfc4d1d41' THEN '278'  -- 象子茶铺茶(凤庆店)
    WHEN 'oc_eb9b751396c9a226ac126f8768881c79' THEN '293'  -- 象子茶铺茶(创基尚城店)
    WHEN 'oc_7a7e95a18dac274ab677357c8ae442b3' THEN '274'  -- 象子茶铺茶(剑川店)
    WHEN 'oc_fd012d9e81fa434362a7330d4aa2f6ad' THEN '236'  -- 象子茶铺茶(勐海店)
    WHEN 'oc_dd94bb051802c1b2ab1c41a6419ddc9f' THEN '354'  -- 象子茶铺茶(北城天地店)
    WHEN 'oc_8b0d65e1324cd31bd0e4100689997c23' THEN '245'  -- 象子茶铺茶(北辰财富中心店)
    WHEN 'oc_5ffdf74aeab80b8fa26401a5fa8f2bf0' THEN '211'  -- 象子茶铺茶(华坪店)
    WHEN 'oc_721e64fa72cee60bb01731490cf63a99' THEN '175'  -- 象子茶铺茶(南亚风情店)
    WHEN 'oc_196c18e7d67c44c31204a10bcf86d908' THEN '230'  -- 象子茶铺茶(南华店)
    WHEN 'oc_c42b0dd26e5448e1614cb27344675560' THEN '212'  -- 象子茶铺茶(南涧店)
    WHEN 'oc_fb772bf297e66262b2d0fe4ef54a4fbd' THEN '321'  -- 象子茶铺茶(南湖荟店)
    WHEN 'oc_d9ce2c4ef405c4e66d740b76e568b9cb' THEN '195'  -- 象子茶铺茶(印象花园店)
    WHEN 'oc_dbcea0ab7210ed4b7fc6fa9d779bd213' THEN '246'  -- 象子茶铺茶(双江店)
    WHEN 'oc_1e9bd36cf4b5b34598a543ab617bca39' THEN '284'  -- 象子茶铺茶(合景广场店)
    WHEN 'oc_97651ecb4f498a969846bca97e5a4e49' THEN '268'  -- 象子茶铺茶(吴井路店)
    WHEN 'oc_d84e47deb6f6302570e023c71545a0a6' THEN '330'  -- 象子茶铺茶(呈贡吾悦店)
    WHEN 'oc_7a0f37656062de7bf6bce12c443dc9e5' THEN '270'  -- 象子茶铺茶(唐人财富中心店)
    WHEN 'oc_17859da0822781b2b87fdd8e2fa00094' THEN '364'  -- 象子茶铺茶(喜洲古镇店)
    WHEN 'oc_e53b1bdb694e4c4a91c38d26187ed42c' THEN '242'  -- 象子茶铺茶(嘉禾路店)
    WHEN 'oc_0cbc7198bc88c25977069373b5fe14dd' THEN '215'  -- 象子茶铺茶(嘉誉广场店)
    WHEN 'oc_bb041e4a5f87b7c1e0484b45bfbccdc4' THEN '305'  -- 象子茶铺茶(园西路店)
    WHEN 'oc_08d9722ba2db394b6ee9debbd43705e2' THEN '260'  -- 象子茶铺茶(城南店)
    WHEN 'oc_0c543ba3622d0aa96d5e0e05dce63492' THEN '314'  -- 象子茶铺茶(塘子巷店)
    WHEN 'oc_5d36d88736015f699c3ef387ef3b647b' THEN '277'  -- 象子茶铺茶(墨江店)
    WHEN 'oc_05cc7a666f42f722dcf7df0c3abe63f0' THEN '216'  -- 象子茶铺茶(大姚店)
    WHEN 'oc_2ed37d04b416679b967505aae592e728' THEN '286'  -- 象子茶铺茶(大润发店)
    WHEN 'oc_05923d4f2bf37a6051be814c3ee87e3d' THEN '263'  -- 象子茶铺茶(大理古城复兴路店)
    WHEN 'oc_68525c31f47d5e46a61fc88292cd937f' THEN '319'  -- 象子茶铺茶(大理古城玉洱路店)
    WHEN 'oc_a2796abd9dbddf808901062f8de50f7a' THEN '348'  -- 象子茶铺茶(大理机场店)
    WHEN 'oc_c3744787bf80154cf5c24de4675e9064' THEN '192'  -- 象子茶铺茶(天骄北麓店)
    WHEN 'oc_3390af8150e77604f46928cebbf7c132' THEN '297'  -- 象子茶铺茶(好悦天地店)
    WHEN 'oc_fb25a4495db1f1ab3a212112fed6167b' THEN '232'  -- 象子茶铺茶(姚安店)
    WHEN 'oc_32af8f1a2500a7b3b88bec9631e80bf9' THEN '310'  -- 象子茶铺茶(孟定店)
    WHEN 'oc_40f5c6651eb2e563a99b87f658039f31' THEN '287'  -- 象子茶铺茶(孟连店)
    WHEN 'oc_9190e777a403e6564fb17f80f04298fc' THEN '327'  -- 象子茶铺茶(安宁吾悦店)
    WHEN 'oc_7739317259e66337955db5d04ae19cf9' THEN '269'  -- 象子茶铺茶(宏泰财富广场店)
    WHEN 'oc_4196cb6c029019690e7f387dee6ae7e9' THEN '326'  -- 象子茶铺茶(官渡古镇店)
    WHEN 'oc_d5b8800e2e4884aeabbf0cc086c15947' THEN '228'  -- 象子茶铺茶(宜良云锦中心店)
    WHEN 'oc_13e12ed1c4f6245459ff54b32774bf01' THEN '312'  -- 象子茶铺茶(宣威店)
    WHEN 'oc_b931aea97013542ae9f516484475fb87' THEN '231'  -- 象子茶铺茶(宾川店)
    WHEN 'oc_5984bc68e6319e0b6993a9e9f83f9493' THEN '358'  -- 象子茶铺茶(富宁店)
    WHEN 'oc_4806a563516e9b2a2cd03f6f2835bdcf' THEN '307'  -- 象子茶铺茶(富康城店)
    WHEN 'oc_d4e410390b1987232f2abec69b866798' THEN '265'  -- 象子茶铺茶(小桂湖店)
    WHEN 'oc_15e0b2ea16f396b853991fca871b92be' THEN '186'  -- 象子茶铺茶(峨山店)
    WHEN 'oc_23c6dee063d4f9e8e434e100cf8e33eb' THEN '222'  -- 象子茶铺茶(巍山店)
    WHEN 'oc_e57ba1424f910a485607bdb83cafa210' THEN '303'  -- 象子茶铺茶(师宗店)
    WHEN 'oc_21052e49c707740345895257e9170e9d' THEN '329'  -- 象子茶铺茶(广福路爱琴海店)
    WHEN 'oc_c295f5218730d270e6280762708a752f' THEN '317'  -- 象子茶铺茶(建工新城店)
    WHEN 'oc_d20ece0f15a849405c4b2111815c794a' THEN '300'  -- 象子茶铺茶(开远店)
    WHEN 'oc_27c9fa25ee9d004895c67a8070b3ed34' THEN '207'  -- 象子茶铺茶(弥勒印象街店)
    WHEN 'oc_b1966daf19711032ee3a1dcc42fd6b69' THEN '197'  -- 象子茶铺茶(弥渡店)
    WHEN 'oc_e6c6d3ee0ad883dd2e74a9927dfc77d0' THEN '248'  -- 象子茶铺茶(彝人古镇店)
    WHEN 'oc_2dea5634fa4423cb117448651d508bfb' THEN '255'  -- 象子茶铺茶(彝海公园店)
    WHEN 'oc_ae29f18450c8a6a67805b8da6810e485' THEN '243'  -- 象子茶铺茶(彩云城店)
    WHEN 'oc_85b6b4206b3c5b0d1622fc15e0cefc24' THEN '173'  -- 象子茶铺茶(彩玉国际店)
    WHEN 'oc_4ef0eba8822ff9fc689688b874b99334' THEN '292'  -- 象子茶铺茶(德龙珠宝城店)
    WHEN 'oc_53ea18e62b8e319b7a442a9f8113a5be' THEN '257'  -- 象子茶铺茶(恒基广场店)
    WHEN 'oc_716eed324d0215766a7946b7315611b2' THEN '308'  -- 象子茶铺茶(招商花园店)
    WHEN 'oc_dbe0be797bb53a5e9aac6a1a217b9cb8' THEN '283'  -- 象子茶铺茶(文峰路店)
    WHEN 'oc_e9fc878dead17de41b6f09bd93b0de07' THEN '199'  -- 象子茶铺茶(新亚洲店)
    WHEN 'oc_06de92a81bc43a263f43e697246f7993' THEN '183'  -- 象子茶铺茶(新平店)
    WHEN 'oc_a807445cae091d193d8446d8e8e9ef41' THEN '241'  -- 象子茶铺茶(新迎新城店)
    WHEN 'oc_fdb5782857c217d10dfe6d2af2bd926f' THEN '169'  -- 象子茶铺茶(施甸交通路店)
    WHEN 'oc_0211b11ebe2c2e252b8f94dc733ec5ed' THEN '201'  -- 象子茶铺茶(施甸肆方街店)
    WHEN 'oc_ece03731bb6927ff459ee64c7063b73d' THEN '301'  -- 象子茶铺茶(旅游学院店)
    WHEN 'oc_7b709773d3f03af3142c097e406a00ba' THEN '367'  -- 象子茶铺茶(时代俊园店)
    WHEN 'oc_277dac9330fe835296b71704eb9964a8' THEN '239'  -- 象子茶铺茶(时代天街店)
    WHEN 'oc_a38badc8bc54189c67b654600769f336' THEN '191'  -- 象子茶铺茶(昌宁店)
    WHEN 'oc_4bcf5c9a0ddf74e7b07323274e686f94' THEN '188'  -- 象子茶铺茶(易门店)
    WHEN 'oc_1dae941724e3e44778c9fac2b7aef8fa' THEN '357'  -- 象子茶铺茶(昭通吾悦店)
    WHEN 'oc_7b4395fc55f948e8b52d6301a421358f' THEN '272'  -- 象子茶铺茶(昭通实验中学店)
    WHEN 'oc_c3099c6e15e9684bbb27764a71a32827' THEN '223'  -- 象子茶铺茶(晋城区店)
    WHEN 'oc_a32fa9b648c02a8643234cc09506caf4' THEN '250'  -- 象子茶铺茶(普洱悦城店)
    WHEN 'oc_fe2681d97414b640a91485dfe59280a3' THEN '256'  -- 象子茶铺茶(景东店)
    WHEN 'oc_68fe8ca181d6d625d28ca3545ef36254' THEN '206'  -- 象子茶铺茶(景谷店)
    WHEN 'oc_e63e45b4e404fdb1ee5e409a94f33bcc' THEN '262'  -- 象子茶铺茶(曲靖万达店)
    WHEN 'oc_6bcfedf6c5d3ee2f7b91faca1e1e548e' THEN '285'  -- 象子茶铺茶(曼城店)
    WHEN 'oc_24298dd2217f041c467fe1744bd6a32d' THEN '332'  -- 象子茶铺茶(月光印巷店)
    WHEN 'oc_1aecb6e3781c4934c481cd148e4f2e81' THEN '304'  -- 象子茶铺茶(望谟店)
    WHEN 'oc_c3d988189c52e18a3f2dc9d204b2be0d' THEN '275'  -- 象子茶铺茶(束河古镇店)
    WHEN 'oc_523527a4702b6695771e6206df984811' THEN '203'  -- 象子茶铺茶(板桥店)
    WHEN 'oc_7200e61cbee4471e648830bcbaaf2586' THEN '271'  -- 象子茶铺茶(果林广场店)
    WHEN 'oc_274711c29a9927e72ce859344bb7fe39' THEN '282'  -- 象子茶铺茶(柏联广场店)
    WHEN 'oc_92824dfb71dc432bc94a3726752d6a13' THEN '209'  -- 象子茶铺茶(梁河店)
    WHEN 'oc_e624967481827c8339c3874bf3475b6f' THEN '322'  -- 象子茶铺茶(欢乐橙店)
    WHEN 'oc_04a210c3e2ca031e79587f8b4c699d2f' THEN '205'  -- 象子茶铺茶(正阳店)
    WHEN 'oc_53208b64791c960a5c92342b86cad8d0' THEN '178'  -- 象子茶铺茶(永平店)
    WHEN 'oc_754971d8d76721b8edb9c67facd34a72' THEN '362'  -- 象子茶铺茶(永德店)
    WHEN 'oc_f8860f1a5432d5bd617d30be29c230a4' THEN '208'  -- 象子茶铺茶(永胜店)
    WHEN 'oc_c631121dc56ebbbd6e335160f2351bff' THEN '213'  -- 象子茶铺茶(江川店)
    WHEN 'oc_ee0d311997541abe499d1a97310ad46f' THEN '185'  -- 象子茶铺茶(沧源店)
    WHEN 'oc_d6696d672cc0f925b36b18fa7e4bf1a2' THEN '294'  -- 象子茶铺茶(河口店)
    WHEN 'oc_3b789156605aad8195346ba6f1b35e9f' THEN '309'  -- 象子茶铺茶(沾益区西正街店)
    WHEN 'oc_751a225e04ad93ac227d242358eec2df' THEN '182'  -- 象子茶铺茶(泰业一店)
    WHEN 'oc_f75fe3fcf965d9e9803a234687776211' THEN '315'  -- 象子茶铺茶(泰业二店)
    WHEN 'oc_f030224aaed78e47dd4a6a563eb76220' THEN '313'  -- 象子茶铺茶(泸西店)
    WHEN 'oc_3a9699cc6879aaf2cf9b583612022cc0' THEN '290'  -- 象子茶铺茶(洱源店)
    WHEN 'oc_b85930c803d4f352a7de6bc66f082793' THEN '171'  -- 象子茶铺茶(海乐达人汇店)
    WHEN 'oc_fec7be5898e970cc649922d0905c8442' THEN '221'  -- 象子茶铺茶(滇池名门店)
    WHEN 'oc_98dca0efe5ac5ad3471f5015757b4d8d' THEN '289'  -- 象子茶铺茶(满江店)
    WHEN 'oc_307d7917f9cbc353aeabb3cb7409a448' THEN '316'  -- 象子茶铺茶(漾濞店)
    WHEN 'oc_cb46fee660214eec4a13a8f16d3438e2' THEN '288'  -- 象子茶铺茶(潞江坝店)
    WHEN 'oc_fc0151fb14fdf1759b6e7c1b17dde579' THEN '177'  -- 象子茶铺茶(澄江店)
    WHEN 'oc_c34d38d06f82a431590e042376bec76e' THEN '218'  -- 象子茶铺茶(澜沧店)
    WHEN 'oc_950c2f61111c0b858ff49ebaaab4d8ca' THEN '210'  -- 象子茶铺茶(牟定店)
    WHEN 'oc_12d0664d27e194c9cb459791c3b48853' THEN '258'  -- 象子茶铺茶(玉龙店)
    WHEN 'oc_444dedfd8550b0d774dbc8bc8fd456cb' THEN '196'  -- 象子茶铺茶(王府井滇池小镇店)
    WHEN 'oc_5c5ee4effda873962f14f6657fa074b3' THEN '238'  -- 象子茶铺茶(瑞鼎城店)
    WHEN 'oc_6b38f0483349099a6e3aec3776807f2a' THEN '261'  -- 象子茶铺茶(白沙古镇店)
    WHEN 'oc_93ec26ab34f58ab3d58b234ba696ad94' THEN '355'  -- 象子茶铺茶(白龙派公园店)
    WHEN 'oc_98c00abc3e4c27f50e55320534ab0d5e' THEN '184'  -- 象子茶铺茶(盈江店)
    WHEN 'oc_33e8ecbbbedb953aad30af2a7ae50a7b' THEN '229'  -- 象子茶铺茶(盘州店)
    WHEN 'oc_d00f54d8f93e4b9a88493997c3209202' THEN '202'  -- 象子茶铺茶(盛和雅苑店)
    WHEN 'oc_9ccb0480106f6f99e8edf7eaaa9d2f4b' THEN '254'  -- 象子茶铺茶(石屏店)
    WHEN 'oc_c5b23b4c47a402b89080aef0ca78c2a5' THEN '259'  -- 象子茶铺茶(祥和店)
    WHEN 'oc_baff826998dc7faa5807161036719e5c' THEN '324'  -- 象子茶铺茶(禄丰店)
    WHEN 'oc_907fee96c237467334a0634f0d21ca66' THEN '204'  -- 象子茶铺茶(禄劝店)
    WHEN 'oc_06895ffa3c4b7db5a6511a6378c58e33' THEN '233'  -- 象子茶铺茶(福保店)
    WHEN 'oc_a50e3e0410635586fcc63a61cce825d3' THEN '172'  -- 象子茶铺茶(福德店)
    WHEN 'oc_96dc6aba6d83584e7d12f086ac6fb348' THEN '247'  -- 象子茶铺茶(第七街区店)
    WHEN 'oc_99d4f912ad10558002010eca168fd4e9' THEN '253'  -- 象子茶铺茶(紫金中心店)
    WHEN 'oc_9ad71ecec4380888266ce346c8c483dc' THEN '328'  -- 象子茶铺茶(紫陶街店)
    WHEN 'oc_7588f3edff852f96683e5d6639d2ccdb' THEN '325'  -- 象子茶铺茶(红河店)
    WHEN 'oc_1db141902c9e6ac3f793e5d5eea9c6c2' THEN '276'  -- 象子茶铺茶(绿春店)
    WHEN 'oc_bf5f9bd735071084abe4d33aeb1b4de8' THEN '167'  -- 象子茶铺茶(翠湖店)
    WHEN 'oc_8c030838d2bfb5e9454d489ab5b706f8' THEN '194'  -- 象子茶铺茶(翡翠园店)
    WHEN 'oc_d4f0908660bb5f4fe4175cee934a2329' THEN '179'  -- 象子茶铺茶(翰林大观店)
    WHEN 'oc_a5efa3df34edabbe1e39f33de76498f9' THEN '252'  -- 象子茶铺茶(腾冲天成店)
    WHEN 'oc_beeadb4a361ed81cde53e2a0e8728d03' THEN '174'  -- 象子茶铺茶(芒市三棵树店)
    WHEN 'oc_afb137cc32afb1c6e88b89b8958aa2c8' THEN '365'  -- 象子茶铺茶(芒市财富广场店)
    WHEN 'oc_b0b6ae7fa135c887d57e0ab56b385b14' THEN '299'  -- 象子茶铺茶(花柯店)
    WHEN 'oc_a6b9eaab50166fd0d4085e60868ce3ed' THEN '356'  -- 象子茶铺茶(茶马花街店)
    WHEN 'oc_2b9c82a405ee6c0eb0bf143b05bb4039' THEN '187'  -- 象子茶铺茶(西岸店)
    WHEN 'oc_b03c5750d7eecd3a693182c4d8feb81f' THEN '320'  -- 象子茶铺茶(财大龙泉路店)
    WHEN 'oc_d1ae64b95873d0df6b32bbadf4cc9caa' THEN '349'  -- 象子茶铺茶(里外里店)
    WHEN 'oc_994578bbb2228c19e014db1aaf24115a' THEN '323'  -- 象子茶铺茶(金池购物中心店)
    WHEN 'oc_3294bf48fc090f245df476997e75f2ac' THEN '264'  -- 象子茶铺茶(金色时代店)
    WHEN 'oc_381c34043fee2cc3770e04f779462fd7' THEN '193'  -- 象子茶铺茶(金辰店)
    WHEN 'oc_acf6bff1fc9dd3d693b8817bae625768' THEN '168'  -- 象子茶铺茶(银海尚御店)
    WHEN 'oc_2b8991de8f2038dc0d029762e89b07ef' THEN '224'  -- 象子茶铺茶(镇沅店)
    WHEN 'oc_9e9820f330aca0b93999080eebf2140d' THEN '359'  -- 象子茶铺茶(镇雄店)
    WHEN 'oc_a9f1fd5b5ef8178edcf7b4f02adcd635' THEN '279'  -- 象子茶铺茶(长征大道店)
    WHEN 'oc_e2ba82b2e91998b131a3c7a54e93bc3c' THEN '352'  -- 象子茶铺茶(长水机场到达厅店)
    WHEN 'oc_b0da1aadfed4e2579dcb5224117d22e6' THEN '353'  -- 象子茶铺茶(长水机场卫星厅店)
    WHEN 'oc_505688162590a5f3533e541add2b60e0' THEN '347'  -- 象子茶铺茶(长水机场店)
    WHEN 'oc_104f354b03ba997b7ef8b41c5b2baa07' THEN '298'  -- 象子茶铺茶(陆良店)
    WHEN 'oc_ac0edeffa0e3215b740f4ed6f1bf7d7e' THEN '237'  -- 象子茶铺茶(陇川店)
    WHEN 'oc_b6dd7fdb6a8df39a385f64bd07eef088' THEN '189'  -- 象子茶铺茶(高山铺店)
    WHEN 'oc_5aecde41a5b018c37a881dc01af74815' THEN '235'  -- 象子茶铺茶(鹤庆店)
    ELSE store_code
END
WHERE del_flag = 0 AND chat_id IN (
    'oc_ad0d326c0ade64a2fec6ea55ffbc2c66',
    'oc_6c15c0a7436488e6afc4a23495738202',
    'oc_22dd707fca076b08ae4ce5afdb1dcc94',
    'oc_b2025452510d1f4e4bcd37a9b3a26105',
    'oc_2152dbf41937d36e145b32af10ad4ea1',
    'oc_16172865153f11b1ba21b0afe957b9d0',
    'oc_607fb56228fbefa1a7b6fdec21442e7b',
    'oc_1ba79daf3ac006d14203f5f6bb9773e8',
    'oc_56e03201fa59d62fe908da8ee8d392fd',
    'oc_40443900a43769c8a878b6fc63a24d34',
    'oc_1267d8636e3bd782628fd1e5b4a78fed',
    'oc_ea916ee096f79357c3ce2bcf806b7e8f',
    'oc_1bb44b73fa0d38b9a78fd5eae25397ea',
    'oc_d45585893e393d844331ef34a9518fdb',
    'oc_e55229e50b249ab24f7ba7efcfa28aae',
    'oc_bea97c025f07ab68cb25f708ddf6690b',
    'oc_4df03b617a5b6e773986f3f66eba8f2f',
    'oc_c573c17061795fb0dbc8f9af66b9a0b2',
    'oc_fc935c54b075076f67b504ea8b10d98d',
    'oc_1459c84900eac6ce428faf26362390c1',
    'oc_8a1bcff6daa83c7d415775788d7be748',
    'oc_cdb70b3110c2de3d5db6900c0ddda27e',
    'oc_21d3e453e690078427a2c0f4fa8c6745',
    'oc_142fe4a887918b1774fd4b40a9909d02',
    'oc_667c4d093bc96f7563ae5b9d61758a0f',
    'oc_ca15393203d15c303a9b77fc254fd81e',
    'oc_307cf62de9408becce20a559056bf6bf',
    'oc_19e27ace2a8a4625278fa16f2f9c6196',
    'oc_a0a61b9bacdf9e6cc95d5028ac735739',
    'oc_f7525779dd69827573063154211cc399',
    'oc_026ac97df5c953c214420a3cfc4d1d41',
    'oc_eb9b751396c9a226ac126f8768881c79',
    'oc_7a7e95a18dac274ab677357c8ae442b3',
    'oc_fd012d9e81fa434362a7330d4aa2f6ad',
    'oc_dd94bb051802c1b2ab1c41a6419ddc9f',
    'oc_8b0d65e1324cd31bd0e4100689997c23',
    'oc_5ffdf74aeab80b8fa26401a5fa8f2bf0',
    'oc_721e64fa72cee60bb01731490cf63a99',
    'oc_196c18e7d67c44c31204a10bcf86d908',
    'oc_c42b0dd26e5448e1614cb27344675560',
    'oc_fb772bf297e66262b2d0fe4ef54a4fbd',
    'oc_d9ce2c4ef405c4e66d740b76e568b9cb',
    'oc_dbcea0ab7210ed4b7fc6fa9d779bd213',
    'oc_1e9bd36cf4b5b34598a543ab617bca39',
    'oc_97651ecb4f498a969846bca97e5a4e49',
    'oc_d84e47deb6f6302570e023c71545a0a6',
    'oc_7a0f37656062de7bf6bce12c443dc9e5',
    'oc_17859da0822781b2b87fdd8e2fa00094',
    'oc_e53b1bdb694e4c4a91c38d26187ed42c',
    'oc_0cbc7198bc88c25977069373b5fe14dd',
    'oc_bb041e4a5f87b7c1e0484b45bfbccdc4',
    'oc_08d9722ba2db394b6ee9debbd43705e2',
    'oc_0c543ba3622d0aa96d5e0e05dce63492',
    'oc_5d36d88736015f699c3ef387ef3b647b',
    'oc_05cc7a666f42f722dcf7df0c3abe63f0',
    'oc_2ed37d04b416679b967505aae592e728',
    'oc_05923d4f2bf37a6051be814c3ee87e3d',
    'oc_68525c31f47d5e46a61fc88292cd937f',
    'oc_a2796abd9dbddf808901062f8de50f7a',
    'oc_c3744787bf80154cf5c24de4675e9064',
    'oc_3390af8150e77604f46928cebbf7c132',
    'oc_fb25a4495db1f1ab3a212112fed6167b',
    'oc_32af8f1a2500a7b3b88bec9631e80bf9',
    'oc_40f5c6651eb2e563a99b87f658039f31',
    'oc_9190e777a403e6564fb17f80f04298fc',
    'oc_7739317259e66337955db5d04ae19cf9',
    'oc_4196cb6c029019690e7f387dee6ae7e9',
    'oc_d5b8800e2e4884aeabbf0cc086c15947',
    'oc_13e12ed1c4f6245459ff54b32774bf01',
    'oc_b931aea97013542ae9f516484475fb87',
    'oc_5984bc68e6319e0b6993a9e9f83f9493',
    'oc_4806a563516e9b2a2cd03f6f2835bdcf',
    'oc_d4e410390b1987232f2abec69b866798',
    'oc_15e0b2ea16f396b853991fca871b92be',
    'oc_23c6dee063d4f9e8e434e100cf8e33eb',
    'oc_e57ba1424f910a485607bdb83cafa210',
    'oc_21052e49c707740345895257e9170e9d',
    'oc_c295f5218730d270e6280762708a752f',
    'oc_d20ece0f15a849405c4b2111815c794a',
    'oc_27c9fa25ee9d004895c67a8070b3ed34',
    'oc_b1966daf19711032ee3a1dcc42fd6b69',
    'oc_e6c6d3ee0ad883dd2e74a9927dfc77d0',
    'oc_2dea5634fa4423cb117448651d508bfb',
    'oc_ae29f18450c8a6a67805b8da6810e485',
    'oc_85b6b4206b3c5b0d1622fc15e0cefc24',
    'oc_4ef0eba8822ff9fc689688b874b99334',
    'oc_53ea18e62b8e319b7a442a9f8113a5be',
    'oc_716eed324d0215766a7946b7315611b2',
    'oc_dbe0be797bb53a5e9aac6a1a217b9cb8',
    'oc_e9fc878dead17de41b6f09bd93b0de07',
    'oc_06de92a81bc43a263f43e697246f7993',
    'oc_a807445cae091d193d8446d8e8e9ef41',
    'oc_fdb5782857c217d10dfe6d2af2bd926f',
    'oc_0211b11ebe2c2e252b8f94dc733ec5ed',
    'oc_ece03731bb6927ff459ee64c7063b73d',
    'oc_7b709773d3f03af3142c097e406a00ba',
    'oc_277dac9330fe835296b71704eb9964a8',
    'oc_a38badc8bc54189c67b654600769f336',
    'oc_4bcf5c9a0ddf74e7b07323274e686f94',
    'oc_1dae941724e3e44778c9fac2b7aef8fa',
    'oc_7b4395fc55f948e8b52d6301a421358f',
    'oc_c3099c6e15e9684bbb27764a71a32827',
    'oc_a32fa9b648c02a8643234cc09506caf4',
    'oc_fe2681d97414b640a91485dfe59280a3',
    'oc_68fe8ca181d6d625d28ca3545ef36254',
    'oc_e63e45b4e404fdb1ee5e409a94f33bcc',
    'oc_6bcfedf6c5d3ee2f7b91faca1e1e548e',
    'oc_24298dd2217f041c467fe1744bd6a32d',
    'oc_1aecb6e3781c4934c481cd148e4f2e81',
    'oc_c3d988189c52e18a3f2dc9d204b2be0d',
    'oc_523527a4702b6695771e6206df984811',
    'oc_7200e61cbee4471e648830bcbaaf2586',
    'oc_274711c29a9927e72ce859344bb7fe39',
    'oc_92824dfb71dc432bc94a3726752d6a13',
    'oc_e624967481827c8339c3874bf3475b6f',
    'oc_04a210c3e2ca031e79587f8b4c699d2f',
    'oc_53208b64791c960a5c92342b86cad8d0',
    'oc_754971d8d76721b8edb9c67facd34a72',
    'oc_f8860f1a5432d5bd617d30be29c230a4',
    'oc_c631121dc56ebbbd6e335160f2351bff',
    'oc_ee0d311997541abe499d1a97310ad46f',
    'oc_d6696d672cc0f925b36b18fa7e4bf1a2',
    'oc_3b789156605aad8195346ba6f1b35e9f',
    'oc_751a225e04ad93ac227d242358eec2df',
    'oc_f75fe3fcf965d9e9803a234687776211',
    'oc_f030224aaed78e47dd4a6a563eb76220',
    'oc_3a9699cc6879aaf2cf9b583612022cc0',
    'oc_b85930c803d4f352a7de6bc66f082793',
    'oc_fec7be5898e970cc649922d0905c8442',
    'oc_98dca0efe5ac5ad3471f5015757b4d8d',
    'oc_307d7917f9cbc353aeabb3cb7409a448',
    'oc_cb46fee660214eec4a13a8f16d3438e2',
    'oc_fc0151fb14fdf1759b6e7c1b17dde579',
    'oc_c34d38d06f82a431590e042376bec76e',
    'oc_950c2f61111c0b858ff49ebaaab4d8ca',
    'oc_12d0664d27e194c9cb459791c3b48853',
    'oc_444dedfd8550b0d774dbc8bc8fd456cb',
    'oc_5c5ee4effda873962f14f6657fa074b3',
    'oc_6b38f0483349099a6e3aec3776807f2a',
    'oc_93ec26ab34f58ab3d58b234ba696ad94',
    'oc_98c00abc3e4c27f50e55320534ab0d5e',
    'oc_33e8ecbbbedb953aad30af2a7ae50a7b',
    'oc_d00f54d8f93e4b9a88493997c3209202',
    'oc_9ccb0480106f6f99e8edf7eaaa9d2f4b',
    'oc_c5b23b4c47a402b89080aef0ca78c2a5',
    'oc_baff826998dc7faa5807161036719e5c',
    'oc_907fee96c237467334a0634f0d21ca66',
    'oc_06895ffa3c4b7db5a6511a6378c58e33',
    'oc_a50e3e0410635586fcc63a61cce825d3',
    'oc_96dc6aba6d83584e7d12f086ac6fb348',
    'oc_99d4f912ad10558002010eca168fd4e9',
    'oc_9ad71ecec4380888266ce346c8c483dc',
    'oc_7588f3edff852f96683e5d6639d2ccdb',
    'oc_1db141902c9e6ac3f793e5d5eea9c6c2',
    'oc_bf5f9bd735071084abe4d33aeb1b4de8',
    'oc_8c030838d2bfb5e9454d489ab5b706f8',
    'oc_d4f0908660bb5f4fe4175cee934a2329',
    'oc_a5efa3df34edabbe1e39f33de76498f9',
    'oc_beeadb4a361ed81cde53e2a0e8728d03',
    'oc_afb137cc32afb1c6e88b89b8958aa2c8',
    'oc_b0b6ae7fa135c887d57e0ab56b385b14',
    'oc_a6b9eaab50166fd0d4085e60868ce3ed',
    'oc_2b9c82a405ee6c0eb0bf143b05bb4039',
    'oc_b03c5750d7eecd3a693182c4d8feb81f',
    'oc_d1ae64b95873d0df6b32bbadf4cc9caa',
    'oc_994578bbb2228c19e014db1aaf24115a',
    'oc_3294bf48fc090f245df476997e75f2ac',
    'oc_381c34043fee2cc3770e04f779462fd7',
    'oc_acf6bff1fc9dd3d693b8817bae625768',
    'oc_2b8991de8f2038dc0d029762e89b07ef',
    'oc_9e9820f330aca0b93999080eebf2140d',
    'oc_a9f1fd5b5ef8178edcf7b4f02adcd635',
    'oc_e2ba82b2e91998b131a3c7a54e93bc3c',
    'oc_b0da1aadfed4e2579dcb5224117d22e6',
    'oc_505688162590a5f3533e541add2b60e0',
    'oc_104f354b03ba997b7ef8b41c5b2baa07',
    'oc_ac0edeffa0e3215b740f4ed6f1bf7d7e',
    'oc_b6dd7fdb6a8df39a385f64bd07eef088',
    'oc_5aecde41a5b018c37a881dc01af74815'
);

-- 4) 校验：查看更新结果
SELECT store_id, store_name, chat_id, store_code
FROM store_inventory.store_info
WHERE del_flag = 0 AND chat_id IN (
    'oc_ad0d326c0ade64a2fec6ea55ffbc2c66',
    'oc_6c15c0a7436488e6afc4a23495738202',
    'oc_22dd707fca076b08ae4ce5afdb1dcc94',
    'oc_b2025452510d1f4e4bcd37a9b3a26105',
    'oc_2152dbf41937d36e145b32af10ad4ea1',
    'oc_16172865153f11b1ba21b0afe957b9d0',
    'oc_607fb56228fbefa1a7b6fdec21442e7b',
    'oc_1ba79daf3ac006d14203f5f6bb9773e8',
    'oc_56e03201fa59d62fe908da8ee8d392fd',
    'oc_40443900a43769c8a878b6fc63a24d34',
    'oc_1267d8636e3bd782628fd1e5b4a78fed',
    'oc_ea916ee096f79357c3ce2bcf806b7e8f',
    'oc_1bb44b73fa0d38b9a78fd5eae25397ea',
    'oc_d45585893e393d844331ef34a9518fdb',
    'oc_e55229e50b249ab24f7ba7efcfa28aae',
    'oc_bea97c025f07ab68cb25f708ddf6690b',
    'oc_4df03b617a5b6e773986f3f66eba8f2f',
    'oc_c573c17061795fb0dbc8f9af66b9a0b2',
    'oc_fc935c54b075076f67b504ea8b10d98d',
    'oc_1459c84900eac6ce428faf26362390c1',
    'oc_8a1bcff6daa83c7d415775788d7be748',
    'oc_cdb70b3110c2de3d5db6900c0ddda27e',
    'oc_21d3e453e690078427a2c0f4fa8c6745',
    'oc_142fe4a887918b1774fd4b40a9909d02',
    'oc_667c4d093bc96f7563ae5b9d61758a0f',
    'oc_ca15393203d15c303a9b77fc254fd81e',
    'oc_307cf62de9408becce20a559056bf6bf',
    'oc_19e27ace2a8a4625278fa16f2f9c6196',
    'oc_a0a61b9bacdf9e6cc95d5028ac735739',
    'oc_f7525779dd69827573063154211cc399',
    'oc_026ac97df5c953c214420a3cfc4d1d41',
    'oc_eb9b751396c9a226ac126f8768881c79',
    'oc_7a7e95a18dac274ab677357c8ae442b3',
    'oc_fd012d9e81fa434362a7330d4aa2f6ad',
    'oc_dd94bb051802c1b2ab1c41a6419ddc9f',
    'oc_8b0d65e1324cd31bd0e4100689997c23',
    'oc_5ffdf74aeab80b8fa26401a5fa8f2bf0',
    'oc_721e64fa72cee60bb01731490cf63a99',
    'oc_196c18e7d67c44c31204a10bcf86d908',
    'oc_c42b0dd26e5448e1614cb27344675560',
    'oc_fb772bf297e66262b2d0fe4ef54a4fbd',
    'oc_d9ce2c4ef405c4e66d740b76e568b9cb',
    'oc_dbcea0ab7210ed4b7fc6fa9d779bd213',
    'oc_1e9bd36cf4b5b34598a543ab617bca39',
    'oc_97651ecb4f498a969846bca97e5a4e49',
    'oc_d84e47deb6f6302570e023c71545a0a6',
    'oc_7a0f37656062de7bf6bce12c443dc9e5',
    'oc_17859da0822781b2b87fdd8e2fa00094',
    'oc_e53b1bdb694e4c4a91c38d26187ed42c',
    'oc_0cbc7198bc88c25977069373b5fe14dd',
    'oc_bb041e4a5f87b7c1e0484b45bfbccdc4',
    'oc_08d9722ba2db394b6ee9debbd43705e2',
    'oc_0c543ba3622d0aa96d5e0e05dce63492',
    'oc_5d36d88736015f699c3ef387ef3b647b',
    'oc_05cc7a666f42f722dcf7df0c3abe63f0',
    'oc_2ed37d04b416679b967505aae592e728',
    'oc_05923d4f2bf37a6051be814c3ee87e3d',
    'oc_68525c31f47d5e46a61fc88292cd937f',
    'oc_a2796abd9dbddf808901062f8de50f7a',
    'oc_c3744787bf80154cf5c24de4675e9064',
    'oc_3390af8150e77604f46928cebbf7c132',
    'oc_fb25a4495db1f1ab3a212112fed6167b',
    'oc_32af8f1a2500a7b3b88bec9631e80bf9',
    'oc_40f5c6651eb2e563a99b87f658039f31',
    'oc_9190e777a403e6564fb17f80f04298fc',
    'oc_7739317259e66337955db5d04ae19cf9',
    'oc_4196cb6c029019690e7f387dee6ae7e9',
    'oc_d5b8800e2e4884aeabbf0cc086c15947',
    'oc_13e12ed1c4f6245459ff54b32774bf01',
    'oc_b931aea97013542ae9f516484475fb87',
    'oc_5984bc68e6319e0b6993a9e9f83f9493',
    'oc_4806a563516e9b2a2cd03f6f2835bdcf',
    'oc_d4e410390b1987232f2abec69b866798',
    'oc_15e0b2ea16f396b853991fca871b92be',
    'oc_23c6dee063d4f9e8e434e100cf8e33eb',
    'oc_e57ba1424f910a485607bdb83cafa210',
    'oc_21052e49c707740345895257e9170e9d',
    'oc_c295f5218730d270e6280762708a752f',
    'oc_d20ece0f15a849405c4b2111815c794a',
    'oc_27c9fa25ee9d004895c67a8070b3ed34',
    'oc_b1966daf19711032ee3a1dcc42fd6b69',
    'oc_e6c6d3ee0ad883dd2e74a9927dfc77d0',
    'oc_2dea5634fa4423cb117448651d508bfb',
    'oc_ae29f18450c8a6a67805b8da6810e485',
    'oc_85b6b4206b3c5b0d1622fc15e0cefc24',
    'oc_4ef0eba8822ff9fc689688b874b99334',
    'oc_53ea18e62b8e319b7a442a9f8113a5be',
    'oc_716eed324d0215766a7946b7315611b2',
    'oc_dbe0be797bb53a5e9aac6a1a217b9cb8',
    'oc_e9fc878dead17de41b6f09bd93b0de07',
    'oc_06de92a81bc43a263f43e697246f7993',
    'oc_a807445cae091d193d8446d8e8e9ef41',
    'oc_fdb5782857c217d10dfe6d2af2bd926f',
    'oc_0211b11ebe2c2e252b8f94dc733ec5ed',
    'oc_ece03731bb6927ff459ee64c7063b73d',
    'oc_7b709773d3f03af3142c097e406a00ba',
    'oc_277dac9330fe835296b71704eb9964a8',
    'oc_a38badc8bc54189c67b654600769f336',
    'oc_4bcf5c9a0ddf74e7b07323274e686f94',
    'oc_1dae941724e3e44778c9fac2b7aef8fa',
    'oc_7b4395fc55f948e8b52d6301a421358f',
    'oc_c3099c6e15e9684bbb27764a71a32827',
    'oc_a32fa9b648c02a8643234cc09506caf4',
    'oc_fe2681d97414b640a91485dfe59280a3',
    'oc_68fe8ca181d6d625d28ca3545ef36254',
    'oc_e63e45b4e404fdb1ee5e409a94f33bcc',
    'oc_6bcfedf6c5d3ee2f7b91faca1e1e548e',
    'oc_24298dd2217f041c467fe1744bd6a32d',
    'oc_1aecb6e3781c4934c481cd148e4f2e81',
    'oc_c3d988189c52e18a3f2dc9d204b2be0d',
    'oc_523527a4702b6695771e6206df984811',
    'oc_7200e61cbee4471e648830bcbaaf2586',
    'oc_274711c29a9927e72ce859344bb7fe39',
    'oc_92824dfb71dc432bc94a3726752d6a13',
    'oc_e624967481827c8339c3874bf3475b6f',
    'oc_04a210c3e2ca031e79587f8b4c699d2f',
    'oc_53208b64791c960a5c92342b86cad8d0',
    'oc_754971d8d76721b8edb9c67facd34a72',
    'oc_f8860f1a5432d5bd617d30be29c230a4',
    'oc_c631121dc56ebbbd6e335160f2351bff',
    'oc_ee0d311997541abe499d1a97310ad46f',
    'oc_d6696d672cc0f925b36b18fa7e4bf1a2',
    'oc_3b789156605aad8195346ba6f1b35e9f',
    'oc_751a225e04ad93ac227d242358eec2df',
    'oc_f75fe3fcf965d9e9803a234687776211',
    'oc_f030224aaed78e47dd4a6a563eb76220',
    'oc_3a9699cc6879aaf2cf9b583612022cc0',
    'oc_b85930c803d4f352a7de6bc66f082793',
    'oc_fec7be5898e970cc649922d0905c8442',
    'oc_98dca0efe5ac5ad3471f5015757b4d8d',
    'oc_307d7917f9cbc353aeabb3cb7409a448',
    'oc_cb46fee660214eec4a13a8f16d3438e2',
    'oc_fc0151fb14fdf1759b6e7c1b17dde579',
    'oc_c34d38d06f82a431590e042376bec76e',
    'oc_950c2f61111c0b858ff49ebaaab4d8ca',
    'oc_12d0664d27e194c9cb459791c3b48853',
    'oc_444dedfd8550b0d774dbc8bc8fd456cb',
    'oc_5c5ee4effda873962f14f6657fa074b3',
    'oc_6b38f0483349099a6e3aec3776807f2a',
    'oc_93ec26ab34f58ab3d58b234ba696ad94',
    'oc_98c00abc3e4c27f50e55320534ab0d5e',
    'oc_33e8ecbbbedb953aad30af2a7ae50a7b',
    'oc_d00f54d8f93e4b9a88493997c3209202',
    'oc_9ccb0480106f6f99e8edf7eaaa9d2f4b',
    'oc_c5b23b4c47a402b89080aef0ca78c2a5',
    'oc_baff826998dc7faa5807161036719e5c',
    'oc_907fee96c237467334a0634f0d21ca66',
    'oc_06895ffa3c4b7db5a6511a6378c58e33',
    'oc_a50e3e0410635586fcc63a61cce825d3',
    'oc_96dc6aba6d83584e7d12f086ac6fb348',
    'oc_99d4f912ad10558002010eca168fd4e9',
    'oc_9ad71ecec4380888266ce346c8c483dc',
    'oc_7588f3edff852f96683e5d6639d2ccdb',
    'oc_1db141902c9e6ac3f793e5d5eea9c6c2',
    'oc_bf5f9bd735071084abe4d33aeb1b4de8',
    'oc_8c030838d2bfb5e9454d489ab5b706f8',
    'oc_d4f0908660bb5f4fe4175cee934a2329',
    'oc_a5efa3df34edabbe1e39f33de76498f9',
    'oc_beeadb4a361ed81cde53e2a0e8728d03',
    'oc_afb137cc32afb1c6e88b89b8958aa2c8',
    'oc_b0b6ae7fa135c887d57e0ab56b385b14',
    'oc_a6b9eaab50166fd0d4085e60868ce3ed',
    'oc_2b9c82a405ee6c0eb0bf143b05bb4039',
    'oc_b03c5750d7eecd3a693182c4d8feb81f',
    'oc_d1ae64b95873d0df6b32bbadf4cc9caa',
    'oc_994578bbb2228c19e014db1aaf24115a',
    'oc_3294bf48fc090f245df476997e75f2ac',
    'oc_381c34043fee2cc3770e04f779462fd7',
    'oc_acf6bff1fc9dd3d693b8817bae625768',
    'oc_2b8991de8f2038dc0d029762e89b07ef',
    'oc_9e9820f330aca0b93999080eebf2140d',
    'oc_a9f1fd5b5ef8178edcf7b4f02adcd635',
    'oc_e2ba82b2e91998b131a3c7a54e93bc3c',
    'oc_b0da1aadfed4e2579dcb5224117d22e6',
    'oc_505688162590a5f3533e541add2b60e0',
    'oc_104f354b03ba997b7ef8b41c5b2baa07',
    'oc_ac0edeffa0e3215b740f4ed6f1bf7d7e',
    'oc_b6dd7fdb6a8df39a385f64bd07eef088',
    'oc_5aecde41a5b018c37a881dc01af74815'
)
ORDER BY store_name;

-- ============================================================
-- 以下 7 家未自动更新，请人工确认后手工补：
-- ============================================================
-- A) 群ID缺失（接口未返回 feishuChatId，无法匹配）：
--     象子茶铺茶(内蒙店) (id=331, code=S331A9F4C1157)  ← 无 feishuChatId
-- B) 群ID重复（同一群ID对应多家门店，无法确定归属，跳过）：
--     象子茶铺茶(华宁店) (id=249, code=SA6AFF99969AE)  ← 群 oc_89e5e461d495a86284f24472cc343aeb 与其他门店共用
--     象子茶铺茶(广南店) (id=296, code=S6B5CAB83CEB1)  ← 群 oc_3af29202a5f0f4b3ab71e7597ba75d24 与其他门店共用
--     象子茶铺茶(广南店暂停营业) (id=219, code=SA21E5497A551)  ← 群 oc_3af29202a5f0f4b3ab71e7597ba75d24 与其他门店共用
--     象子茶铺茶(盘溪店) (id=360, code=S6638848DECDF)  ← 群 oc_89e5e461d495a86284f24472cc343aeb 与其他门店共用
--     象子茶铺茶(镇康店) (id=306, code=S6A935BF90AAC)  ← 群 oc_c999469b12ca7b6d80a1b0e5035b080c 与其他门店共用
--     象子茶铺茶(镇康店) (id=350, code=S9FD222417D1E)  ← 群 oc_c999469b12ca7b6d80a1b0e5035b080c 与其他门店共用
-- 人工确认后参考模板：
-- UPDATE store_inventory.store_info SET store_code='<id>' WHERE del_flag=0 AND chat_id='<群ID>' AND store_name LIKE '%<门店名>%';

-- 校验更新行数（应等于预检/第3步影响行数）
SELECT COUNT(*) AS updated_rows FROM store_inventory.store_info WHERE del_flag=0 AND store_code REGEXP "^[0-9]+$";
