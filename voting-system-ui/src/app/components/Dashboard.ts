import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import {ELECTION_TYPE_LABELS, ElectionResponse, ElectionStatus} from '../core/interfaces';
import {ElectionService} from '../core/services/election.service';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="p-8 max-w-6xl mx-auto">
      <!-- Page header -->
      <div class="mb-8">
        <h1 class="text-2xl font-bold text-white" style="font-family: 'Georgia', serif;">
          Election Dashboard
        </h1>
        <p class="text-slate-400 text-sm mt-1">Real-time overview of all elections and activity</p>
      </div>

      @if (loading()) {
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          @for (i of [1,2,3,4]; track i) {
            <div class="bg-slate-900 rounded-xl h-28 animate-pulse border border-slate-800"></div>
          }
        </div>
      } @else {
        <!-- Stats grid -->
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          <div class="bg-slate-900 border border-slate-800 rounded-xl p-5">
            <p class="text-xs text-slate-500 uppercase tracking-wide">Active Elections</p>
            <p class="text-3xl font-bold text-white mt-2">{{ activeCount() }}</p>
            <div class="mt-3 h-0.5 w-12 bg-green-500 rounded-full"></div>
          </div>
          <div class="bg-slate-900 border border-slate-800 rounded-xl p-5">
            <p class="text-xs text-slate-500 uppercase tracking-wide">Total Elections</p>
            <p class="text-3xl font-bold text-white mt-2">{{ elections().length }}</p>
            <div class="mt-3 h-0.5 w-12 bg-red-500 rounded-full"></div>
          </div>
          <div class="bg-slate-900 border border-slate-800 rounded-xl p-5">
            <p class="text-xs text-slate-500 uppercase tracking-wide">Total Votes Cast</p>
            <p class="text-3xl font-bold text-white mt-2">{{ totalVotes() | number }}</p>
            <div class="mt-3 h-0.5 w-12 bg-blue-500 rounded-full"></div>
          </div>
          <div class="bg-slate-900 border border-slate-800 rounded-xl p-5">
            <p class="text-xs text-slate-500 uppercase tracking-wide">Closed Elections</p>
            <p class="text-3xl font-bold text-white mt-2">{{ closedCount() }}</p>
            <div class="mt-3 h-0.5 w-12 bg-slate-500 rounded-full"></div>
          </div>
        </div>
      }

      <!-- Active elections -->
      <div class="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <div class="px-6 py-4 border-b border-slate-800 flex items-center justify-between">
          <h2 class="text-sm font-semibold text-white">Active Elections</h2>
          <a routerLink="/elections/new"
             class="text-xs text-red-400 hover:text-red-300 transition-colors focus:outline-none focus:underline">
            + New Election
          </a>
        </div>

        @if (loading()) {
          <div class="p-6 space-y-4">
            @for (i of [1,2]; track i) {
              <div class="h-16 bg-slate-800 rounded-lg animate-pulse"></div>
            }
          </div>
        } @else if (activeElections().length === 0) {
          <div class="p-12 text-center">
            <p class="text-slate-500 text-sm">No active elections.</p>
            <a routerLink="/elections/new"
               class="mt-3 inline-block text-red-400 hover:text-red-300 text-sm transition-colors focus:outline-none focus:underline">
              Create one →
            </a>
          </div>
        } @else {
          <div class="divide-y divide-slate-800">
            @for (election of activeElections(); track election.id) {
              <a [routerLink]="['/elections', election.id]"
                 class="flex items-center justify-between px-6 py-4 hover:bg-slate-800/50 transition-colors group focus:outline-none focus:bg-slate-800/50">
                <div class="flex items-center gap-4">
                  <div class="w-2 h-2 rounded-full bg-green-400 animate-pulse" aria-hidden="true"></div>
                  <div>
                    <p class="text-sm font-medium text-white group-hover:text-red-300 transition-colors">
                      {{ election.name }}
                    </p>
                    <p class="text-xs text-slate-500 mt-0.5">{{ typeLabel(election) }}</p>
                  </div>
                </div>
                <div class="flex items-center gap-6">
                  <div class="text-right">
                    <p class="text-sm font-semibold text-white">{{ election.totalVotesCast | number }}</p>
                    <p class="text-xs text-slate-500">votes cast</p>
                  </div>
                  <div class="text-right w-20">
                    <p class="text-sm font-semibold text-white">{{ election.turnoutPercentage | number:'1.1-1' }}%</p>
                    <div class="mt-1 h-1 bg-slate-700 rounded-full overflow-hidden w-20">
                      <div class="h-full bg-red-500 rounded-full transition-all"
                           [style.width]="turnoutWidth(election)"
                           role="progressbar"
                           [attr.aria-valuenow]="election.turnoutPercentage"
                           aria-valuemin="0"
                           aria-valuemax="100">
                      </div>
                    </div>
                  </div>
                  <svg class="w-4 h-4 text-slate-600 group-hover:text-slate-400 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/>
                  </svg>
                </div>
              </a>
            }
          </div>
        }
      </div>

      <!-- All elections list -->
      @if (!loading() && nonActiveElections().length > 0) {
        <div class="mt-6 bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
          <div class="px-6 py-4 border-b border-slate-800">
            <h2 class="text-sm font-semibold text-white">All Elections</h2>
          </div>
          <div class="divide-y divide-slate-800">
            @for (election of nonActiveElections(); track election.id) {
              <a [routerLink]="['/elections', election.id]"
                 class="flex items-center justify-between px-6 py-4 hover:bg-slate-800/50 transition-colors group focus:outline-none focus:bg-slate-800/50">
                <div>
                  <p class="text-sm font-medium text-white group-hover:text-red-300 transition-colors">
                    {{ election.name }}
                  </p>
                  <p class="text-xs text-slate-500 mt-0.5">{{ typeLabel(election) }}</p>
                </div>
                <span class="px-2.5 py-1 rounded-full text-xs font-medium"
                      [class]="statusClass(election.status)">
                  {{ statusLabel(election.status) }}
                </span>
              </a>
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class DashboardComponent implements OnInit {
  private electionService = inject(ElectionService);

  elections = signal<ElectionResponse[]>([]);
  loading = signal(true);

  activeElections = computed(() =>
    this.elections().filter((e) => e.status === ElectionStatus.STARTED),
  );
  nonActiveElections = computed(() =>
    this.elections().filter((e) => e.status !== ElectionStatus.STARTED),
  );
  activeCount = computed(() => this.activeElections().length);
  closedCount = computed(() =>
    this.elections().filter((e) => e.status === ElectionStatus.CLOSED).length,
  );
  totalVotes = computed(() =>
    this.elections().reduce((sum, e) => sum + e.totalVotesCast, 0),
  );

  ngOnInit(): void {
    this.electionService.getAllElections(0, 50).subscribe({
      next: (page) => {
        this.elections.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  typeLabel(election: ElectionResponse): string {
    return ELECTION_TYPE_LABELS[election.electionType] ?? election.electionType;
  }

  statusLabel(status: ElectionStatus): string {
    const labels: Record<ElectionStatus, string> = {
      [ElectionStatus.CREATED]: 'Created',
      [ElectionStatus.CANDIDATES_IMPORTED]: 'Ready',
      [ElectionStatus.STARTED]: 'Active',
      [ElectionStatus.CLOSED]: 'Closed',
      [ElectionStatus.RESULTS_PUBLISHED]: 'Published',
    };
    return labels[status] ?? status;
  }

  statusClass(status: ElectionStatus): string {
    const classes: Record<ElectionStatus, string> = {
      [ElectionStatus.CREATED]: 'bg-slate-800 text-slate-300',
      [ElectionStatus.CANDIDATES_IMPORTED]: 'bg-blue-950/50 text-blue-300',
      [ElectionStatus.STARTED]: 'bg-green-950/50 text-green-300',
      [ElectionStatus.CLOSED]: 'bg-slate-800 text-slate-400',
      [ElectionStatus.RESULTS_PUBLISHED]: 'bg-purple-950/50 text-purple-300',
    };
    return classes[status] ?? 'bg-slate-800 text-slate-300';
  }

  turnoutWidth(election: ElectionResponse): string {
    return Math.min(election.turnoutPercentage, 100) + '%';
  }
}
