-- ============================================================
-- 回填 store_info.chat_id（按门店名映射 store_chat_mapping 生成）
-- 生成来源：zgstore.xls × jlstroe.xls，按“去品牌前缀+位置后缀”精确匹配
-- 自动回填 152 家；另有 20 家待人工确认（见文件末尾）
-- ============================================================

UPDATE store_inventory.store_info
SET chat_id = CASE store_id
    WHEN 'cmpaocun900l93pmbczh07a1b' THEN 'oc_2152dbf41937d36e145b32af10ad4ea1'  -- 象子茶铺茶万宏国际店  ←  昆明盘龙万宏国际店
    WHEN 'cmpaocu2b00fu3pmbwf04mo09' THEN 'oc_1ba79daf3ac006d14203f5f6bb9773e8'  -- 象子茶铺茶世纪金源店  ←  昆明官渡世纪金源店
    WHEN 'cmpaocugt00jg3pmbibe1oo1b' THEN 'oc_56e03201fa59d62fe908da8ee8d392fd'  -- 象子茶铺茶丘北店  ←  文山丘北店
    WHEN 'cmpnw02j802903pk30xpbgnq3' THEN 'oc_ea916ee096f79357c3ce2bcf806b7e8f'  -- 象子茶铺茶东川店  ←  昆明东川店
    WHEN 'cmpaocua000hn3pmbe8m4pdif' THEN 'oc_1bb44b73fa0d38b9a78fd5eae25397ea'  -- 象子茶铺茶中铁云时代店  ←  昆明五华中铁云时代店
    WHEN 'cmpnw033k02dp3pk3iycpwe1e' THEN 'oc_22dd707fca076b08ae4ce5afdb1dcc94'  -- 象子茶铺茶丽江七星街店  ←  丽江七星街店
    WHEN 'cmp4vo7kp023l3ps1nocqjwzu' THEN 'oc_bea97c025f07ab68cb25f708ddf6690b'  -- 象子茶铺茶丽江古城店  ←  丽江古城店
    WHEN 'cmpaocuq800m33pmbkr999ukh' THEN 'oc_c3d988189c52e18a3f2dc9d204b2be0d'  -- 象子茶铺茶丽江束河古镇店  ←  丽江束河古镇店
    WHEN 'cmpnw034502du3pk3aodryu7j' THEN 'oc_f8860f1a5432d5bd617d30be29c230a4'  -- 象子茶铺茶丽江永胜店  ←  丽江永胜店
    WHEN 'cmpaocuj800k53pmbx1fw8o4z' THEN 'oc_12d0664d27e194c9cb459791c3b48853'  -- 象子茶铺茶丽江玉龙店  ←  丽江玉龙店
    WHEN 'cmpaocuku00kk3pmbpji33nqr' THEN 'oc_6b38f0483349099a6e3aec3776807f2a'  -- 象子茶铺茶丽江白沙古镇店  ←  丽江白沙古镇店
    WHEN 'cmpaocujq00ka3pmb2kpxvvdz' THEN 'oc_c5b23b4c47a402b89080aef0ca78c2a5'  -- 象子茶铺茶丽江祥和店  ←  丽江祥和店
    WHEN 'cmpaocucp00ic3pmb3l3cgc41' THEN 'oc_4df03b617a5b6e773986f3f66eba8f2f'  -- 象子茶铺茶云县店  ←  临沧云县店
    WHEN 'cmpaoctrs00d23pmb0nygzz46' THEN 'oc_1459c84900eac6ce428faf26362390c1'  -- 象子茶铺茶仕林街店  ←  昆明呈贡仕林街店
    WHEN 'cmpaocu7x00h33pmb9g8fut1r' THEN 'oc_16172865153f11b1ba21b0afe957b9d0'  -- 象子茶铺茶保山三馆店  ←  保山三馆店
    WHEN 'cmpnw02so02b33pk394rfjlf7' THEN 'oc_40443900a43769c8a878b6fc63a24d34'  -- 象子茶铺茶保山东城店  ←  保山东城店
    WHEN 'cmpaocu6y00gt3pmbzzje05ni' THEN 'oc_fc935c54b075076f67b504ea8b10d98d'  -- 象子茶铺茶保山五洲店  ←  保山五洲店
    WHEN 'cmpaoctn900c33pmb5al74m4p' THEN 'oc_cdb70b3110c2de3d5db6900c0ddda27e'  -- 象子茶铺茶保山吾悦店  ←  保山吾悦店
    WHEN 'cmpnw035h02e43pk3oqdqnq7l' THEN 'oc_523527a4702b6695771e6206df984811'  -- 象子茶铺茶保山板桥镇店  ←  保山板桥镇店
    WHEN 'cmpaocuup00nc3pmbasprkdjp' THEN 'oc_cb46fee660214eec4a13a8f16d3438e2'  -- 象子茶铺茶保山潞江坝镇  ←  保山潞江坝镇
    WHEN 'cmpaocupa00lt3pmb04eg3beb' THEN 'oc_21d3e453e690078427a2c0f4fa8c6745'  -- 象子茶铺茶元谋店  ←  楚雄元谋店
    WHEN 'cmpaocue900ir3pmbzef9yzuu' THEN 'oc_307cf62de9408becce20a559056bf6bf'  -- 象子茶铺茶六盘水店  ←  贵州六盘水店
    WHEN 'cmpaocu4l00g93pmb0r3zeqjk' THEN 'oc_19e27ace2a8a4625278fa16f2f9c6196'  -- 象子茶铺茶兰坪店  ←  怒江兰坪店
    WHEN 'cmpaoctur00dw3pmb570eobo5' THEN 'oc_f7525779dd69827573063154211cc399'  -- 象子茶铺茶凤仪店  ←  大理凤仪店
    WHEN 'cmpaocuqp00m83pmbu48evshq' THEN 'oc_026ac97df5c953c214420a3cfc4d1d41'  -- 象子茶铺茶凤庆店  ←  临沧凤庆店
    WHEN 'cmpaocub300hx3pmb9n0gis5s' THEN 'oc_fd012d9e81fa434362a7330d4aa2f6ad'  -- 象子茶铺茶勐海店  ←  西双版纳勐海店
    WHEN 'cmqrfhlkt06o13pq3n1oa7z18' THEN 'oc_dd94bb051802c1b2ab1c41a6419ddc9f'  -- 象子茶铺茶北城天地店  ←  昆明盘龙北城天地店
    WHEN 'cmp3hkfj701wy3ps118v7sky9' THEN 'oc_8b0d65e1324cd31bd0e4100689997c23'  -- 象子茶铺茶北辰财富中心店  ←  昆明盘龙北辰财富中心店
    WHEN 'cmpaocu0o00ff3pmblc7bap7l' THEN 'oc_5ffdf74aeab80b8fa26401a5fa8f2bf0'  -- 象子茶铺茶华坪店  ←  丽江华坪店
    WHEN 'cmpaocufu00j63pmbaoig7krk' THEN 'oc_89e5e461d495a86284f24472cc343aeb'  -- 象子茶铺茶华宁店  ←  玉溪华宁店
    WHEN 'cmpaoctm800bt3pmbdo8azaqf' THEN 'oc_721e64fa72cee60bb01731490cf63a99'  -- 象子茶铺茶南亚风情店  ←  昆明西山南亚风情店
    WHEN 'cmpnw030u02cz3pk3utnkoy2g' THEN 'oc_196c18e7d67c44c31204a10bcf86d908'  -- 象子茶铺茶南华店  ←  楚雄南华店
    WHEN 'cmpaocu1600fk3pmb6h3e7ntb' THEN 'oc_c42b0dd26e5448e1614cb27344675560'  -- 象子茶铺茶南涧店  ←  大理南涧店
    WHEN 'cmpaocueq00iw3pmbhzkhlsbx' THEN 'oc_dbcea0ab7210ed4b7fc6fa9d779bd213'  -- 象子茶铺茶双江店  ←  临沧双江店
    WHEN 'cmpaocunp00le3pmbgrv5o5bi' THEN 'oc_97651ecb4f498a969846bca97e5a4e49'  -- 象子茶铺茶吴井路店  ←  昆明官渡吴井路店
    WHEN 'cmpaocufc00j13pmb4dk5xbor' THEN 'oc_96dc6aba6d83584e7d12f086ac6fb348'  -- 象子茶铺茶呈贡第七街区店  ←  昆明呈贡第七街区店
    WHEN 'cmpnw02pf02ae3pk3eamh9p1b' THEN 'oc_0c543ba3622d0aa96d5e0e05dce63492'  -- 象子茶铺茶塘子巷店  ←  昆明官渡塘子巷店
    WHEN 'cmpnw02s102ay3pk3eiqqqqbw' THEN 'oc_5d36d88736015f699c3ef387ef3b647b'  -- 象子茶铺茶墨江店  ←  普洱墨江店
    WHEN 'cmpaocuw600nr3pmbkcpch2s6' THEN 'oc_c573c17061795fb0dbc8f9af66b9a0b2'  -- 象子茶铺茶大理云龙店  ←  大理云龙店
    WHEN 'cmpaocupr00ly3pmbol801znp' THEN 'oc_7a7e95a18dac274ab677357c8ae442b3'  -- 象子茶铺茶大理剑川店  ←  大理剑川店
    WHEN 'cmpw9a8it00un3pq3epyrnye5' THEN 'oc_68525c31f47d5e46a61fc88292cd937f'  -- 象子茶铺茶大理古城二店  ←  大理古城二店
    WHEN 'cmpaocult00ku3pmbgqmu7w7f' THEN 'oc_05923d4f2bf37a6051be814c3ee87e3d'  -- 象子茶铺茶大理古城店  ←  大理古城店
    WHEN 'cmpaocuc800i73pmbe87g92xz' THEN 'oc_277dac9330fe835296b71704eb9964a8'  -- 象子茶铺茶大理市天街店  ←  大理市天街店
    WHEN 'cmpaoctxw00eq3pmb4w2lilg3' THEN 'oc_04a210c3e2ca031e79587f8b4c699d2f'  -- 象子茶铺茶大理正阳店  ←  大理正阳店
    WHEN 'cmpwamro401283pq3c3z6t97p' THEN 'oc_f75fe3fcf965d9e9803a234687776211'  -- 象子茶铺茶大理泰业二店  ←  大理泰业二店
    WHEN 'cmpaoctod00cd3pmbnux3srjj' THEN 'oc_751a225e04ad93ac227d242358eec2df'  -- 象子茶铺茶大理泰业店  ←  大理泰业店
    WHEN 'cmpaocuvo00nm3pmbep491ola' THEN 'oc_3a9699cc6879aaf2cf9b583612022cc0'  -- 象子茶铺茶大理洱源店  ←  大理洱源店
    WHEN 'cmpaocuv600nh3pmbcy9pm54g' THEN 'oc_98dca0efe5ac5ad3471f5015757b4d8d'  -- 象子茶铺茶大理满江店  ←  大理满江店
    WHEN 'cmpnw036o02ee3pk3oxese1bg' THEN 'oc_c3744787bf80154cf5c24de4675e9064'  -- 象子茶铺茶天骄北麓店  ←  昆明五华天骄北麓店
    WHEN 'cmpnw02yx02cf3pk3p2hnbdrd' THEN 'oc_fb25a4495db1f1ab3a212112fed6167b'  -- 象子茶铺茶姚安店  ←  楚雄姚安店
    WHEN 'cmp4vo7mu02403ps1hxrjujb5' THEN 'oc_32af8f1a2500a7b3b88bec9631e80bf9'  -- 象子茶铺茶孟定店  ←  临沧耿马孟定店
    WHEN 'cmqrfhlir06nr3pq3kws5wfil' THEN 'oc_9190e777a403e6564fb17f80f04298fc'  -- 象子茶铺茶安宁吾悦店  ←  昆明安宁吾悦店
    WHEN 'cmpaocuma00kz3pmbltedt7om' THEN 'oc_3294bf48fc090f245df476997e75f2ac'  -- 象子茶铺茶安宁金色时代店  ←  昆明安宁金色时代店
    WHEN 'cmpw9d9jm00vc3pq3weyoug12' THEN 'oc_4196cb6c029019690e7f387dee6ae7e9'  -- 象子茶铺茶官渡古镇店  ←  昆明官渡古镇店
    WHEN 'cmp3hkfip01wt3ps1ald5dyrg' THEN 'oc_7a0f37656062de7bf6bce12c443dc9e5'  -- 象子茶铺茶宜宾唐人财富中心店  ←  四川宜宾唐人财富中心店
    WHEN 'cmpaocuo600lj3pmbb6uukpqx' THEN 'oc_7739317259e66337955db5d04ae19cf9'  -- 象子茶铺茶宜宾长宁宏泰财富广场店  ←  四川宜宾长宁宏泰财富广场店
    WHEN 'cmpaocu8g00h83pmbefwlwve4' THEN 'oc_d5b8800e2e4884aeabbf0cc086c15947'  -- 象子茶铺茶宜良店  ←  昆明宜良店
    WHEN 'cmpnw02lb029f3pk3c1k2db61' THEN 'oc_13e12ed1c4f6245459ff54b32774bf01'  -- 象子茶铺茶宣威店  ←  曲靖宣威店
    WHEN 'cmpnw031n02da3pk3n72nhcqb' THEN 'oc_b931aea97013542ae9f516484475fb87'  -- 象子茶铺茶宾川店  ←  大理宾川店
    WHEN 'cmpnw02n3029u3pk3mx0vr247' THEN 'oc_4806a563516e9b2a2cd03f6f2835bdcf'  -- 象子茶铺茶富康城店  ←  昆明官渡富康城店
    WHEN 'cmpaoctl800bj3pmby8x67mqu' THEN 'oc_85b6b4206b3c5b0d1622fc15e0cefc24'  -- 象子茶铺茶富民彩玉国际店  ←  昆明富民彩玉国际店
    WHEN 'cmpaocu5t00gj3pmbqt5rdgbz' THEN 'oc_23c6dee063d4f9e8e434e100cf8e33eb'  -- 象子茶铺茶巍山店  ←  大理巍山店
    WHEN 'cmpnw02ov02a93pk3tuad001a' THEN 'oc_e57ba1424f910a485607bdb83cafa210'  -- 象子茶铺茶师宗店  ←  曲靖师宗店
    WHEN 'cmpaocuy400ob3pmb6mzv6ppu' THEN 'oc_3af29202a5f0f4b3ab71e7597ba75d24'  -- 象子茶铺茶广南店  ←  文山广南店
    WHEN 'cmpwau8kz013n3pq322wypoqt' THEN 'oc_21052e49c707740345895257e9170e9d'  -- 象子茶铺茶广福路爱琴海店  ←  昆明西山广福路爱琴海店
    WHEN 'cmp4vo7lg023q3ps10d4jevol' THEN 'oc_d4e410390b1987232f2abec69b866798'  -- 象子茶铺茶建水小桂湖店  ←  红河建水小桂湖店
    WHEN 'cmpnw02jz02953pk3xu4hmv35' THEN 'oc_d20ece0f15a849405c4b2111815c794a'  -- 象子茶铺茶开远店  ←  红河开远店
    WHEN 'cmpaoctsq00dc3pmbhjnibzpd' THEN 'oc_381c34043fee2cc3770e04f779462fd7'  -- 象子茶铺茶弥勒金辰店  ←  红河弥勒金辰店
    WHEN 'cmpaoctu600dr3pmbzw08uw77' THEN 'oc_b1966daf19711032ee3a1dcc42fd6b69'  -- 象子茶铺茶弥渡店  ←  大理弥渡店
    WHEN 'cmpnw037w02eo3pk3obt1bzg8' THEN 'oc_98c00abc3e4c27f50e55320534ab0d5e'  -- 象子茶铺茶德宏盈江店  ←  德宏盈江店
    WHEN 'cmpaocu7f00gy3pmbqoqoh5y7' THEN 'oc_1267d8636e3bd782628fd1e5b4a78fed'  -- 象子茶铺茶怒江东岸人民路店  ←  怒江东岸人民路店
    WHEN 'cmpaoctpd00cn3pmbvufgxp8s' THEN 'oc_2b9c82a405ee6c0eb0bf143b05bb4039'  -- 象子茶铺茶怒江西岸店  ←  怒江西岸店
    WHEN 'cmpaocuir00k03pmbboxmssqg' THEN 'oc_53ea18e62b8e319b7a442a9f8113a5be'  -- 象子茶铺茶恒基广场店  ←  临沧恒基广场店
    WHEN 'cmp3hkfh301we3ps1xi1nqwvj' THEN 'oc_667c4d093bc96f7563ae5b9d61758a0f'  -- 象子茶铺茶文山光大店  ←  文山光大店
    WHEN 'cmpaoctv800e13pmbw3w2ahxu' THEN 'oc_e9fc878dead17de41b6f09bd93b0de07'  -- 象子茶铺茶新亚洲店  ←  昆明官渡新亚洲店
    WHEN 'cmpnw037902ej3pk3w57xd3rn' THEN 'oc_06de92a81bc43a263f43e697246f7993'  -- 象子茶铺茶新平店  ←  玉溪新平店
    WHEN 'cmpaocud700ih3pmbe6opynv0' THEN 'oc_a807445cae091d193d8446d8e8e9ef41'  -- 象子茶铺茶新迎新城店  ←  昆明盘龙新迎新城店
    WHEN 'cmpaoctjb00b43pmbntacy4bi' THEN 'oc_fdb5782857c217d10dfe6d2af2bd926f'  -- 象子茶铺茶施甸交通路店  ←  保山施甸交通路店
    WHEN 'cmpaoctw700eb3pmb3e66xj14' THEN 'oc_0211b11ebe2c2e252b8f94dc733ec5ed'  -- 象子茶铺茶施甸肆方街店  ←  保山施甸肆方街店
    WHEN 'cmpaocts900d73pmbehtktmhz' THEN 'oc_a38badc8bc54189c67b654600769f336'  -- 象子茶铺茶昌宁店  ←  保山昌宁店
    WHEN 'cmpaoctpv00cs3pmbhizaelfq' THEN 'oc_4bcf5c9a0ddf74e7b07323274e686f94'  -- 象子茶铺茶易门店  ←  玉溪易门店
    WHEN 'cmpaocusn00ms3pmbwzu4qg1q' THEN 'oc_1e9bd36cf4b5b34598a543ab617bca39'  -- 象子茶铺茶昭通合景广场店  ←  昭通合景广场店
    WHEN 'cmpaocuoo00lo3pmbiwyeakdk' THEN 'oc_7b4395fc55f948e8b52d6301a421358f'  -- 象子茶铺茶昭通实验中学店  ←  昭通实验中学店
    WHEN 'cmp3hkhsu01xu3ps1f5eu1z76' THEN 'oc_c3099c6e15e9684bbb27764a71a32827'  -- 象子茶铺茶晋城区店  ←  昆明晋宁晋城区店
    WHEN 'cmpnw032b02df3pk3g1lqinad' THEN 'oc_0cbc7198bc88c25977069373b5fe14dd'  -- 象子茶铺茶晋宁嘉誉广场店  ←  晋宁嘉誉广场店
    WHEN 'cmpaocux500o13pmb5yog1s1l' THEN 'oc_eb9b751396c9a226ac126f8768881c79'  -- 象子茶铺茶普洱创基店  ←  普洱创基店
    WHEN 'cmpaocuu600n73pmbx2efwhxa' THEN 'oc_40f5c6651eb2e563a99b87f658039f31'  -- 象子茶铺茶普洱孟连店  ←  普洱孟连店
    WHEN 'cmpaocugb00jb3pmbcnsj4kjf' THEN 'oc_a32fa9b648c02a8643234cc09506caf4'  -- 象子茶铺茶普洱悦城店  ←  普洱悦城店
    WHEN 'cmp4vo7m5023v3ps1e5k9eru0' THEN 'oc_fe2681d97414b640a91485dfe59280a3'  -- 象子茶铺茶景东店  ←  普洱景东店
    WHEN 'cmpaoctyj00ev3pmbo3dxzrm1' THEN 'oc_68fe8ca181d6d625d28ca3545ef36254'  -- 象子茶铺茶景谷店  ←  普洱景谷店
    WHEN 'cmpaoculc00kp3pmbkblhlnyn' THEN 'oc_e63e45b4e404fdb1ee5e409a94f33bcc'  -- 象子茶铺茶曲靖万达店  ←  曲靖万达店
    WHEN 'cmpaocuro00mi3pmb3v5u3nc5' THEN 'oc_8a1bcff6daa83c7d415775788d7be748'  -- 象子茶铺茶曲靖会泽店  ←  曲靖会泽店
    WHEN 'cmpnw02lw029k3pk311bekjaz' THEN 'oc_3b789156605aad8195346ba6f1b35e9f'  -- 象子茶铺茶曲靖沾益西正街店  ←  曲靖沾益区西正街店
    WHEN 'cmpaocuze00ol3pmbq78thom9' THEN 'oc_b0b6ae7fa135c887d57e0ab56b385b14'  -- 象子茶铺茶曲靖花柯店  ←  曲靖花柯店
    WHEN 'cmpnw02ua02bd3pk3gwbzbqpg' THEN 'oc_7200e61cbee4471e648830bcbaaf2586'  -- 象子茶铺茶果林广场店  ←  昆明呈贡果林广场店
    WHEN 'cmpaocus500mn3pmbzfw9mhi3' THEN 'oc_274711c29a9927e72ce859344bb7fe39'  -- 象子茶铺茶柏联广场店  ←  昆明五华柏联广场店
    WHEN 'cmpaoctzm00f53pmbinxvl6p2' THEN 'oc_92824dfb71dc432bc94a3726752d6a13'  -- 象子茶铺茶梁河店  ←  德宏梁河店
    WHEN 'cmpaoctnv00c83pmbflyt9mel' THEN 'oc_e55229e50b249ab24f7ba7efcfa28aae'  -- 象子茶铺茶楚雄丹麓旗舰店  ←  楚雄丹麓旗舰店
    WHEN 'cmpnw032y02dk3pk3dp4idgbw' THEN 'oc_05cc7a666f42f722dcf7df0c3abe63f0'  -- 象子茶铺茶楚雄大姚店  ←  楚雄大姚店
    WHEN 'cmpnw02wq02bv3pk3ushlro0s' THEN 'oc_e6c6d3ee0ad883dd2e74a9927dfc77d0'  -- 象子茶铺茶楚雄彝人古镇店  ←  楚雄彝人古镇店
    WHEN 'cmpaocu0700fa3pmba47qeuxb' THEN 'oc_950c2f61111c0b858ff49ebaaab4d8ca'  -- 象子茶铺茶楚雄牟定店  ←  楚雄牟定店
    WHEN 'cmpnw02mh029p3pk3huw4beqc' THEN 'oc_716eed324d0215766a7946b7315611b2'  -- 象子茶铺茶毕节店  ←  贵州毕节店
    WHEN 'cmp3hkfi901wo3ps1gw3nxcwl' THEN 'oc_53208b64791c960a5c92342b86cad8d0'  -- 象子茶铺茶永平县店  ←  大理永平县店
    WHEN 'cmpaocu1n00fp3pmbbutnsku1' THEN 'oc_c631121dc56ebbbd6e335160f2351bff'  -- 象子茶铺茶江川店  ←  玉溪江川店
    WHEN 'cmpaoctou00ci3pmbjbfguo5o' THEN 'oc_ee0d311997541abe499d1a97310ad46f'  -- 象子茶铺茶沧源店  ←  临沧沧源店
    WHEN 'cmpnw02qq02ao3pk3dc923f3o' THEN 'oc_d6696d672cc0f925b36b18fa7e4bf1a2'  -- 象子茶铺茶河口店  ←  红河河口店
    WHEN 'cmpaoctk400b93pmbd8v3zj4k' THEN 'oc_b85930c803d4f352a7de6bc66f082793'  -- 象子茶铺茶海乐达人汇店  ←  昆明官渡海乐达人汇店
    WHEN 'cmpaocu5500ge3pmb70lbe792' THEN 'oc_fec7be5898e970cc649922d0905c8442'  -- 象子茶铺茶滇池名门店  ←  昆明西山滇池名门店
    WHEN 'cmpw9ltj400x73pq39uqttify' THEN 'oc_307d7917f9cbc353aeabb3cb7409a448'  -- 象子茶铺茶漾濞店  ←  大理漾濞店
    WHEN 'cmp3hkhvr01ye3ps124g3hhkk' THEN 'oc_fc0151fb14fdf1759b6e7c1b17dde579'  -- 象子茶铺茶澄江店  ←  玉溪澄江店
    WHEN 'cmpaocu3200fz3pmbwrlc4uk8' THEN 'oc_c34d38d06f82a431590e042376bec76e'  -- 象子茶铺茶澜沧店  ←  普洱澜沧店
    WHEN 'cmpaocutk00n23pmbamimpsnw' THEN 'oc_2ed37d04b416679b967505aae592e728'  -- 象子茶铺茶版纳大润发店  ←  西双版纳大润发店
    WHEN 'cmpaocut300mx3pmblfjyarxm' THEN 'oc_6bcfedf6c5d3ee2f7b91faca1e1e548e'  -- 象子茶铺茶版纳曼城店  ←  西双版纳曼城店
    WHEN 'cmpnw038h02et3pk3zi3iwbv1' THEN 'oc_15e0b2ea16f396b853991fca871b92be'  -- 象子茶铺茶玉溪峨山店  ←  玉溪峨山店
    WHEN 'cmpnw036202e93pk3o7c1i0ck' THEN 'oc_444dedfd8550b0d774dbc8bc8fd456cb'  -- 象子茶铺茶王府井滇池小镇店  ←  昆明官渡王府井滇池小镇店
    WHEN 'cmpaocudp00im3pmbs3q0p529' THEN 'oc_ae29f18450c8a6a67805b8da6810e485'  -- 象子茶铺茶瑞丽彩云城店  ←  瑞丽彩云城店
    WHEN 'cmpaocuwn00nw3pmbnlkjavfz' THEN 'oc_4ef0eba8822ff9fc689688b874b99334'  -- 象子茶铺茶瑞丽德龙珠宝城  ←  瑞丽德龙珠宝城
    WHEN 'cmpaoctt800dh3pmbb51j6wx1' THEN 'oc_8c030838d2bfb5e9454d489ab5b706f8'  -- 象子茶铺茶瑞丽翡翠园店  ←  瑞丽翡翠园店
    WHEN 'cmpaocubk00i23pmb3bxmyoqe' THEN 'oc_5c5ee4effda873962f14f6657fa074b3'  -- 象子茶铺茶瑞鼎城店  ←  昆明盘龙瑞鼎城店
    WHEN 'cmqrfhlld06o63pq32xz6dwrg' THEN 'oc_93ec26ab34f58ab3d58b234ba696ad94'  -- 象子茶铺茶白龙派公园店  ←  昆明盘龙白龙派公园店
    WHEN 'cmpaocu8x00hd3pmbrjv6qg5p' THEN 'oc_33e8ecbbbedb953aad30af2a7ae50a7b'  -- 象子茶铺茶盘州店  ←  贵州六盘水盘州店
    WHEN 'cmp3hkfk701x83ps1w3301jiz' THEN 'oc_9ccb0480106f6f99e8edf7eaaa9d2f4b'  -- 象子茶铺茶石屏店  ←  红河石屏店
    WHEN 'cmpnw039202ey3pk309hgy8oe' THEN 'oc_b2025452510d1f4e4bcd37a9b3a26105'  -- 象子茶铺茶砚山七都广场店  ←  文山砚山七都广场店
    WHEN 'cmpnw02xq02c63pk3bmaulhiv' THEN 'oc_e53b1bdb694e4c4a91c38d26187ed42c'  -- 象子茶铺茶砚山嘉禾路店  ←  文山砚山嘉禾路店
    WHEN 'cmpaocttp00dm3pmbysze7vef' THEN 'oc_d9ce2c4ef405c4e66d740b76e568b9cb'  -- 象子茶铺茶祥云印象花园店  ←  大理祥云印象花园店
    WHEN 'cmpaocuk700kf3pmb75e9s0ih' THEN 'oc_08d9722ba2db394b6ee9debbd43705e2'  -- 象子茶铺茶祥云城南店  ←  大理祥云城南店
    WHEN 'cmqrfhlfa06n73pq35jkt4540' THEN 'oc_baff826998dc7faa5807161036719e5c'  -- 象子茶铺茶禄丰店  ←  楚雄禄丰店
    WHEN 'cmpaoctxf00el3pmbikfhxjir' THEN 'oc_907fee96c237467334a0634f0d21ca66'  -- 象子茶铺茶禄劝店  ←  昆明禄劝店
    WHEN 'cmpaoctko00be3pmbvpeiogtc' THEN 'oc_a50e3e0410635586fcc63a61cce825d3'  -- 象子茶铺茶福德店  ←  昆明官渡福德店
    WHEN 'cmpaocuht00jq3pmbwgtosfb2' THEN 'oc_99d4f912ad10558002010eca168fd4e9'  -- 象子茶铺茶紫金中心店  ←  昆明官渡紫金中心店
    WHEN 'cmqrfhlk306nw3pq3u4pd1zh5' THEN 'oc_7588f3edff852f96683e5d6639d2ccdb'  -- 象子茶铺茶红河县店  ←  红河县店
    WHEN 'cmpw8zu8800u23pq35b0q7zdn' THEN 'oc_f030224aaed78e47dd4a6a563eb76220'  -- 象子茶铺茶红河泸西店  ←  红河泸西店
    WHEN 'cmpnw02ta02b83pk3j51aqeg4' THEN 'oc_1db141902c9e6ac3f793e5d5eea9c6c2'  -- 象子茶铺茶绿春店  ←  红河绿春店
    WHEN 'cmpaoctij00az3pmbfl6c71mz' THEN 'oc_bf5f9bd735071084abe4d33aeb1b4de8'  -- 象子茶铺茶翠湖店  ←  昆明五华翠湖店
    WHEN 'cmpaoctmr00by3pmb20hdtxl5' THEN 'oc_d4f0908660bb5f4fe4175cee934a2329'  -- 象子茶铺茶翰林大观店  ←  昆明呈贡翰林大观店
    WHEN 'cmpaocuhb00jl3pmb99zr2r3n' THEN 'oc_a5efa3df34edabbe1e39f33de76498f9'  -- 象子茶铺茶腾冲天成店  ←  保山腾冲天成店
    WHEN 'cmpaoctlq00bo3pmbh7n6te7r' THEN 'oc_beeadb4a361ed81cde53e2a0e8728d03'  -- 象子茶铺茶芒市三棵树店  ←  德宏芒市三棵树店
    WHEN 'cmpw9lsb300wz3pq3ez6f1dju' THEN 'oc_fb772bf297e66262b2d0fe4ef54a4fbd'  -- 象子茶铺茶蒙自南湖荟店  ←  红河蒙自南湖荟店
    WHEN 'cmp4vo7j1023g3ps1m5qlimmo' THEN 'oc_3390af8150e77604f46928cebbf7c132'  -- 象子茶铺茶西山区好悦天地店  ←  昆明西山区好悦天地店
    WHEN 'cmqrfhlga06nc3pq3tryceg7n' THEN 'oc_a6b9eaab50166fd0d4085e60868ce3ed'  -- 象子茶铺茶西山茶马花街店  ←  昆明西山茶马花街店
    WHEN 'cmp3hkhwq01yo3ps1f058kss7' THEN 'oc_acf6bff1fc9dd3d693b8817bae625768'  -- 象子茶铺茶银海尚御店  ←  昆明官渡银海尚御店
    WHEN 'cmpaocu6d00go3pmbf779nx4d' THEN 'oc_2b8991de8f2038dc0d029762e89b07ef'  -- 象子茶铺茶镇沅店  ←  普洱镇沅店
    WHEN 'cmpwcym7p01693pq3ps61q1rz' THEN 'oc_e2ba82b2e91998b131a3c7a54e93bc3c'  -- 象子茶铺茶长水机场到达厅  ←  长水到达厅
    WHEN 'cmpwcx3oo015k3pq3bivxc6e0' THEN 'oc_b0da1aadfed4e2579dcb5224117d22e6'  -- 象子茶铺茶长水机场卫星厅  ←  长水卫星厅
    WHEN 'cmpnw039p02f33pk3o3a4hwqg' THEN 'oc_505688162590a5f3533e541add2b60e0'  -- 象子茶铺茶长水机场店  ←  长水机场店
    WHEN 'cmpnw02kp029a3pk3a7ze7gvj' THEN 'oc_104f354b03ba997b7ef8b41c5b2baa07'  -- 象子茶铺茶陆良店  ←  曲靖陆良店
    WHEN 'cmpnw02zy02cq3pk3oyu6gvnq' THEN 'oc_ac0edeffa0e3215b740f4ed6f1bf7d7e'  -- 象子茶铺茶陇川店  ←  德宏陇川店
    WHEN 'cmpaocur700md3pmbhxdfsoa3' THEN 'oc_a9f1fd5b5ef8178edcf7b4f02adcd635'  -- 象子茶铺茶香格里拉店  ←  迪庆香格里拉店
    WHEN 'cmqrfhlh406nh3pq3v0y32b94' THEN 'oc_24298dd2217f041c467fe1744bd6a32d'  -- 象子茶铺茶香格里拉月光印象店  ←  迪庆香格里拉月光印象店
    WHEN 'cmpaoctqy00cx3pmb43nouafn' THEN 'oc_b6dd7fdb6a8df39a385f64bd07eef088'  -- 象子茶铺茶高山铺店  ←  昆明五华高山铺店
    WHEN 'cmpaocuai00hs3pmbc3ta1d36' THEN 'oc_5aecde41a5b018c37a881dc01af74815'  -- 象子茶铺茶鹤庆店  ←  大理鹤庆店
    WHEN 'cmpaoctww00eg3pmbwq58ot35' THEN 'oc_d00f54d8f93e4b9a88493997c3209202'  -- 象子茶铺茶龙陵盛和雅苑店  ←  保山龙陵盛和雅苑店
    ELSE chat_id
