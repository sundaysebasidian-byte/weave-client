#!/usr/bin/env python3
"""Inspect APK bytes using installed Android tools; never install or sign an APK."""
import argparse, hashlib, json, os, re, subprocess, zipfile
from pathlib import Path

DEVELOPMENT_CERT = '54271ffd8e45ca026f886b96a78a55fa97ff4175c7403f4938310047a4f784c2'
CORE_SHA256 = 'd98ef51225c40b89e2ddc8eecb0677c677b80ae7298eee08859b9392d136f6df'
ALLOWED_PERMISSIONS = {'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.FOREGROUND_SERVICE', 'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
    'android.permission.POST_NOTIFICATIONS', 'android.permission.CAMERA',
    'android.permission.RECEIVE_BOOT_COMPLETED', 'io.weave.client.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}

def parse_metadata(badging, certificates):
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package:
        raise ValueError('APK package/version metadata unavailable')
    sdk = re.search(r"(?:minSdkVersion|sdkVersion):'(\d+)'", badging)
    abi = re.search(r'native-code: (.+)', badging)
    signers = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})', certificates)
    return {'application_id':package[1], 'version_code':int(package[2]), 'version_name':package[3],
        'min_sdk':int(sdk[1]) if sdk else None, 'debuggable':'application-debuggable' in badging,
        'abis':re.findall(r"'([^']+)'", abi[1]) if abi else [],
        'permissions':sorted(re.findall(r"uses-permission: name='([^']+)'", badging)),
        'signer_sha256':[x.lower() for x in signers], 'debug_signer_dn':'CN=Android Debug' in certificates}

def violations(metadata, channel, expected_version, expected_code, production_certificate=None):
    errors=[]
    if metadata['application_id'] != 'io.weave.client':errors.append('Wrong application ID')
    if metadata['version_name'] != expected_version or metadata['version_code'] != expected_code:errors.append('Unexpected application version')
    if metadata['debuggable']:errors.append('APK is debuggable')
    if metadata['abis'] != ['arm64-v8a']:errors.append('Expected exactly one ARM64 ABI')
    if metadata['min_sdk'] != 26:errors.append('Unexpected minimum Android API')
    if len(metadata['signer_sha256']) != 1:errors.append('Expected one verified signer')
    if set(metadata['permissions']) - ALLOWED_PERMISSIONS:errors.append('Unexpected Android permission')
    if channel == 'candidate':
        if not re.fullmatch(r'\d+\.\d+\.\d+-rc\d+', expected_version):errors.append('Candidate must carry an RC version')
        if production_certificate and metadata['signer_sha256'] != [production_certificate.lower()]:
            errors.append('Production signer does not match the confirmed certificate')
    else:
        if not re.fullmatch(r'\d+\.\d+\.\d+', expected_version):errors.append('Stable version must not contain a prerelease suffix')
        if not production_certificate or not re.fullmatch(r'[0-9a-fA-F]{64}', production_certificate):errors.append('Confirmed production certificate SHA256 is required')
        elif metadata['signer_sha256'] != [production_certificate.lower()]:errors.append('Production signer does not match the confirmed certificate')
        if DEVELOPMENT_CERT in metadata['signer_sha256'] or metadata['debug_signer_dn']:errors.append('Development signer is not allowed for stable publication')
    return errors

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path);parser.add_argument('--channel',choices=['candidate','stable'],required=True)
    parser.add_argument('--version',required=True);parser.add_argument('--version-code',type=int,required=True)
    parser.add_argument('--production-certificate-sha256',help='Public certificate fingerprint only; never pass a private key')
    parser.add_argument('--sdk',type=Path,default=os.environ.get('ANDROID_HOME'))
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    if not args.sdk:parser.error('ANDROID_HOME or --sdk is required')
    tool_dir=args.sdk/'build-tools/36.0.0'
    def run(name,*options):return subprocess.check_output([str(tool_dir/name),*map(str,options)],text=True,stderr=subprocess.STDOUT)
    badging=run('aapt2','dump','badging',args.apk)
    certificates=run('apksigner','verify','--verbose','--print-certs',args.apk)
    manifest=run('aapt2','dump','xmltree',args.apk,'--file','AndroidManifest.xml')
    metadata=parse_metadata(badging,certificates)
    errors=violations(metadata,args.channel,args.version,args.version_code,args.production_certificate_sha256)
    if re.search(r'android:testOnly[^\n]*0xffffffff',manifest):errors.append('APK is marked testOnly')
    if 'android:allowBackup' not in manifest or re.search(r'android:allowBackup[^\n]*0xffffffff',manifest):errors.append('Android app backup must remain disabled')
    if re.search(r'android:usesCleartextTraffic[^\n]*0xffffffff',manifest):errors.append('App-wide cleartext traffic must remain disabled')
    with zipfile.ZipFile(args.apk) as z:
        names=z.namelist()
        core_hash=hashlib.sha256(z.read('lib/arm64-v8a/libclash.so')).hexdigest()
        if core_hash!=CORE_SHA256:errors.append('Packaged native core changed without provenance review')
        dex=b''.join(z.read(n) for n in names if re.fullmatch(r'classes\d*\.dex',n))
        for contract in ['NativeBridge','NativeCompletion','NativeTunCallback']:
            if ('Lio/weave/client/core/bridge/'+contract+';').encode() not in dex:errors.append('JNI contract missing after R8: '+contract)
        for fixture in ['Build105SubscriptionBaselineKt','HomeStateFixturesKt']:
            if fixture.encode() in dex:errors.append('Debug screen fixtures leaked into APK')
        if any(n.endswith('cache.db') for n in names):errors.append('Runtime cache leaked into APK')
    result={'channel':args.channel,'passed':not errors,'errors':errors,'metadata':metadata,
        'bytes':args.apk.stat().st_size,'sha256':hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        'native_core_sha256':core_hash,'signature_verified':True,'installed_or_vpn_tested':False}
    if args.output:args.output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2));raise SystemExit(0 if not errors else 1)

if __name__=='__main__':main()
