/** Mirrors the backend response records (kernel.security.MeController, masterdata.*, kernel.esign.Signature). */

export interface SiteRef {
  id: string;
  code: string;
  name: string;
  timeZone: string;
}

export interface Me {
  userId: string;
  username: string;
  displayName: string;
  tenant: string;
  permissions: string[];
  allSites: boolean;
  siteIds: string[];
  sites: SiteRef[];
  idleLockSeconds: number;
}

export type ResultsFormat = 'NUMERIC' | 'TEXT';

export interface TestMethodInput {
  code: string;
  name: string;
  methodReference?: string | null;
  labNumber?: string | null;
  resultsFormat: ResultsFormat;
  unitCode?: string | null;
  replicates?: number | null;
  decimals?: number | null;
  defaultLowerLimit?: number | null;
  defaultUpperLimit?: number | null;
  defaultTextLimit?: string | null;
  defaultAlertLower?: number | null;
  defaultAlertUpper?: number | null;
  defaultVarianceLowerPct?: number | null;
  defaultVarianceUpperPct?: number | null;
  trendable?: boolean | null;
}

export interface TestMethod extends Required<Omit<TestMethodInput, 'trendable' | 'replicates'>> {
  id: string;
  version: number;
  replicates: number;
  trendable: boolean;
  status: 'ACTIVE' | 'RETIRED';
  createdAt: string;
  updatedAt: string | null;
}

export type ProductType = 'DRUG_SUBSTANCE' | 'DRUG_PRODUCT' | 'BIOLOGIC_DS' | 'BIOLOGIC_DP' | 'EXCIPIENT' | 'OTHER';
export type ExpirationStatus = 'ESTABLISHED' | 'TO_BE_DETERMINED' | 'NOT_APPLICABLE';
export type ExpirationUnit = 'DAY' | 'WEEK' | 'MONTH';
export type ProductStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'RETIRED';

export interface ProductInput {
  siteId: string;
  code: string;
  name: string;
  description?: string | null;
  productType: ProductType;
  dosageForm?: string | null;
  strength?: string | null;
  expirationStatus: ExpirationStatus;
  expirationValue?: number | null;
  expirationUnit?: ExpirationUnit | null;
  controlledSubstance: boolean;
}

