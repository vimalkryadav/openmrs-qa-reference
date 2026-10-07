"""Package verified reporting API/controllers and core editor overrides over captured inputs."""
from pathlib import Path
import io, zipfile, shutil
ROOT=Path(__file__).resolve().parent

def patched_zip(data, replacements):
    remaining=dict(replacements)
    result=io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as source, zipfile.ZipFile(result,"w",zipfile.ZIP_DEFLATED) as target:
        for item in source.infolist():
            target.writestr(item,remaining.pop(item.filename,source.read(item.filename)))
        for name,content in remaining.items(): target.writestr(name,content)
    return result.getvalue()

def classes(folder):
    root=ROOT/folder
    return {p.relative_to(root).as_posix():p.read_bytes() for p in root.rglob("*.class")}

original=(ROOT/"backend-inputs/reporting-2.1.0.omod").read_bytes()
with zipfile.ZipFile(io.BytesIO(original)) as archive:
    api=archive.read("lib/reporting-api-2.1.0.jar")
all_controller_classes=classes("controller-classes")
rest_replacements={name:data for name,data in all_controller_classes.items() if name.startswith("org/openmrs/module/reportingrest/")}
replacements={name:data for name,data in all_controller_classes.items() if name not in rest_replacements}
rest_original=(ROOT/"backend-inputs/reportingrest-2.0.0.omod").read_bytes()
(ROOT/"backend/reportingrest-2.0.0.omod").write_bytes(patched_zip(rest_original,rest_replacements))
for folder in ("reporting-web", "web-overrides"):
    for page in (ROOT/folder).rglob("*.jsp"):
        replacements["web/module/"+page.relative_to(ROOT/folder).as_posix()]=page.read_bytes()
api_replacements=classes("backend-classes")
for resource in (ROOT/"reporting-resources").rglob("*"):
    if resource.is_file(): api_replacements[resource.relative_to(ROOT/"reporting-resources").as_posix()]=resource.read_bytes()
replacements["lib/reporting-api-2.1.0.jar"]=patched_zip(api,api_replacements)
(ROOT/"backend/reporting-2.1.0.omod").write_bytes(patched_zip(original,replacements))
shutil.copytree(ROOT/"core-classes",ROOT/"backend/core/WEB-INF/classes",dirs_exist_ok=True)
print("Prepared patched reporting module and core property editor")

original=(ROOT/"backend-inputs/legacyui-2.1.0.omod").read_bytes()
(ROOT/"backend/legacyui-2.1.0.omod").write_bytes(patched_zip(original,classes("legacyui-classes")))
original=(ROOT/"backend-inputs/patientdocuments-1.1.0.omod").read_bytes()
with zipfile.ZipFile(io.BytesIO(original)) as archive:
    name=next(n for n in archive.namelist() if n.endswith("patientdocuments-api-1.1.0.jar"))
    api=archive.read(name)
(ROOT/"backend/patientdocuments-1.1.0.omod").write_bytes(patched_zip(original,{name:patched_zip(api,classes("patientdocuments-classes"))}))

original=(ROOT/"backend-inputs/openconceptlab-3.1.0.omod").read_bytes()
with zipfile.ZipFile(io.BytesIO(original)) as archive:
    name=next(n for n in archive.namelist() if n.endswith("openconceptlab-api-3.1.0.jar"))
    api=archive.read(name)
replacements=classes("ocl-web-classes")
replacements[name]=patched_zip(api,classes("ocl-classes"))
(ROOT/"backend/openconceptlab-3.1.0.omod").write_bytes(patched_zip(original,replacements))

original=(ROOT/"backend-inputs/owa-1.15.0.omod").read_bytes()
replacements=classes("owa-classes")
for page in (ROOT/"owa-web").rglob("*.jsp"):
    replacements["web/module/"+page.relative_to(ROOT/"owa-web").as_posix()]=page.read_bytes()
(ROOT/"backend/owa-1.15.0.omod").write_bytes(patched_zip(original,replacements))
