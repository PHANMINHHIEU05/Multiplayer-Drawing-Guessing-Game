import React, { useEffect, useState, useMemo } from 'react';
import { useMetricsStore, metricsStore, DrawingProtocolMode } from '../store/metricsStore';

/**
 * Network Inspector — floating telemetry panel for the game screen.
 *
 * Visibility is owned by metricsStore.isInspectorOpen (default: closed).
 * Metrics collection lives in the store / WebSocketClient and keeps running
 * regardless of whether this UI is visible.
 *
 * Positioning: mounted inside the canvas container of GamePage (absolute
 * bottom-right) so it never covers the chat panel or the guess input.
 */
export const NetworkInspector: React.FC = () => {
  const metrics = useMetricsStore((s) => s);
  const [showDetails, setShowDetails] = useState<boolean>(false);

  // Global hotkeys: Ctrl+Shift+N or ` (backtick) toggle, Escape closes
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey && e.shiftKey && e.key.toLowerCase() === 'n') || e.key === '`') {
        e.preventDefault();
        metricsStore.toggleInspector();
      } else if (e.key === 'Escape' && metricsStore.getState().isInspectorOpen) {
        metricsStore.setInspectorOpen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const formatBytes = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'CONNECTED':
        return {
          bg: 'bg-emerald-400/25 text-emerald-100 border-emerald-300/50',
          dot: 'bg-emerald-300',
          text: 'Đã kết nối',
        };
      case 'CONNECTING':
      case 'RECONNECTING':
        return {
          bg: 'bg-amber-400/25 text-amber-100 border-amber-300/50 animate-pulse',
          dot: 'bg-amber-300 animate-ping',
          text: status === 'RECONNECTING' ? 'Đang thử lại' : 'Đang kết nối',
        };
      default:
        return {
          bg: 'bg-rose-400/25 text-rose-100 border-rose-300/50',
          dot: 'bg-rose-400',
          text: 'Mất kết nối',
        };
    }
  };

  const statusBadge = useMemo(() => getStatusBadge(metrics.status), [metrics.status]);

  const getRttColor = (rtt: number, sampleCount: number) => {
    if (sampleCount === 0 || metrics.status !== 'CONNECTED') return 'text-blue-200/60';
    if (rtt < 150) return 'text-emerald-300';
    if (rtt <= 300) return 'text-amber-300';
    return 'text-rose-300';
  };

  // ─── Compact floating NET chip (default state) ───────────────────────
  // Never auto-opens the panel; reflects warning/error state visually only.
  if (!metrics.isInspectorOpen) {
    const isError = metrics.status === 'DISCONNECTED';
    const isWarning =
      metrics.status === 'CONNECTING' ||
      metrics.status === 'RECONNECTING' ||
      (metrics.status === 'CONNECTED' && metrics.rttSamplesCount > 0 && metrics.rttCurrent > 150);

    const chipTone = isError
      ? 'bg-rose-500/25 border-rose-300/50'
      : isWarning
        ? 'bg-amber-400/25 border-amber-300/50'
        : 'bg-white/20 border-white/40';

    return (
      <button
        onClick={() => metricsStore.setInspectorOpen(true)}
        className={`absolute bottom-3 right-3 z-40 px-3 py-1.5 rounded-full ${chipTone} backdrop-blur-md text-white text-xs font-bold shadow-lg hover:bg-white/30 transition-all flex items-center gap-2 select-none`}
        title="Mở Network Inspector (Ctrl+Shift+N hoặc `)"
      >
        <span className={`w-2 h-2 rounded-full ${statusBadge.dot}`} />
        <span>⚡ NET</span>
        <span className="text-white/80 text-[11px] font-mono">
          {isError ? 'offline' : metrics.rttSamplesCount > 0 ? `${metrics.rttCurrent}ms` : '—'}
        </span>
      </button>
    );
  }

  // ─── Expanded panel (glass theme, matches game visual language) ──────
  return (
    <div className="absolute bottom-3 right-3 z-40 w-[340px] max-sm:fixed max-sm:inset-x-3 max-sm:bottom-3 max-sm:w-auto max-h-[65vh] flex flex-col rounded-3xl bg-white/15 backdrop-blur-xl border-2 border-white/40 shadow-2xl text-slate-100 text-xs overflow-hidden select-none animate-fadeIn">
      {/* Header */}
      <div className="flex items-center justify-between px-3.5 py-2.5 bg-white/10 border-b border-white/30 shrink-0">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-cyan-300 font-extrabold tracking-wide flex items-center gap-1">
            ⚡ Network Inspector
          </span>
          <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold border flex items-center gap-1 shrink-0 ${statusBadge.bg}`}>
            <span className={`w-1.5 h-1.5 rounded-full ${statusBadge.dot}`} />
            {statusBadge.text}
          </span>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          <button
            onClick={() => metricsStore.reset()}
            className="px-2 py-0.5 rounded-lg bg-white/15 hover:bg-white/30 text-blue-100 hover:text-white text-[10px] font-bold transition border border-white/30"
            title="Reset các bộ đếm telemetry"
          >
            Reset
          </button>
          <button
            onClick={() => metricsStore.setInspectorOpen(false)}
            className="w-5 h-5 flex items-center justify-center rounded-lg hover:bg-white/20 text-blue-100 hover:text-rose-300 text-xs font-bold"
            title="Đóng Inspector (Esc)"
          >
            ✕
          </button>
        </div>
      </div>

      <div className="p-3 space-y-2.5 overflow-y-auto custom-scrollbar">
        {/* Protocol Selector */}
        <div>
          <div className="flex justify-between items-center text-[10px] text-blue-200/70 font-bold uppercase tracking-wider mb-1">
            <span>Protocol</span>
            <span className="text-cyan-300 lowercase text-[9px] font-normal">{metrics.drawingMode.replace('_', ' ')}</span>
          </div>
          <div className="grid grid-cols-3 gap-1 bg-white/10 p-1 rounded-xl border border-white/25">
            {(['BINARY_BATCH', 'JSON_BATCH', 'JSON_POINT'] as DrawingProtocolMode[]).map((mode) => (
              <button
                key={mode}
                onClick={() => metricsStore.setDrawingMode(mode)}
                className={`py-1 px-1 rounded-lg text-[10px] font-bold transition-all text-center ${
                  metrics.drawingMode === mode
                    ? 'bg-gradient-to-r from-primary to-secondary text-white shadow-md'
                    : 'text-blue-100/80 hover:text-white hover:bg-white/20'
                }`}
              >
                {mode === 'BINARY_BATCH' ? 'Binary' : mode === 'JSON_BATCH' ? 'JSON Batch' : 'JSON Point'}
              </button>
            ))}
          </div>
        </div>

        {/* 1. LATENCY — current RTT is the hero metric */}
        <div className="bg-white/10 p-2.5 rounded-2xl border border-white/25 space-y-1.5">
          <div className="flex justify-between items-baseline">
            <span className="text-[10px] text-blue-200/70 font-bold uppercase tracking-wider">Latency</span>
            <div className="flex items-baseline gap-1">
              <span className={`text-2xl font-black tracking-tight leading-none ${getRttColor(metrics.rttCurrent, metrics.rttSamplesCount)}`}>
                {metrics.rttSamplesCount > 0 ? metrics.rttCurrent : '—'}
              </span>
              <span className="text-blue-200/60 text-[10px]">ms</span>
            </div>
          </div>
          <div className="grid grid-cols-3 gap-2 pt-1.5 border-t border-white/20 text-[10px]">
            <div>
              <span className="text-blue-200/60 block text-[9px]">Avg</span>
              <span className="text-white font-bold">
                {metrics.rttSamplesCount > 0 ? `${metrics.rttAvg} ms` : '—'}
              </span>
            </div>
            <div>
              <span className="text-blue-200/60 block text-[9px]">P95</span>
              <span className="text-cyan-300 font-bold">
                {metrics.rttSamplesCount > 0 ? `${metrics.rttP95} ms` : '—'}
              </span>
            </div>
            <div>
              <span className="text-blue-200/60 block text-[9px]">Jitter</span>
              <span className="text-indigo-300 font-bold">
                {metrics.rttSamplesCount > 1 ? `${metrics.jitter} ms` : '—'}
              </span>
            </div>
          </div>
        </div>

        {/* 2. TRAFFIC */}
        <div className="bg-white/10 p-2.5 rounded-2xl border border-white/25 space-y-1.5">
          <div className="flex justify-between items-center text-[10px] text-blue-200/70 font-bold uppercase tracking-wider">
            <span>Traffic</span>
            <button
              onClick={() => setShowDetails((prev) => !prev)}
              className="text-[9px] text-cyan-300/90 hover:text-cyan-200 underline font-normal"
            >
              {showDetails ? 'ẩn tổng' : 'xem tổng'}
            </button>
          </div>
          <div className="grid grid-cols-2 gap-2 text-[10px]">
            <div className="space-y-0.5 bg-white/10 p-1.5 rounded-xl border border-white/20">
              <div className="flex justify-between items-center">
                <span className="text-blue-200/70 text-[9px]">↑ TX</span>
                <span className="text-cyan-300 font-bold">{metrics.txMsgRate} msg/s</span>
              </div>
              <div className="flex justify-between items-center text-blue-100/80">
                <span className="text-[9px]">Băng thông</span>
                <span className="text-white">{formatBytes(metrics.txBandwidthBytesPerSec)}/s</span>
              </div>
              {showDetails && (
                <div className="pt-1 border-t border-white/20 flex justify-between text-blue-200/60 text-[9px]">
                  <span>Tổng TX</span>
                  <span>{formatBytes(metrics.txBytes)} ({metrics.txMessages})</span>
                </div>
              )}
            </div>

            <div className="space-y-0.5 bg-white/10 p-1.5 rounded-xl border border-white/20">
              <div className="flex justify-between items-center">
                <span className="text-blue-200/70 text-[9px]">↓ RX</span>
                <span className="text-emerald-300 font-bold">{metrics.rxMsgRate} msg/s</span>
              </div>
              <div className="flex justify-between items-center text-blue-100/80">
                <span className="text-[9px]">Băng thông</span>
                <span className="text-white">{formatBytes(metrics.rxBandwidthBytesPerSec)}/s</span>
              </div>
              {showDetails && (
                <div className="pt-1 border-t border-white/20 flex justify-between text-blue-200/60 text-[9px]">
                  <span>Tổng RX</span>
                  <span>{formatBytes(metrics.rxBytes)} ({metrics.rxMessages})</span>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* 3. DRAWING STREAM */}
        <div className="bg-white/10 p-2.5 rounded-2xl border border-white/25 space-y-1.5">
          <div className="text-[10px] text-blue-200/70 font-bold uppercase tracking-wider">
            Drawing Stream
          </div>
          <div className="grid grid-cols-2 gap-2 text-[10px]">
            <div className="space-y-0.5">
              <div className="flex justify-between text-blue-100/80">
                <span className="text-[9px]">Batches/s</span>
                <span className="text-cyan-300 font-bold">{metrics.drawBatchesPerSec} /s</span>
              </div>
              <div className="flex justify-between text-blue-100/80">
                <span className="text-[9px]">Đã gửi</span>
                <span className="text-white">{metrics.drawBatchesSent}</span>
              </div>
            </div>
            <div className="space-y-0.5">
              <div className="flex justify-between text-blue-100/80">
                <span className="text-[9px]">Pts/Batch</span>
                <span className="text-white font-bold">{metrics.avgPointsPerBatch}</span>
              </div>
              <div className="flex justify-between text-blue-100/80">
                <span className="text-[9px]">Đã nhận</span>
                <span className="text-white">{metrics.drawBatchesReceived}</span>
              </div>
            </div>
          </div>
          <div className="flex justify-between items-center pt-1.5 border-t border-white/20 text-[10px]">
            <span className="text-blue-200/70 text-[9px]">Sequence gaps</span>
            {/* Zero is a healthy value — calm green, not "missing data" */}
            <span
              className={`font-bold px-1.5 py-0.5 rounded-md text-[10px] ${
                metrics.sequenceGapCount === 0
                  ? 'bg-emerald-400/15 text-emerald-300'
                  : 'bg-amber-400/25 text-amber-200 border border-amber-300/50'
              }`}
            >
              {metrics.sequenceGapCount === 0 ? '0 ✓' : metrics.sequenceGapCount}
            </span>
          </div>
        </div>

        {/* 4. CONNECTION & RELIABILITY */}
        <div className="bg-white/10 p-2.5 rounded-2xl border border-white/25 space-y-1.5 text-[10px]">
          <div className="flex justify-between items-center text-[10px] text-blue-200/70 font-bold uppercase tracking-wider">
            <span>Connection</span>
            <span className="text-[9px] text-blue-200/50 font-mono font-normal normal-case">{metrics.gatewayId}</span>
          </div>
          <div className="grid grid-cols-3 gap-1 text-[10px]">
            <div className="bg-white/10 p-1.5 rounded-xl border border-white/20 text-center">
              <span className="text-blue-200/60 block text-[9px]">Queue</span>
              <span className={`font-bold ${metrics.gatewayQueueSize > 10 ? 'text-amber-300' : 'text-emerald-300'}`}>
                {metrics.gatewayQueueSize}
              </span>
            </div>
            <div className="bg-white/10 p-1.5 rounded-xl border border-white/20 text-center">
              <span className="text-blue-200/60 block text-[9px]">Reconnect</span>
              <span className={`font-bold ${metrics.reconnectCount > 0 ? 'text-amber-300' : 'text-emerald-300'}`}>
                {metrics.reconnectCount}
              </span>
            </div>
            <div className="bg-white/10 p-1.5 rounded-xl border border-white/20 text-center">
              <span className="text-blue-200/60 block text-[9px]">Missed HB</span>
              <span className={`font-bold ${metrics.heartbeatTimeoutCount > 0 ? 'text-rose-300' : 'text-emerald-300'}`}>
                {metrics.heartbeatTimeoutCount}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Footer Hotkey Guide */}
      <div className="px-3 py-1.5 bg-white/10 border-t border-white/30 text-[9px] text-blue-200/70 flex justify-between items-center shrink-0">
        <span>Toggle: <kbd className="px-1 py-0.5 rounded bg-white/20 text-white">Ctrl+Shift+N</kbd> hoặc <kbd className="px-1 py-0.5 rounded bg-white/20 text-white">`</kbd></span>
        <span>Đóng: <kbd className="px-1 py-0.5 rounded bg-white/20 text-white">Esc</kbd></span>
      </div>
    </div>
  );
};
