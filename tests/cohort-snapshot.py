"""Capture the selected existing cohort metadata and membership without mutation."""
from setup import read_reference_sql
print(read_reference_sql("SELECT cohort_id,uuid,name,description,voided FROM cohort WHERE cohort_id=7; SELECT patient_id FROM cohort_member WHERE cohort_id=7 ORDER BY patient_id"), end='')
