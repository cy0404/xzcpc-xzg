import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;

/**
 * 企迈报货单详情只读查询探针（无副作用，不创建任何单据）。
 * 用法: javac -encoding UTF-8 QmaiDetailProbe.java && java QmaiDetailProbe BH20260817000118
 */
public class QmaiDetailProbe {

    static final String OPEN_ID = "71fcea7abc9709d653693116410b5385";
    static final String GRANT_CODE = "WaGI2rqy9b";
    static final String OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ";
    static final String URL = "https://openapi.qmai.cn/v3/scm/order/declare/order/detail";

    public static void main(String[] args) throws Exception {
        String declareNo = args.length > 0 ? args[0] : "BH20260817000118";
        long ts = System.currentTimeMillis() / 1000;
        int nonce = 10000 + new Random().nextInt(90000);
        String token = makeToken(ts, nonce);
        String body = "{\"openId\":\"" + OPEN_ID + "\",\"grantCode\":\"" + GRANT_CODE
                + "\",\"nonce\":" + nonce + ",\"timestamp\":" + ts
                + ",\"token\":\"" + token + "\",\"params\":{\"declareNo\":\"" + declareNo + "\"}}";
        System.out.println("== detail " + declareNo + " ==>\n" + post(body));
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
}
