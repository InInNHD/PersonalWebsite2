"""恢复失败回滚的标准库测试；不连接MySQL，不读取个人配置。"""
from pathlib import Path
from unittest.mock import patch
from urllib.parse import urlsplit
import hashlib, importlib.util, json, subprocess, sys, tempfile, unittest, zipfile

spec=importlib.util.spec_from_file_location('local_backup',Path(__file__).resolve().parents[1]/'local-backup.py')
backup=importlib.util.module_from_spec(spec);spec.loader.exec_module(backup)

class RestoreRollbackTests(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.addCleanup(self.temporary.cleanup)
        self.root=Path(self.temporary.name);self.source=self.root/'backup';self.source.mkdir()
        self.target=self.root/'restored';self.name='00000000-0000-0000-0000-000000000001.png'
        self.image=b'synthetic-media';self.sql='SELECT 1;';self.commands=[]
        (self.source/'database.sql').write_text(self.sql,encoding='utf-8')
        with zipfile.ZipFile(self.source/'media.zip','w') as archive:archive.writestr(self.name,self.image)
        with zipfile.ZipFile(self.source/'source.zip','w'):pass
        manifest={'format':1,'media':{self.name:hashlib.sha256(self.image).hexdigest()},'files':{p.name:backup.digest(p) for p in self.source.iterdir()}}
        (self.source/'manifest.json').write_text(json.dumps(manifest),encoding='utf-8')

    def run_restore(self,fail_import=False,fail_create=False,fail_drop=False,fail_media=False):
        def run(command,**kwargs):
            text=kwargs.get('input');self.commands.append(text)
            failed=(fail_create and text and text.startswith('CREATE DATABASE')) or (fail_import and text==self.sql) or (fail_drop and text and text.startswith('DROP DATABASE'))
            return subprocess.CompletedProcess(command,1 if failed else 0,stdout='',stderr='')
        original=zipfile.ZipFile.read;reads=0
        def read(archive,*args,**kwargs):
            nonlocal reads;reads+=1
            if fail_media and reads==2:raise OSError('simulated media write/read failure')
            return original(archive,*args,**kwargs)
        argv=['local-backup.py','restore','--offline','--backup',str(self.source),'--media',str(self.target),'--database','pw_media_restore_unittest']
        with patch.object(sys,'argv',argv),patch.object(backup,'properties',return_value=({},urlsplit('mysql://127.0.0.1/source'),'fake','fake')),patch.object(backup.shutil,'which',return_value=sys.executable),patch.object(backup.subprocess,'run',side_effect=run),patch.object(zipfile.ZipFile,'read',read):
            backup.main()

    def test_import_failure_drops_only_just_created_database(self):
        with self.assertRaisesRegex(RuntimeError,'已回滚'):self.run_restore(fail_import=True)
        self.assertTrue(self.commands[-1].startswith('DROP DATABASE `pw_media_restore_unittest`'))
        self.assertFalse(self.target.exists())

    def test_existing_database_is_never_dropped(self):
        with self.assertRaises(RuntimeError):self.run_restore(fail_create=True)
        self.assertEqual(len(self.commands),1);self.assertFalse(self.target.exists())

    def test_media_failure_removes_partial_file_and_new_directory(self):
        with self.assertRaisesRegex(RuntimeError,'已回滚'):self.run_restore(fail_media=True)
        self.assertFalse(self.target.exists());self.assertTrue(self.commands[-1].startswith('DROP DATABASE'))

    def test_failed_rollback_is_reported(self):
        with self.assertRaisesRegex(RuntimeError,'部分回滚未完成'):self.run_restore(fail_import=True,fail_drop=True)

    def test_success_keeps_restored_media_and_database(self):
        self.run_restore();self.assertEqual((self.target/self.name).read_bytes(),self.image)
        self.assertFalse(any(text and text.startswith('DROP DATABASE') for text in self.commands))

if __name__=='__main__':unittest.main()
