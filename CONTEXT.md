# Ngọc Khánh Clinic Health Examination

The `healthcheck` bounded context models adult and corporate health-examination campaigns, their validated roster, and visit-specific administrative records. It does not own clinical results, encounters, or retail billing. `healthcheck` remains the accepted technical module identifier; `Health Examination` is the canonical business term.

## Corporate health examination

**Company**:
A corporate customer that owns its roster and may run multiple health-examination batches. It has exactly one primary contact in the MVP.
_Avoid_: client, account

**Company Employee**:
A company roster member identified within one company by employee code and identification number (CCCD). It is not a Patient until an authorized preparation workflow links it by exact CCCD.
_Avoid_: patient, staff member

**Health Examination Batch**:
One company health-examination campaign with a planned window, examination site, selected service scope, negotiated prices, and a frozen master-form version.
_Avoid_: appointment group, order batch

**Batch Employee**:
One Company Employee's participation in one Health Examination Batch, including the confirmed administrative roster snapshot for that campaign.
_Avoid_: patient visit

**Health Examination Record**:
The visit-specific adult health-examination record that owns the immutable SHS and administrative print snapshot for one Encounter.
_Avoid_: form, document

**SHS**:
The immutable health-examination record code shared by Mẫu số 03 and all specialist forms of one health-examination Encounter.
_Avoid_: document code, barcode ID

**Employee Service Assignment**:
A doctor-confirmed selection of one in-scope Batch Service for one Batch Employee, linked to a Service Request and its corporate price snapshot.
_Avoid_: selected service flag, examination result

**Roster Import**:
The upload, validation, and confirmation of employee-list data into Company Employees and Batch Employees. It never creates Patients or Encounters.
_Avoid_: patient import
