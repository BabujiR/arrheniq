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

export interface ProductDetails {
  product: Product;
  signatures: Signature[];
}
