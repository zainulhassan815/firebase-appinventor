#!/usr/bin/env python3
import base64
import json
import sys
import time
import urllib.parse
import urllib.request

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

USAGE = '''usage: admin.py <service-account.json> send <token|topic> <target> <message.json>
       admin.py <service-account.json> publish <parameters.json>'''
MESSAGING = 'https://www.googleapis.com/auth/firebase.messaging'
REMOTE_CONFIG = 'https://www.googleapis.com/auth/firebase.remoteconfig'


def encode(value):
    raw = value if isinstance(value, bytes) else json.dumps(value).encode()
    return base64.urlsafe_b64encode(raw).rstrip(b'=').decode()


def access_token(account, scope):
    now = int(time.time())
    claims = {'iss': account['client_email'], 'scope': scope, 'aud': account['token_uri'], 'iat': now, 'exp': now + 600}
    unsigned = f"{encode({'alg': 'RS256', 'typ': 'JWT'})}.{encode(claims)}"
    key = serialization.load_pem_private_key(account['private_key'].encode(), password=None)
    assertion = f'{unsigned}.{encode(key.sign(unsigned.encode(), padding.PKCS1v15(), hashes.SHA256()))}'
    body = urllib.parse.urlencode({'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer', 'assertion': assertion})
    with urllib.request.urlopen(account['token_uri'], body.encode()) as response:
        return json.load(response)['access_token']


def call(request):
    try:
        with urllib.request.urlopen(request) as response:
            return json.load(response), response.headers
    except urllib.error.HTTPError as error:
        sys.exit(f'{error.code} {error.read().decode()}')


def send(account, kind, target, content):
    message = {kind: target, **content, 'android': {'priority': 'high', **content.get('android', {})}}
    body, _ = call(urllib.request.Request(
        f"https://fcm.googleapis.com/v1/projects/{account['project_id']}/messages:send",
        json.dumps({'message': message}).encode(),
        {'Authorization': f'Bearer {access_token(account, MESSAGING)}', 'Content-Type': 'application/json'}))
    print(body['name'])


def publish(account, parameters):
    url = f"https://firebaseremoteconfig.googleapis.com/v1/projects/{account['project_id']}/remoteConfig"
    auth = {'Authorization': f'Bearer {access_token(account, REMOTE_CONFIG)}'}
    template, headers = call(urllib.request.Request(url, headers=auth))
    template.pop('version', None)
    template.setdefault('parameters', {}).update(
        {key: {'defaultValue': {'value': value}} for key, value in parameters.items()})
    body, _ = call(urllib.request.Request(
        url, json.dumps(template).encode(),
        {**auth, 'Content-Type': 'application/json; UTF-8', 'If-Match': headers.get('ETag') or '*'}, method='PUT'))
    print(body['version']['versionNumber'])


if __name__ == '__main__':
    commands = {'send': (send, 3), 'publish': (publish, 1)}
    if len(sys.argv) < 3 or sys.argv[2] not in commands or len(sys.argv) != 3 + commands[sys.argv[2]][1]:
        sys.exit(USAGE)
    with open(sys.argv[1]) as f:
        account = json.load(f)
    command, arguments = commands[sys.argv[2]][0], sys.argv[3:]
    command(account, *arguments[:-1], json.loads(arguments[-1]))
