import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import {ElectionType} from '../core/interfaces';
import {ElectionService} from '../core/services/election.service';

@Component({
  selector: 'app-create-election',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="p-8 max-w-3xl mx-auto">
      <!-- Header -->
      <div class="mb-8">
        <a routerLink="/elections"
           class="inline-flex items-center gap-2 text-slate-400 hover:text-white text-sm mb-4 transition-colors focus:outline-none focus:underline">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
          </svg>
          Back to Elections
        </a>
        <h1 class="text-2xl font-bold text-white" style="font-family: 'Georgia', serif;">Create New Election</h1>
        <p class="text-slate-400 text-sm mt-1">Configure a new election for the Albanian voting system</p>
      </div>

      <!-- Success banner -->
      @if (success()) {
        <div class="mb-6 p-4 bg-emerald-950 border border-emerald-700 rounded-xl flex items-start gap-3" role="alert">
          <svg class="w-5 h-5 text-emerald-400 mt-0.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
          </svg>
          <div>
            <p class="text-emerald-300 font-medium text-sm">Election created successfully!</p>
            <p class="text-emerald-500 text-xs mt-0.5">Redirecting to election details…</p>
          </div>
        </div>
      }

      <!-- Error banner -->
      @if (error()) {
        <div class="mb-6 p-4 bg-red-950 border border-red-700 rounded-xl flex items-start gap-3" role="alert">
          <svg class="w-5 h-5 text-red-400 mt-0.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
          </svg>
          <p class="text-red-300 text-sm">{{ error() }}</p>
        </div>
      }

      <!-- Form -->
      <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">

          <!-- Election Name -->
          <div>
            <label for="name" class="block text-sm font-medium text-slate-300 mb-2">
              Election Name <span class="text-red-500" aria-hidden="true">*</span>
            </label>
            <input
              id="name"
              type="text"
              formControlName="name"
              placeholder="e.g. Parliamentary Elections 2025"
              class="w-full px-4 py-2.5 bg-slate-800 border rounded-xl text-white placeholder-slate-500 text-sm focus:outline-none focus:ring-2 focus:ring-red-600 transition-colors"
              [class]="form.get('name')?.invalid && form.get('name')?.touched
                ? 'border-red-600'
                : 'border-slate-700'"
              [attr.aria-describedby]="form.get('name')?.invalid && form.get('name')?.touched ? 'name-error' : null"
            />
            @if (form.get('name')?.invalid && form.get('name')?.touched) {
              <p id="name-error" class="mt-1.5 text-xs text-red-400">Election name is required</p>
            }
          </div>

          <!-- Description -->
          <div>
            <label for="description" class="block text-sm font-medium text-slate-300 mb-2">
              Description <span class="text-slate-500 text-xs font-normal">(optional)</span>
            </label>
            <textarea
              id="description"
              formControlName="description"
              rows="3"
              placeholder="Brief description of this election…"
              class="w-full px-4 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-white placeholder-slate-500 text-sm focus:outline-none focus:ring-2 focus:ring-red-600 transition-colors resize-none"
            ></textarea>
          </div>

          <!-- Election Type -->
          <fieldset>
            <legend class="block text-sm font-medium text-slate-300 mb-3">
              Election Type <span class="text-red-500" aria-hidden="true">*</span>
            </legend>
            <div class="grid grid-cols-2 gap-3">
              @for (type of electionTypes; track type.value) {
                <label
                  class="relative flex flex-col gap-1.5 p-4 rounded-xl border cursor-pointer transition-all"
                  [class]="form.get('electionType')?.value === type.value
                    ? 'border-red-600 bg-red-950/40'
                    : 'border-slate-700 bg-slate-800 hover:border-slate-600'">
                  <input
                    type="radio"
                    formControlName="electionType"
                    [value]="type.value"
                    class="sr-only"
                  />
                  <span class="text-2xl">{{ type.icon }}</span>
                  <span class="text-sm font-semibold text-white">{{ type.label }}</span>
                  <span class="text-xs text-slate-400">{{ type.desc }}</span>
                  @if (form.get('electionType')?.value === type.value) {
                    <span class="absolute top-3 right-3 w-4 h-4 bg-red-600 rounded-full flex items-center justify-center" aria-hidden="true">
                      <svg class="w-2.5 h-2.5 text-white" fill="currentColor" viewBox="0 0 20 20">
                        <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"/>
                      </svg>
                    </span>
                  }
                </label>
              }
            </div>
            @if (form.get('electionType')?.invalid && form.get('electionType')?.touched) {
              <p class="mt-1.5 text-xs text-red-400">Please select an election type</p>
            }
          </fieldset>

          <!-- Dates row -->
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label for="electionDate" class="block text-sm font-medium text-slate-300 mb-2">
                Election Date <span class="text-red-500" aria-hidden="true">*</span>
              </label>
              <input
                id="electionDate"
                type="datetime-local"
                formControlName="electionDate"
                class="w-full px-4 py-2.5 bg-slate-800 border rounded-xl text-white text-sm focus:outline-none focus:ring-2 focus:ring-red-600 transition-colors"
                [class]="form.get('electionDate')?.invalid && form.get('electionDate')?.touched
                  ? 'border-red-600'
                  : 'border-slate-700'"
              />
              @if (form.get('electionDate')?.invalid && form.get('electionDate')?.touched) {
                <p class="mt-1.5 text-xs text-red-400">Election date is required</p>
              }
            </div>
            <div>
              <label for="registrationDeadline" class="block text-sm font-medium text-slate-300 mb-2">
                Registration Deadline <span class="text-red-500" aria-hidden="true">*</span>
              </label>
              <input
                id="registrationDeadline"
                type="datetime-local"
                formControlName="registrationDeadline"
                class="w-full px-4 py-2.5 bg-slate-800 border rounded-xl text-white text-sm focus:outline-none focus:ring-2 focus:ring-red-600 transition-colors"
                [class]="form.get('registrationDeadline')?.invalid && form.get('registrationDeadline')?.touched
                  ? 'border-red-600'
                  : 'border-slate-700'"
              />
              @if (form.get('registrationDeadline')?.invalid && form.get('registrationDeadline')?.touched) {
                <p class="mt-1.5 text-xs text-red-400">Registration deadline is required</p>
              }
            </div>
          </div>

          <!-- Optional dates -->
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label for="startDate" class="block text-sm font-medium text-slate-300 mb-2">
                Voting Start <span class="text-slate-500 text-xs font-normal">(optional)</span>
              </label>
              <input
                id="startDate"
                type="datetime-local"
                formControlName="startDate"
                class="w-full px-4 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-white text-sm focus:outline-none focus:ring-2 focus:ring-red-600 transition-colors"
              />
            </div>
            <div>
              <label for="endDate" class="block text-sm font-medium text-slate-300 mb-2">
                Voting End <span class="text-slate-500 text-xs font-normal">(optional)</span>
              </label>
              <input
                id="endDate"
                type="datetime-local"
                formControlName="endDate"
                class="w-full px-4 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-white text-sm focus:outline-none focus:ring-2 focus:ring-red-600 transition-colors"
              />
            </div>
          </div>

          <!-- External data source -->
          <div>
            <label for="externalDataSource" class="block text-sm font-medium text-slate-300 mb-2">
              External Data Source URL <span class="text-slate-500 text-xs font-normal">(optional)</span>
            </label>
            <input
              id="externalDataSource"
              type="url"
              formControlName="externalDataSource"
              placeholder="https://api.cec.gov.al/elections/..."
              class="w-full px-4 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-white placeholder-slate-500 text-sm focus:outline-none focus:ring-2 focus:ring-red-600 transition-colors"
            />
            <p class="mt-1.5 text-xs text-slate-500">Leave blank to use mock candidate data for testing</p>
          </div>
        </div>

        <!-- Actions -->
        <div class="mt-6 flex items-center justify-end gap-3">
          <a routerLink="/elections"
             class="px-5 py-2.5 text-sm font-medium text-slate-300 hover:text-white border border-slate-700 hover:border-slate-600 rounded-xl transition-colors focus:outline-none focus:ring-2 focus:ring-slate-500">
            Cancel
          </a>
          <button
            type="submit"
            [disabled]="loading() || form.invalid"
            class="inline-flex items-center gap-2 px-6 py-2.5 bg-red-600 hover:bg-red-700 disabled:bg-red-800 disabled:cursor-not-allowed rounded-xl text-white text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2 focus:ring-offset-slate-950">
            @if (loading()) {
              <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
              </svg>
              Creating…
            } @else {
              Create Election
            }
          </button>
        </div>
      </form>
    </div>
  `,
})
export class CreateElectionComponent {
  private fb = inject(FormBuilder);
  private electionService = inject(ElectionService);
  private router = inject(Router);

  loading = signal(false);
  error = signal<string | null>(null);
  success = signal(false);

  form = this.fb.group({
    name: ['', Validators.required],
    description: [''],
    electionType: ['', Validators.required],
    electionDate: ['', Validators.required],
    registrationDeadline: ['', Validators.required],
    startDate: [''],
    endDate: [''],
    externalDataSource: [''],
  });

  electionTypes = [
    {
      value: ElectionType.PARLIAMENTARY,
      label: 'Parliamentary',
      icon: '🏛️',
      desc: '12 counties — national legislature',
    },
    {
      value: ElectionType.LOCAL_GOVERNMENT,
      label: 'Local Government',
      icon: '🏘️',
      desc: '65 municipalities — local councils',
    },
  ];

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    const v = this.form.value;
    const request = {
      name: v.name!,
      description: v.description || undefined,
      electionType: v.electionType as ElectionType,
      electionDate: v.electionDate!,
      registrationDeadline: v.registrationDeadline!,
      startDate: v.startDate || undefined,
      endDate: v.endDate || undefined,
      externalDataSource: v.externalDataSource || undefined,
    };

    this.electionService.createElection(request).subscribe({
      next: (election) => {
        this.loading.set(false);
        this.success.set(true);
        setTimeout(() => this.router.navigate(['/elections', election.id]), 1200);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to create election. Please try again.');
      },
    });
  }
}
