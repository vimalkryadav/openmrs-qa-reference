"""Assert generated XLSX bytes and header-toggle cell semantics without Excel."""
import os
from pathlib import Path
import json
import zipfile
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]/os.environ.get('REPORTS_EVIDENCE','test-results/objects')
ns={'x':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
def cells(name):
    with zipfile.ZipFile(root/(name+'.xlsx')) as z:
        strings=[''.join(t.itertext()) for t in ET.fromstring(z.read('xl/sharedStrings.xml')).findall('x:si',ns)] if 'xl/sharedStrings.xml' in z.namelist() else []
        rows=[]
        for row in ET.fromstring(z.read('xl/worksheets/sheet1.xml')).findall('.//x:row',ns):
            values=[]
            for cell in row.findall('x:c',ns):
                v=cell.find('x:v',ns);text='' if v is None else v.text
                if cell.attrib.get('t')=='s':text=strings[int(text)]
                elif cell.attrib.get('t')=='inlineStr':text=''.join(cell.find('x:is',ns).itertext())
                elif text is not None and text!='':
                    number=float(text);text=int(number) if number.is_integer() else number
                values.append(text)
            rows.append(values)
        return rows
result={name:cells(name) for name in ['fallback-excel','header-enabled','header-disabled']}
(root/'metadata-cells.json').write_text(json.dumps(result,indent=2))
assert result['fallback-excel']==[['qa_value'],[7]],result
assert result['header-enabled'][0]==['QA Reports Fix R3 20261008-scalar'],result
assert result['header-disabled']==[['qa_value'],[7]],result
print('PASS XLSX fallback cells and enabled/disabled header rows')
