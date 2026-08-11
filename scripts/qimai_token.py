"""企迈 API Token 生成工具"""
import hmac, hashlib, base64, urllib.parse, random, time

OPEN_KEY = 'KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ'
OPEN_ID = '71fcea7abc9709d653693116410b5385'
GRANT_CODE = 'WaGI2rqy9b'

timestamp = str(int(time.time()))
nonce = str(random.randint(10000, 99999))

params = {'grantCode': GRANT_CODE, 'nonce': nonce, 'openId': OPEN_ID, 'timestamp': timestamp}
sorted_str = '&'.join(f'{k}={v}' for k, v in sorted(params.items()))
encoded = urllib.parse.quote(sorted_str, safe='')
restored = encoded.replace('%3D', '=').replace('%26', '&')
hmac_result = hmac.new(OPEN_KEY.encode('utf-8'), restored.encode('utf-8'), hashlib.sha1).digest()
base64_token = base64.b64encode(hmac_result).decode('utf-8')
final_token = urllib.parse.quote(base64_token, safe='')

print(f'timestamp={timestamp}')
print(f'nonce={nonce}')
print(f'token={final_token}')
