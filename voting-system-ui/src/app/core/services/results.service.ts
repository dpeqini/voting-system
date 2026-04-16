// src/app/core/services/results.service.ts

import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, interval, of } from 'rxjs';
import { switchMap, startWith, catchError, shareReplay } from 'rxjs/operators';

// ── Shape of the data coming from the backend ─────────────────────────────────

export interface CandidateResult {
  candidateId:       number;
  candidateName:     string;
  partyName:         string;
  partyAbbreviation: string;
  region:            string;
  voteCount:         number;
  percentage:        number;
}

export interface PartyResult {
  partyId:           number;
  partyName:         string;
  partyAbbreviation: string;
  totalVotes:        number;
  percentage:        number;
}

export interface ElectionResults {
  electionId:     number;
  electionName:   string;
  electionType:   'PARLIAMENTARY' | 'LOCAL_GOVERNMENT';
  electionStatus: 'STARTED' | 'CLOSED' | 'CREATED' | 'FINISHED';
  totalVotes:     number;
  computedAt:     string;        // ISO-8601 datetime string from Jackson
  regionFilter:   string | null;
  byCandidate:    CandidateResult[];
  byParty:        PartyResult[];
}

// ── Service ───────────────────────────────────────────────────────────────────
const API = 'http://localhost:8081/api/v1';

@Injectable({ providedIn: 'root' })
export class ResultsService {

  // How often Angular re-fetches the results while the page is open.
  // 15 seconds is a good balance: fresh enough to feel "live" during an
  // election, but not so frequent that it hammers the database.
  private readonly POLL_INTERVAL_MS = 15_000;

  private readonly apiBase = `${API}/results`;

  constructor(private http: HttpClient) {}

  /**
   * Returns an Observable that immediately emits the current results and then
   * re-fetches and emits again every POLL_INTERVAL_MS milliseconds.
   *
   * The Angular async pipe in the template manages the subscription lifecycle
   * automatically, so the polling stops the moment the component is destroyed
   * — no manual unsubscription needed when used with async pipe.
   *
   * If a fetch fails (network error, server down) the Observable emits null
   * instead of throwing, so the template can show an "unable to load" state
   * without the entire Observable dying and stopping future retries.
   *
   * @param electionId  The election to fetch results for.
   * @param region      Optional county (for PARLIAMENTARY) or municipality
   *                    (for LOCAL_GOVERNMENT) to filter results.  Omit or
   *                    pass null/empty for national totals.
   */
  pollResults(
    electionId: string,
    region?: string | null
  ): Observable<ElectionResults | null> {

    return interval(this.POLL_INTERVAL_MS).pipe(
      startWith(0),                    // emit immediately on subscribe, then every interval
      switchMap(() => this.fetchOnce(electionId, region))
    );
  }

  /**
   * One-shot fetch — useful when you need to refresh on a user action
   * (e.g. they change the region filter) rather than waiting for the
   * next scheduled tick.
   */
  fetchOnce(
    electionId: string,
    region?: string | null
  ): Observable<ElectionResults | null> {

    let params = new HttpParams();
    if (region && region.trim().length > 0) {
      params = params.set('region', region.trim());
    }

    return this.http
      .get<ElectionResults>(`${this.apiBase}/election/${electionId}`, { params })
      .pipe(
        catchError(err => {
          // Log the error but return null so the poll Observable doesn't die.
          console.error('[ResultsService] Failed to fetch results:', err);
          return of(null);
        })
      );
  }
}