export interface Product {
  id: string;
  version: number;
  siteId: string;
  code: string;
  name: string;
  description: string | null;
  productType: ProductType;
  dosageForm: string | null;
  strength: string | null;
  expirationStatus: ExpirationStatus;
  expirationValue: number | null;
  expirationUnit: ExpirationUnit | null;
  controlledSubstance: boolean;
  status: ProductStatus;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface Signature {
  id: string;
  meaning: string;
  signerUserId: string;
  signerName: string;
  signedAt: string;
  recordVersion: number;
  status: 'VALID' | 'SUPERSEDED' | 'REVOKED';
  comment: string | null;
}

/** Where an approval stands (backend ApprovalService.Progress; tenant setting approval.<kind>). */
export interface ApprovalProgress {
  level: 'NONE' | 'SINGLE' | 'DOUBLE';
  required: number;
  collected: number;
}

export interface ProductDetails {
  product: Product;
  signatures: Signature[];
  approval: ApprovalProgress;
}

/** A controlled list (backend masterdata.Dictionary; URS-MD-006). */
export interface Dictionary {
  id: string;
  code: string;
  name: string;
  category: 'STABILITY' | 'PACKAGING' | 'RESULTS' | 'ORGANISATION' | 'SYSTEM';
  systemManaged: boolean;
  activeValues: number;
  retiredValues: number;
}

export type AttributeValue = string | number | boolean;

export interface DictionaryValue {
  id: string;
  version: number;
  dictionaryCode: string;
  code: string;
  label: string;
  description: string | null;
  sortOrder: number;
  siteId: string | null;
  attributes: Record<string, AttributeValue>;
  status: 'ACTIVE' | 'RETIRED';
  /** shipped with ArrheniQ (e.g. ICH conditions): retire only */
  fromCatalog: boolean;
  /** referenced by a record: retire only */
  inUse: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface DictionaryValueInput {
  code?: string;
  label?: string;
  description?: string | null;
  sortOrder?: number;
  siteId?: string | null;
  attributes?: Record<string, AttributeValue>;
}

/** ICH time-point template (backend masterdata.ProtocolTemplate; URS-MD-007). */
export interface ProtocolTemplate {
  code: string;
  name: string;
  studyType: string;
  conditionCode: string;
  conditionLabel: string;
  timepointUnit: 'DAY' | 'WEEK' | 'MONTH';
  timepoints: number[];
  source: string;
  note: string;
}

/** A specification in lists (backend masterdata.SpecificationSummary; URS-MD-003/004). */
export interface SpecificationSummary {
  id: string;
  specNumber: string;
  title: string;
  productId: string;
  productCode: string;
  productName: string;
  siteId: string;
  effectiveVersionNo: number | null;
  effectiveFrom: string | null;
  openVersionId: string | null;
  openVersionNo: number | null;
  openStatus: 'DRAFT' | 'IN_REVIEW' | null;
}

export type SpecVersionStatus = 'DRAFT' | 'IN_REVIEW' | 'EFFECTIVE' | 'SUPERSEDED' | 'RETIRED';

export interface SpecVersionSummary {
  id: string;
  versionNo: number;
  status: SpecVersionStatus;
  effectiveFrom: string | null;
  changeSummary: string | null;
  testCount: number;
  createdAt: string;
  updatedAt: string | null;
}

export interface SpecificationDetails {
  specification: SpecificationSummary;
  versions: SpecVersionSummary[];
}

export interface SpecTest {
  testMethodId: string;
  testCode: string;
  testStatus: 'ACTIVE' | 'RETIRED';
  sortOrder: number;
  displayName: string;
  resultsFormat: ResultsFormat;
  unitCode: string | null;
  replicates: number;
  decimals: number | null;
  lowerLimit: number | null;
  upperLimit: number | null;
  textLimit: string | null;
  alertLower: number | null;
  alertUpper: number | null;
  trendable: boolean;
}

export interface SpecTestInput {
  testMethodId: string;
  displayName?: string | null;
  unitCode?: string | null;
  replicates?: number | null;
  decimals?: number | null;
  lowerLimit?: number | null;
  upperLimit?: number | null;
  textLimit?: string | null;
  alertLower?: number | null;
  alertUpper?: number | null;
  trendable?: boolean | null;
}

export interface SpecVersionDetails {
  id: string;
  version: number;
  specification: SpecificationSummary;
  versionNo: number;
  status: SpecVersionStatus;
  effectiveFrom: string | null;
  changeSummary: string | null;
  tests: SpecTest[];
  previousVersionId: string | null;
  signatures: Signature[];
  approval: ApprovalProgress;
  createdAt: string;
  updatedAt: string | null;
}

/** Protocols (backend masterdata.Protocol*; URS-PRO-001/002/003/005/007). */
export type StudyType =
  | 'LONG_TERM'
  | 'INTERMEDIATE'
  | 'ACCELERATED'
  | 'STRESS'
  | 'PHOTOSTABILITY'
  | 'IN_USE'
  | 'EXCURSION'
  | 'COMMITMENT'
  | 'ANNUAL'
  | 'OTHER';
export type TimeUnit = 'DAY' | 'WEEK' | 'MONTH';
export type ProtocolVersionStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'SUPERSEDED' | 'RETIRED';

export interface ProtocolSummary {
  id: string;
  protocolNumber: string;
  name: string;
  productId: string;
  productCode: string;
  productName: string;
  siteId: string;
  approvedVersionNo: number | null;
  approvedFrom: string | null;
  studyType: StudyType | null;
  conditionCode: string | null;
  openVersionId: string | null;
  openVersionNo: number | null;
  openStatus: 'DRAFT' | 'IN_REVIEW' | null;
}

export interface ProtocolVersionSummary {
  id: string;
  versionNo: number;
  status: ProtocolVersionStatus;
  studyType: StudyType;
  conditionCode: string | null;
  effectiveFrom: string | null;
  changeSummary: string | null;
  timepointCount: number;
  testCount: number;
  createdAt: string;
  updatedAt: string | null;
}

export interface ProtocolDetails {
  protocol: ProtocolSummary;
  versions: ProtocolVersionSummary[];
}

export interface TimePoint {
  value: number;
  unit: TimeUnit;
  label: string;
  unitsPlanned: number;
}

export interface ProtocolTest {
  testMethodId: string;
  testCode: string;
  testName: string;
  testStatus: 'ACTIVE' | 'RETIRED';
  resultsFormat: ResultsFormat;
  /** indexes into timepoints */
  at: number[];
}

export interface ProtocolContent {
  studyType: StudyType;
  conditionCode: string | null;
  reserveUnits: number;
  fillUnitCode: string | null;
  changeSummary: string | null;
  timepoints: TimePoint[];
  tests: ProtocolTest[];
}

export interface ProtocolVersionDetails {
  id: string;
  version: number;
  protocol: ProtocolSummary;
  versionNo: number;
  status: ProtocolVersionStatus;
  effectiveFrom: string | null;
  content: ProtocolContent;
  conditionLabel: string | null;
  totalUnits: number;
  warnings: string[];
  previousVersionId: string | null;
  signatures: Signature[];
  approval: ApprovalProgress;
  createdAt: string;
  updatedAt: string | null;
}
