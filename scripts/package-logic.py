"""Package the Java-8-compiled upstream Logic 0.5.5 compatibility build."""
from pathlib import Path
import zipfile, shutil
root=Path(__file__).parent
source=root/'logic-source'
classes=root/'logic-classes'
files={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
for folder,prefix in [(source/'api/src/main/resources',''),(source/'omod/src/main/resources',''),(source/'omod/src/main/webapp','web/module/')]:
 for path in folder.rglob('*'):
  if not path.is_file():continue
  data=path.read_bytes()
  if path.name=='config.xml':data=data.replace(b'@MODULE_VERSION@',b'0.5.5').replace(b'@MODULE_ID@',b'logic').replace(b'@MODULE_NAME@',b'Logic Module')
  if path.name=='moduleApplicationContext.xml':data=data.replace(b'<!-- Register LogicService',b'<context:component-scan base-package="org.openmrs.logic"/>\n\t<!-- Register LogicService')
  files[prefix+path.relative_to(folder).as_posix()]=data
# Spring's classpath scanner needs directory entries for packages in this archive.
dirs={parent.as_posix()+'/' for name in files for parent in Path(name).parents if str(parent)!='.'}
with zipfile.ZipFile(root/'backend-inputs/logic-0.5.5.omod','w',zipfile.ZIP_DEFLATED) as archive:
 for name in sorted(dirs):archive.writestr(name,b'')
 for name,data in files.items():archive.writestr(name,data)
shutil.copy2(root/'backend-inputs/logic-0.5.5.omod',root/'backend/logic-0.5.5.omod')