END
WHERE del_flag = 0 AND store_id IN ('cmpaocun900l93pmbczh07a1b','cmpaocu2b00fu3pmbwf04mo09','cmpaocugt00jg3pmbibe1oo1b','cmpnw02j802903pk30xpbgnq3','cmpaocua000hn3pmbe8m4pdif','cmpnw033k02dp3pk3iycpwe1e','cmp4vo7kp023l3ps1nocqjwzu','cmpaocuq800m33pmbkr999ukh','cmpnw034502du3pk3aodryu7j','cmpaocuj800k53pmbx1fw8o4z','cmpaocuku00kk3pmbpji33nqr','cmpaocujq00ka3pmb2kpxvvdz','cmpaocucp00ic3pmb3l3cgc41','cmpaoctrs00d23pmb0nygzz46','cmpaocu7x00h33pmb9g8fut1r','cmpnw02so02b33pk394rfjlf7','cmpaocu6y00gt3pmbzzje05ni','cmpaoctn900c33pmb5al74m4p','cmpnw035h02e43pk3oqdqnq7l','cmpaocuup00nc3pmbasprkdjp','cmpaocupa00lt3pmb04eg3beb','cmpaocue900ir3pmbzef9yzuu','cmpaocu4l00g93pmb0r3zeqjk','cmpaoctur00dw3pmb570eobo5','cmpaocuqp00m83pmbu48evshq','cmpaocub300hx3pmb9n0gis5s','cmqrfhlkt06o13pq3n1oa7z18','cmp3hkfj701wy3ps118v7sky9','cmpaocu0o00ff3pmblc7bap7l','cmpaocufu00j63pmbaoig7krk','cmpaoctm800bt3pmbdo8azaqf','cmpnw030u02cz3pk3utnkoy2g','cmpaocu1600fk3pmb6h3e7ntb','cmpaocueq00iw3pmbhzkhlsbx','cmpaocunp00le3pmbgrv5o5bi','cmpaocufc00j13pmb4dk5xbor','cmpnw02pf02ae3pk3eamh9p1b','cmpnw02s102ay3pk3eiqqqqbw','cmpaocuw600nr3pmbkcpch2s6','cmpaocupr00ly3pmbol801znp','cmpw9a8it00un3pq3epyrnye5','cmpaocult00ku3pmbgqmu7w7f','cmpaocuc800i73pmbe87g92xz','cmpaoctxw00eq3pmb4w2lilg3','cmpwamro401283pq3c3z6t97p','cmpaoctod00cd3pmbnux3srjj','cmpaocuvo00nm3pmbep491ola','cmpaocuv600nh3pmbcy9pm54g','cmpnw036o02ee3pk3oxese1bg','cmpnw02yx02cf3pk3p2hnbdrd','cmp4vo7mu02403ps1hxrjujb5','cmqrfhlir06nr3pq3kws5wfil','cmpaocuma00kz3pmbltedt7om','cmpw9d9jm00vc3pq3weyoug12','cmp3hkfip01wt3ps1ald5dyrg','cmpaocuo600lj3pmbb6uukpqx','cmpaocu8g00h83pmbefwlwve4','cmpnw02lb029f3pk3c1k2db61','cmpnw031n02da3pk3n72nhcqb','cmpnw02n3029u3pk3mx0vr247','cmpaoctl800bj3pmby8x67mqu','cmpaocu5t00gj3pmbqt5rdgbz','cmpnw02ov02a93pk3tuad001a','cmpaocuy400ob3pmb6mzv6ppu','cmpwau8kz013n3pq322wypoqt','cmp4vo7lg023q3ps10d4jevol','cmpnw02jz02953pk3xu4hmv35','cmpaoctsq00dc3pmbhjnibzpd','cmpaoctu600dr3pmbzw08uw77','cmpnw037w02eo3pk3obt1bzg8','cmpaocu7f00gy3pmbqoqoh5y7','cmpaoctpd00cn3pmbvufgxp8s','cmpaocuir00k03pmbboxmssqg','cmp3hkfh301we3ps1xi1nqwvj','cmpaoctv800e13pmbw3w2ahxu','cmpnw037902ej3pk3w57xd3rn','cmpaocud700ih3pmbe6opynv0','cmpaoctjb00b43pmbntacy4bi','cmpaoctw700eb3pmb3e66xj14','cmpaocts900d73pmbehtktmhz','cmpaoctpv00cs3pmbhizaelfq','cmpaocusn00ms3pmbwzu4qg1q','cmpaocuoo00lo3pmbiwyeakdk','cmp3hkhsu01xu3ps1f5eu1z76','cmpnw032b02df3pk3g1lqinad','cmpaocux500o13pmb5yog1s1l','cmpaocuu600n73pmbx2efwhxa','cmpaocugb00jb3pmbcnsj4kjf','cmp4vo7m5023v3ps1e5k9eru0','cmpaoctyj00ev3pmbo3dxzrm1','cmpaoculc00kp3pmbkblhlnyn','cmpaocuro00mi3pmb3v5u3nc5','cmpnw02lw029k3pk311bekjaz','cmpaocuze00ol3pmbq78thom9','cmpnw02ua02bd3pk3gwbzbqpg','cmpaocus500mn3pmbzfw9mhi3','cmpaoctzm00f53pmbinxvl6p2','cmpaoctnv00c83pmbflyt9mel','cmpnw032y02dk3pk3dp4idgbw','cmpnw02wq02bv3pk3ushlro0s','cmpaocu0700fa3pmba47qeuxb','cmpnw02mh029p3pk3huw4beqc','cmp3hkfi901wo3ps1gw3nxcwl','cmpaocu1n00fp3pmbbutnsku1','cmpaoctou00ci3pmbjbfguo5o','cmpnw02qq02ao3pk3dc923f3o','cmpaoctk400b93pmbd8v3zj4k','cmpaocu5500ge3pmb70lbe792','cmpw9ltj400x73pq39uqttify','cmp3hkhvr01ye3ps124g3hhkk','cmpaocu3200fz3pmbwrlc4uk8','cmpaocutk00n23pmbamimpsnw','cmpaocut300mx3pmblfjyarxm','cmpnw038h02et3pk3zi3iwbv1','cmpnw036202e93pk3o7c1i0ck','cmpaocudp00im3pmbs3q0p529','cmpaocuwn00nw3pmbnlkjavfz','cmpaoctt800dh3pmbb51j6wx1','cmpaocubk00i23pmb3bxmyoqe','cmqrfhlld06o63pq32xz6dwrg','cmpaocu8x00hd3pmbrjv6qg5p','cmp3hkfk701x83ps1w3301jiz','cmpnw039202ey3pk309hgy8oe','cmpnw02xq02c63pk3bmaulhiv','cmpaocttp00dm3pmbysze7vef','cmpaocuk700kf3pmb75e9s0ih','cmqrfhlfa06n73pq35jkt4540','cmpaoctxf00el3pmbikfhxjir','cmpaoctko00be3pmbvpeiogtc','cmpaocuht00jq3pmbwgtosfb2','cmqrfhlk306nw3pq3u4pd1zh5','cmpw8zu8800u23pq35b0q7zdn','cmpnw02ta02b83pk3j51aqeg4','cmpaoctij00az3pmbfl6c71mz','cmpaoctmr00by3pmb20hdtxl5','cmpaocuhb00jl3pmb99zr2r3n','cmpaoctlq00bo3pmbh7n6te7r','cmpw9lsb300wz3pq3ez6f1dju','cmp4vo7j1023g3ps1m5qlimmo','cmqrfhlga06nc3pq3tryceg7n','cmp3hkhwq01yo3ps1f058kss7','cmpaocu6d00go3pmbf779nx4d','cmpwcym7p01693pq3ps61q1rz','cmpwcx3oo015k3pq3bivxc6e0','cmpnw039p02f33pk3o3a4hwqg','cmpnw02kp029a3pk3a7ze7gvj','cmpnw02zy02cq3pk3oyu6gvnq','cmpaocur700md3pmbhxdfsoa3','cmqrfhlh406nh3pq3v0y32b94','cmpaoctqy00cx3pmb43nouafn','cmpaocuai00hs3pmbc3ta1d36','cmpaoctww00eg3pmbwq58ot35');

