let ctx: AudioContext | null = null;

export function playClick() {
  const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AudioCtx) return;
  if (!ctx) ctx = new AudioCtx();
  if (ctx.state === 'suspended') void ctx.resume();
  const t = ctx.currentTime;
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.type = 'triangle';
  osc.frequency.setValueAtTime(740, t);
  osc.frequency.exponentialRampToValueAtTime(240, t + 0.07);
  gain.gain.setValueAtTime(0.0001, t);
  gain.gain.exponentialRampToValueAtTime(0.045, t + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.09);
  osc.connect(gain);
  gain.connect(ctx.destination);
  osc.start(t);
  osc.stop(t + 0.1);
}
