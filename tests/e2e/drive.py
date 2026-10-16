#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

USAGE = 'usage: drive.py <apk> <steps.json> <service-account.json>'
PACKAGE = 'dev.dreamerslab.firebase.e2e'
ADB = os.environ.get('ADB', os.path.expanduser('~/Android/sdk/platform-tools/adb'))
SEND = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'send.py')


class Failure(Exception):
    pass


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True, check=True).stdout


def screen_nodes():
    for _ in range(3):
        if 'dumped' in adb('shell', 'uiautomator', 'dump', '/sdcard/e2e.xml'):
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/e2e.xml')).iter('node'))
        time.sleep(1)
    raise Failure('uiautomator dump failed')


def report():
    texts = [n.get('text') for n in screen_nodes()
             if n.get('class') == 'android.widget.TextView' and n.get('package') == PACKAGE and n.get('text')]
    return max(texts, key=len).splitlines() if texts else []


def shown(lines):
    return '\n'.join('token <redacted>' if line.startswith('token ') else line for line in lines)


def crashed():
    return any(f'Process: {PACKAGE}' in block for block in adb('logcat', '-d').split('FATAL EXCEPTION')[1:])


def until(condition, timeout, what):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if crashed():
            raise Failure(f'the app crashed while waiting for {what}')
        if result := condition():
            return result
        time.sleep(1)
    raise Failure(f'timed out waiting for {what}')


class Driver:
    def __init__(self, apk, account):
        self.apk = apk
        self.account = account
        self.token = None

    def install(self, grant=True):
        if PACKAGE in adb('shell', 'pm', 'list', 'packages', PACKAGE):
            adb('uninstall', PACKAGE)
        adb('install', *(['-g'] if grant else []), self.apk)
        adb('logcat', '-c')

    def launch(self):
        adb('shell', 'monkey', '-p', PACKAGE, '-c', 'android.intent.category.LAUNCHER', '1')

    def wait(self, line, timeout=60):
        until(lambda: any(re.fullmatch(line, l) for l in report()), timeout, f'/{line}/')

    def home(self):
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        time.sleep(2)

    def kill(self):
        adb('shell', 'am', 'kill', PACKAGE)
        until(lambda: not adb('shell', 'pidof', PACKAGE).strip(), 10, 'the process to stop')

    def send(self, topic=None, **message):
        target = ['topic', topic] if topic else ['token', self._device_token()]
        subprocess.run([sys.executable, '-I', SEND, self.account, *target, json.dumps(message)],
                       check=True, stdout=subprocess.DEVNULL)

    def pause(self, seconds):
        time.sleep(seconds)

    def _device_token(self):
        self.token = self.token or next(l[6:] for l in report() if l.startswith('token '))
        return self.token

    def notification(self, title, timeout=30):
        until(lambda: f'android.title=String ({title})' in adb('shell', 'dumpsys', 'notification', '--noredact'),
              timeout, f'notification "{title}"')

    def tap(self, text):
        adb('shell', 'cmd', 'statusbar', 'expand-notifications')
        node = until(lambda: next((n for n in screen_nodes() if n.get('text') == text), None), 10, f'"{text}"')
        left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
        adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

    def expect(self, lines, timeout=30):
        try:
            until(lambda: len(report()) == len(lines) and all(map(re.fullmatch, lines, report())), timeout,
                  'the expected report')
        except Failure:
            raise Failure('expected:\n' + '\n'.join(lines) + '\ngot:\n' + shown(report()))


def main(apk, steps, account):
    driver = Driver(apk, account)
    with open(steps) as f:
        for step in json.load(f):
            (action, argument), = step.items()
            print(f'{action} {json.dumps(argument) if argument else ""}'.strip())
            getattr(driver, action)(**argument)
    print(shown(report()))
    print('PASS')


if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(USAGE)
    try:
        main(*sys.argv[1:])
    except Failure as failure:
        sys.exit(f'FAIL: {failure}')
