import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import {ElectionService} from '../core/services/election.service';
import {ELECTION_STATUS_LABELS, ELECTION_TYPE_LABELS, ElectionResponse, ElectionStatus} from '../core/interfaces';

type Filter = 'ALL' | ElectionStatus;

@Component({
  selector: 'app-elections-list',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="p-8 max-w-6xl mx-auto">
      <!-- Header -->
      <div class="mb-8 flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-white" style="font-family: 'Georgia', serif;">Elections</h1>
          <p class="text-slate-400 text-sm mt-1">Manage and monitor all elections</p>
        </div>
        <a routerLink="/elections/new"
           class="inline-flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg text-white text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2 focus:ring-offset-slate-950">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
          </svg>
          New Election
        </a>
      </div>

      <!-- Filter tabs -->
      <div class="flex gap-1 mb-6 bg-slate-900 rounded-xl p-1 border border-slate-800 w-fit" role="tablist">
        @for (tab of tabs; track tab.key) {
          <button
            (click)="setFilter(tab.key)"
            role="tab"
            [attr.aria-selected]="activeFilter() === tab.key"
            class="px-4 py-1.5 rounded-lg text-xs font-medium transition-all focus:outline-none focus:ring-2 focus:ring-red-600"
            [class]="activeFilter() === tab.key
              ? 'bg-red-600 text-white'
              : 'text-slate-400 hover:text-white'">
            {{ tab.label }}
            <span class="ml-1.5 text-xs opacity-70">({{ countForFilter(tab.key) }})</span>
          </button>
        }
      </div>

      <!-- Table -->
      <div class="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        @if (loading()) {
          <div class="p-8 space-y-3">
            @for (i of [1,2,3]; track i) {
              <div class="h-14 bg-slate-800 rounded-lg animate-pulse"></div>
            }
          </div>
        } @else if (filtered().length === 0) {
          <div class="py-16 text-center">
            <p class="text-slate-500 text-sm">No elections found.</p>
          </div>
        } @else {
          <table class="w-full" aria-label="Elections list">
            <thead>
              <tr class="border-b border-slate-800">
                <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide">Election</th>
                <th scope="col" class="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide">Type</th>
                <th scope="col" class="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide">Status</th>
                <th scope="col" class="px-4 py-3 text-right text-xs font-medium text-slate-500 uppercase tracking-wide">Votes</th>
                <th scope="col" class="px-4 py-3 text-right text-xs font-medium text-slate-500 uppercase tracking-wide">Turnout</th>
                <th scope="col" class="px-6 py-3 text-right text-xs font-medium text-slate-500 uppercase tracking-wide">Action</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              @for (election of filtered(); track election.id) {
                <tr class="hover:bg-slate-800/40 transition-colors group">
                  <td class="px-6 py-4">
                    <p class="text-sm font-medium text-white">{{ election.name }}</p>
                    <p class="text-xs text-slate-500 mt-0.5">
                      {{ election.candidateCount }} candidates · {{ election.partyCount }} parties
                    </p>
                  </td>
                  <td class="px-4 py-4">
                    <span class="text-xs text-slate-400">{{ typeLabel(election) }}</span>
                  </td>
                  <td class="px-4 py-4">
                    <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium"
                          [class]="statusClass(election.status)">
                      @if (election.status === 'STARTED') {
                        <span class="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse" aria-hidden="true"></span>
                      }
                      {{ statusLabel(election.status) }}
                    </span>
                  </td>
                  <td class="px-4 py-4 text-right">
                    <span class="text-sm text-white">{{ election.totalVotesCast.toLocaleString() }}</span>
                  </td>
                  <td class="px-4 py-4 text-right">
                    <span class="text-sm text-white">{{ election.turnoutPercentage.toFixed(1) }}%</span>
                  </td>
                  <td class="px-6 py-4 text-right">
                    <a [routerLink]="['/elections', election.id]"
                       class="text-xs text-slate-500 group-hover:text-red-400 transition-colors focus:outline-none focus:underline"
                       [attr.aria-label]="'View ' + election.name">
                      View →
                    </a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    </div>
  `,
})
export class ElectionsListComponent implements OnInit {
  private electionService = inject(ElectionService);

  elections = signal<ElectionResponse[]>([]);
  loading = signal(true);
  activeFilter = signal<Filter>('ALL');

  tabs: { key: Filter; label: string }[] = [
    { key: 'ALL', label: 'All' },
    { key: ElectionStatus.STARTED, label: 'Active' },
    { key: ElectionStatus.CANDIDATES_IMPORTED, label: 'Ready' },
    { key: ElectionStatus.CREATED, label: 'Created' },
    { key: ElectionStatus.CLOSED, label: 'Closed' },
  ];

  filtered = computed(() => {
    const f = this.activeFilter();
    if (f === 'ALL') return this.elections();
    return this.elections().filter((e) => e.status === f);
  });

  ngOnInit(): void {
    this.electionService.getAllElections(0, 100).subscribe({
      next: (page) => {
        this.elections.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  setFilter(f: Filter): void {
    this.activeFilter.set(f);
  }

  countForFilter(f: Filter): number {
    if (f === 'ALL') return this.elections().length;
    return this.elections().filter((e) => e.status === f).length;
  }

  typeLabel(e: ElectionResponse): string {
    return ELECTION_TYPE_LABELS[e.electionType] ?? e.electionType;
  }

  statusLabel(s: ElectionStatus): string {
    return ELECTION_STATUS_LABELS[s] ?? s;
  }

  statusClass(s: ElectionStatus): string {
    const map: Record<ElectionStatus, string> = {
      [ElectionStatus.CREATED]: 'bg-slate-800 text-slate-300',
      [ElectionStatus.CANDIDATES_IMPORTED]: 'bg-blue-950/50 text-blue-300',
      [ElectionStatus.STARTED]: 'bg-green-950/50 text-green-300',
      [ElectionStatus.CLOSED]: 'bg-slate-800 text-slate-400',
      [ElectionStatus.RESULTS_PUBLISHED]: 'bg-purple-950/50 text-purple-300',
    };
    return map[s] ?? 'bg-slate-800 text-slate-300';
  }
}
