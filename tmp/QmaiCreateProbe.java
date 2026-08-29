import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 企迈 createDeclareOrder 参数探针（本地独立运行，无 Spring 依赖）。
 * 一次性测试多组参数组合，定位 160098「报货单单据属性值错误」。
 * 运行：javac QmaiCreateProbe.java && java QmaiCreateProbe
 */
public class QmaiCreateProbe {

    static final String OPEN_ID = "71fcea7abc9709d653693116410b5385";
    static final String GRANT_CODE = "WaGI2rqy9b";
    static final String OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ";
    static final String URL = "https://openapi.qmai.cn/v3/newPattern/scmApiserver/post/declare/order/create";

    public static void main(String[] args) throws Exception {
        // 基准商品：WP0301 数量1 单价5（与线上最近一次失败请求一致）
        Map<String, Object> prodWithPrice = LinkedHashMap();
        prodWithPrice.put("productCode", "WP0301");
        prodWithPrice.put("productNum", 1);
        prodWithPrice.put("price", 5);

        Map<String, Object> prodNoPrice = LinkedHashMap();
        prodNoPrice.put("productCode", "WP0301");
        prodNoPrice.put("productNum", 1);

        Map<String, Object> prodFull = LinkedHashMap();
        prodFull.put("productCode", "WP0301");
        prodFull.put("productNum", 1);
        prodFull.put("price", 5);
        prodFull.put("freightTag", 1);
        prodFull.put("activityTag", 2);
        prodFull.put("giftTag", 2);
        prodFull.put("logisticsMode", 1);
        prodFull.put("deliveryType", 1);

        // A: 文档请求示例原样（orderAttribute=1）
        run("A  文档示例: onlinePay=1 orderAttribute=1 + price", params("MDCK000019", 1, 1, null, List.of(prodWithPrice)));
        // B: orderAttribute=1 但不传 price
        run("B  orderAttribute=1 无price", params("MDCK000019", 1, 1, null, List.of(prodNoPrice)));
        // C: 完全不传 onlinePay / orderAttribute（走门店配置默认）
        Map<String, Object> c = LinkedHashMap();
        c.put("warehouseNo", "MDCK000019");
        c.put("productList", List.of(prodWithPrice));
        run("C  不传onlinePay/orderAttribute", c);
        // D: 当前线上参数（onlinePay=1 orderAttribute=0）去掉 creator
        run("D  onlinePay=1 orderAttribute=0 无creator 带price", params("MDCK000019", 1, 0, null, List.of(prodWithPrice)));
        // E: orderAttribute=1 带完整可选字段
        run("E  orderAttribute=1 全可选字段", params("MDCK000019", 1, 1, null, List.of(prodFull)));
        // F: 不传 price + 不带 orderAttribute
        Map<String, Object> f = LinkedHashMap();
        f.put("warehouseNo", "MDCK000019");
        f.put("onlinePay", 1);
        f.put("productList", List.of(prodNoPrice));
        run("F  只传onlinePay=1 无price", f);
    }

    static Map<String, Object> params(String warehouseNo, Integer onlinePay, Integer orderAttribute,
                                      String creator, List<Map<String, Object>> products) {
        Map<String, Object> m = LinkedHashMap();
        m.put("warehouseNo", warehouseNo);
        if (onlinePay != null) m.put("onlinePay", onlinePay);
        if (orderAttribute != null) m.put("orderAttribute", orderAttribute);
        if (creator != null) m.put("creator", creator);
        m.put("productList", products);
        return m;
    }

    static void run(String label, Map<String, Object> bizParams) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        int nonce = 10000 + new Random().nextInt(90000);
        String token = makeToken(ts, nonce);
        String body = "{\"openId\":\"" + OPEN_ID + "\",\"grantCode\":\"" + GRANT_CODE
                + "\",\"nonce\":" + nonce + ",\"timestamp\":" + ts
                + ",\"token\":\"" + token + "\",\"params\":" + json(bizParams) + "}";
        System.out.println("== " + label + " ==>\n  请求: " + body + "\n  响应: " + post(body) + "\n");
    }

    static String makeToken(long ts, int nonce) throws Exception {
        String sorted = "grantCode=" + GRANT_CODE + "&nonce=" + nonce + "&openId=" + OPEN_ID + "&timestamp=" + ts;
        String restored = URLEncoder.encode(sorted, StandardCharsets.UTF_8)
                .replace("%3D", "=").replace("%26", "&");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(OPEN_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] sign = mac.doFinal(restored.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(Base64.getEncoder().encodeToString(sign), StandardCharsets.UTF_8);
    }

    static String post(String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(URL).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        try (OutputStream os = c.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return code + " " + sb;
    }

    static String json(Object o) {
        if (o instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(e.getKey()).append("\":").append(json(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object x : (List<?>) o) {
                if (!first) sb.append(',');
                first = false;
                sb.append(json(x));
            }
            return sb.append(']').toString();
        }
        if (o instanceof Number) return o.toString();
        return "\"" + o + "\"";
    }

    static Map<String, Object> LinkedHashMap() {
        return new LinkedHashMap<>();
    }
}