-- 校验
SELECT store_id, store_name, chat_id FROM store_inventory.store_info
WHERE del_flag = 0 ORDER BY (chat_id IS NULL OR chat_id='') DESC, store_name;

-- ============================================================
-- 待人工确认 / 无匹配（20 家，均未写入，chat_id 保持原值）
-- 请核对后手工填 chat_id 并取消注释；找不到映射的保持留空
-- ============================================================
-- [平局-疑误配] 测试门店  (store_id=cmpz23uu201ta3pq33tchfhdo)  最近似=昆明西山滇池名门店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpz23uu201ta3pq33tchfhdo';
-- [无匹配] 测试门店1001  (store_id=test1001)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='test1001';
-- [无匹配] 测试门店1002  (store_id=test1002)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='test1002';
-- [无匹配] 测试门店1003  (store_id=test1003)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='test1003';
-- [平局-疑误配] 象子茶铺茶临沧百树广场店  (store_id=cmpnw02q202aj3pk3i0a7084x)  最近似=临沧恒基广场店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpnw02q202aj3pk3i0a7084x';
-- [无匹配] 象子茶铺茶丽江旅游学院店  (store_id=cmpaocuzx00oq3pmbe7yvn9gg)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpaocuzx00oq3pmbe7yvn9gg';
-- [无匹配] 象子茶铺茶兴义店  (store_id=cmpw9lqu300wr3pq33cog9iq9)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpw9lqu300wr3pq33cog9iq9';
-- [回退-疑误配] 象子茶铺茶南屏街世纪广场店  (store_id=cmpwa93tr010v3pq3hzddyxs2)  最近似=昆明五华世纪广场店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpwa93tr010v3pq3hzddyxs2';
-- [平局-疑误配] 象子茶铺茶呈贡七彩云南第壹城店  (store_id=cmpaoctvq00e63pmbobapy64w)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpaoctvq00e63pmbobapy64w';
-- [平局-疑误配] 象子茶铺茶园西路店  (store_id=cmpnw02np029z3pk3olncxkvk)  最近似=保山施甸交通路店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpnw02np029z3pk3olncxkvk';
-- [平局-疑误配] 象子茶铺茶建水紫陶街店  (store_id=cmqrfhli506nm3pq3wevdtbvb)  最近似=丽江七星街店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmqrfhli506nm3pq3wevdtbvb';
-- [平局-疑误配] 象子茶铺茶弥勒印象街店  (store_id=cmpaoctz100f03pmbuyaf4h73)  最近似=丽江七星街店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpaoctz100f03pmbuyaf4h73';
-- [回退-疑误配] 象子茶铺茶昆明建工新城店  (store_id=cmpwa91ya010f3pq343o6pqxk)  最近似=昆明盘龙新迎新城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpwa91ya010f3pq343o6pqxk';
-- [无匹配] 象子茶铺茶昆明财经学院店  (store_id=cmpwa90gt01073pq3uw60hg23)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpwa90gt01073pq3uw60hg23';
-- [无匹配] 象子茶铺茶昭通金池店  (store_id=cmpway27w014d3pq3jvhup4pb)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpway27w014d3pq3jvhup4pb';
-- [无匹配] 象子茶铺茶楚雄彝海公园  (store_id=cmpnw02vr02bm3pk3tm33dbe0)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpnw02vr02bm3pk3tm33dbe0';
-- [无匹配] 象子茶铺茶福保店  (store_id=cmpaocu9f00hi3pmbmpvq836q)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpaocu9f00hi3pmbmpvq836q';
-- [无匹配] 象子茶铺茶贵州望谟店  (store_id=cmpnw02oa02a43pk35j04jbjg)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpnw02oa02a43pk35j04jbjg';
-- [回退-疑误配] 象子茶铺茶贵州黔西店  (store_id=cmpnw02rb02at3pk3xs15zeg9)  最近似=红河泸西店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmpnw02rb02at3pk3xs15zeg9';
-- [无匹配] 象子茶铺茶镇康南伞店  (store_id=cmp3hkhxa01yt3ps1buu08pso)  最近似=临沧中骏世界城店
-- UPDATE store_inventory.store_info SET chat_id='' WHERE store_id='cmp3hkhxa01yt3ps1buu08pso';
-- ============================================================
-- 14 家门店 chat_id 手动确认后回填
-- 来源：用户提供 jlstroe 对应门店名，程序匹配出 chat_id
-- ============================================================

