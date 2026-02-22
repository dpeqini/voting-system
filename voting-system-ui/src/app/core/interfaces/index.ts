export enum AdminRole {
  ADMIN = 'ADMIN',
  ELECTION_OFFICIAL = 'ELECTION_OFFICIAL',
  OBSERVER = 'OBSERVER',
}

export enum ElectionType {
  PARLIAMENTARY = 'PARLIAMENTARY',
  LOCAL_GOVERNMENT = 'LOCAL_GOVERNMENT',
}

export enum ElectionStatus {
  CREATED = 'CREATED',
  CANDIDATES_IMPORTED = 'CANDIDATES_IMPORTED',
  STARTED = 'STARTED',
  CLOSED = 'CLOSED',
  RESULTS_PUBLISHED = 'RESULTS_PUBLISHED',
}

export const ELECTION_STATUS_LABELS: Record<ElectionStatus, string> = {
  [ElectionStatus.CREATED]: 'Created',
  [ElectionStatus.CANDIDATES_IMPORTED]: 'Candidates Imported',
  [ElectionStatus.STARTED]: 'Active',
  [ElectionStatus.CLOSED]: 'Closed',
  [ElectionStatus.RESULTS_PUBLISHED]: 'Results Published',
};

export const ELECTION_TYPE_LABELS: Record<ElectionType, string> = {
  [ElectionType.PARLIAMENTARY]: 'Parliamentary',
  [ElectionType.LOCAL_GOVERNMENT]: 'Local Government',
};

export interface AuthRequest {
  email: string;
  password: string;
}

export interface AdminAuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  adminId: string;
  email: string;
  fullName: string;
  role: AdminRole;
  loginTime: string;
  message: string;
  success: boolean;
}

export interface ElectionRequest {
  name: string;
  description?: string;
  electionType: ElectionType;
  electionDate: string;
  registrationDeadline: string;
  startDate?: string;
  endDate?: string;
  externalDataSource?: string;
}

export interface PartyResponse {
  id: string;
  partyCode: string;
  name: string;
  description?: string;
  logoUrl?: string;
  color?: string;
  leader?: string;
  listNumber: number;
  candidateCount: number;
}

export interface CandidateResponse {
  id: string;
  firstName: string;
  lastName: string;
  fullName: string;
  partyId?: string;
  partyName?: string;
  partyCode?: string;
  county?: string;
  countyName?: string;
  municipality?: string;
  municipalityName?: string;
  positionInList?: number;
  independent: boolean;
}

export interface ElectionResponse {
  id: string;
  name: string;
  description?: string;
  electionType: ElectionType;
  status: ElectionStatus;
  electionDate: string;
  startDate?: string;
  endDate?: string;
  registrationDeadline: string;
  totalEligibleVoters: number;
  totalVotesCast: number;
  turnoutPercentage: number;
  candidatesImported: boolean;
  candidateCount: number;
  partyCount: number;
  blockchainContractAddress?: string;
  createdAt: string;
  lastSyncedAt?: string;
  candidates?: CandidateResponse[];
  parties?: PartyResponse[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface VerificationResponse {
  verified: boolean;
  voteHash?: string;
  blockchainTransactionId?: string;
  blockNumber?: number;
  blockHash?: string;
  voteTimestamp?: string;
  verificationTimestamp: string;
  merkleProof?: string;
  blockchainConsistent: boolean;
  message?: string;
  electionId?: string;
  electionName?: string;
}
