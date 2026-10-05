import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('releasecheck',Path(__file__).with_name('verify-android-artifact.py'));check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)
class ReleaseArtifactPolicyTest(unittest.TestCase):
    def metadata(self,**changes):
        value={'application_id':'io.weave.client','version_code':107,'version_name':'0.4.0-rc1','min_sdk':26,
            'debuggable':False,'abis':['arm64-v8a'],'permissions':list(check.ALLOWED_PERMISSIONS),
            'signer_sha256':[check.DEVELOPMENT_CERT],'debug_signer_dn':True}
        return value|changes
    def test_valid_optimized_candidate(self):self.assertEqual([],check.violations(self.metadata(),'candidate','0.4.0-rc1',107))
    def test_filename_does_not_hide_debuggable(self):self.assertIn('APK is debuggable',check.violations(self.metadata(debuggable=True),'candidate','0.4.0-rc1',107))
    def test_stable_rejects_developer_certificate(self):self.assertIn('Development signer is not allowed for stable publication',check.violations(self.metadata(version_name='0.4.0'),'stable','0.4.0',107,check.DEVELOPMENT_CERT))
    def test_stable_requires_owner_confirmed_signer(self):self.assertIn('Confirmed production certificate SHA256 is required',check.violations(self.metadata(version_name='0.4.0'),'stable','0.4.0',107))
    def test_stable_rejects_prerelease_suffix(self):self.assertIn('Stable version must not contain a prerelease suffix',check.violations(self.metadata(),'stable','0.4.0-rc1',107))
    def test_stable_accepts_matching_non_debug_signer(self):self.assertEqual([],check.violations(self.metadata(version_name='0.4.0',signer_sha256=['a'*64],debug_signer_dn=False),'stable','0.4.0',107,'a'*64))
    def test_wrong_production_signer_is_rejected(self):self.assertIn('Production signer does not match the confirmed certificate',check.violations(self.metadata(version_name='0.4.0',signer_sha256=['b'*64],debug_signer_dn=False),'stable','0.4.0',107,'a'*64))
    def test_universal_and_wrong_architecture_are_rejected(self):
        for abis in [['arm64-v8a','x86_64'],['x86_64'],[]]:self.assertIn('Expected exactly one ARM64 ABI',check.violations(self.metadata(abis=abis),'candidate','0.4.0-rc1',107))
    def test_added_permission_requires_review(self):self.assertIn('Unexpected Android permission',check.violations(self.metadata(permissions=['android.permission.QUERY_ALL_PACKAGES']),'candidate','0.4.0-rc1',107))
    def test_production_candidate_requires_exact_previous_certificate_when_requested(self):
        self.assertIn('Production signer does not match the confirmed certificate',check.violations(self.metadata(),'candidate','0.4.0-rc1',107,'a'*64))
        self.assertEqual([],check.violations(self.metadata(signer_sha256=['a'*64],debug_signer_dn=False),'candidate','0.4.0-rc1',107,'a'*64))
if __name__=='__main__':unittest.main()
