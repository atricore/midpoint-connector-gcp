# Testing the Connector

## Functional Tests

To be conducted manually for now.

### 1. Initial import

**Step: 1 - import GCP roles**
- configure the connector
- gcp roles to be unmanaged
- run task GCP Roles - <project> - import (SIM)
- verify: compare with configured policy
- verify: archetype is assigned to GCP roles in MidPoint
- run task GCP Roles - <project> - import (PROD)

**Step: 2 - import GWS Groups**

- run task GWS Groups - <project> - import (SIM)
- verify: groups HAVE an association
- verify: groups are CORRELATED to MidPoint roles for GWS Groups
- run task GWS Groups - <project> - import (PROD)

**Step: 3 - import GWS Users**

- run task GWS Users - <project> - import (SIM)
- verify: users HAVE an association
- verify: users are CORRELATED to MidPoint roles for GWS Groups
- run task GWS Groups - <project> - import (PROD)

### 2. Update policy while unmanaged

Changes should modify MidPoint objects

**Step: 1 - modify policy in GCP**
- add role binding
- remove role binding

**Step: 2 - reconcile GWS Accounts**
- run task GWS Accounts - <project> - reconcile (SIM)
- verify changes on MidPoint objects
- run task GWS Accounts - <project> - reconcile (PROD)

**Step: 3 - reconcile GWS Accounts**
- run task GWS Groups - <project> - reconcile (SIM)
- verify changes on MidPoint objects
- run task GWS Groups - <project> - reconcile (PROD)

### 3. Update policy while managed

Changes should modify GCP Policy

**Step: 1 - modify policy in GCP**
- add role binding
- remove role binding

**Step: 2 - reconcile GWS Accounts**
- run task GWS Accounts - <project> - reconcile (SIM)
- verify changes on MidPoint objects
- run task GWS Accounts - <project> - reconcile (PROD)

**Step: 3 - reconcile GWS Accounts**
- run task GWS Groups - <project> - reconcile (SIM)
- verify changes on MidPoint objects
- run task GWS Groups - <project> - reconcile (PROD)

**Step: 4 - modify assignments in MidPoint**
- add role binding
- remove role binding

**Step: 5 - Reconcile**
- repeat Steps 2 and 3
