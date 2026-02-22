import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CandidateResponse,
  ElectionRequest,
  ElectionResponse,
  ElectionStatus,
  PageResponse,
  PartyResponse, VerificationResponse
} from '../interfaces';


const API = 'http://localhost:8081/api/v1';

@Injectable({ providedIn: 'root' })
export class ElectionService {
  private http = inject(HttpClient);

  getActiveElections(): Observable<ElectionResponse[]> {
    return this.http.get<ElectionResponse[]>(`${API}/elections/active`);
  }

  getAllElections(page = 0, size = 20): Observable<PageResponse<ElectionResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<ElectionResponse>>(`${API}/elections`, { params });
  }

  getElectionsByStatus(status: ElectionStatus): Observable<ElectionResponse[]> {
    return this.http.get<ElectionResponse[]>(`${API}/elections/status/${status}`);
  }

  getElection(id: string): Observable<ElectionResponse> {
    return this.http.get<ElectionResponse>(`${API}/elections/${id}`);
  }

  createElection(request: ElectionRequest): Observable<ElectionResponse> {
    return this.http.post<ElectionResponse>(`${API}/elections`, request);
  }

  importCandidates(electionId: string, dataSourceUrl?: string): Observable<ElectionResponse> {
    let params = new HttpParams();
    if (dataSourceUrl) params = params.set('dataSourceUrl', dataSourceUrl);
    return this.http.post<ElectionResponse>(
      `${API}/elections/${electionId}/import-candidates`,
      {},
      { params },
    );
  }

  startElection(electionId: string): Observable<ElectionResponse> {
    return this.http.post<ElectionResponse>(`${API}/elections/${electionId}/start`, {});
  }

  closeElection(electionId: string): Observable<ElectionResponse> {
    return this.http.post<ElectionResponse>(`${API}/elections/${electionId}/close`, {});
  }

  getCandidates(electionId: string): Observable<CandidateResponse[]> {
    return this.http.get<CandidateResponse[]>(`${API}/elections/${electionId}/candidates`);
  }

  getParties(electionId: string): Observable<PartyResponse[]> {
    return this.http.get<PartyResponse[]>(`${API}/elections/${electionId}/parties`);
  }

  verifyVote(voteHash: string): Observable<VerificationResponse> {
    return this.http.get<VerificationResponse>(`${API}/verification/vote/${voteHash}`);
  }

  verifyByReceipt(receiptToken: string): Observable<VerificationResponse> {
    return this.http.get<VerificationResponse>(`${API}/verification/vote/receipt/${receiptToken}`);
  }

  validateBlockchain(electionId: string): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(
      `${API}/verification/blockchain/${electionId}/validate`,
    );
  }

  getBlockchainStats(electionId: string): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(
      `${API}/verification/blockchain/${electionId}/stats`,
    );
  }
}
