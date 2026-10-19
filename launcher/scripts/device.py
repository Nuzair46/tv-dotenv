#!/usr/bin/env python3
"""Explicitly targeted launcher deployment. No debloat, root, reset or picture tweaks."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = 'dev.nuzair.home'
COMPONENT = PACKAGE + '/.MainActivity'
SERVICE = PACKAGE + '/' + PACKAGE + '.HomeOverrideService'
SERVICE_ALIASES = {SERVICE, PACKAGE + '/.HomeOverrideService'}

def configuration():
    values = {}
    path = ROOT / '.env'
    if path.exists():
        for line in path.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            key, separator, value = line.partition('=')
            if not separator or not re.fullmatch(r'TV_[A-Z_]+', key):
                raise ValueError('Invalid .env entry; use literal TV_KEY=value lines')
            values[key] = value
    for key in ('TV_ADB_SERIAL', 'TV_HARDWARE_SERIAL', 'TV_PRODUCT', 'TV_FINGERPRINT'):
        values[key] = os.environ.get(key, values.get(key, ''))
        if not values[key]:
            raise ValueError(f'Set {key} in .env or the environment; see .env.example')
    return values

class Device:
    def __init__(self, config):
        self.config = config
        # Store per-device state; changing the endpoint does not lose recovery data.
        identity = hashlib.sha256(config['TV_HARDWARE_SERIAL'].encode()).hexdigest()[:16]
        self.private = ROOT / '.local' / identity
        self.backup = self.private / 'launcher-before.json'

    def adb(self, *args, check=True):
        result = subprocess.run(['adb', '-s', self.config['TV_ADB_SERIAL'], *args],
                                capture_output=True, text=True, timeout=120)
        if check and result.returncode:
            raise RuntimeError(result.stderr + result.stdout)
        return result.stdout.strip()

    def shell(self, *args, **kwargs):
        return self.adb('shell', ' '.join(shlex.quote(str(arg)) for arg in args), **kwargs)

    def identify(self):
        if self.adb('get-state') != 'device':
            raise RuntimeError('Selected ADB endpoint is not connected')
        for prop, key in [('ro.serialno', 'TV_HARDWARE_SERIAL'),
                          ('ro.product.device', 'TV_PRODUCT'),
                          ('ro.build.fingerprint', 'TV_FINGERPRINT')]:
            if self.shell('getprop', prop) != self.config[key]:
                raise RuntimeError(f'Device identity mismatch: {prop}; inspect before continuing')
        if self.shell('am', 'get-current-user') != '0':
            raise RuntimeError('Only owner user 0 is supported')

    def journal(self, action, **fields):
        self.private.mkdir(parents=True, exist_ok=True, mode=0o700)
        with (self.private / 'actions.jsonl').open('a') as log:
            log.write(json.dumps(dict(time=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                                      action=action, **fields)) + '\n')
            log.flush()
            os.fsync(log.fileno())

    def services(self):
        value = self.shell('settings', 'get', 'secure', 'enabled_accessibility_services')
        return set(value.split(':')) - {'', 'null'}

    def snapshot(self):
        if self.backup.exists():
            return json.loads(self.backup.read_text())
        resolved = self.shell('cmd', 'package', 'resolve-activity', '--brief',
                              '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
        data = dict(hardware_serial=self.config['TV_HARDWARE_SERIAL'],
                    resolved_home=resolved.splitlines()[-1],
                    home_role=self.shell('cmd', 'role', 'get-role-holders', 'android.app.role.HOME'),
                    services=sorted(self.services()))
        if '/' not in data['resolved_home']:
            raise RuntimeError('Unable to resolve existing HOME; no change made')
        self.journal('snapshot', state=data)
        self.backup.write_text(json.dumps(data, indent=2) + '\n')
        self.backup.chmod(0o600)
        return data

    def install(self):
        apk = ROOT / 'launcher/build/nuzair-home.apk'
        if not apk.is_file():
            raise RuntimeError('Build the APK first')
        self.snapshot()
        digest = hashlib.sha256(apk.read_bytes()).hexdigest()
        self.journal('install-intent', sha256=digest)
        result = self.adb('install', '-r', '--user', '0', str(apk))
        if 'Success' not in result:
            raise RuntimeError(result)
        self.journal('install-complete', sha256=digest)
        print(result)

    def activate(self):
        if not self.shell('pm', 'path', PACKAGE, check=False):
            raise RuntimeError('Install CustomTV first')
        self.snapshot()
        self.journal('activate-intent')
        result = self.shell('cmd', 'package', 'set-home-activity', '--user', '0', COMPONENT)
        if 'Success' not in result:
            raise RuntimeError(result)
        # Preserve unrelated accessibility services. No other launcher is disabled.
        services = (self.services() - SERVICE_ALIASES) | {SERVICE}
        self.shell('settings', 'put', 'secure', 'enabled_accessibility_services', ':'.join(sorted(services)))
        self.shell('settings', 'put', 'secure', 'accessibility_enabled', '1')
        self.shell('pm', 'grant', '--user', '0', PACKAGE, 'android.permission.READ_TV_LISTINGS')
        self.shell('am', 'start', '-W', '-n', COMPONENT)
        self.journal('activate-complete')
        print('CustomTV activated. Verify physical Home/gear and app shortcuts on the TV.')

    def rollback(self):
        if not self.backup.is_file():
            raise RuntimeError('No deployment snapshot exists; no change made')
        before = json.loads(self.backup.read_text())
        if before['hardware_serial'] != self.config['TV_HARDWARE_SERIAL']:
            raise RuntimeError('Recovery snapshot belongs to another device')
        component = before['resolved_home']
        # A role holder may differ from the firmware-prioritized HOME resolution.
        role = before['home_role'].strip()
        if role == PACKAGE:
            raise RuntimeError('Snapshot already used CustomTV; use the original migration backup or TV Settings')
        if role:
            result = self.shell('cmd', 'package', 'resolve-activity', '--brief',
                                '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME',
                                '-p', role).splitlines()[-1]
            if '/' in result:
                component = result
        if component.split('/')[0] == PACKAGE:
            raise RuntimeError('Snapshot already used CustomTV; select another HOME in TV Settings')
        if not self.shell('pm', 'path', component.split('/')[0], check=False):
            raise RuntimeError('Previous launcher is missing; no change made')
        self.journal('rollback-intent', component=component)
        services = self.services() - SERVICE_ALIASES
        self.shell('settings', 'put', 'secure', 'enabled_accessibility_services', ':'.join(sorted(services)))
        self.shell('settings', 'put', 'secure', 'accessibility_enabled', '1' if services else '0')
        result = self.shell('cmd', 'package', 'set-home-activity', '--user', '0', component)
        if 'Success' not in result:
            raise RuntimeError(result)
        self.shell('am', 'start', '-W', '-n', component)
        self.shell('am', 'force-stop', PACKAGE)
        self.journal('rollback-complete', component=component)
        print('Previous HOME restored; CustomTV remains installed and retains its data.')

    def status(self):
        print('Device identity matched; user 0.')
        print('Installed:', bool(self.shell('pm', 'path', PACKAGE, check=False)))
        print('HOME role:', self.shell('cmd', 'role', 'get-role-holders', 'android.app.role.HOME'))
        print('Remote service enabled:', bool(self.services() & SERVICE_ALIASES))
        print('This checks configuration, not physical remote/HDMI/playback functionality.')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['status', 'install', 'activate', 'rollback'])
    args = parser.parse_args()
    device = Device(configuration())
    device.identify()
    getattr(device, args.action)()

if __name__ == '__main__':
    main()
