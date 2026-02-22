import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import {ElectionService} from '../core/services/election.service';
import {VerificationResponse} from '../core/interfaces';

@Component({
  selector: 'app-verification',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="p-8 max-w-2xl mx-auto">
      <!-- Header -->
      <div class="mb-8">
        <h1 class="text-2xl font-bold text-white" style="font-family: 'Georgia', serif;">Vote Verification</h1>
        <p class="text-slate-400 text-sm mt-1">Verify that a vote was recorded on the blockchain</p>
      </div>

      <!-- Search form -->
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 mb-6">
        <div class="flex gap-2 mb-4">
          <button
            (click)="mode.set('hash')"
            class="px-4 py-1.5 rounded-lg text-xs font-medium transition-all focus:outline-none focus:ring-2 focus:ring-red-600"
            [class]="mode() === 'hash' ? 'bg-red-600 text-white' : 'text-slate-400 hover:text-white'"
            [attr.aria-pressed]="mode() === 'hash'">
            Vote Hash
          </button>
          <button
            (click)="mode.set('receipt')"
            class="px-4 py-1.5 rounded-lg text-xs font-medium transition-all focus:outline-none focus:ring-2 focus:ring-red-600"
            [class]="mode() === 'receipt' ? 'bg-red-600 text-white' : 'text-slate-400 hover:text-white'"
            [attr.aria-pressed]="mode() === 'receipt'">
            Receipt Token
          </button>
        </div>

        <form [formGroup]="form" (ngSubmit)="onVerify()" novalidate>
          <div class="flex gap-3">
            <div class="flex-1">
              <label [for]="'token-input'" class="sr-only">
                {{ mode() === 'hash' ? 'Vote Hash' : 'Receipt Token' }}
              </label>
              <input
                id="token-input"
                type="text"
                formControlName="token"
                [placeholder]="mode() === 'hash' ? 'Paste vote hash…' : 'Paste receipt token…'"
                class="w-full px-4 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-white placeholder-slate-500 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-red-600 transition-colors"
                [attr.aria-describedby]="form.get('token')?.invalid && form.get('token')?.touched ? 'token-error' : null"
              />
              @if (form.get('token')?.invalid && form.get('token')?.touched) {
                <p id="token-error" class="mt-1.5 text-xs text-red-400">Please enter a valid token</p>
              }
            </div>
            <button
              type="submit"
              [disabled]="loading() || form.invalid"
              class="px-5 py-2.5 bg-red-600 hover:bg-red-700 disabled:bg-red-800 disabled:cursor-not-allowed rounded-xl text-white text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-red-500 whitespace-nowrap">
              @if (loading()) {
                <svg class="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                </svg>
              } @else {
                Verify
              }
            </button>
          </div>
        </form>
      </div>

      <!-- Error -->
      @if (error()) {
        <div class="p-4 bg-red-950 border border-red-700 rounded-2xl flex items-start gap-3 mb-6" role="alert">
          <svg class="w-5 h-5 text-red-400 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
          </svg>
          <p class="text-red-300 text-sm">{{ error() }}</p>
        </div>
      }

      <!-- Result -->
      @if (result()) {
        <div class="bg-slate-900 border rounded-2xl p-6"
             [class]="result()!.verified ? 'border-emerald-700' : 'border-red-700'">
          <!-- Status header -->
          <div class="flex items-center gap-3 mb-5 pb-4 border-b border-slate-800">
            @if (result()!.verified) {
              <div class="w-10 h-10 bg-emerald-900 rounded-full flex items-center justify-center shrink-0" aria-hidden="true">
                <svg class="w-5 h-5 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7"/>
                </svg>
              </div>
              <div>
                <p class="text-base font-semibold text-emerald-300">Vote Verified</p>
                <p class="text-xs text-slate-400">This vote exists on the blockchain</p>
              </div>
            } @else {
              <div class="w-10 h-10 bg-red-900 rounded-full flex items-center justify-center shrink-0" aria-hidden="true">
                <svg class="w-5 h-5 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M6 18L18 6M6 6l12 12"/>
                </svg>
              </div>
              <div>
                <p class="text-base font-semibold text-red-300">Vote Not Found</p>
                <p class="text-xs text-slate-400">{{ result()!.message }}</p>
              </div>
            }
          </div>

          @if (result()!.verified) {
            <!-- Details grid -->
            <div class="space-y-3">
              @for (field of resultFields(); track field.label) {
                @if (field.value) {
                  <div class="flex items-start gap-3">
                    <p class="text-xs text-slate-500 w-32 shrink-0 pt-0.5">{{ field.label }}</p>
                    <p class="text-sm text-white font-mono break-all flex-1">{{ field.value }}</p>
                  </div>
                }
              }

              <!-- Blockchain consistency -->
              <div class="mt-4 pt-4 border-t border-slate-800 flex items-center gap-3">
                @if (result()!.blockchainConsistent) {
                  <span class="flex items-center gap-1.5 text-xs text-emerald-400">
                    <svg class="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
                      <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"/>
                    </svg>
                    Blockchain consistent
                  </span>
                } @else {
                  <span class="text-xs text-amber-400">⚠ Awaiting blockchain confirmation</span>
                }
                @if (result()!.merkleProof) {
                  <span class="text-xs text-slate-500">· Merkle proof available</span>
                }
              </div>
            </div>
          }
        </div>
      }

      <!-- Explainer -->
      @if (!result() && !loading()) {
        <div class="p-5 bg-slate-900/50 border border-slate-800 rounded-2xl">
          <h2 class="text-sm font-semibold text-slate-300 mb-3">How vote verification works</h2>
          <div class="space-y-2 text-xs text-slate-500">
            <p>1. After casting a vote, the voter receives a <span class="text-slate-300 font-medium">vote hash</span> and a <span class="text-slate-300 font-medium">receipt token</span>.</p>
            <p>2. These tokens can be used here to confirm the vote was recorded on the blockchain.</p>
            <p>3. Vote identity is protected — verification only confirms the vote exists, not who cast it.</p>
          </div>
        </div>
      }
    </div>
  `,
})
export class VerificationComponent {
  private fb = inject(FormBuilder);
  private electionService = inject(ElectionService);

  mode = signal<'hash' | 'receipt'>('hash');
  loading = signal(false);
  error = signal<string | null>(null);
  result = signal<VerificationResponse | null>(null);

  form = this.fb.group({
    token: ['', [Validators.required, Validators.minLength(8)]],
  });

  resultFields() {
    const r = this.result();
    if (!r) return [];
    return [
      { label: 'Election', value: r.electionName },
      { label: 'Block #', value: r.blockNumber?.toString() },
      { label: 'Block Hash', value: r.blockHash },
      { label: 'Vote Hash', value: r.voteHash },
      { label: 'Timestamp', value: r.voteTimestamp ? this.formatDate(r.voteTimestamp) : null },
      { label: 'Verified At', value: r.verificationTimestamp ? this.formatDate(r.verificationTimestamp) : null },
    ];
  }

  onVerify(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const token = this.form.value.token!.trim();
    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);

    const obs = this.mode() === 'hash'
      ? this.electionService.verifyVote(token)
      : this.electionService.verifyByReceipt(token);

    obs.subscribe({
      next: (r) => {
        this.loading.set(false);
        this.result.set(r);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Verification failed. Please check the token and try again.');
      },
    });
  }

  formatDate(value: string): string {
    try {
      return new Intl.DateTimeFormat('en-GB', {
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
      }).format(new Date(value));
    } catch {
      return value;
    }
  }
}