UPDATE store_inventory.store_info
SET chat_id = CASE store_id
    WHEN 'cmpaocuzx00oq3pmbe7yvn9gg' THEN 'oc_ece03731bb6927ff459ee64c7063b73d'  -- 象子茶铺茶丽江旅游学院店  →  丽江旅游学院  (suffix=6)
    WHEN 'cmpw9lqu300wr3pq33cog9iq9' THEN 'oc_e624967481827c8339c3874bf3475b6f'  -- 象子茶铺茶兴义店  →  贵州黔西南兴义梦乐橙店  (suffix=11)
    WHEN 'cmpwa90gt01073pq3uw60hg23' THEN 'oc_b03c5750d7eecd3a693182c4d8feb81f'  -- 象子茶铺茶昆明财经学院店  →  昆明五华财经学院  (suffix=8)
    WHEN 'cmpway27w014d3pq3jvhup4pb' THEN 'oc_994578bbb2228c19e014db1aaf24115a'  -- 象子茶铺茶昭通金池店  →  昭通金池购物中心店  (suffix=9)
    WHEN 'cmpnw02vr02bm3pk3tm33dbe0' THEN 'oc_2dea5634fa4423cb117448651d508bfb'  -- 象子茶铺茶楚雄彝海公园  →  楚雄彝海公园店  (suffix=7)
    WHEN 'cmpnw02oa02a43pk35j04jbjg' THEN 'oc_1aecb6e3781c4934c481cd148e4f2e81'  -- 象子茶铺茶贵州望谟店  →  贵州黔西南望谟县店  (suffix=9)
    WHEN 'cmp3hkhxa01yt3ps1buu08pso' THEN 'oc_c999469b12ca7b6d80a1b0e5035b080c'  -- 象子茶铺茶镇康南伞店  →  临沧镇康县  (suffix=5)
    WHEN 'cmpaoctvq00e63pmbobapy64w' THEN 'oc_ad0d326c0ade64a2fec6ea55ffbc2c66'  -- 象子茶铺茶呈贡七彩云南第壹城店  →  昆明呈贡七彩云南第一城店  (suffix=12)
    WHEN 'cmpnw02np029z3pk3olncxkvk' THEN 'oc_bb041e4a5f87b7c1e0484b45bfbccdc4'  -- 象子茶铺茶园西路店  →  昆明五华园西路  (suffix=7)
    WHEN 'cmqrfhli506nm3pq3wevdtbvb' THEN 'oc_9ad71ecec4380888266ce346c8c483dc'  -- 象子茶铺茶建水紫陶街店  →  红河建水紫陶街  (suffix=7)
    WHEN 'cmpaoctz100f03pmbuyaf4h73' THEN 'oc_27c9fa25ee9d004895c67a8070b3ed34'  -- 象子茶铺茶弥勒印象街店  →  红河弥勒印象店  (suffix=7)
    WHEN 'cmpwa91ya010f3pq343o6pqxk' THEN 'oc_c295f5218730d270e6280762708a752f'  -- 象子茶铺茶昆明建工新城店  →  昆明呈贡建工新城  (suffix=8)
    WHEN 'cmpwa93tr010v3pq3hzddyxs2' THEN 'oc_607fb56228fbefa1a7b6fdec21442e7b'  -- 象子茶铺茶南屏街世纪广场店  →  昆明五华世纪广场店  (suffix=9)
    WHEN 'cmpnw02rb02at3pk3xs15zeg9' THEN 'oc_dbe0be797bb53a5e9aac6a1a217b9cb8'  -- 象子茶铺茶贵州黔西店  →  黔西文峰路店  (suffix=6)
    ELSE chat_id
