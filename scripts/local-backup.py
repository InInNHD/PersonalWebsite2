"""本地离线备份/隔离恢复；只使用Python标准库与MySQL客户端，不输出密码。"""
from pathlib import Path
import argparse, datetime, hashlib, json, os, re, shutil, subprocess, tempfile, zipfile
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
NAME = re.compile(r'[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}\.(png|jpg)')
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def no_links(path):
    for parent in [path, *path.parents]:
        if parent.is_symlink() or (hasattr(parent,'is_junction') and parent.is_junction()): raise ValueError('不允许符号链接或junction目录和文件')
def properties():
    values = {}
    path = ROOT/'personalwebsite/src/main/resources/application.properties'
    if path.exists():
        for line in path.read_text(encoding='utf-8').splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                key,value = line.split('=',1); values[key.strip()] = value.strip()
    def resolved(key, env, default=''):
        value = os.environ.get(env, values.get(key,default))
        return re.sub(r'\$\{([^}:]+)(?::([^}]*))?\}',lambda match: os.environ.get(match[1],match[2] or ''),value)
    url = urlsplit(resolved('spring.datasource.url','DB_URL').removeprefix('jdbc:'))
    if url.scheme != 'mysql' or not url.hostname: raise ValueError('请配置本地MySQL连接或DB_URL')
    return values, url, resolved('spring.datasource.username','DB_USERNAME'), resolved('spring.datasource.password','DB_PASSWORD')
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['backup','restore'])
    parser.add_argument('--offline',action='store_true',help='确认已停止写入源库及媒体目录的应用和其他工具')
    parser.add_argument('--backup',type=Path,help='备份目标（必须不存在），或恢复来源')
    parser.add_argument('--media',type=Path,help='备份源媒体目录；默认后端media目录；恢复时为必须不存在的目标目录')
    parser.add_argument('--database',help='恢复目标必须是不存在且以pw_media_restore_开头的专用库')
    parser.add_argument('--mysql-bin',type=Path,default=Path('C:/Program Files/MySQL/MySQL Server 8.0/bin'))
    args=parser.parse_args()
    if not args.offline: parser.error('请先停止相关应用写入，再加--offline执行')
    props,url,user,password=properties()
    database=url.path.strip('/')
    if not re.fullmatch(r'[A-Za-z0-9_]+',database): raise ValueError('数据库名不合法')
    def executable(name):
        found=shutil.which(name) or str(args.mysql_bin/(name+'.exe'))
        if not Path(found).is_file(): raise ValueError('找不到MySQL客户端，请设置--mysql-bin')
        return found
    quote=lambda value: '"'+value.replace('\\','\\\\').replace('"','\\"').replace('\n','\\n').replace('\r','\\r')+'"'
    # 临时配置只用于客户端进程；finally删除。密码不进入命令行或日志。
    with tempfile.TemporaryDirectory(prefix='pw-backup-') as temporary:
        config=Path(temporary)/'client.cnf'
        config.write_text('[client]\nhost='+url.hostname+'\nport='+str(url.port or 3306)+'\nuser='+quote(user)+'\npassword='+quote(password)+'\ndefault-character-set=utf8mb4\n',encoding='utf-8')
        def run(name, arguments, sql=None):
            result=subprocess.run([executable(name),'--defaults-file='+str(config),*arguments],input=sql,encoding='utf-8',capture_output=True)
            if result.returncode: raise RuntimeError(name+'执行失败，请检查本机连接、权限和目标；未输出私有连接信息')
            return result.stdout
        backup=(args.backup or ROOT/'backups'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S-media')).absolute()
        no_links(backup)
        backup=backup.resolve(strict=False)
        if args.action=='backup':
            if backup.is_relative_to(ROOT) and not backup.is_relative_to(ROOT/'backups'):
                raise ValueError('工作区内的备份只允许放在Git忽略的backups目录')
            configured=os.environ.get('APP_MEDIA_DIRECTORY') or os.environ.get('MEDIA_DIRECTORY') or props.get('app.media.directory','./media')
            configured=re.sub(r'\$\{([^}:]+)(?::([^}]*))?\}',lambda match: os.environ.get(match[1],match[2] or ''),configured)
            default_media=Path(configured)
            if not default_media.is_absolute(): default_media=ROOT/'personalwebsite'/default_media
            media=(args.media or default_media).absolute()
            no_links(media)
            media=media.resolve(strict=False)
            if media.exists() and any(not p.is_file() for p in media.iterdir()): raise ValueError('媒体目录中存在子目录或其他未识别项')
            files=list(media.iterdir()) if media.exists() else []
            for file in files:
                if not NAME.fullmatch(file.name): raise ValueError('媒体目录包含未识别文件，请检查后重试')
                no_links(file)
            # 不能把错误的空目录当作完整媒体备份；与数据库记录核对文件及字节数。
            exists=run('mysql',['--batch','--skip-column-names'],f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{database}' AND table_name='media_asset';").strip()
            if exists=='1':
                rows=run('mysql',['--batch','--skip-column-names',database],'SELECT filename,byte_size FROM media_asset;')
                for row in rows.splitlines():
                    name,size=row.split('\t')
                    if not NAME.fullmatch(name) or not (media/name).is_file() or (media/name).stat().st_size!=int(size):
                        raise ValueError('数据库记录对应的媒体文件缺失或大小不符，拒绝生成不完整备份')
            before={p.name:digest(p) for p in files}
            backup.mkdir(parents=True,exist_ok=False)
            run('mysqldump',['--single-transaction','--hex-blob','--no-tablespaces','--skip-databases','--set-gtid-purged=OFF','--result-file='+str(backup/'database.sql'),database])
            with zipfile.ZipFile(backup/'media.zip','w',zipfile.ZIP_STORED) as archive:
                for name in sorted(before): archive.write(media/name,name)
            after={p.name:digest(p) for p in media.iterdir() if p.is_file()} if media.exists() else {}
            if before!=after: raise RuntimeError('媒体目录在备份中发生变化，此备份未完成，请停止写入后重试')
            # 当前工作树源码也纳入，避免只归档HEAD导致未提交业务修改丢失。
            files=subprocess.run(['git','ls-files','-co','--exclude-standard','-z'],cwd=ROOT,capture_output=True,check=True).stdout.decode('utf-8').split('\0')
            with zipfile.ZipFile(backup/'source.zip','w',zipfile.ZIP_DEFLATED) as archive:
                for name in sorted(set(files)-{''}):
                    file=ROOT/name
                    if file.is_file() and not file.absolute().is_relative_to(media) and not file.absolute().is_relative_to(backup):
                        no_links(file); archive.write(file,name)
            config_source=ROOT/'personalwebsite/src/main/resources/application.properties'
            if config_source.exists(): no_links(config_source); shutil.copyfile(config_source,backup/'application.properties')
            manifest={'format':1,'database':database,'media':before,'files':{p.name:digest(p) for p in backup.iterdir() if p.is_file()},'configuration_note':'若配置引用环境变量，另行离线保管其值；恢复不自动执行私有配置。'}
            (backup/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
            print('备份完成：'+str(backup)+'；媒体 '+str(len(before))+' 张。配置和SQL含私有信息，请勿提交。')
        else:
            if not args.backup or not args.media or not re.fullmatch(r'pw_media_restore_[A-Za-z0-9_]+',args.database or ''):
                raise ValueError('恢复必须显式指定--backup、--media及pw_media_restore_开头的--database')
            destination=args.media.absolute()
            no_links(destination)
            destination=destination.resolve(strict=False)
            if destination.exists(): raise ValueError('媒体恢复目标已存在，拒绝覆盖')
            no_links(backup/'manifest.json')
            manifest=json.loads((backup/'manifest.json').read_text(encoding='utf-8'))
            if manifest.get('format')!=1: raise ValueError('不支持的备份格式')
            if not {'database.sql','media.zip','source.zip'}.issubset(manifest['files']): raise ValueError('备份缺少必要文件清单')
            for name,expected in manifest['files'].items():
                no_links(backup/name)
                if Path(name).name!=name or digest(backup/name)!=expected: raise ValueError('备份文件校验失败')
            # 修改数据库之前完成所有媒体预检，避免损坏归档留下半成品恢复库。
            with zipfile.ZipFile(backup/'media.zip') as archive:
                if set(archive.namelist())!=set(manifest['media']) or len(archive.namelist())!=len(manifest['media']): raise ValueError('媒体清单不一致')
                for item in archive.infolist():
                    if not NAME.fullmatch(item.filename) or item.file_size>5*1024*1024: raise ValueError('媒体归档路径或大小不合法')
                    if hashlib.sha256(archive.read(item)).hexdigest()!=manifest['media'][item.filename]: raise ValueError('媒体SHA256不一致')
            # 只在全新专用库恢复；CREATE DATABASE失败则绝不继续导入。
            run('mysql',[],f'CREATE DATABASE `{args.database}` CHARACTER SET utf8mb4;')
            # 只有上面的CREATE成功后，才拥有这个新库；失败时不能清理已有库。
            destination_created=False
            written=[]
            try:
                run('mysql',[args.database],(backup/'database.sql').read_text(encoding='utf-8'))
                destination.mkdir(parents=True,exist_ok=False)
                destination_created=True
                with zipfile.ZipFile(backup/'media.zip') as archive:
                    if set(archive.namelist())!=set(manifest['media']): raise ValueError('媒体清单不一致')
                    for name in archive.namelist():
                        if not NAME.fullmatch(name): raise ValueError('媒体归档路径不合法')
                        file=destination/name
                        # 独占创建，回滚只删除本次确实创建的文件，不覆盖其他文件。
                        with file.open('xb') as output:
                            written.append(file)
                            output.write(archive.read(name))
                        if digest(file)!=manifest['media'][name]: raise ValueError('媒体SHA256不一致')
            except Exception as failure:
                rollback_errors=[]
                if destination_created:
                    try:
                        no_links(destination)
                        for file in written:
                            no_links(file); file.unlink(missing_ok=True)
                        destination.rmdir() # 只移除空目录；不递归删除意外出现的内容。
                    except OSError: rollback_errors.append('媒体目录')
                    except ValueError: rollback_errors.append('媒体路径')
                try: run('mysql',[],f'DROP DATABASE `{args.database}`;')
                except RuntimeError: rollback_errors.append('专用恢复库')
                if rollback_errors: raise RuntimeError('恢复失败，部分回滚未完成：'+ '、'.join(rollback_errors)+'；请保留现场检查，未触碰源库。') from failure
                raise RuntimeError('恢复失败，本次新建的专用库与媒体文件已回滚；未触碰源库。') from failure
            print('隔离恢复完成：'+args.database+'；媒体SHA256全部一致。私有配置未自动加载。')
if __name__=='__main__': main()
