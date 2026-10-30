#!/usr/bin/env python3
"""Exercise the real service/core under an ephemeral, unprivileged launchd job in CI."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import plistlib
import signal
import socket
import subprocess
import time
import uuid


def command(*args, check=True):
    result = subprocess.run(args, text=True, capture_output=True, timeout=20)
    if check and result.returncode:
        raise RuntimeError(f'{args[0:3]} failed ({result.returncode}): {result.stderr.strip()}')
    return result


def processes():
    result = {}
    for row in command('/bin/ps', '-axo', 'pid=,ppid=,pgid=,lstart=,command=').stdout.splitlines():
        fields = row.strip().split(None, 8)
        if len(fields) == 9:
            result[int(fields[0])] = {'ppid': int(fields[1]), 'pgid': int(fields[2]),
                                     'started': ' '.join(fields[3:8]), 'command': fields[8]}
    return result


def wait_for(predicate, description, timeout=15):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        value = predicate()
        if value:
            return value
        time.sleep(0.05)
    raise AssertionError(f'Timed out: {description}')


def can_lock(path):
    with path.open('r+b') as file:
        try:
            fcntl.flock(file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            return True
        except BlockingIOError:
            return False


def port_closed(port):
    with socket.socket() as connection:
        connection.settimeout(0.2)
        return connection.connect_ex(('127.0.0.1', port)) != 0


def kill_owned(pid, expected, number):
    assert processes().get(pid) == expected, f'Process identity changed: {pid}'
    os.kill(pid, number)


def run_case(base, helper, core, mode):
    case = base / mode
    case.mkdir(mode=0o700)
    root = case / 'runtime'
    report = case / 'ready.json'
    label = 'io.weave.test.lifecycle.' + uuid.uuid4().hex
    target = f'user/{os.getuid()}/{label}'
    plist = case / (label + '.plist')
    policy = {'Label': label, 'ProgramArguments': [str(helper), str(core), str(root), str(report), 'wait'],
              'RunAtLoad': True, 'KeepAlive': False, 'AbandonProcessGroup': mode == 'negative-abandon',
              'ExitTimeOut': 5, 'ProcessType': 'Interactive', 'LimitLoadToSessionType': 'Background',
              'StandardOutPath': str(case / 'stdout.log'), 'StandardErrorPath': str(case / 'stderr.log')}
    plist.write_bytes(plistlib.dumps(policy))
    registered = False
    owned = {}
    evidence = {'mode': mode, 'target': target, 'policy': policy, 'success': False}
    try:
        # No global overrides, enable/disable state, sudo or system domain operations.
        command('/usr/bin/plutil', '-lint', str(plist))
        domain = command('/bin/launchctl', 'print', f'user/{os.getuid()}')
        (case / 'domain-before.log').write_text(domain.stdout[:32768])
        try:
            command('/bin/launchctl', 'bootstrap', f'user/{os.getuid()}', str(plist))
        except RuntimeError:
            diagnostic = command('/usr/bin/log', 'show', '--style', 'compact', '--last', '2m',
                                 '--predicate', f'process == "launchd" AND eventMessage CONTAINS "{label}"', check=False)
            (case / 'bootstrap-diagnostic.log').write_text((diagnostic.stdout + diagnostic.stderr)[-32768:])
            raise
        registered = True
        wait_for(report.exists, 'first core readiness')
        ready = json.loads(report.read_text())
        table = processes()
        host = ready['pid']
        assert host in table and table[host]['pgid'] == host == ready['pgid']
        owned[host] = table[host]
        children = {pid: info for pid, info in table.items() if info['ppid'] == host}
        assert len(children) == 1, children
        child, identity = next(iter(children.items()))
        assert identity['command'].startswith(str(core) + ' -d ' + str(root) + '/')
        assert identity['pgid'] == host
        owned[child] = identity
        directories = [p for p in root.iterdir() if p.is_dir()]
        assert len(directories) == 1
        old_runtime = directories[0]
        service_lease = root / '.service-lease'
        lease_inode = service_lease.stat().st_ino
        assert not can_lock(service_lease) and not can_lock(old_runtime / '.lease')
        evidence.update(host=host, core=child, process_group=host, first_runtime=str(old_runtime),
                        lease_inode=lease_inode, child_identity=identity, first_ready=ready)
        if mode == 'stopped-core-kill-host':
            kill_owned(child, identity, signal.SIGSTOP)
        if mode == 'bootout-stopped-host':
            kill_owned(host, table[host], signal.SIGSTOP)
            command('/bin/launchctl', 'bootout', target)
            registered = False
        else:
            kill_owned(host, table[host], signal.SIGTERM if mode == 'graceful' else signal.SIGKILL)
        wait_for(lambda: host not in processes(), 'host exit')
        if mode == 'negative-abandon':
            # A control with cleanup disabled must expose the orphan and held leases.
            time.sleep(0.3)
            current = processes().get(child)
            assert current and current['command'] == identity['command'] and current['started'] == identity['started']
            assert not can_lock(service_lease) and not can_lock(old_runtime / '.lease')
            evidence['negative_control_orphan_observed'] = True
            # The expected PPID has changed after host exit; refresh the exact identity.
            kill_owned(child, current, signal.SIGKILL)
        wait_for(lambda: child not in processes(), 'core exit after host termination')
        wait_for(lambda: can_lock(service_lease), 'machine lease release')
        assert port_closed(ready['mixedPort'])
        if mode == 'graceful':
            assert report.with_suffix('.json.stopped').read_text() == 'stopped'
            assert not old_runtime.exists()
        # Reuse exactly the same registered job/root to exercise orphan recovery and
        # reconnection. No alternate root or lock inode can make this pass.
        report.unlink()
        if not registered:
            command('/bin/launchctl', 'bootstrap', f'user/{os.getuid()}', str(plist))
            registered = True
        else:
            command('/bin/launchctl', 'kickstart', target)
        wait_for(report.exists, 'restarted service readiness')
        restarted = json.loads(report.read_text())
        assert restarted['pid'] != host
        assert service_lease.stat().st_ino == lease_inode
        assert not old_runtime.exists()
        table = processes()
        assert restarted['pid'] in table
        owned[restarted['pid']] = table[restarted['pid']]
        restarted_children = {pid: info for pid, info in table.items() if info['ppid'] == restarted['pid']}
        assert len(restarted_children) == 1
        for pid, info in restarted_children.items():
            assert info['command'].startswith(str(core) + ' -d ' + str(root) + '/')
            assert info['pgid'] == restarted['pid']
            owned[pid] = info
        evidence.update(restarted_host=restarted['pid'], restarted_ready=restarted)
        command('/bin/launchctl', 'bootout', target)
        registered = False
        # bootout can return while the host is still completing cleanup after its
        # core exits. Wait for both owners before asserting that the lease is free.
        evidence['after_final_bootout'] = {'host_present': restarted['pid'] in processes(),
                                          'lease_available': can_lock(service_lease),
                                          'port_closed': port_closed(restarted['mixedPort'])}
        wait_for(lambda: all(pid not in processes() for pid in owned), 'final host and core cleanup')
        assert can_lock(service_lease), 'Installation host exited but service lease remains held'
        assert port_closed(restarted['mixedPort']), 'Host/core exited but proxy port remains open'
        assert not [p for p in root.iterdir() if p.is_dir()]
        evidence.update(success=True, restarted_host=restarted['pid'], restarted_ready=restarted, final_lease_inode=service_lease.stat().st_ino)
    finally:
        if registered or command('/bin/launchctl', 'print', target, check=False).returncode == 0:
            result = command('/bin/launchctl', 'bootout', target, check=False)
            evidence['cleanup_bootout_returncode'] = result.returncode
        # Also identify a child whose parent failed before publishing readiness. The
        # private UUID job root and exact pinned executable belong only to this case.
        for pid, info in processes().items():
            if info['command'].startswith(str(core) + ' -d ' + str(root) + '/'):
                owned.setdefault(pid, info)
        # Only stop exact host/core identities observed here, never a matching name alone.
        for pid, expected in owned.items():
            current = processes().get(pid)
            if current and current['command'] == expected['command'] and current['started'] == expected['started']:
                kill_owned(pid, current, signal.SIGKILL)
        try:
            wait_for(lambda: all(pid not in processes() for pid in owned), 'owned cleanup', timeout=5)
        except AssertionError as error:
            evidence['cleanup_error'] = str(error)
        evidence['remaining_owned_pids'] = [pid for pid in owned if pid in processes()]
        (case / 'evidence.json').write_text(json.dumps(evidence, indent=2) + '\n')
    assert not evidence['remaining_owned_pids']
    return evidence


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--helper', required=True, type=Path)
    parser.add_argument('--core', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    assert os.geteuid() != 0
    assert os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted'
    assert os.environ.get('RUNNER_OS') == 'macOS'
    def interrupted(_number, _frame):
        raise KeyboardInterrupt('CI interrupted')
    signal.signal(signal.SIGTERM, interrupted)
    base = args.output.resolve()
    assert base.is_relative_to(Path(os.environ['RUNNER_TEMP']).resolve())
    base.mkdir(mode=0o700, parents=False, exist_ok=False)
    helper, core = args.helper.resolve(strict=True), args.core.resolve(strict=True)
    assert helper.is_file() and core.is_file()
    modes = ['graceful', 'kill-host', 'stopped-core-kill-host', 'bootout-stopped-host', 'negative-abandon']
    results = [run_case(base, helper, core, mode) for mode in modes]
    (base / 'summary.json').write_text(json.dumps(results, indent=2) + '\n')
    print(f'{len(results)} independent launchd lifecycle scenarios passed')


if __name__ == '__main__':
    main()
