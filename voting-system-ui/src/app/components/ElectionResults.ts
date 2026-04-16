// src/app/features/results/election-results.component.ts

import {
  Component,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
  signal,
  computed
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  CandidateResult,
  ElectionResults,
  PartyResult,
  ResultsService
} from '../core/services/results.service';

type Tab = 'candidate' | 'party'

@Component({
  selector: 'app-election-results',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- ═══════════════════════════════════════════════════════════════════════ -->
    <!-- RESULTS PAGE                                                            -->
    <!-- ═══════════════════════════════════════════════════════════════════════ -->
    <div class="page">

      <!-- ── Back link ──────────────────────────────────────────────────────── -->
      <a class="back-link" routerLink="/elections">
        ← Kthehu te zgjedhjet
      </a>

      <!-- ── Loading skeleton ───────────────────────────────────────────────── -->
      <div class="skeleton-wrapper" *ngIf="!results() && !fetchError()">
        <div class="skeleton-title"></div>
        <div class="skeleton-bar"></div>
        <div class="skeleton-bar short"></div>
        <div class="skeleton-bar"></div>
      </div>

      <!-- ── Error banner ───────────────────────────────────────────────────── -->
      <div class="error-banner" *ngIf="fetchError()">
        <span>⚠ Nuk mund të ngarkohen rezultatet. Kontrolloni lidhjen.</span>
        <button class="retry-btn" (click)="retryNow()">Provo sërish</button>
      </div>

      <ng-container *ngIf="results() as r">

        <!-- ── Header ─────────────────────────────────────────────────────── -->
        <div class="header">
          <div class="header-row">
            <h1 class="title">{{ r.electionName }}</h1>
            <span class="status-chip" [ngClass]="statusClass(r.electionStatus)">
          {{ statusLabel(r.electionStatus) }}
        </span>
          </div>
          <div class="meta-row">
            <!-- Pulsing dot — green while polling is active -->
            <span class="pulse-dot" [class.active]="!fetchError()"></span>
            <span class="meta-text">Përditësohet automatikisht çdo 15 s</span>
            <span class="meta-sep">·</span>
            <span class="meta-text">
          Rifreskuar: <strong>{{ formatTime(r.computedAt) }}</strong>
        </span>
            <span class="meta-sep">·</span>
            <span class="meta-text">
          <strong>{{ r.totalVotes | number }}</strong> vota gjithsej
        </span>
          </div>
        </div>

        <!-- ── Region filter ──────────────────────────────────────────────── -->
        <div class="filter-row">
          <label class="filter-label">
            {{ r.electionType === 'PARLIAMENTARY' ? 'Filtro sipas Qarkut' : 'Filtro sipas Bashkisë' }}
          </label>

          <!-- ✅ FIX: mos përdor [(ngModel)] dhe (ngModelChange) bashkë -->
          <select class="filter-select"
                  [ngModel]="selectedRegion"
                  (ngModelChange)="selectedRegion = $event; onRegionChange($event)">
            <option value="">— Të gjitha —</option>
            <option *ngFor="let reg of uniqueRegions()" [value]="reg">{{ reg }}</option>
          </select>
        </div>

        <!-- ── Tab switcher ────────────────────────────────────────────────── -->
        <div class="tab-bar">
          <button class="tab-btn" [class.active]="activeTab === 'candidate'"
                  (click)="activeTab = 'candidate'">
            👤 Sipas Kandidatit
          </button>
          <button class="tab-btn" [class.active]="activeTab === 'party'"
                  (click)="activeTab = 'party'">
            🏛 Sipas Partisë
          </button>
        </div>

        <!-- ── CANDIDATE tab ───────────────────────────────────────────────── -->
        <div class="results-list" *ngIf="activeTab === 'candidate'">
          <div class="empty-state" *ngIf="r.byCandidate.length === 0">
            Nuk ka ende vota të regjistruara.
          </div>

          <div class="result-card"
               *ngFor="let c of r.byCandidate; let i = index; trackBy: trackById">

            <!-- Rank -->
            <div class="rank" [ngClass]="rankClass(i)">{{ i + 1 }}</div>

            <!-- Identity -->
            <div class="identity">
              <div class="name">{{ c.candidateName }}</div>
              <div class="sub">
                <span class="chip">{{ c.partyAbbreviation }}</span>
                <span class="party-name">{{ c.partyName }}</span>
                <span class="region-tag" *ngIf="c.region"> · {{ c.region }}</span>
              </div>
            </div>

            <!-- Bar + numbers -->
            <div class="bar-col">
              <div class="bar-track">
                <div class="bar-fill"
                     [ngClass]="'fill-rank-' + Math.min(i + 1, 5)"
                     [style.width.%]="c.percentage">
                </div>
              </div>
              <div class="bar-labels">
                <span class="votes">{{ c.voteCount | number }}</span>
                <span class="pct">{{ c.percentage | number:'1.1-1' }}%</span>
              </div>
            </div>

          </div>
        </div>

        <!-- ── PARTY tab ───────────────────────────────────────────────────── -->
        <div class="results-list" *ngIf="activeTab === 'party'">
          <div class="empty-state" *ngIf="r.byParty.length === 0">
            Nuk ka ende vota të regjistruara.
          </div>

          <div class="result-card"
               *ngFor="let p of r.byParty; let i = index; trackBy: trackById">

            <div class="rank" [ngClass]="rankClass(i)">{{ i + 1 }}</div>

            <div class="identity">
              <div class="name">{{ p.partyName }}</div>
              <div class="sub">
                <span class="chip">{{ p.partyAbbreviation }}</span>
              </div>
            </div>

            <div class="bar-col">
              <div class="bar-track">
                <div class="bar-fill party-fill"
                     [ngClass]="'fill-party-' + Math.min(i + 1, 5)"
                     [style.width.%]="p.percentage">
                </div>
              </div>
              <div class="bar-labels">
                <span class="votes">{{ p.totalVotes | number }}</span>
                <span class="pct">{{ p.percentage | number:'1.1-1' }}%</span>
              </div>
            </div>

          </div>

          <!-- Stacked summary bar (party view only) -->
          <div class="stacked-bar-wrapper" *ngIf="r.byParty.length > 0">
            <div class="stacked-label">Shpërndarje totale</div>
            <div class="stacked-track">
              <div *ngFor="let p of r.byParty; let i = index"
                   class="stacked-seg"
                   [ngClass]="'seg-' + Math.min(i + 1, 8)"
                   [style.flex]="p.percentage"
                   [title]="p.partyAbbreviation + ' ' + p.percentage + '%'">
              </div>
            </div>
            <div class="stacked-legend">
              <div class="legend-entry" *ngFor="let p of r.byParty; let i = index">
                <span class="legend-dot" [ngClass]="'seg-' + Math.min(i + 1, 8)"></span>
                {{ p.partyAbbreviation }}
                <span class="legend-pct">{{ p.percentage | number:'1.0-0' }}%</span>
              </div>
            </div>
          </div>

        </div><!-- /party tab -->

      </ng-container>
    </div><!-- /page -->
  `,
  styles: [`
    /* ── Design tokens ────────────────────────────────────────────────── */
    :host {
      /* ✅ FIX: deklaro --blue-500 sepse e përdor në CSS */
      --blue-500: #1d4ed8;
      --blue: var(--blue-500);

      --green:   #16a34a;
      --red:     #dc2626;
      --amber:   #d97706;
      --gray-50: #f8fafc;
      --gray-100:#f1f5f9;
      --gray-200:#e2e8f0;
      --gray-400:#94a3b8;
      --gray-600:#475569;
      --gray-900:#0f172a;

      display: block;
      font-family: 'Inter', system-ui, sans-serif;
    }

    /* ── Page wrapper ─────────────────────────────────────────────────── */
    .page {
      max-width: 820px;
      margin: 0 auto;
      padding: 24px 16px 64px;
    }

    .back-link {
      display: inline-block;
      margin-bottom: 20px;
      color: var(--blue-500);
      font-size: 0.875rem;
      text-decoration: none;
    }
    .back-link:hover { text-decoration: underline; }

    /* ── Skeleton ─────────────────────────────────────────────────────── */
    .skeleton-wrapper { padding-top: 8px; }
    .skeleton-title, .skeleton-bar {
      background: linear-gradient(90deg, var(--gray-100) 25%, var(--gray-200) 50%, var(--gray-100) 75%);
      background-size: 200% 100%;
      animation: shimmer 1.4s infinite;
      border-radius: 6px;
      margin-bottom: 12px;
    }
    .skeleton-title  { height: 28px; width: 55%; }
    .skeleton-bar    { height: 52px; }
    .skeleton-bar.short { width: 75%; }
    @keyframes shimmer { to { background-position: -200% 0; } }

    /* ── Error banner ─────────────────────────────────────────────────── */
    .error-banner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 12px 16px;
      background: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 8px;
      color: var(--red);
      font-size: 0.875rem;
      margin-bottom: 24px;
    }
    .retry-btn {
      padding: 5px 14px;
      background: var(--red);
      color: #fff;
      border: none;
      border-radius: 6px;
      font-size: 0.8rem;
      cursor: pointer;
      white-space: nowrap;
    }

    /* ── Header ───────────────────────────────────────────────────────── */
    .header { margin-bottom: 20px; }
    .header-row {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
      margin-bottom: 8px;
    }
    .title {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 700;
      color: var(--gray-900);
    }

    /* Status chip */
    .status-chip {
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: .06em;
      text-transform: uppercase;
      padding: 3px 10px;
      border-radius: 20px;
    }
    .chip-started  { background: #dcfce7; color: var(--green); }
    .chip-closed   { background: #fee2e2; color: var(--red); }
    .chip-default  { background: var(--gray-100); color: var(--gray-600); }

    /* Meta row */
    .meta-row {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.8rem;
      color: var(--gray-600);
      flex-wrap: wrap;
    }
    .pulse-dot {
      width: 8px; height: 8px;
      border-radius: 50%;
      background: var(--gray-400);
      flex-shrink: 0;
      transition: background .3s;
    }
    .pulse-dot.active {
      background: var(--green);
      animation: pulse 2.5s ease-in-out infinite;
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50%       { opacity: .3; }
    }
    .meta-sep { color: var(--gray-200); }

    /* ── Filter row ───────────────────────────────────────────────────── */
    .filter-row {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 20px;
      padding: 10px 14px;
      background: var(--gray-50);
      border: 1px solid var(--gray-200);
      border-radius: 8px;
    }
    .filter-label {
      font-size: 0.83rem;
      font-weight: 500;
      color: var(--gray-600);
      white-space: nowrap;
    }
    .filter-select {
      flex: 1;
      max-width: 260px;
      padding: 5px 8px;
      border: 1px solid var(--gray-200);
      border-radius: 6px;
      font-size: 0.85rem;
      background: #fff;
      color: var(--gray-900);
      cursor: pointer;
    }
    .filter-select:focus { outline: 2px solid var(--blue-500); }

    /* ── Tab bar ──────────────────────────────────────────────────────── */
    .tab-bar {
      display: flex;
      gap: 4px;
      background: var(--gray-100);
      border-radius: 10px;
      padding: 4px;
      margin-bottom: 20px;
    }
    .tab-btn {
      flex: 1;
      padding: 8px 12px;
      border: none;
      border-radius: 7px;
      background: transparent;
      color: var(--gray-600);
      font-size: 0.88rem;
      font-weight: 500;
      cursor: pointer;
      transition: background .15s, color .15s;
    }
    .tab-btn.active {
      background: #fff;
      color: var(--blue-500);
      box-shadow: 0 1px 4px rgba(0,0,0,.1);
    }

    /* ── Result cards list ────────────────────────────────────────────── */
    .results-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .empty-state {
      text-align: center;
      padding: 48px;
      background: var(--gray-50);
      border-radius: 10px;
      color: var(--gray-400);
      font-size: 0.9rem;
    }

    /* ── Single result card ───────────────────────────────────────────── */
    .result-card {
      display: grid;
      grid-template-columns: 36px 200px 1fr;
      align-items: center;
      gap: 14px;
      padding: 12px 16px;
      background: #fff;
      border: 1px solid var(--gray-200);
      border-radius: 10px;
      transition: box-shadow .15s;
    }
    .result-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.07); }

    @media (max-width: 580px) {
      .result-card {
        grid-template-columns: 32px 1fr;
        grid-template-rows: auto auto;
      }
      .bar-col { grid-column: 1 / -1; }
    }

    /* Rank badge */
    .rank {
      width: 30px; height: 30px;
      border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
      font-size: 0.8rem;
      font-weight: 700;
      color: #fff;
      background: var(--gray-400);
      flex-shrink: 0;
    }
    .rank.gold   { background: #f59e0b; }
    .rank.silver { background: #94a3b8; }
    .rank.bronze { background: #b45309; }

    /* Identity */
    .identity { min-width: 0; }
    .name {
      font-size: 0.9rem;
      font-weight: 600;
      color: var(--gray-900);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .sub {
      display: flex;
      align-items: center;
      gap: 5px;
      margin-top: 2px;
      font-size: 0.75rem;
      color: var(--gray-600);
    }
    .chip {
      padding: 1px 6px;
      background: #eff6ff;
      color: var(--blue-500);
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.7rem;
    }

    /* Bar column */
    .bar-col { min-width: 0; }
    .bar-track {
      height: 10px;
      background: var(--gray-100);
      border-radius: 5px;
      overflow: hidden;
      margin-bottom: 5px;
    }
    .bar-fill {
      height: 100%;
      border-radius: 5px;
      transition: width .55s cubic-bezier(.4,0,.2,1);
    }

    /* Candidate bar colours by rank */
    .fill-rank-1 { background: linear-gradient(90deg,#1d4ed8,#60a5fa); }
    .fill-rank-2 { background: linear-gradient(90deg,#0369a1,#38bdf8); }
    .fill-rank-3 { background: linear-gradient(90deg,#0f766e,#34d399); }
    .fill-rank-4 { background: linear-gradient(90deg,#6d28d9,#a78bfa); }
    .fill-rank-5 { background: linear-gradient(90deg,#9d174d,#f472b6); }

    /* Party bar colours */
    .fill-party-1 { background: linear-gradient(90deg,#15803d,#4ade80); }
    .fill-party-2 { background: linear-gradient(90deg,#b45309,#fbbf24); }
    .fill-party-3 { background: linear-gradient(90deg,#be123c,#fb7185); }
    .fill-party-4 { background: linear-gradient(90deg,#7c3aed,#c4b5fd); }
    .fill-party-5 { background: linear-gradient(90deg,#0e7490,#22d3ee); }

    .bar-labels {
      display: flex;
      justify-content: space-between;
      font-size: 0.77rem;
    }
    .votes { font-weight: 600; color: var(--gray-900); }
    .pct   { color: var(--gray-600); }

    /* ── Stacked summary bar ──────────────────────────────────────────── */
    .stacked-bar-wrapper {
      margin-top: 24px;
      padding: 16px;
      background: var(--gray-50);
      border: 1px solid var(--gray-200);
      border-radius: 10px;
    }
    .stacked-label {
      font-size: 0.78rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: .05em;
      color: var(--gray-600);
      margin-bottom: 10px;
    }
    .stacked-track {
      display: flex;
      height: 18px;
      border-radius: 9px;
      overflow: hidden;
      gap: 1px;
    }
    .stacked-seg {
      min-width: 2px;
      transition: flex .55s cubic-bezier(.4,0,.2,1);
    }
    .seg-1 { background: #4ade80; }
    .seg-2 { background: #fbbf24; }
    .seg-3 { background: #fb7185; }
    .seg-4 { background: #c4b5fd; }
    .seg-5 { background: #22d3ee; }
    .seg-6 { background: #fdba74; }
    .seg-7 { background: #86efac; }
    .seg-8 { background: #f9a8d4; }

    .stacked-legend {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin-top: 10px;
    }
    .legend-entry {
      display: flex;
      align-items: center;
      gap: 5px;
      font-size: 0.78rem;
      color: var(--gray-900);
      font-weight: 500;
    }
    .legend-dot {
      width: 10px; height: 10px;
      border-radius: 2px;
      display: inline-block;
    }
    .legend-pct { color: var(--gray-600); font-weight: 400; }
  `]
})
export class ElectionResultsComponent implements OnInit, OnDestroy {

  // ── Reactive state (Angular Signals) ────────────────────────────────────
  results = signal<ElectionResults | null>(null);
  fetchError = signal(false);

  activeTab: Tab = 'candidate';
  selectedRegion = '';

  // Derived: unique regions extracted from the candidate list for the filter dropdown.
  uniqueRegions = computed(() => {
    const r = this.results();
    if (!r) return [];
    const seen = new Set<string>();
    r.byCandidate.forEach(c => { if (c.region) seen.add(c.region); });
    return [...seen].sort();
  });

  // Expose Math.min to the template.
  readonly Math = Math;

  private electionId!: string;
  private destroy$ = new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private service: ResultsService
  ) {}

  ngOnInit(): void {
    this.electionId = this.route.snapshot.paramMap.get('electionId') ?? '';
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private startPolling(): void {
    this.service
      .pollResults(this.electionId, this.selectedRegion || null)
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => {
        if (data) {
          this.results.set(data);
          this.fetchError.set(false);
        } else {
          // null = error rrjeti — mbaj rezultatet e fundit, por shfaq banner-in
          this.fetchError.set(true);
        }
      });
  }

  onRegionChange(_region: string): void {
    // Cancel current poll and restart with the new region.
    this.destroy$.next();
    this.startPolling();
  }

  retryNow(): void {
    this.fetchError.set(false);
    this.destroy$.next();
    this.startPolling();
  }

  formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString('sq-AL', {
      hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }

  statusClass(status: string): string {
    if (status === 'STARTED') return 'chip-started';
    if (status === 'CLOSED') return 'chip-closed';
    return 'chip-default';
  }

  statusLabel(status: string): string {
    const map: Record<string, string> = {
      STARTED: 'Aktive',
      CLOSED: 'Mbyllur',
      CREATED: 'E krijuar',
      FINISHED: 'Përfunduar'
    };
    return map[status] ?? status;
  }

  rankClass(index: number): string {
    if (index === 0) return 'rank gold';
    if (index === 1) return 'rank silver';
    if (index === 2) return 'rank bronze';
    return 'rank';
  }

  trackById(_i: number, item: CandidateResult | PartyResult): number {
    return 'candidateId' in item ? item.candidateId : item.partyId;
  }
}
