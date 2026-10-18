import glob
import os
import sys
import zipfile

SAMPLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'push-demo')
PROJECT_FOLDERS = ('youngandroidproject', 'src', 'assets')


def package(aix_path, aia_path):
    with zipfile.ZipFile(aix_path) as aix, zipfile.ZipFile(aia_path, 'w', zipfile.ZIP_DEFLATED) as aia:
        for folder in PROJECT_FOLDERS:
            for root, _, files in os.walk(os.path.join(SAMPLE, folder)):
                for name in files:
                    path = os.path.join(root, name)
                    aia.write(path, os.path.relpath(path, SAMPLE))
        for name in aix.namelist():
            if not name.endswith('/'):
                aia.writestr(f'assets/external_comps/{name}', aix.read(name))


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else 'out'
    package(glob.glob(os.path.join(out, '*.aix'))[0], os.path.join(out, 'PushDemo.aia'))
