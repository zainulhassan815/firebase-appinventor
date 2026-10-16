#!/usr/bin/env python3
import base64
import json
import sys
import time
import urllib.parse
import urllib.request

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

USAGE = 'usage: send.py <service-account.json> <token|topic> <target> <message.json>'
SCOPE = 'https://www.googleapis.com/auth/firebase.messaging'


def encode(value):
    raw = value if isinstance(value, bytes) else json.dumps(value).encode()
    return base64.urlsafe_b64encode(raw).rstrip(b'=').decode()


def access_token(account):
    now = int(time.time())
    claims = {'iss': account['client_email'], 'scope': SCOPE, 'aud': account['token_uri'], 'iat': now, 'exp': now + 600}
    unsigned = f"{encode({'alg': 'RS256', 'typ': 'JWT'})}.{encode(claims)}"
    key = serialization.load_pem_private_key(account['private_key'].encode(), password=None)
    assertion = f'{unsigned}.{encode(key.sign(unsigned.encode(), padding.PKCS1v15(), hashes.SHA256()))}'
    body = urllib.parse.urlencode({'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer', 'assertion': assertion})
    with urllib.request.urlopen(account['token_uri'], body.encode()) as response:
        return json.load(response)['access_token']


def send(account, kind, target, content):
    message = {kind: target, **content, 'android': {'priority': 'high', **content.get('android', {})}}
    request = urllib.request.Request(
        f"https://fcm.googleapis.com/v1/projects/{account['project_id']}/messages:send",
        json.dumps({'message': message}).encode(),
        {'Authorization': f'Bearer {access_token(account)}', 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request) as response:
            print(json.load(response)['name'])
    except urllib.error.HTTPError as error:
        sys.exit(f'{error.code} {error.read().decode()}')


if __name__ == '__main__':
    if len(sys.argv) != 5:
        sys.exit(USAGE)
    with open(sys.argv[1]) as f:
        send(json.load(f), sys.argv[2], sys.argv[3], json.loads(sys.argv[4]))
