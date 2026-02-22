import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
  input,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import {ELECTION_STATUS_LABELS, ELECTION_TYPE_LABELS, ElectionResponse, ElectionStatus} from '../core/interfaces';
import {ElectionService} from '../core/services/election.service';


@Component({
  selector: 'app-election-detail',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="p-8 max-w-5xl mx-auto">
      <!-- Back -->
      <a routerLink="/elections"
         class="inline-flex items-center gap-2 text-slate-400 hover:text-white text-sm mb-6 transition-colors focus:outline-none focus:underline">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
        </svg>
        Back to Elections
      </a>

      @if (loading()) {
        <div class="space-y-4">
          @for (i of [1, 2, 3]; track i) {
            <div class="h-24 bg-slate-800 rounded-2xl animate-pulse"></div>
          }
        </div>
      } @else if (error()) {
        <div class="p-6 bg-red-950 border border-red-700 rounded-2xl text-center" role="alert">
          <p class="text-red-300">{{ error() }}</p>
          <button (click)="loadElection()"
                  class="mt-3 px-4 py-2 bg-red-700 hover:bg-red-600 rounded-lg text-white text-sm transition-colors">
            Retry
          </button>
        </div>
      } @else if (election()) {
        <!-- Header card -->
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 mb-6">
          <div class="flex items-start justify-between gap-4 flex-wrap">
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-3 flex-wrap mb-2">
                <!-- Status badge -->
                <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold"
                      [class]="statusBadgeClass()">
                  @if (election()!.status === 'STARTED') {
                    <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" aria-hidden="true"></span>
                  }
                  {{ statusLabel() }}
                </span>
                <span class="px-3 py-1 bg-slate-800 border border-slate-700 rounded-full text-xs text-slate-400">
                  {{ typeLabel() }}
                </span>
              </div>
              <h1 class="text-2xl font-bold text-white truncate" style="font-family: 'Georgia', serif;">
                {{ election()!.name }}
              </h1>
              @if (election()!.description) {
                <p class="text-slate-400 text-sm mt-2 leading-relaxed">{{ election()!.description }}</p>
              }
              <p class="text-slate-500 text-xs mt-3">
                Created {{ formatDate(election()!.createdAt) }}
              </p>
            </div>

            <!-- Action buttons -->
            <div class="flex items-center gap-2 flex-wrap shrink-0">
              @if (election()!.status === 'CREATED') {
                <button (click)="importCandidates()"
                        [disabled]="actionLoading()"
                        class="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-xl text-white text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500">
                  @if (actionLoading()) {
                    <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                    </svg>
                  }
                  Import Candidates
                </button>
              }
              @if (election()!.status === 'CANDIDATES_IMPORTED') {
                <button (click)="startElection()"
                        [disabled]="actionLoading()"
                        class="inline-flex items-center gap-2 px-4 py-2 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 rounded-xl text-white text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-emerald-500">
                  @if (actionLoading()) {
                    <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                    </svg>
                  }
                  🚀 Start Election
                </button>
              }
              @if (election()!.status === 'STARTED') {
                @if (confirmClose()) {
                  <div class="flex items-center gap-2">
                    <span class="text-xs text-slate-400">Are you sure?</span>
                    <button (click)="closeElection()"
                            [disabled]="actionLoading()"
                            class="px-3 py-1.5 bg-red-600 hover:bg-red-700 disabled:opacity-50 rounded-lg text-white text-xs font-medium transition-colors focus:outline-none">
                      Yes, Close
                    </button>
                    <button (click)="confirmClose.set(false)"
                            class="px-3 py-1.5 bg-slate-700 hover:bg-slate-600 rounded-lg text-white text-xs font-medium transition-colors focus:outline-none">
                      Cancel
                    </button>
                  </div>
                } @else {
                  <button (click)="confirmClose.set(true)"
                          class="inline-flex items-center gap-2 px-4 py-2 bg-red-900 hover:bg-red-800 border border-red-700 rounded-xl text-red-300 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-red-600">
                    🔒 Close Election
                  </button>
                }
              }
            </div>
          </div>

          <!-- Action feedback -->
          @if (actionSuccess()) {
            <div class="mt-4 p-3 bg-emerald-950 border border-emerald-700 rounded-xl flex items-center gap-2" role="alert">
              <svg class="w-4 h-4 text-emerald-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
              </svg>
              <p class="text-emerald-300 text-sm">{{ actionSuccess() }}</p>
            </div>
          }
          @if (actionError()) {
            <div class="mt-4 p-3 bg-red-950 border border-red-700 rounded-xl" role="alert">
              <p class="text-red-300 text-sm">{{ actionError() }}</p>
            </div>
          }
        </div>

        <!-- Stats grid -->
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          @for (stat of stats(); track stat.label) {
            <div class="bg-slate-900 border border-slate-800 rounded-xl p-4">
              <p class="text-xs text-slate-500 uppercase tracking-widest mb-1">{{ stat.label }}</p>
              <p class="text-2xl font-bold text-white">{{ stat.value }}</p>
              @if (stat.sub) {
                <p class="text-xs text-slate-500 mt-0.5">{{ stat.sub }}</p>
              }
            </div>
          }
        </div>

        <!-- Two columns -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <!-- Blockchain stats -->
          @if (election()!.status === 'STARTED' || election()!.status === 'CLOSED' || election()!.status === 'RESULTS_PUBLISHED') {
            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5">
              <h2 class="text-sm font-semibold text-slate-300 mb-4 flex items-center gap-2">
                <span class="text-lg" aria-hidden="true">⛓️</span>
                Blockchain Status
              </h2>
              @if (chainLoading()) {
                <div class="space-y-2">
                  @for (i of [1, 2, 3]; track i) {
                    <div class="h-8 bg-slate-800 rounded-lg animate-pulse"></div>
                  }
                </div>
              } @else if (chainStats()) {
                <div class="space-y-3">
                  <div class="flex items-center justify-between py-2 border-b border-slate-800">
                    <span class="text-xs text-slate-400">Chain Valid</span>
                    @if (chainStats()!['chainValid']) {
                      <span class="flex items-center gap-1.5 text-xs font-medium text-emerald-400">
                        <svg class="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
                          <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"/>
                        </svg>
                        Verified
                      </span>
                    } @else {
                      <span class="text-xs font-medium text-red-400">⚠ Invalid</span>
                    }
                  </div>
                  <div class="flex items-center justify-between py-2 border-b border-slate-800">
                    <span class="text-xs text-slate-400">Block Count</span>
                    <span class="text-sm font-mono text-white">{{ chainStats()!['blockCount'] }}</span>
                  </div>
                  <div class="flex items-center justify-between py-2">
                    <span class="text-xs text-slate-400">Total Transactions</span>
                    <span class="text-sm font-mono text-white">{{ chainStats()!['totalTransactions'] }}</span>
                  </div>
                </div>
              }
            </div>
          }

          <!-- Timeline -->
          <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5">
            <h2 class="text-sm font-semibold text-slate-300 mb-4 flex items-center gap-2">
              <span class="text-lg" aria-hidden="true">📅</span>
              Timeline
            </h2>
            <div class="space-y-3">
              @for (item of timeline(); track item.label) {
                <div class="flex items-start gap-3">
                  <div class="w-1.5 h-1.5 rounded-full mt-2 shrink-0"
                       [class]="item.value ? 'bg-red-500' : 'bg-slate-700'"
                       aria-hidden="true"></div>
                  <div>
                    <p class="text-xs text-slate-500">{{ item.label }}</p>
                    <p class="text-sm text-white">{{ item.value ? formatDate(item.value) : '—' }}</p>
                  </div>
                </div>
              }
            </div>
          </div>
        </div>

        <!-- Parties list -->
        @if (election()!.parties && election()!.parties!.length > 0) {
          <div class="mt-6 bg-slate-900 border border-slate-800 rounded-2xl p-5">
            <h2 class="text-sm font-semibold text-slate-300 mb-4 flex items-center gap-2">
              <span class="text-lg" aria-hidden="true">🏛️</span>
              Parties ({{ election()!.parties!.length }})
            </h2>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              @for (party of election()!.parties; track party.id) {
                <div class="flex items-center gap-3 p-3 bg-slate-800 rounded-xl">
                  <div class="w-8 h-8 rounded-lg shrink-0 flex items-center justify-center text-xs font-bold text-white"
                       [style]="'background-color: ' + (party.color || '#6b7280')">
                    {{ party.partyCode }}
                  </div>
                  <div class="min-w-0">
                    <p class="text-sm font-medium text-white truncate">{{ party.name }}</p>
                    <p class="text-xs text-slate-400">{{ party.candidateCount }} candidates{{ party.leader ? ' · ' + party.leader : '' }}</p>
                  </div>
                </div>
              }
            </div>
          </div>
        }
      }
    </div>
  `,
})
export class ElectionDetailComponent implements OnInit {
  id = input.required<string>();

  private electionService = inject(ElectionService);

  loading = signal(true);
  error = signal<string | null>(null);
  election = signal<ElectionResponse | null>(null);
  actionLoading = signal(false);
  actionSuccess = signal<string | null>(null);
  actionError = signal<string | null>(null);
  confirmClose = signal(false);
  chainLoading = signal(false);
  chainStats = signal<Record<string, unknown> | null>(null);

  statusLabel = computed(() => {
    const e = this.election();
    return e ? (ELECTION_STATUS_LABELS[e.status] ?? e.status) : '';
  });

  typeLabel = computed(() => {
    const e = this.election();
    return e ? (ELECTION_TYPE_LABELS[e.electionType] ?? e.electionType) : '';
  });

  statusBadgeClass = computed(() => {
    const e = this.election();
    if (!e) return '';
    const map: Record<string, string> = {
      STARTED: 'bg-emerald-900 text-emerald-300 border border-emerald-700',
      CREATED: 'bg-slate-800 text-slate-300 border border-slate-600',
      CANDIDATES_IMPORTED: 'bg-blue-900 text-blue-300 border border-blue-700',
      CLOSED: 'bg-slate-700 text-slate-300 border border-slate-600',
      RESULTS_PUBLISHED: 'bg-purple-900 text-purple-300 border border-purple-700',
    };
    return map[e.status] ?? 'bg-slate-800 text-slate-300';
  });

  stats = computed(() => {
    const e = this.election();
    if (!e) return [];
    const turnout = e.totalEligibleVoters > 0
      ? ((e.totalVotesCast / e.totalEligibleVoters) * 100).toFixed(1) + '%'
      : '—';
    return [
      { label: 'Votes Cast', value: e.totalVotesCast.toLocaleString(), sub: undefined },
      { label: 'Turnout', value: turnout, sub: e.totalEligibleVoters > 0 ? `of ${e.totalEligibleVoters.toLocaleString()} eligible` : undefined },
      { label: 'Candidates', value: e.candidateCount.toString(), sub: undefined },
      { label: 'Parties', value: e.partyCount.toString(), sub: undefined },
    ];
  });

  timeline = computed(() => {
    const e = this.election();
    if (!e) return [];
    return [
      { label: 'Election Date', value: e.electionDate },
      { label: 'Registration Deadline', value: e.registrationDeadline },
      { label: 'Voting Started', value: e.startDate },
      { label: 'Voting Ended', value: e.endDate },
    ];
  });

  ngOnInit(): void {
    this.loadElection();
  }

  loadElection(): void {
    this.loading.set(true);
    this.error.set(null);
    this.electionService.getElection(this.id()).subscribe({
      next: (e) => {
        this.loading.set(false);
        this.election.set(e);
        if (e.status === ElectionStatus.STARTED || e.status === ElectionStatus.CLOSED) {
          this.loadChainStats(e.id);
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load election');
      },
    });
  }

  loadChainStats(id: string): void {
    this.chainLoading.set(true);
    this.electionService.getBlockchainStats(id).subscribe({
      next: (stats) => {
        this.chainLoading.set(false);
        this.chainStats.set(stats);
      },
      error: () => this.chainLoading.set(false),
    });
  }

  importCandidates(): void {
    this.runAction(
      () => this.electionService.importCandidates(this.id()),
      'Candidates imported successfully!',
    );
  }

  startElection(): void {
    this.runAction(
      () => this.electionService.startElection(this.id()),
      'Election started! Blockchain initialized.',
    );
  }

  closeElection(): void {
    this.confirmClose.set(false);
    this.runAction(
      () => this.electionService.closeElection(this.id()),
      'Election closed and results finalized.',
    );
  }

  private runAction(
    action: () => import('rxjs').Observable<ElectionResponse>,
    successMsg: string,
  ): void {
    this.actionLoading.set(true);
    this.actionError.set(null);
    this.actionSuccess.set(null);
    action().subscribe({
      next: (e) => {
        this.actionLoading.set(false);
        this.election.set(e);
        this.actionSuccess.set(successMsg);
        if (e.status === ElectionStatus.STARTED || e.status === ElectionStatus.CLOSED) {
          this.loadChainStats(e.id);
        }
        setTimeout(() => this.actionSuccess.set(null), 5000);
      },
      error: (err) => {
        this.actionLoading.set(false);
        this.actionError.set(err?.error?.message ?? 'Action failed. Please try again.');
        setTimeout(() => this.actionError.set(null), 8000);
      },
    });
  }

  formatDate(value: string | null | undefined): string {
    if (!value) return '—';
    try {
      return new Intl.DateTimeFormat('en-GB', {
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit',
      }).format(new Date(value));
    } catch {
      return value;
    }
  }
}
