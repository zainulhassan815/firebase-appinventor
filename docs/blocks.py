import glob
import html
import json
import os
import re
import sys
import zipfile

DOCS = os.path.dirname(os.path.abspath(__file__))
IMAGES = 'images/blocks'

COMPONENTS = [
    ('FirebaseMessaging', 'Messaging', 'messaging.md', [
        ('Connecting', ['GoogleServicesFile', 'InitializeApp']),
        ('Token', ['GetToken', 'GotToken', 'NewToken', 'DeleteToken', 'TokenDeleted']),
        ('Topics', ['SubscribeToTopic', 'SubscribedToTopic', 'UnsubscribeFromTopic', 'UnsubscribedFromTopic']),
        ('Messages', ['MessageReceived', 'NotificationOpened', 'DeletedMessages']),
        ('Notifications', ['RequestNotificationPermission', 'NotificationPermissionResult', 'NotificationsEnabled',
                           'ShowNotification', 'ChannelName', 'ChannelImportance', 'NotificationIcon']),
        ('Errors', ['Error']),
    ]),
]


def sentence(text):
    text = text.strip()
    return text if text.endswith(('.', '!', '?')) else text + '.'


def plain(text):
    return html.unescape(re.sub(r'<[^>]+>', '', text)).strip()


def anchor(title):
    return re.sub(r'[^\w\- ]', '', title.lower()).replace(' ', '-')


class Reference:

    def __init__(self, components):
        self.components = {c['name']: c for c in components}
        self.images = []

    def image(self, name, alt):
        self.images.append(name)
        return f'![{alt}]({IMAGES}/{name}.png)'

    def block(self, instance, entry):
        return f'{self.image(f"{instance}-{entry["name"]}", entry["name"])}\n\n**{entry["name"]}:** {sentence(entry["description"])}'

    def property(self, instance, prop):
        shots = [self.image(f'{instance}-get{prop["name"]}', f'get {prop["name"]}')]
        if prop['rw'] == 'read-write':
            shots.append(self.image(f'{instance}-set{prop["name"]}', f'set {prop["name"]}'))
        return ' '.join(shots) + f'\n\n**{prop["name"]}** ({prop["rw"]}): {sentence(prop["description"])}'

    def entries(self, component):
        instance = f'{component["name"]}1'
        entries = {e['name']: self.block(instance, e) for e in component['methods'] + component['events']}
        entries.update({p['name']: self.property(instance, p) for p in component['blockProperties']})
        return entries

    def dropdowns(self):
        found = {}
        for component in self.components.values():
            for entry in component['events'] + component['methods'] + component['blockProperties']:
                for owner in [entry] + entry.get('params', []):
                    data = owner.get('helper', {}).get('data')
                    if data:
                        found[data['key']] = data
        return [f'{self.image(f"helper-{key}", key)}\n\n**{key}:** ' + ', '.join(o['name'] for o in data['options'])
                for key, data in sorted(found.items())]

    def component(self, name, guide, path, sections):
        component = self.components[name]
        entries = self.entries(component)
        listed = [entry for _, names in sections for entry in names]
        unlisted = sorted(set(entries) - set(listed))
        unknown = sorted(set(listed) - set(entries))
        if unlisted or unknown:
            sys.exit(f'{name}: blocks without a section: {unlisted}. Sections naming no block: {unknown}')
        lines = ['', f'## {name}', '', f'{sentence(plain(component["helpString"]))} Guide: [{guide}]({path}).']
        for title, names in sections:
            lines += ['', f'### {title}', '', '\n\n'.join(entries[entry] for entry in names)]
        return lines

    def markdown(self):
        named = {name for name, _, _, _ in COMPONENTS}
        if set(self.components) != named:
            sys.exit(f'Components without a section: {sorted(set(self.components) ^ named)}')
        lines = [
            '# Blocks',
            '',
            'Every block of the Firebase extension. Each image contains its block, so you can drag it '
            'straight into your workspace.',
            '',
        ]
        for name, _, _, sections in COMPONENTS:
            lines.append(f'- [{name}](#{anchor(name)})')
            lines += [f'  - [{title}](#{anchor(title)})' for title, _ in sections]
        lines.append('- [Dropdowns](#dropdowns)')
        for name, guide, path, sections in COMPONENTS:
            lines += self.component(name, guide, path, sections)
        lines += ['', '## Dropdowns', '', '\n\n'.join(self.dropdowns()), '']
        return '\n'.join(lines)


def main(aix_path):
    with zipfile.ZipFile(aix_path) as aix:
        components = next(json.loads(aix.read(n)) for n in aix.namelist() if n.endswith('/components.json'))
    reference = Reference(components)
    text = reference.markdown()
    missing = [n for n in reference.images if not os.path.exists(os.path.join(DOCS, IMAGES, f'{n}.png'))]
    if missing:
        sys.exit(f'Missing block images: {", ".join(missing)}')
    with open(os.path.join(DOCS, 'blocks.md'), 'w') as f:
        f.write(text)


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else 'out'
    main(glob.glob(os.path.join(out, '*.aix'))[0])
