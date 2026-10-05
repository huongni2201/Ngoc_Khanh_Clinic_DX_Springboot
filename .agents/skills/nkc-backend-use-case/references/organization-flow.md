# Current organization-flow references

Use these existing source files for the PUT organization flow. Load only affected
roles through CodeGraph before reading/searching indexed code. These are live
references, not copyable templates or a claim of complete policy compliance.

| Role | Current source |
|---|---|
| HTTP mapping | [OrganizationController.java](../../../../src/main/java/com/ngockhanh/clinic/healthexamination/api/controller/OrganizationController.java) |
| HTTP validation | [UpdateOrganizationRequest.java](../../../../src/main/java/com/ngockhanh/clinic/healthexamination/api/request/UpdateOrganizationRequest.java) |
| Application input | [UpdateOrganizationCommand.java](../../../../src/main/java/com/ngockhanh/clinic/healthexamination/application/command/UpdateOrganizationCommand.java) |
| Expected-version update and audit orchestration | [UpdateOrganizationUseCase.java](../../../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/UpdateOrganizationUseCase.java) |
| Application response | [OrganizationResponse.java](../../../../src/main/java/com/ngockhanh/clinic/healthexamination/application/response/OrganizationResponse.java) |
| Aggregate factories/invariants | [Organization.java](../../../../src/main/java/com/ngockhanh/clinic/healthexamination/domain/aggregate/Organization.java) |
| Repository port | [OrganizationRepository.java](../../../../src/main/java/com/ngockhanh/clinic/healthexamination/domain/repository/OrganizationRepository.java) |
| Table record | [OrganizationRecord.java](../../../../src/main/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/record/OrganizationRecord.java) |
| Pure converter | [OrganizationPersistenceConverter.java](../../../../src/main/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/converter/OrganizationPersistenceConverter.java) |
| SQL adapter | [MyBatisOrganizationRepository.java](../../../../src/main/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/repository/MyBatisOrganizationRepository.java) |
| Mapper | [OrganizationMyBatisMapper.java](../../../../src/main/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/mapper/OrganizationMyBatisMapper.java) |
| Bound SQL/version predicate | [OrganizationMyBatisMapper.xml](../../../../src/main/resources/mapper/healthexamination/OrganizationMyBatisMapper.xml) |

## Test evidence and limits

- [OrganizationCrudUseCaseTest](../../../../src/test/java/com/ngockhanh/clinic/healthexamination/application/usecase/OrganizationCrudUseCaseTest.java):
  mocked orchestration and failure cases; no proof of SQL commit/rollback.
- [OrganizationControllerTest](../../../../src/test/java/com/ngockhanh/clinic/healthexamination/api/controller/OrganizationControllerTest.java):
  HTTP validation/mapping examples; standalone controller tests do not prove
  full security-filter behavior.
- [MyBatisOrganizationRepositoryIntegrationTest](../../../../src/test/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/MyBatisOrganizationRepositoryIntegrationTest.java):
  inspect its actual SQL/version/rollback assertions before claiming a guarantee.
  Its [BatchTestConfiguration](../../../../src/test/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/HealthExaminationBatchCrudIntegrationTest.java)
  supplies PostgreSQL/Flyway/MyBatis/audit wiring; Docker is required.
- [ProductionAuthSecurityTest](../../../../src/test/java/com/ngockhanh/clinic/identity/ProductionAuthSecurityTest.java)
  and [LocalAuthSecurityTest](../../../../src/test/java/com/ngockhanh/clinic/identity/LocalAuthSecurityTest.java):
  current baseline access-policy evidence. Production denial uses organization GET;
  the local test uses a synthetic business GET. Neither proves PUT-specific access
  or CSRF behavior. Add affected-endpoint security coverage when changing that flow.

A skipped integration test proves no SQL/rollback guarantee. Source examples must
be checked against [API/error policy](../../../../PROJECT_RULES.md#envelope-errors-and-pagination),
[types](../../../../PROJECT_RULES.md#java-types-and-collections),
[logging](../../../../PROJECT_RULES.md#27-logging) and
[projection ownership](../../../../PROJECT_RULES.md#9-domain-vs-persistence-model).
Actor IDs are audit identity, not authorization. Do not copy profile actor fallbacks.
These update references supply no undocumented create/idempotency/release contract.
