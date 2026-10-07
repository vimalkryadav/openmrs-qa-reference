"""Capture owned renderer configuration alongside generated workbook evidence."""
import json
import re
import sys
from pathlib import Path
import setup
root=Path(__file__).resolve().parents[1]/'test-results/objects'
phase=sys.argv[1];assert phase in ('enabled','disabled')
uuid=json.loads((root/'ref-ids.json').read_text())['headerExcel'];assert re.fullmatch(r'[a-f0-9-]{36}',uuid)
raw=setup.read_reference_sql("SELECT uuid,name,renderer_type,properties FROM reporting_report_design WHERE uuid='"+uuid+"'")
(root/('header-'+phase+'-configuration.tsv')).write_text(raw)
print(raw)