END
WHERE del_flag = 0 AND store_id IN ('cmpaocuzx00oq3pmbe7yvn9gg','cmpw9lqu300wr3pq33cog9iq9','cmpwa90gt01073pq3uw60hg23','cmpway27w014d3pq3jvhup4pb','cmpnw02vr02bm3pk3tm33dbe0','cmpnw02oa02a43pk35j04jbjg','cmp3hkhxa01yt3ps1buu08pso','cmpaoctvq00e63pmbobapy64w','cmpnw02np029z3pk3olncxkvk','cmqrfhli506nm3pq3wevdtbvb','cmpaoctz100f03pmbuyaf4h73','cmpwa91ya010f3pq343o6pqxk','cmpwa93tr010v3pq3hzddyxs2','cmpnw02rb02at3pk3xs15zeg9');

-- ============================================================
-- 临沧百树广场店 chat_id 手动确认
-- ============================================================

UPDATE store_inventory.store_info
SET chat_id = 'oc_d45585893e393d844331ef34a9518fdb'
WHERE del_flag = 0 AND store_id = 'cmpnw02q202aj3pk3i0a7084x';


-- ============================================================
-- 测试门店 chat_id 手动确认
-- ============================================================

UPDATE store_inventory.store_info
SET chat_id = 'oc_ea177cd1cd4c074677c53785db6fd1b7'
WHERE del_flag = 0 AND store_id = 'cmpz23uu201ta3pq33tchfhdo';

